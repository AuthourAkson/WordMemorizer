package com.example.wordmemorizer.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

object RelatedWordService {
    private val client = OkHttpClient()

    fun fetchDatamuseWords(word: String, relType: String): List<String> {
        val url = "https://api.datamuse.com/words?$relType=$word"
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val array = JSONArray(body)
            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val wordItem = array.getJSONObject(i).optString("word", null)
                if (!wordItem.isNullOrBlank()) {
                    result.add(wordItem)
                }
            }

            Log.d("RelatedWordService", "成功获取 [$relType]：$result")
            result.take(5)
        } catch (e: Exception) {
            Log.e("RelatedWordService", "请求 [$relType] 失败：${e.message}")
            emptyList()
        }
    }

}
