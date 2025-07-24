package com.example.wordmemorizer.ui

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.data.WordNetUtils
import com.example.wordmemorizer.network.ApiService
import com.example.wordmemorizer.network.RelatedWordService
import com.example.wordmemorizer.utils.IpaGenerator
import kotlinx.coroutines.*
import org.json.JSONArray

class AddWordActivity : AppCompatActivity() {

    private var currentRelatedWords: List<String> = emptyList()
    private var currentSynonyms: List<String> = emptyList()
    private var currentAntonyms: List<String> = emptyList()
    private var currentPronunciation: String = ""
    private var currentRoot: String? = null
    private var currentPartOfSpeech: String? = null

    private lateinit var inputWord: EditText
    private lateinit var textDefinition: TextView
    private lateinit var textExample: TextView
    private lateinit var buttonSave: Button
    private lateinit var buttonGenerate: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_word)

        inputWord = findViewById(R.id.inputWord)
        textDefinition = findViewById(R.id.textDefinition)
        textExample = findViewById(R.id.textExample)
        buttonGenerate = findViewById(R.id.buttonGenerate)
        buttonSave = findViewById(R.id.buttonSave)

        buttonGenerate.setOnClickListener {
            val word = inputWord.text.toString()
            if (word.isNotEmpty()) {
                buttonGenerate.isEnabled = false
                buttonGenerate.text = "生成中..."

                ApiService.getWordExplanation(
                    word = word,
                    callback = { definition, example ->
                        scope.launch {
                            // 并发启动
                            val ipaDeferred = async(Dispatchers.IO) {
                                IpaGenerator.generate(word)
                            }
                            val synonymsDeferred = async(Dispatchers.IO) {
                                RelatedWordService.fetchDatamuseWords(word, "rel_syn")
                            }
                            val antonymsDeferred = async(Dispatchers.IO) {
                                RelatedWordService.fetchDatamuseWords(word, "rel_ant")
                            }
                            val relatedDeferred = async(Dispatchers.IO) {
                                RelatedWordService.fetchDatamuseWords(word, "ml")
                            }

                            // 等待结果
                            currentPronunciation = ipaDeferred.await() ?: ""
                            currentSynonyms = synonymsDeferred.await()
                            currentAntonyms = antonymsDeferred.await()
                            currentRelatedWords = relatedDeferred.await()

                            // 推断词根词性
                            currentRoot = WordNetUtils.extractRootWord(word)
                            currentPartOfSpeech = WordNetUtils.guessPartOfSpeech(word)

                            textDefinition.text = definition
                            textExample.text = example

                            Toast.makeText(
                                this@AddWordActivity,
                                "生成完毕，可保存单词",
                                Toast.LENGTH_SHORT
                            ).show()
                            buttonGenerate.isEnabled = true
                            buttonGenerate.text = "生成翻译和例句"
                        }
                    },
                    errorCallback = { error ->
                        runOnUiThread {
                            Toast.makeText(this, "AI解释获取失败：$error", Toast.LENGTH_LONG).show()
                            buttonGenerate.isEnabled = true
                            buttonGenerate.text = "生成翻译和例句"
                        }
                    }
                )
            }
        }

        buttonSave.setOnClickListener {
            val word = Word(
                word = inputWord.text.toString(),
                definition = textDefinition.text.toString(),
                example = textExample.text.toString().split("\n"),
                pronunciation = currentPronunciation,
                relatedWords = currentRelatedWords,
                synonyms = currentSynonyms,
                antonyms = currentAntonyms,
                rootWord = currentRoot,
                partOfSpeech = currentPartOfSpeech
            )
            WordRepository.saveWord(this, word)
            Toast.makeText(this, "单词已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // 取消协程
    }
}

