package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.util.*


class WatchCartoonOnlineProvider : MainAPI() {
    override var name = "WatchCartoonOnline"
    override var mainUrl = "https://www.wcostream.net"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val rows = doc.select("div.recent-release").mapNotNull {
            val rowName = it.text()
            val parent = it.parent() ?: return@mapNotNull null
            val list = parent.select("ul.items > li").map { item ->
                val link = item.select("a").attr("href")
                val name = item.text()
                newTvSeriesSearchResponse(
                    name, link
                ) {
                    this.posterUrl = fixUrl(item.select("img").attr("src"))
                }
            }
            HomePageList(rowName, list)
        }
        return HomePageResponse(rows, false)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search"

        val seriesDocument =
            app.post(
                url,
                headers = mapOf("Referer" to url),
                data = mapOf("catara" to query, "konuara" to "series")
            ).document

        val series = seriesDocument.select("div#blog > div.cerceve").toList().mapNotNull { item ->
            val header = item.selectFirst("> div.iccerceve") ?: return@mapNotNull null
            val titleHeader = header.selectFirst("> div.aramadabaslik > a")
            val title = titleHeader!!.text()
            val href = fixUrl(titleHeader.attr("href"))
            val poster = fixUrl(header.selectFirst("> a > img")!!.attr("src"))
            val genreText = item.selectFirst("div.cerceve-tur-ve-genre")!!.ownText()
            if (genreText.contains("cartoon")) {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Cartoon,
                    poster,
                    null,
                    null
                )
            } else {
                val isDubbed = genreText.contains("dubbed")
                val set: EnumSet<DubStatus> =
                    EnumSet.of(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed)
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    poster,
                    null,
                    set,
                )
            }
        }

        // "episodes-search", is used for finding movies, anime episodes should be filtered out
        val episodesDocument =
            app.post(
                url,
                headers = mapOf("Referer" to url),
                data = mapOf("catara" to query, "konuara" to "episodes")
            ).document
        val items = episodesDocument.select("#catlist-listview2 > ul > li")
            // Filter away episodes and blanks
            .filterNot { element ->
                element.text().contains("Episode") || element.text().isNullOrBlank()
            }

        val episodes = items.mapNotNull { item ->
            val titleHeader = item.selectFirst("a") ?: return@mapNotNull null
            val title = titleHeader.text()
            val href = fixUrl(titleHeader.attr("href"))
            //val isDubbed = title.contains("dubbed")
            //val set: EnumSet<DubStatus> =
            //   EnumSet.of(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed)
            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.AnimeMovie,
                null,
                null,
                null,
            )
        }

        return series + episodes
    }

    override suspend fun load(url: String): LoadResponse {
        val isSeries = url.contains("/anime/")
        val episodeRegex = Regex("""-episode-\d+""")
        val document = app.get(url).document

        // Retries link loading when an episode page is opened, usually from home.
        // The slug system is too unpredictable to skip this step.
        if (url.contains(episodeRegex)) {
            val link = document.select("div.ildate > a").attr("href")
            // Must not contain the episode regex, otherwise infinite recursion.
            if (!link.contains(episodeRegex)) {
                return load(link)
            }
        }

        return if (isSeries) {
            val title = document.selectFirst("td.vsbaslik > h2")!!.text()
            val poster =
                fixUrlNull(document.selectFirst("div#cat-img-desc > div > img")?.attr("src"))
            val plot = document.selectFirst("div.iltext")!!.text()
            val genres = document.select("div#cat-genre > div.wcobtn > a").map { it.text() }
            val episodes = document.select("div#catlist-listview > ul > li > a").reversed().map {
                val text = it.text()
                val match = Regex("""Season (\d*) Episode (\d*).*? (.*)""").find(text)
                val href = it.attr("href")
                if (match != null) {
                    val last = match.groupValues[3]
                    return@map Episode(
                        href,
                        if (last.startsWith("English")) null else last,
                        match.groupValues[1].toIntOrNull(),
                        match.groupValues[2].toIntOrNull(),
                    )
                }
                val match2 = Regex("""Episode (\d*).*? (.*)""").find(text)
                if (match2 != null) {
                    val last = match2.groupValues[2]
                    return@map Episode(
                        href,
                        if (last.startsWith("English")) null else last,
                        1,
                        match2.groupValues[1].toIntOrNull(),
                    )
                }
                return@map Episode(
                    href,
                    text
                )
            }
            TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodes,
                poster,
                null,
                plot,
                null,
                null,
                tags = genres
            )
        } else {
            val title = document.selectFirst("td.ilxbaslik8")?.text().toString()

            newMovieLoadResponse(
                title,
                url,
                TvType.AnimeMovie,
                url
            ) {
                plot = document.select("div.iltext").text()
                    .substringAfter("Episode Description:")
                    .substringBefore("Share:")
            }
        }
    }

    data class LinkResponse(
        //  @JsonProperty("cdn")
        //  val cdn: String,
        @JsonProperty("enc") val enc: String,
        @JsonProperty("hd") val hd: String,
        @JsonProperty("fhd") val fhd: String,
        @JsonProperty("server") val server: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val src = document.select("iframe#frameNewAnimeuploads0").attr("src").ifBlank { null } ?: mainWork {
            val foundJS = document.select("div.iltext > script").html()

            // Find the variable name, eg: var HAi = "";
            val varRegex = Regex("""var (\S*)""")

            val varName = varRegex.find(foundJS)?.groupValues?.get(1)
                ?: throw RuntimeException("Cannot find var name!")

            // Rhino needs to be on main
            val rhino = Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()

            val decodeBase64 = "atob = function(s) {\n" +
                    "    var e={},i,b=0,c,x,l=0,a,r='',w=String.fromCharCode,L=s.length;\n" +
                    "    var A=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\";\n" +
                    "    for(i=0;i<64;i++){e[A.charAt(i)]=i;}\n" +
                    "    for(x=0;x<L;x++){\n" +
                    "        c=e[s.charAt(x)];b=(b<<6)+c;l+=6;\n" +
                    "        while(l>=8){((a=(b>>>(l-=8))&0xff)||(x<(L-2)))&&(r+=w(a));}\n" +
                    "    }\n" +
                    "    return r;\n" +
                    "};"
            val documentJs = """
                document = { write: function () {} };
            """.trimIndent()
            rhino.evaluateString(scope, documentJs + decodeBase64 + foundJS, "JavaScript", 1, null)

            val jsEval = scope.get(varName, scope)?.toString()
                ?: throw RuntimeException("Cannot get the Rhino scope variable")
            val url =
                Regex("src=\"(.*?)\"").find(jsEval)?.groupValues?.get(1) ?: return@mainWork null
            fixUrl(url)
        } ?: return false

        val embedResponse = app.get(
            src,
            headers = mapOf("Referer" to src)
        )

        val getVidLink = fixUrl(
            Regex("getJSON\\(\"(.*?)\"").find(embedResponse.text)?.groupValues?.get(1)
                ?: return false
        )

        val link = app.get(
            getVidLink, headers = mapOf(
                "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-origin",
                "accept" to "*/*",
                "x-requested-with" to "XMLHttpRequest",
                "referer" to src.replace(" ", "%20"),
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "cookie" to "countrytabs=0"
            )
        ).parsed<LinkResponse>()

        fun loadLink(server: String, link: String?, quality: Qualities) {
            if (link.isNullOrBlank()) return
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "${server}/getvid?evid=${link}",
                    "",
                    quality.value
                )
            )
        }

        loadLink(link.server, link.hd, Qualities.P720)
        loadLink(link.server, link.enc, Qualities.P480)
        loadLink(link.server, link.fhd, Qualities.P1080)

        return true
    }
}
