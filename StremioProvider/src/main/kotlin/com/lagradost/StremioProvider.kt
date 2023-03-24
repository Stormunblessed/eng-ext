package com.lagradost

import android.util.Log
import com.lagradost.StremioProvider.Companion.encodeUri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson

import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import java.net.URLEncoder

private const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"

class StremioProvider : MainAPI() {
    override var mainUrl = "https://stremio.github.io/stremio-static-addon-example"
    override var name = "Stremio example"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val res = tryParseJson<Manifest>(app.get("${mainUrl}/manifest.json").text) ?: return null
        val lists = mutableListOf<HomePageList>()
        res.catalogs.forEach { catalog ->
            catalog.toHomePageList(this)?.let {
                lists.add(it)
            }
        }
        return HomePageResponse(
            lists,
            false
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val res = tryParseJson<Manifest>(app.get("${mainUrl}/manifest.json").text) ?: return null
        val list = mutableListOf<SearchResponse>()
        res.catalogs.forEach { catalog ->
            list.addAll(catalog.search(query, this))
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse? {
        val res = tryParseJson<CatalogEntry>(url) ?: throw RuntimeException(url)
        return res.toLoadResponse(this)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = tryParseJson<StreamsResponse>(app.get(data).text) ?: return false
        res.streams.forEach { stream ->
            stream.runCallback(subtitleCallback, callback)
        }

        return true
    }

    private data class Manifest(val catalogs: List<Catalog>)
    private data class Catalog(
        var name: String?,
        val id: String,
        val type: String?,
        val types: MutableList<String> = mutableListOf()
    ) {
        init {
            if (type != null) types.add(type)
        }

        suspend fun search(query: String, provider: StremioProvider): List<SearchResponse> {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { type ->
                val res = tryParseJson<CatalogResponse>(app.get("${provider.mainUrl}/catalog/${type.encodeUri()}/${id.encodeUri()}/search=${query.encodeUri()}.json").text) ?: return@forEach
                res.metas.forEach {  entry ->
                    entries.add(entry.toSearchResponse(provider))
                }
            }
            return entries
        }

        suspend fun toHomePageList(provider: StremioProvider): HomePageList? {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { type ->
                val res = tryParseJson<CatalogResponse>(app.get("${provider.mainUrl}/catalog/${type.encodeUri()}/${id.encodeUri()}.json").text) ?: return@forEach
                res.metas.forEach {  entry ->
                    entries.add(entry.toSearchResponse(provider))
                }
            }
            return HomePageList(
                name ?: id,
                entries
            )
        }
    }

    private data class CatalogResponse(val metas: List<CatalogEntry>)
    private data class CatalogEntry(
        val name: String,
        val id: String,
        val poster: String?,
        val description: String?,
        val type: String?,
        val videos: List<Video>?
    ) {
        fun toSearchResponse(provider: StremioProvider): SearchResponse {
            return provider.newMovieSearchResponse(
                name,
                this.toJson(),
                TvType.Others
            ) {
                posterUrl = poster
            }
        }
        suspend fun toLoadResponse(provider: StremioProvider): LoadResponse {
            if (videos == null || videos.isEmpty()) {
                return provider.newMovieLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type?.encodeUri()}/${id.encodeUri()}.json",
                    TvType.Others,
                    "${provider.mainUrl}/stream/${type?.encodeUri()}/${id.encodeUri()}.json"
                ) {
                    posterUrl = poster
                    plot = description
                }
            } else {
                return provider.newTvSeriesLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type?.encodeUri()}/${id.encodeUri()}.json",
                    TvType.Others,
                    videos.map {
                        it.toEpisode(provider, type)
                    }
                ) {
                    posterUrl = poster
                    plot = description
                }
            }

        }
    }

    private data class Video(val id: String, val title: String?, val thumbnail: String?, val overview: String?) {
        fun toEpisode(provider: StremioProvider, type: String?): Episode {
            return provider.newEpisode(
                "${provider.mainUrl}/stream/${type?.encodeUri()}/${id.encodeUri()}.json"
            ) {
                this.name = title
                this.posterUrl = thumbnail
                this.description = overview
            }
        }
    }

    private data class StreamsResponse(val streams: List<Stream>)
    private data class Stream(
        val name: String?,
        val title: String?,
        val url: String?,
        val ytId: String?,
        val externalUrl: String?,
        val behaviorHints: JSONObject?,
        val infoHash: String?,
        val sources: List<String> = emptyList()
    ) {
        suspend fun runCallback(subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            if (url != null) {
                var referer: String? = null
                try {
                    val headers = ((behaviorHints?.get("proxyHeaders") as? JSONObject)
                        ?.get("request") as? JSONObject)
                    referer = headers?.get("referer") as? String ?: headers?.get("origin") as? String
                } catch (ex: Throwable) {
                    Log.e("Stremio", Log.getStackTraceString(ex))
                }

                if (url.endsWith(".m3u8")) {
                    callback.invoke(
                        ExtractorLink(
                        name ?: "",
                        title ?: name ?: "",
                        url,
                            referer ?: "",
                        Qualities.Unknown.value,
                        isM3u8 = true
                    ))
                } else {
                    callback.invoke(
                        ExtractorLink(
                            name ?: "",
                            title ?: name ?: "",
                            url,
                            referer ?: "",
                            Qualities.Unknown.value,
                            isM3u8 = false
                        ))
                }
            }
            if (ytId != null) {
                loadExtractor("https://www.youtube.com/watch?v=$ytId", subtitleCallback, callback)
            }
            if (externalUrl != null) {
                loadExtractor(externalUrl, subtitleCallback, callback)
            }
            if (infoHash != null) {
                val resp = app.get(TRACKER_LIST_URL).text
                val otherTrackers = resp
                    .split("\n")
                    .filterIndexed{i, s -> i%2==0}
                    .filter{s -> !s.isNullOrEmpty()}
                    .map{it -> "&tr=$it"}
                    .joinToString("")
                
                val sourceTrackers = sources
                    .filter{it->it.startsWith("tracker:")}
                    .map{it->it.removePrefix("tracker:")}
                    .filter{s -> !s.isNullOrEmpty()}
                    .map{it -> "&tr=$it"}
                    .joinToString("")

                val magnet = "magnet:?xt=urn:btih:${infoHash}${sourceTrackers}${otherTrackers}"
                callback.invoke(
                    ExtractorLink(
                        name ?: "",
                        title ?: name ?: "",
                        magnet,
                        "",
                        Qualities.Unknown.value
                    )
                )
            }
        }
    }

    companion object {
        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
    }
}
