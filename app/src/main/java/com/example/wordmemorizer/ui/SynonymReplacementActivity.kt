package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button // 移除此行，因为我们将使用 MaterialButton
import android.widget.EditText // 移除此行，因为我们将使用 TextInputEditText
import android.widget.TextView
import android.widget.Toast
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.data.SynonymReplacementEntry
import com.example.wordmemorizer.sm2.SM2
import com.google.gson.Gson
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 导入 Material Design 组件
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.radiobutton.MaterialRadioButton // 确保导入

class SynonymReplacementActivity : AppCompatActivity() {

    private lateinit var textViewOriginalSentence: TextView
    private lateinit var textViewTargetWord: TextView
    private lateinit var editTextStudentSentence: TextInputEditText // 类型改为 TextInputEditText
    private lateinit var buttonSubmit: MaterialButton // 类型改为 MaterialButton
    private lateinit var buttonHint: MaterialButton // 类型改为 MaterialButton
    private lateinit var textViewFeedback: TextView
    private lateinit var textViewCorrectAnswer: TextView
    private lateinit var buttonNextWord: MaterialButton // 类型改为 MaterialButton
    private lateinit var ratingGroup: RadioGroup // 保持 RadioGroup，内部是 MaterialRadioButton

    // 新增一个 TextView 用于评分提示
    private lateinit var textViewRatingPrompt: TextView
    // 新增 feedback 卡片视图
    private lateinit var feedbackCard: com.google.android.material.card.MaterialCardView

    private var currentWord: Word? = null
    private var currentReplacementTask: SynonymReplacementEntry? = null

    private val TAG = "SynonymReplacement"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_synonym_replacement)

        textViewOriginalSentence = findViewById(R.id.textViewOriginalSentence)
        textViewTargetWord = findViewById(R.id.textViewTargetWord)
        editTextStudentSentence = findViewById(R.id.editTextStudentSentence)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        buttonHint = findViewById(R.id.buttonHint)
        textViewFeedback = findViewById(R.id.textViewFeedback)
        textViewCorrectAnswer = findViewById(R.id.textViewCorrectAnswer)
        buttonNextWord = findViewById(R.id.buttonNextWord)
        ratingGroup = findViewById(R.id.ratingGroup)
        textViewRatingPrompt = findViewById(R.id.textViewRatingPrompt) // 初始化新的TextView
        feedbackCard = findViewById(R.id.feedbackCard) // 初始化新的MaterialCardView

        val wordJson = intent.getStringExtra("currentWordJson")
        currentWord = Gson().fromJson(wordJson, Word::class.java)

        if (currentWord == null) {
            Toast.makeText(this, "无法加载单词信息，请重试。", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        buttonSubmit.setOnClickListener {
            showRecommendedAnswerAndRating()
        }

        buttonHint.setOnClickListener {
            Toast.makeText(this, "请尝试输入你的改写，然后点击提交按钮查看推荐答案。", Toast.LENGTH_LONG).show()
        }

        buttonNextWord.setOnClickListener {
            submitRatingAndFinish()
        }

        loadReplacementTask()
    }

    @SuppressLint("SetTextI18n")
    private fun loadReplacementTask() {
        // 清空之前的狀態
        editTextStudentSentence.text?.clear() // 使用安全调用
        textViewFeedback.text = ""
        textViewCorrectAnswer.text = ""
        feedbackCard.visibility = View.GONE // 隐藏整个反馈卡片
        buttonNextWord.visibility = View.GONE
        buttonSubmit.visibility = View.VISIBLE
        buttonHint.visibility = View.VISIBLE // 提示按钮也显示
        ratingGroup.visibility = View.GONE // 隐藏评分组
        textViewRatingPrompt.visibility = View.GONE // 隐藏评分提示
        ratingGroup.clearCheck() // 清除之前的选择

        currentWord?.let { word ->
            val availableTasks = word.synonymReplacementExamples
            if (availableTasks.isEmpty()) {
                Toast.makeText(this, "${word.word} 没有可用的同义替换例句，已跳过。", Toast.LENGTH_SHORT).show()
                val resultIntent = Intent()
                resultIntent.putExtra("updatedWordJson", Gson().toJson(word))
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                return
            }

            // 随机选择一个同义替换任务
            currentReplacementTask = availableTasks[Random.nextInt(availableTasks.size)]
            val taskEntry = currentReplacementTask!!

            val originalSentence = taskEntry.originalSentence
            val originalTranslation = taskEntry.originalTranslation

            // 假设 originalSentence 中加粗部分用 **word** 表示，我们需要替换成未加粗的
            // 为了美观，这里可能需要一个更复杂的富文本处理，但现在先显示纯文本
            textViewOriginalSentence.text = "原始句子: $originalSentence\n翻译: $originalTranslation"
            textViewTargetWord.text = "目标词：${word.word}"
        } ?: run {
            Toast.makeText(this, "没有单词信息可显示。", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showRecommendedAnswerAndRating() {
        val studentAnswer = editTextStudentSentence.text.toString().trim()
        val recommendedAnswer = currentReplacementTask?.recommendedReplacementSentence?.trim()

        if (recommendedAnswer == null) {
            textViewFeedback.text = "无法获取推荐答案。"
            feedbackCard.visibility = View.VISIBLE // 显示卡片以便用户看到错误信息
            return
        }

        textViewFeedback.text = "你的答案:\n\"$studentAnswer\"\n\n推荐答案:\n\"$recommendedAnswer\""

        if (studentAnswer.equals(recommendedAnswer, ignoreCase = true)) {
            Toast.makeText(this, "你修改的非常完美！", Toast.LENGTH_LONG).show()
            textViewFeedback.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            // 设置一个更鲜明的颜色，例如 Material Design 的一个橙色或红色
            textViewFeedback.setTextColor(ContextCompat.getColor(this, R.color.orange_500)) // 假设您在 colors.xml 中定义了 orange_500
            // 如果没有，可以使用：ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }

        textViewCorrectAnswer.text = "请对比你的答案和推荐答案，学习改写方式。"
        textViewCorrectAnswer.setTextColor(ContextCompat.getColor(this, android.R.color.black)) // 确保文本在白色背景上可见
        feedbackCard.visibility = View.VISIBLE // 显示整个反馈卡片

        buttonSubmit.visibility = View.GONE
        buttonHint.visibility = View.GONE
        ratingGroup.visibility = View.VISIBLE // 显示评分组
        textViewRatingPrompt.visibility = View.VISIBLE // 显示评分提示
        buttonNextWord.visibility = View.VISIBLE // 显示提交评分按钮
    }

    private fun submitRatingAndFinish() {
        val selectedId = ratingGroup.checkedRadioButtonId
        if (selectedId != -1) {
            val score = when (selectedId) {
                R.id.score1 -> 1
                R.id.score2 -> 2
                R.id.score3 -> 3
                R.id.score3 -> 3 // 修复：重复的R.id.score3
                R.id.score4 -> 4
                R.id.score5 -> 5
                else -> 3 // 默认给3分
            }

            currentWord?.let { word ->
                val updated = SM2.updateWord(word, score)
                CoroutineScope(Dispatchers.IO).launch {
                    WordRepository.updateWord(applicationContext, updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SynonymReplacementActivity, "${updated.word} 评分成功！", Toast.LENGTH_SHORT).show()
                        val resultIntent = Intent()
                        resultIntent.putExtra("updatedWordJson", Gson().toJson(updated))
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        } else {
            Toast.makeText(this, "请选择评分！", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val resultIntent = Intent()
        resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }
}