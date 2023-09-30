package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class Ask4MovieProvider : MainAPI() {
    override var mainUrl = "https://ask4movie.mx"

    override var name = "Ask4Movie"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AnimeMovie)
    override val hasMainPage = true

    private fun Element.toSearchResponse(): MovieSearchResponse {
        // style="background-image: url(https://ask4movie.me/wp-content/uploads/2022/08/Your-Name.-2016-cover.jpg)"
        val posterRegex = Regex("""url\((.*?)\)""")
        val poster = posterRegex.find(this.attr("style"))?.groupValues?.get(1)

        val a = this.select("a")
        val href = a.attr("href")
        val title = a.text().trim()

        // Title (2022) -> 2022
        val year =
            Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return MovieSearchResponse(
            title,
            href,
            this@Ask4MovieProvider.name,
            TvType.Movie,
            poster,
            year,
            null,
            null,
            null
        )
    }

    // Used in movies/single seasons to get recommendations
    private fun Element.articleToSearchResponse(): MovieSearchResponse {
        val poster = this.select("img").attr("src")

        val a = this.select("a")
        val href = a.attr("href")
        val title = a.attr("title")

        // Title (2022) -> 2022
        val year =
            Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return MovieSearchResponse(
            title,
            href,
            this@Ask4MovieProvider.name,
            TvType.Movie,
            poster,
            year,
            null,
            null,
            null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.post(
            url,
//            data = mapOf("np_asl_data" to "customset[]=post&customset[]=ct_channel&customset[]=post&customset[]=post&customset[]=post&customset[]=post&asl_gen[]=title&asl_gen[]=exact&qtranslate_lang=0&filters_initial=1&filters_changed=0")
        ).document
        return doc.select("div.item").map {
            it.toSearchResponse()
        }
    }

    private fun getIframe(html: String): String? {
        return Jsoup.parse(html).select("iframe").attr("data-src")
    }

    private suspend fun getEpisodes(iframe: String): List<Episode> {
        val playlistDoc = app.get(iframe).document

        // S04┋E01
        val episodeRegex = Regex("""S(\d+).E(\d+)""")
        return playlistDoc.select("span.episode").mapNotNull { episode ->
            val partialUrl = episode.attr("data-url")
            val fullUrl = "https://${URI(iframe).rawAuthority}$partialUrl"
            val info = episodeRegex.find(episode.text())
            val seasonIndex = info?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeIndex = info?.groupValues?.getOrNull(2)?.toIntOrNull()
            Episode(
                fullUrl,
                episode = episodeIndex,
                season = seasonIndex
            )
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val rows = document.select("div.row")

        val posterRegex = Regex("""url\((.*?)\)""")
        val mappedRows = rows.mapNotNull {
            var isHorizontal = true
            val items = it.select("div.slide-item").map { element ->
                val thumb = element.select("div.item-thumb")
                val poster = posterRegex.find(thumb.attr("style"))?.groupValues?.get(1)
                val href = thumb.select("a").attr("href")
                val title = element.select("div.video-short-intro a").text()
                val year =
                    Regex("""\((\d{4})\)$""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                MovieSearchResponse(title, href, this.name, TvType.Movie, poster, year)
            }.ifEmpty {
                isHorizontal = false
                it.select("div.channel-content.clearfix").map { searchElement ->
                    searchElement.articleToSearchResponse()
                }
            }

            val title = it.select("div.title").text()
            if (title.contains("porn", true) && !settingsForProvider.enableAdult) return@mapNotNull null
            HomePageList(title, items, isHorizontal)
        }
        return HomePageResponse(mappedRows)
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val seasons = document.select("div.item")
        val isSingleVideo = (seasons.isNullOrEmpty())
        val yearRegex = Regex("""\((\d{4})\)$""")

        if (isSingleVideo) {
            val description = document.select("div.custom.video-the-content").text().trim()
            val (title, year) = document.select("a.video-title").text().let {
                it.replace(yearRegex, "") to yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }
            val genres =
                document.selectFirst("div.categories.cactus-info")?.select("a")?.map { it.text() }

            // This is actually a json with all the data, but I opted to just scrape the html
            // Try the json in the future if html turns out bad
            val posterRegex = Regex("""contentUrl['"].*?(http[^"']*)""")
            val poster = posterRegex.find(response.text)?.groupValues?.get(1)
            val recommendations = document.select("div.cactus-sub-wrap article").mapNotNull {
                it.articleToSearchResponse()
            }

            val iframe = getIframe(response.text)
            // It can be a season as a single video iframe!
            return if (iframe?.contains("/p/") == true) {
                val episodes = getEpisodes(iframe)
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    TvType.TvSeries,
                    episodes,
                    poster,
                    year,
                    recommendations = recommendations,
                    tags = genres,
                    plot = description
                )
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, iframe) {
                    this.posterUrl = poster
                    this.tags = genres
                    this.plot = description
                    this.year = year
                    this.recommendations = recommendations
                }
            }
        } else {
            val recommendations = document.select("div.channel.clearfix").mapNotNull {
                it.articleToSearchResponse()
            }

            val descriptionDiv = document.select("div.channel-description")
            val description = descriptionDiv.select("p").firstOrNull()?.text()?.trim()

            val genres = descriptionDiv.select("span.genres").let {
                if (it.isNotEmpty()) it.text().split(",")
                else descriptionDiv.select("p")
                    .firstOrNull { element -> element.text().contains("Genre:") }
                    ?.text()?.substringAfter("Genre:")?.split(",")
            }

            val (title, year) = document.select("h3.channel-name").text().let {
                it.replace(yearRegex, "") to yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }

            val poster = document.select("div.channel-pic > img").attr("src")
            val mappedSeasons = seasons.apmap {
                val href = it.select("div.top-item > a").attr("href")
                val text = app.get(href).text
                val iframe = getIframe(text) ?: return@apmap emptyList()
                getEpisodes(iframe)
            }.flatten()

            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                mappedSeasons,
                poster,
                year,
                recommendations = recommendations,
                tags = genres,
                plot = description
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, this.mainUrl, subtitleCallback, callback)
        return true
    }
}