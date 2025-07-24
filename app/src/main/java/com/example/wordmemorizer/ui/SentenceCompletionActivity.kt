package com.example.wordmemorizer.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import kotlin.random.Random
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SentenceCompletionActivity : AppCompatActivity() {

    private lateinit var textViewSentence: TextView
    private lateinit var textViewTranslation: TextView // 確保這裡有聲明
    private lateinit var editTextAnswer: EditText
    private lateinit var buttonCheck: Button
    private lateinit var textViewFeedback: TextView
    private lateinit var buttonNext: Button

    private var quizWords: MutableList<Word> = mutableListOf()
    private var currentWordIndex = 0
    private var currentCorrectWord: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sentence_completion)

        textViewSentence = findViewById(R.id.textViewSentence)
        textViewTranslation = findViewById(R.id.textViewTranslation) // 確保這裡有初始化
        editTextAnswer = findViewById(R.id.editTextAnswer)
        buttonCheck = findViewById(R.id.buttonCheck)
        textViewFeedback = findViewById(R.id.textViewFeedback)
        buttonNext = findViewById(R.id.buttonNext)

        // ... 後續邏輯保持不變 ...
        quizWords = WordRepository.getCurrentFillInBlankSessionWords().toMutableList()
        quizWords.shuffle()

        if (quizWords.isEmpty()) {
            Toast.makeText(this, "今日沒有需要句子填空的單詞。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        buttonCheck.setOnClickListener {
            checkAnswer()
        }

        buttonNext.setOnClickListener {
            currentWordIndex++
            displayNextQuiz()
        }

        displayNextQuiz()
    }

    private fun displayNextQuiz() {
        editTextAnswer.text.clear()
        textViewFeedback.visibility = Button.GONE
        buttonCheck.visibility = Button.VISIBLE
        buttonNext.visibility = Button.GONE
        editTextAnswer.isEnabled = true
        textViewTranslation.text = ""

        if (currentWordIndex >= quizWords.size) {
            Toast.makeText(this, "所有句子填空題目已完成！", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val currentWord = quizWords[currentWordIndex]
        currentCorrectWord = currentWord.word

        val sentenceWithTranslation = currentWord.fillInTheBlankExamples.randomOrNull()

        if (sentenceWithTranslation != null) {
            val regex = Regex("(.+?) \\((.+)\\)")
            val matchResult = regex.find(sentenceWithTranslation)

            if (matchResult != null && matchResult.groupValues.size == 3) {
                val englishPart = matchResult.groupValues[1].trim()
                val chinesePart = matchResult.groupValues[2].trim()
                textViewSentence.text = englishPart
                textViewTranslation.text = "($chinesePart)"
            } else {
                textViewSentence.text = sentenceWithTranslation
                textViewTranslation.text = ""
                Toast.makeText(this, "例句格式不符合'英文 (中文)'，請檢查！", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "單詞 ${currentWord.word} 沒有可用的填空例句！", Toast.LENGTH_SHORT).show()
            currentWordIndex++
            displayNextQuiz()
            return
        }
    }

    private fun checkAnswer() {
        val userAnswer = editTextAnswer.text.toString().trim()

        if (userAnswer.equals(currentCorrectWord, ignoreCase = true)) {
            textViewFeedback.text = "回答正確！"
            textViewFeedback.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            textViewFeedback.visibility = Button.VISIBLE
            buttonCheck.visibility = Button.GONE
            buttonNext.visibility = Button.VISIBLE
            editTextAnswer.isEnabled = false

            val currentWord = quizWords[currentWordIndex]
            CoroutineScope(Dispatchers.IO).launch {
                WordRepository.updateWordForFillInBlank(applicationContext, currentWord)
                withContext(Dispatchers.Main) { }
            }
        } else {
            textViewFeedback.text = "回答錯誤，請再試試！\n正確答案是: ${currentCorrectWord}"
            textViewFeedback.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            textViewFeedback.visibility = Button.VISIBLE

            val currentWord = quizWords[currentWordIndex]
            val tomorrow = Calendar.getInstance()
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)
            currentWord.nextReviewDate = tomorrow.timeInMillis

            CoroutineScope(Dispatchers.IO).launch {
                WordRepository.saveWord(applicationContext, currentWord)
                WordRepository.updateWordForFillInBlank(applicationContext, currentWord)
                withContext(Dispatchers.Main) { }
            }

            buttonCheck.visibility = Button.GONE
            buttonNext.visibility = Button.VISIBLE
            editTextAnswer.isEnabled = false
        }
    }
}