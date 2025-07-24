// ThemeRepository.kt
package com.example.wordmemorizer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object ThemeRepository {
    private const val THEMES_FILE_NAME = "themes.json" // 定義用於持久化的文件名
    private var themeList: MutableList<ThemeCategory> = mutableListOf()
    private val themeWordMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    // 新增的初始化方法，用於應用啟動時加載主題
    fun init(context: Context) {
        if (themeList.isEmpty()) { // 僅在列表為空時加載，避免重複操作
            loadThemesFromJson(context)
            if (themeList.isEmpty()) { // 如果加載後仍然為空，則初始化默認數據
                // 可以選擇從 assets 導入默認主題，或者直接在代碼中定義
                // 例如：從 assets 導入預設主題
                // val defaultThemes = ThemeImporter.importFromAsset(context, "default_themes.json")
                // themeList.addAll(defaultThemes)

                // 或者直接定義一些初始主題
                themeList.addAll(getDefaultThemes()) // 使用我們新增的默認主題
                saveThemesToJson(context) // 保存初始主題到文件
            }
        }
        rebuildWordMap() // 無論從哪裡加載，都重建 word map
    }

    fun getAllThemes(): List<ThemeCategory> = themeList.toList()

    // 這個方法現在不僅僅是添加，還會觸發保存
    fun addThemes(newThemes: List<ThemeCategory>, context: Context) {
        themeList.clear()
        themeList.addAll(newThemes)
        rebuildWordMap()
        saveThemesToJson(context) // 添加新主題後保存
    }

    fun hasThemes(): Boolean = themeList.isNotEmpty()

    fun getThemeForWord(word: String): List<String>? {
        return themeWordMap[word.lowercase()]?.toList()
    }

    private fun rebuildWordMap() {
        themeWordMap.clear()
        themeList.forEach { theme ->
            theme.relatedWords.forEach { word ->
                val lowerWord = word.lowercase()
                themeWordMap.getOrPut(lowerWord) { mutableListOf() }.add(theme.theme)
            }
        }
    }

    // 將主題列表保存到 JSON 文件
    private fun saveThemesToJson(context: Context) {
        try {
            context.openFileOutput(THEMES_FILE_NAME, Context.MODE_PRIVATE).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    Gson().toJson(themeList, writer)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 可以在這裡添加日誌或錯誤處理
        }
    }

    // 從 JSON 文件加載主題列表
    private fun loadThemesFromJson(context: Context) {
        try {
            val file = File(context.filesDir, THEMES_FILE_NAME)
            if (file.exists()) {
                context.openFileInput(THEMES_FILE_NAME).use { fis ->
                    InputStreamReader(fis).use { reader ->
                        val listType = object : TypeToken<MutableList<ThemeCategory>>() {}.type
                        val loadedThemes: MutableList<ThemeCategory>? = Gson().fromJson(reader, listType)
                        if (loadedThemes != null) {
                            themeList.clear()
                            themeList.addAll(loadedThemes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 文件可能損壞或格式不正確，重置主題列表
            themeList.clear()
        }
    }

    // 提供一個包含至少四個主題的默認數據集
    private fun getDefaultThemes(): List<ThemeCategory> {
        return listOf(
            ThemeCategory("校园", listOf("blackboard", "teacher", "student", "classroom", "desk", "book")),
            ThemeCategory("家庭", listOf("mother", "father", "kitchen", "sofa", "bedroom", "dinner")),
            ThemeCategory("自然", listOf("tree", "river", "mountain", "cloud", "flower", "sun")),
            ThemeCategory("交通", listOf("car", "bus", "train", "airplane", "station", "driver")),
            ThemeCategory("動物", listOf("cat", "dog", "bird", "fish", "elephant", "lion")),
            ThemeCategory("食物", listOf("apple", "banana", "bread", "milk", "cheese", "water"))
        )
    }
}