package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

public object CaptchaSolver {
    suspend fun predictFace(url: String): String? {
        val img = "data:image/jpeg;base64," + base64Encode(app.get(url).body.bytes())
        val reqData = HFRequest(listOf(img)).toJson()
        val res = app.post("https://yuqi-gender-classifier.hf.space/api/queue/push/", json = reqData).text
        val request = tryParseJson<JSONObject>(res)
        for (i in 1..5) {
            delay(500L)
            val document = app.post("https://yuqi-gender-classifier.hf.space/api/queue/status/", json=request?.toJson()).text
            val status = tryParseJson<JSONObject>(document)
            if (status?.get("status") != "COMPLETE") continue
            return (((status.get("data") as? JSONObject?)
                ?.get("data") as? JSONArray?)
                ?.get(0) as? JSONObject?)
                ?.get("label") as String?
        }
        return null
    }

    private data class HFRequest(
        val data: List<String>,
        val action: String = "predict",
        val fn_index: Int = 0,
        val session_hash: String = "aaaaaaaaaaa"
    )
}