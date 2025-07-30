//WordRepository.kt
package com.example.wordmemorizer.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.example.wordmemorizer.utils.IpaGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.Calendar

data class ImportResult(val total: Int, val withIPA: Int, val failedIPA: Int)

object WordRepository {
    private const val PREF_NAME = "word_data"
    private const val KEY_WORD_LIST = "words"
    private const val KEY_LAST_FILL_IN_BLANK_GEN_DATE = "last_fill_in_blank_gen_date"
    private val gson: Gson = GsonBuilder().serializeNulls().create()

    private val wordCache: ConcurrentHashMap<String, Word> = ConcurrentHashMap()
    private var currentFillInBlankSessionWords: MutableList<Word> = mutableListOf()

    fun init(context: Context) {
        val words = getWords(context)
        wordCache.clear()
        words.forEach { word ->
            wordCache[word.word.lowercase()] = word
        }
    }

    fun ensureFillInBlankWordsForToday(context: Context) {
        val today = Calendar.getInstance()
        clearTime(today)

        val lastGenDateMillis = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FILL_IN_BLANK_GEN_DATE, 0L)
        val lastGenCalendar = Calendar.getInstance().apply { timeInMillis = lastGenDateMillis }
        clearTime(lastGenCalendar)

        if (currentFillInBlankSessionWords.isEmpty() || lastGenCalendar.before(today)) {
            Log.d("WordRepoFlow", "Condition met: Generating new fill-in-blank words.")
            generateFillInBlankWords(context) // <-- 這裡調用
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
                putLong(KEY_LAST_FILL_IN_BLANK_GEN_DATE, today.timeInMillis)
                Log.d("WordRepoFlow", "Updated KEY_LAST_FILL_IN_BLANK_GEN_DATE to: ${today.time}")
            }
        } else {
            Log.d("WordRepoFlow", "Condition NOT met: Fill-in-blank words already generated for today OR list not empty.")
        }
        Log.d("WordRepoFlow", "--- Exiting ensureFillInBlankWordsForToday ---")
    }

    // 這個是唯一的 generateFillInBlankWords 函數定義
    private fun generateFillInBlankWords(context: Context) {
        Log.d("WordRepoFlow", "--- Entering generateFillInBlankWords ---")
        val allWords = getWords(context)

        val sevenDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            clearTime(this)
        }.timeInMillis

        Log.d("WordRepoFlow", "Seven days ago (cleared): ${Calendar.getInstance().apply { timeInMillis = sevenDaysAgo }.time}")

        val candidateWords = allWords.filter { word ->
            val hasExamples = word.fillInTheBlankExamples.isNotEmpty()
            val notReviewedRecently = word.lastFillInBlankReviewDate < sevenDaysAgo
            Log.d("WordRepoFilter", "Word: ${word.word}, hasExamples: $hasExamples, notReviewedRecently: $notReviewedRecently, lastReviewDate: ${Calendar.getInstance().apply { timeInMillis = word.lastFillInBlankReviewDate }.time}")
            hasExamples && notReviewedRecently
        }.toMutableList()

        candidateWords.shuffle()
        val wordsForToday = candidateWords.take(10)

        currentFillInBlankSessionWords.clear()
        currentFillInBlankSessionWords.addAll(wordsForToday)
 }
    private fun clearTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    fun saveWord(context: Context, word: Word) {
        val list = getWords(context).toMutableList()
        val existingIndex = list.indexOfFirst { it.word.equals(word.word, ignoreCase = true) }

        if (existingIndex != -1) {
            list[existingIndex] = word
        } else {
            list.add(word)
        }
        saveAllWords(context, list)
        wordCache[word.word.lowercase()] = word
    }

    fun getWords(context: Context): List<Word> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WORD_LIST, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<Word>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("WordRepository", "解析单词列表失败", e)
            emptyList()
        }
    }

    fun updateWord(context: Context, updatedWord: Word) {
        val list = getWords(context).toMutableList()
        val index = list.indexOfFirst { it.word == updatedWord.word }
        if (index != -1) {
            list[index] = updatedWord
            saveAllWords(context, list)
            wordCache[updatedWord.word.lowercase()] = updatedWord
        }
    }
    

    fun getAllWordsMap(): Map<String, Word> {
        return wordCache
    }

    suspend fun importWordsFromJson(context: Context, json: String): ImportResult {
        return try {
            val type = object : TypeToken<List<Word>>() {}.type
            val words: List<Word> = gson.fromJson(json, type) ?: emptyList()

            var ipaSuccess = 0
            var ipaFail = 0

            words.forEach { word ->
                // *** 修改點這裡開始 ***
                var generatedIpa: String? = null
                // 檢查單詞是否已經有音標
                if (word.pronunciation.isNullOrBlank()) { // 如果沒有音標，才去請求
                    generatedIpa = withContextOrNull { IpaGenerator.generate(word.word) }
                    if (generatedIpa != null && generatedIpa.isNotBlank()) {
                        ipaSuccess++
                    } else {
                        ipaFail++
                    }
                } else {
                    // 如果單詞本身已經有音標，則無需請求 API，直接計為成功（或者你也可以選擇不計入成功數）
                    generatedIpa = word.pronunciation // 使用現有的音標
                    ipaSuccess++ // 將已有的音標也計入成功獲取
                }
                // *** 修改點這裡結束 ***

                saveWord(context, word.apply {
                    if (this.nextReviewDate == 0L) this.nextReviewDate = System.currentTimeMillis()
                    if (this.easinessFactor == 0.0) this.easinessFactor = 2.5
                    if (this.interval == 0) this.interval = 1
                    // 使用生成的音標，如果為空則保留原有的空字符串
                    this.pronunciation = generatedIpa ?: ""
                })
            }
            init(context) // 在所有單詞保存後，重新初始化緩存
            ImportResult(words.size, ipaSuccess, ipaFail)
        } catch (e: Exception) {
            Log.e("WordRepository", "导入失败: ${e.message}", e)
            throw IllegalArgumentException("无效的JSON格式: ${e.message?.take(50)}...")
        }
    }

    suspend fun <T> withContextOrNull(block: suspend () -> T): T? = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: Exception) {
        Log.e("IPA", "音标生成异常: ${e.message}", e)
        null
    }

    private fun saveAllWords(context: Context, words: List<Word>) {
        val json = gson.toJson(words)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_WORD_LIST, json)
        }
    }
}