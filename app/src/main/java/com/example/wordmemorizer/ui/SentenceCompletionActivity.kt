package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.google.gson.Gson
import java.util.Calendar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText


class SentenceCompletionActivity : AppCompatActivity() {

    private lateinit var textViewSentence: TextView
    private lateinit var textViewTranslation: TextView
    private lateinit var editTextAnswer: TextInputEditText // 修改为 TextInputEditText
    private lateinit var buttonCheck: MaterialButton // 修改为 MaterialButton
    private lateinit var textViewFeedback: TextView
    private lateinit var buttonNext: MaterialButton // 修改为 MaterialButton

    private var currentWord: Word? = null
    private var currentCorrectWord: String = ""
    private var isCorrectAnswerGiven: Boolean = false // 新增一个标志，表示当前单词是否已给出正确答案

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sentence_completion)

        textViewSentence = findViewById(R.id.textViewSentence)
        textViewTranslation = findViewById(R.id.textViewTranslation)
        editTextAnswer = findViewById(R.id.editTextAnswer)
        buttonCheck = findViewById(R.id.buttonCheck)
        textViewFeedback = findViewById(R.id.textViewFeedback)
        buttonNext = findViewById(R.id.buttonNext)

        val wordJson = intent.getStringExtra("currentWordJson")
        currentWord = Gson().fromJson(wordJson, Word::class.java)

        if (currentWord == null) {
            Toast.makeText(this, "无法加载单词信息，请重试。", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // 检查当前单词是否有填空例句
        if (currentWord?.fillInTheBlankExamples.isNullOrEmpty()) {
            Toast.makeText(this, "单词 ${currentWord?.word} 没有可用的填空例句！", Toast.LENGTH_LONG).show()
            // 如果没有例句，直接视为完成，并允许跳过
            // 这里应该返回 currentWord 的状态，而不是直接 finish
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("updatedWordJson", Gson().toJson(currentWord))
            })
            finish()
            return
        }


        buttonCheck.setOnClickListener {
            checkAnswer()
        }

        buttonNext.setOnClickListener {
            // 当用户点击下一题时，无论是答对还是答错，都表示当前填空任务完成
            // 答对：isCorrectAnswerGiven 为 true
            // 答错：isCorrectAnswerGiven 为 false，但下次复习日期已重置，视为完成当前任务
            val resultIntent = Intent()
            resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
            setResult(Activity.RESULT_OK, resultIntent) // 总是返回 RESULT_OK 表示完成任务
            finish()
        }

        displayQuiz() // 只显示当前单词的填空题
    }

    @SuppressLint("SetTextI18n")
    private fun displayQuiz() {
        editTextAnswer.text?.clear() // 使用安全调用操作符
        textViewFeedback.visibility = TextView.GONE // 使用 TextView.GONE
        buttonCheck.visibility = Button.VISIBLE
        buttonNext.visibility = Button.GONE
        editTextAnswer.isEnabled = true
        textViewTranslation.text = "" // 清空旧的翻译

        isCorrectAnswerGiven = false // 重置标志

        currentWord?.let { word ->
            currentCorrectWord = word.word

            // 确保有例句才进行显示
            val sentenceWithTranslation = word.fillInTheBlankExamples.randomOrNull()

            if (sentenceWithTranslation != null) {
                // 将填空例句中的下划线替换为单词本身，以便匹配
                // 注意：如果您的例句是 "Our school garden has _____ flowers in spring (我们学校的花园在春天有_____的花朵)"
                // 并且您期望用户输入 "beautiful"
                // 那么 fillInTheBlankExamples 字段中的字符串应该是 "Our school garden has beautiful flowers in spring (我们学校的花园在春天有美丽的花朵)"
                // 然后在 displayQuiz 中将 "beautiful" 替换为下划线
                // 这里我们假设 fillInTheBlankExamples 已经包含了下划线，且正确单词是 `currentWord.word`
                val displaySentence = sentenceWithTranslation.replace(currentCorrectWord, "_____", ignoreCase = true)

                val regex = Regex("(.+?) \\((.+)\\)")
                val matchResult = regex.find(displaySentence) // 对替换后的句子进行匹配

                if (matchResult != null && matchResult.groupValues.size == 3) {
                    val englishPart = matchResult.groupValues[1].trim()
                    val chinesePart = matchResult.groupValues[2].trim()
                    textViewSentence.text = englishPart
                    textViewTranslation.text = "($chinesePart)"
                } else {
                    textViewSentence.text = displaySentence // 如果格式不符，至少显示替换后的句子
                    textViewTranslation.text = ""
                    Toast.makeText(this, "例句格式不符合'英文 (中文)'，或未找到填空位置，请检查！", Toast.LENGTH_SHORT).show()
                    // 如果格式错误，也允许用户跳过
                    buttonCheck.visibility = Button.GONE
                    buttonNext.visibility = Button.VISIBLE
                }
            } else {
                // 这个Toast应该在onCreate里处理，这里不应该再触发
                // 因为在onCreate里已经处理了 currentWord?.fillInTheBlankExamples.isNullOrEmpty()
                // 但为了健壮性，这里也保留
                Toast.makeText(this, "单词 ${word.word} 没有可用的填空例句！", Toast.LENGTH_LONG).show()
                buttonCheck.visibility = Button.GONE
                buttonNext.visibility = Button.VISIBLE
            }
        } ?: run {
            Toast.makeText(this, "没有单词信息可展示。", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkAnswer() {
        val userAnswer = editTextAnswer.text.toString().trim()

        if (userAnswer.equals(currentCorrectWord, ignoreCase = true)) {
            textViewFeedback.text = "回答正确！"
            // 使用 ContextCompat.getColor 获取颜色
            textViewFeedback.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            isCorrectAnswerGiven = true // 设置为已答对
        } else {
            textViewFeedback.text = "回答错误，请再试试！\n正确答案是: ${currentCorrectWord}"
            textViewFeedback.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            isCorrectAnswerGiven = false // 答错
            // 错误时，将单词的下次复习日期重置为明天，确保能在短期内再次复习
            currentWord?.let {
                val tomorrow = Calendar.getInstance()
                tomorrow.add(Calendar.DAY_OF_YEAR, 1)
                it.nextReviewDate = tomorrow.timeInMillis
            }
        }
        textViewFeedback.visibility = TextView.VISIBLE // 使用 TextView.VISIBLE
        buttonCheck.visibility = Button.GONE
        buttonNext.visibility = Button.VISIBLE
        editTextAnswer.isEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 用户点击返回键，表示放弃当前填空，返回到 ReviewActivity
        val resultIntent = Intent()
        resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }
}