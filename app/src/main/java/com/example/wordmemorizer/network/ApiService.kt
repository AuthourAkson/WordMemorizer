package com.example.wordmemorizer.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object ApiService {
    private const val API_BASE_URL = "https://api.siliconflow.cn"
    private const val API_KEY = "sk-twtkdgchnpukyandmvwmxtbbpgbuwijviubzirofitkqoyzs"

    private val client = OkHttpClient()

    fun getWordExplanation(
        word: String,
        callback: (definition: String, example: String) -> Unit,
        errorCallback: (error: String) -> Unit
    ) {
        val url = "$API_BASE_URL/v1/chat/completions"

        val jsonMessages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", """
            你是一个专业的英语单词解释器，请严格按照以下格式返回结果：
            
            【释义】
            [单词的英文释义，用简洁的中文解释]
            
            【例句】
            1. [例句1英文]  
               （例句1中文翻译）
            2. [例句2英文]  
               （例句2中文翻译）
            3. [例句3英文]  
               （例句3中文翻译）
            【相关词】
            - 同义词：[词1, 词2]
            - 近义词：[词3, 词4]
            - 派生词：[词5]
            
            注意：
            - 释义和例句之间用空行分隔
            - 例句编号后跟英文例句，换行后是对应的中文翻译
            - 不要添加额外说明
            - 例句最多举上三句
            """.trimIndent())
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "请解释这个词并给出例句：$word")
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", "Pro/deepseek-ai/DeepSeek-V3")
            put("stream", false)
            put("messages", jsonMessages)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorCallback(e.message ?: "网络请求失败")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseStr = response.body?.string()
                    try {
                        val root = JSONObject(responseStr!!)
                        val content = root
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                        // 解析释义（匹配【释义】和【例句】之间的内容）
                        val definition = content
                            .substringAfter("【释义】")
                            .substringBefore("【例句】")
                            .trim()
                            .replace("\n", "")

                        val example = content
                            .substringAfter("【例句】")
                            .substringBefore("【相关词】")
                            .trim()

                        val relatedSection = content
                            .substringAfter("【相关词】", "")
                            .trim()

                        val relatedWords = Regex("\\[(.*?)\\]")
                            .findAll(relatedSection)
                            .flatMap { it.groupValues[1].split(",") }
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toList()

                        callback(definition, example )
                    } catch (e: Exception) {
                        errorCallback("解析错误: ${e.message}")
                    }
                }
            }
        })
    }
}
