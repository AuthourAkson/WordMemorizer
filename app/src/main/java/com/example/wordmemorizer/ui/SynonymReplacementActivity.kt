package com.example.wordmemorizer.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.data.SynonymReplacementEntry // <--- **添加這一行！**
import java.util.Date // 雖然 Word.kt 移除了 lastReviewed，但這裡的 Date 仍然用於更新
import kotlin.random.Random

class SynonymReplacementActivity : AppCompatActivity() {

    private lateinit var textViewOriginalSentence: TextView
    private lateinit var textViewTargetWord: TextView
    private lateinit var editTextStudentSentence: EditText
    private lateinit var buttonSubmit: Button
    private lateinit var buttonHint: Button
    private lateinit var textViewFeedback: TextView
    private lateinit var textViewCorrectAnswer: TextView
    private lateinit var buttonNextWord: Button

    private lateinit var currentWord: Word
    // 這裡的類型已經正確地指向 SynonymReplacementEntry
    private var currentReplacementTask: SynonymReplacementEntry? = null

    private lateinit var wordsForReplacement: MutableList<Word>
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_synonym_replacement)

        // 初始化視圖
        textViewOriginalSentence = findViewById(R.id.textViewOriginalSentence)
        textViewTargetWord = findViewById(R.id.textViewTargetWord)
        editTextStudentSentence = findViewById(R.id.editTextStudentSentence)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        buttonHint = findViewById(R.id.buttonHint)
        textViewFeedback = findViewById(R.id.textViewFeedback)
        textViewCorrectAnswer = findViewById(R.id.textViewCorrectAnswer)
        buttonNextWord = findViewById(R.id.buttonNextWord)

        buttonSubmit.setOnClickListener {
            showRecommendedAnswer()
        }

        buttonHint.setOnClickListener {
            Toast.makeText(this, "請嘗試輸入你的改寫，然後點擊提交按鈕查看推薦答案。", Toast.LENGTH_LONG).show()
        }

        buttonNextWord.setOnClickListener {
            loadNextReplacementTask()
        }

        // 從 WordRepository 中獲取單詞並過濾
        wordsForReplacement = WordRepository.getWords(this) // 獲取所有單詞
            .filter { word ->
                // 條件一：確保有替換例句
                val hasReplacementExamples = word.synonymReplacementExamples.isNotEmpty()
                // 條件二：單詞的復習間隔 (interval) 達到或超過 20 天
                val intervalCheck = word.interval >= 20

                hasReplacementExamples && intervalCheck
            }
            .toMutableList()

        if (wordsForReplacement.isEmpty()) {
            Toast.makeText(this, "暂无可用于同义替换的单词 (需复习间隔达到20天且有例句)。", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        wordsForReplacement.shuffle() // 隨機打亂單詞順序
        loadNextReplacementTask()
    }

    private fun loadNextReplacementTask() {
        // 清空之前的狀態
        editTextStudentSentence.text.clear()
        textViewFeedback.text = ""
        textViewCorrectAnswer.text = ""
        textViewCorrectAnswer.visibility = View.GONE
        buttonNextWord.visibility = View.GONE
        buttonSubmit.visibility = View.VISIBLE

        if (currentIndex >= wordsForReplacement.size) {
            Toast.makeText(this, "同义替换练习完成！", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentWord = wordsForReplacement[currentIndex]
        currentIndex++

        // 從 currentWord 的 synonymReplacementExamples 中隨機抽取一個任務
        val availableTasks = currentWord.synonymReplacementExamples
        if (availableTasks.isEmpty()) {
            // 理論上前面 filter 已經排除了，但以防萬一
            Toast.makeText(this, "${currentWord.word} 没有可用的同义替换例句，已跳过。", Toast.LENGTH_SHORT).show()
            loadNextReplacementTask() // 跳過當前詞
            return
        }

        currentReplacementTask = availableTasks[Random.nextInt(availableTasks.size)]
        val taskEntry = currentReplacementTask!! // 獲取 SynonymReplacementEntry 對象

        val originalSentence = taskEntry.originalSentence
        val originalTranslation = taskEntry.originalTranslation
        // recommendedReplacementSentence 直接從 taskEntry.recommendedReplacementSentence 獲取

        textViewOriginalSentence.text = "原始句子: $originalSentence\n翻译: $originalTranslation"
        textViewTargetWord.text = "目标词：${currentWord.word}"

        // **重要**：這裡我們不直接更新 SM-2 算法的 nextReviewDate, interval 等字段
        // 因為這個模式是「鞏固」，而不是常規的「復習」。
        // 如果每次鞏固都重置 SM-2 參數，可能會影響其間隔。
        //
        // 如果你希望在鞏固後，這個單詞的 `interval` 繼續增加，或者標記為已鞏固，
        // 你可能需要在 `Word` 數據類中增加一個 `lastConsolidatedDate: Long` 或 `consolidationCount: Int` 字段。
    }

    private fun showRecommendedAnswer() {
        val studentAnswer = editTextStudentSentence.text.toString().trim()
        val recommendedAnswer = currentReplacementTask?.recommendedReplacementSentence?.trim() // 確保推薦答案也去除了空格

        if (recommendedAnswer == null) {
            textViewFeedback.text = "無法獲取推薦答案。"
            return
        }

        // 先顯示標準的反饋
        textViewFeedback.text = "你的答案:\n\"$studentAnswer\"\n\n推薦答案:\n\"$recommendedAnswer\""
        textViewFeedback.setTextColor(Color.BLACK)

        // **新增的完美提示邏輯**
        if (studentAnswer == recommendedAnswer) {
            Toast.makeText(this, "你修改的非常完美！", Toast.LENGTH_LONG).show()
            textViewFeedback.setTextColor(Color.BLACK) // 可以將反饋文字設置為綠色
        }

        textViewCorrectAnswer.text = "請比對你的答案和推薦答案，學習改寫方式。"
        textViewCorrectAnswer.setTextColor(Color.BLACK)
        textViewCorrectAnswer.visibility = View.VISIBLE

        buttonSubmit.visibility = View.GONE
        buttonNextWord.visibility = View.VISIBLE
    }

}