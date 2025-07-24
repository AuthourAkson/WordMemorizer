package com.example.wordmemorizer.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.TodayReviewCache
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import kotlin.random.Random
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefinitionQuizActivity : AppCompatActivity() {

    private lateinit var textViewWord: TextView
    private lateinit var buttonOptionA: Button
    private lateinit var buttonOptionB: Button
    private lateinit var buttonOptionC: Button
    private lateinit var buttonOptionD: Button

    private var quizWords: MutableList<Word> = mutableListOf()
    private var currentWordIndex = 0
    private var correctAnswerDefinition: String = ""

    private val optionButtons: MutableList<Button> by lazy {
        mutableListOf(buttonOptionA, buttonOptionB, buttonOptionC, buttonOptionD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_definition_quiz)

        textViewWord = findViewById(R.id.textViewWord)
        buttonOptionA = findViewById(R.id.buttonOptionA)
        buttonOptionB = findViewById(R.id.buttonOptionB)
        buttonOptionC = findViewById(R.id.buttonOptionC)
        buttonOptionD = findViewById(R.id.buttonOptionD)

        // 從 TodayReviewCache 獲取今日復習單詞
        val allReviewWords = TodayReviewCache.getWords()

        // 篩選條件：單詞復習間隔為 10-20 天，且有定義
        quizWords = allReviewWords.filter { word ->
            word.definition.isNotBlank() && word.interval >= 10 && word.interval <= 20
        }.toMutableList()

        if (quizWords.isEmpty()) {
            Toast.makeText(this, "今日沒有符合釋義辨析條件的單詞（需有定義且復習間隔在10-20天之間）。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 隨機打亂題目順序
        quizWords.shuffle()

        setupOptionListeners()
        displayNextQuiz()
    }

    private fun setupOptionListeners() {
        optionButtons.forEach { button ->
            button.setOnClickListener {
                onOptionSelected(button.text.toString())
            }
        }
    }

    private fun displayNextQuiz() {
        // 重置所有選項按鈕的顏色
        optionButtons.forEach { it.setBackgroundResource(android.R.drawable.btn_default) }

        if (currentWordIndex >= quizWords.size) {
            Toast.makeText(this, "所有釋義辨析題目已完成！", Toast.LENGTH_LONG).show()
            TodayReviewCache.clear()
            finish()
            return
        }

        val currentWord = quizWords[currentWordIndex]
        textViewWord.text = currentWord.word
        correctAnswerDefinition = currentWord.definition

        val options = generateQuizOptions(currentWord)

        val shuffledOptions = options.shuffled()
        optionButtons.forEachIndexed { index, button ->
            button.text = shuffledOptions[index]
        }
    }

    private fun generateQuizOptions(word: Word): List<String> {
        val options = mutableSetOf<String>()
        options.add(word.definition)

        val allWordsInRepoMap = WordRepository.getAllWordsMap()
        val similarWordDefinitionsCandidates = mutableListOf<String>()

        word.similarWords.forEach { similarWordText ->
            val similarWord = allWordsInRepoMap[similarWordText.lowercase()]
            if (similarWord != null && similarWord.definition.isNotBlank() && similarWord.definition != word.definition) {
                similarWordDefinitionsCandidates.add(similarWord.definition)
            }
        }

        val random = Random(System.nanoTime())
        val selectedSimilarDefinitions = similarWordDefinitionsCandidates.shuffled(random).take(3)
        options.addAll(selectedSimilarDefinitions)

        val availableFillerDefinitions = allWordsInRepoMap.values
            .filter { it.definition.isNotBlank() && !options.contains(it.definition) }
            .map { it.definition }
            .toMutableList()

        availableFillerDefinitions.shuffle(random)

        var fillerIndex = 0
        while (options.size < 4 && fillerIndex < availableFillerDefinitions.size) {
            options.add(availableFillerDefinitions[fillerIndex])
            fillerIndex++
        }

        return options.toList()
    }

    private fun onOptionSelected(selectedDefinition: String) {
        val currentWord = quizWords[currentWordIndex]

        if (selectedDefinition == correctAnswerDefinition) {
            Toast.makeText(this, "回答正確！", Toast.LENGTH_SHORT).show()

            // 答對後才移除緩存，並進入下一題
            val remainingWords = TodayReviewCache.getWords().filter { it.word != currentWord.word }
            TodayReviewCache.setWords(remainingWords)

            currentWordIndex++
            displayNextQuiz() // 進入下一題
        } else {
            // 回答錯誤
            Toast.makeText(this, "回答錯誤，請再試試！此單詞將於明日再次學習。", Toast.LENGTH_LONG).show()

            // 高亮錯誤選項
            optionButtons.find { it.text.toString() == selectedDefinition }?.apply {
                setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))
            }
            // 高亮正確答案（可選，但有助於學習）
            optionButtons.find { it.text.toString() == correctAnswerDefinition }?.apply {
                setBackgroundColor(resources.getColor(android.R.color.holo_green_light, theme))
            }

            // --- 強化點：錯誤單詞次日重學 ---
            // 只需要在第一次答錯時更新 nextReviewDate 並保存
            val tomorrow = Calendar.getInstance()
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)

            // 僅當當前 nextReviewDate 早於明天時才更新，防止重複答錯導致日期不斷後延
            // 或者更簡單地，每次答錯都直接設置為明天
            currentWord.nextReviewDate = tomorrow.timeInMillis

            CoroutineScope(Dispatchers.IO).launch {
                WordRepository.saveWord(applicationContext, currentWord)
                withContext(Dispatchers.Main) {
                    // 可以選擇在此處添加一個視覺反饋，例如一個簡短的動畫
                }
            }

            // *** 關鍵修改：不推進 currentWordIndex，也不直接顯示下一題 ***
            // 讓用戶有機會在當前題目上選擇正確的答案
        }
    }
}