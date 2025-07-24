package com.example.wordmemorizer.utils

import android.content.Context
import android.net.Uri
import com.example.wordmemorizer.data.ThemeCategory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

object ThemeImporter {
    fun importFromAsset(context: Context, filename: String): List<ThemeCategory> {
        val json = context.assets.open(filename).bufferedReader().use { it.readText() }
        return parseJson(json)
    }

    fun importFromUri(context: Context, uri: Uri): List<ThemeCategory> {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val json = stream.bufferedReader().use(BufferedReader::readText)
            parseJson(json)
        } ?: throw IllegalArgumentException("无法读取文件")
    }

    private fun parseJson(json: String): List<ThemeCategory> {
        return Gson().fromJson(json, object : TypeToken<List<ThemeCategory>>() {}.type)
    }
}