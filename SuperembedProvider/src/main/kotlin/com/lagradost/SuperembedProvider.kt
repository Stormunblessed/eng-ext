package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import android.util.Log

class SuperembedProvider : TmdbProvider() {
    override var mainUrl = "https://seapi.link"
    override val apiName = "Superembed"
    override var name = "Superembed"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = tryParseJson<TmdbLink>(data) ?: return false
        val tmdbId = mappedData?.tmdbID ?: return false

        val document = app.get(
            if (mappedData.season == null || mappedData.episode == null) "https://seapi.link/?type=tmdb&id=${tmdbId}&max_results=1"
            else "https://seapi.link/?type=tmdb&id=${tmdbId}&season=${mappedData.season}&episode=${mappedData.episode}&max_results=1"
        ).text
        val response = tryParseJson<ApiResponse>(document) ?: return false

        response.results.forEach {
            it.getIframeContents()?.let { it1 ->
                loadExtractor(it1, subtitleCallback, callback)
            }
        }

        return true
    }

    private data class ApiResponse(
        val results: List<ApiResultItem>
    )

    private data class ApiResultItem(
        val server: String,
        val title: String,
        val quality: String,
        val size: Int,
        val url: String
    ) {
        suspend fun getIframeContents(): String? {
            var document = app.get(url)
            for (i in 1..5) {
                if ("captcha-message" in document.text) {
                    val soup = document.document
                    val prompt = soup.selectFirst(".captcha-message")?.text() ?: continue
                    val captchaId = soup.selectFirst("input[name=\"captcha_id\"]")?.attr("value") ?: continue
                    val promptGender = if ("female" in prompt) "female" else "male"
                    val checkboxes = soup.select(".captcha-checkbox").mapNotNull { it -> 
                        val img = it.selectFirst("img")?.attr("src") ?: return@mapNotNull null
                        val gender = CaptchaSolver.predictFace("https://streamembed.net${img}") ?: return@mapNotNull null
                        if (gender != promptGender) return@mapNotNull null
                        return@mapNotNull it.selectFirst("input")?.attr("value")
                    }
                    val formData = FormBody.Builder().apply {
                        add("captcha_id", captchaId)
                        checkboxes.forEach { check ->
                            add("captcha_answer[]", check)
                        }
                    }.build()
                    document = app.post(url, requestBody=formData)
                } else { break }
            }
            val regex = "<iframe[^+]+\\+(?:window\\.)?atob\\(['\"]([-A-Za-z0-9+/=]+)".toRegex()
            val encoded = regex.find(document.text)?.groupValues?.get(1) ?: return null
            return base64Decode(encoded)
        }
    }
}
