package com.example.wordmemorizer.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object IpaGenerator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun generate(word: String): String? {
        return try {
            val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${word.lowercase()}"
            Log.d("IPA", "请求URL: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WordMemorizer/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            Log.d("IPA", "响应状态: ${response.code}, 响应体: ${body?.take(200)}...")

            when {
                !response.isSuccessful -> {
                    Log.w("IPA", "API请求失败: ${response.code}")
                    null
                }
                body == null -> {
                    Log.w("IPA", "响应体为空")
                    null
                }
                else -> parseIpaFromJson(body).also {
                    Log.d("IPA", "解析结果: $it")
                }
            }
        } catch (e: Exception) {
            Log.e("IPA", "获取音标异常: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun parseIpaFromJson(json: String): String? {
        return try {
            val jsonArray = JSONArray(json)
            val firstEntry = jsonArray.getJSONObject(0)

            // 尝试获取一级 phonetic 字段
            firstEntry.optString("phonetic")
                .takeIf { it.isNotBlank() }
                ?: run {
                    // fallback 到 phonetics 数组
                    val phonetics = firstEntry.optJSONArray("phonetics")
                    (0 until phonetics.length())
                        .mapNotNull { i -> phonetics.getJSONObject(i).optString("text") }
                        .firstOrNull { it.isNotBlank() }
                }
        } catch (e: Exception) {
            Log.e("IPA", "解析JSON失败: ${e.message}")
            null
        }
    }
}
