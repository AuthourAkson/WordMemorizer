package com.example.wordmemorizer.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.sm2.SM2
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.random.Random
import android.util.TypedValue // <-- 确保这个导入存在

class DefinitionQuizActivity : AppCompatActivity() {

    private lateinit var textViewWord: TextView
    private lateinit var buttonOptionA: MaterialButton
    private lateinit var buttonOptionB: MaterialButton
    private lateinit var buttonOptionC: MaterialButton
    private lateinit var buttonOptionD: MaterialButton

    private lateinit var currentWord: Word
    private var correctAnswerDefinition: String = ""

    private val optionButtons: MutableList<MaterialButton> by lazy {
        mutableListOf(buttonOptionA, buttonOptionB, buttonOptionC, buttonOptionD)
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_definition_quiz)

        textViewWord = findViewById(R.id.textViewWord)
        buttonOptionA = findViewById(R.id.buttonOptionA)
        buttonOptionB = findViewById(R.id.buttonOptionB)
        buttonOptionC = findViewById(R.id.buttonOptionC)
        buttonOptionD = findViewById(R.id.buttonOptionD)

        val wordJson = intent.getStringExtra("currentWordJson")
        if (wordJson.isNullOrEmpty()) {
            Toast.makeText(this, "未接收到有效單詞數據。", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        currentWord = Gson().fromJson(wordJson, Word::class.java)

        if (currentWord.definition.isBlank()) {
            Toast.makeText(this, "當前單詞沒有定義，無法進行釋義辨析。", Toast.LENGTH_LONG).show()
            returnUpdatedWord(currentWord)
            return
        }

        setupOptionListeners()
        displayQuiz()
    }

    private fun setupOptionListeners() {
        optionButtons.forEach { button ->
            button.setOnClickListener {
                setOptionButtonsEnabled(false) // 禁用所有選項，防止連點
                onOptionSelected(button.text.toString(), button) // 传入被点击的按钮
            }
        }
    }

    // 显示当前的单词的释义辨析題目
    private fun displayQuiz() {
        // 重置所有选项按钮的颜色和可用状态
        // 恢复 MaterialButton 默认的描边样式
        optionButtons.forEach {
            it.backgroundTintList = null // 清除可能设置的背景色
            it.setTextColor(ContextCompat.getColorStateList(this, android.R.color.white)) // 确保文本颜色为白色
            it.strokeColor = ContextCompat.getColorStateList(this, android.R.color.white) // 确保描边颜色为白色
            it.strokeWidth = dpToPx(2f) // <-- 这里的 dpToPx 应该返回 Int, 但 strokeWidth 属性是 Float. 我再检查一下。

            // MaterialButton 的 strokeWidth 属性确实是 Int 类型。
            // 因此，dpToPx 返回 Int 是正确的，并且直接赋值即可。
            it.strokeWidth = dpToPx(2f) // <-- 调用 dpToPx 传入 Float 参数，但其内部会转为 Int

            it.isEnabled = true // 确保按钮可用
        }

        textViewWord.text = currentWord.word
        correctAnswerDefinition = currentWord.definition

        val options = generateQuizOptions(currentWord)

        val shuffledOptions = options.shuffled(Random(System.nanoTime()))
        optionButtons.forEachIndexed { index, button ->
            button.text = shuffledOptions[index]
            button.isEnabled = true // 確保按鈕可用
        }
    }

    private fun generateQuizOptions(word: Word): List<String> {
        val options = mutableSetOf<String>()
        options.add(word.definition) // 正确答案

        val allWordsInRepoMap = WordRepository.getAllWordsMap()
        val similarWordDefinitionsCandidates = mutableListOf<String>()

        word.similarWords.forEach { similarWordText ->
            val similarWord = allWordsInRepoMap[similarWordText.lowercase(Locale.getDefault())] // 使用Locale.getDefault()
            if (similarWord != null && similarWord.definition.isNotBlank() && similarWord.definition != word.definition) {
                similarWordDefinitionsCandidates.add(similarWord.definition)
            }
        }

        val random = Random(System.nanoTime())
        val selectedSimilarDefinitions = similarWordDefinitionsCandidates.shuffled(random).take(2)
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

        return options.toList().shuffled(random)
    }

    // 接收被点击的按钮
    private fun onOptionSelected(selectedDefinition: String, clickedButton: MaterialButton) {
        if (selectedDefinition == correctAnswerDefinition) {
            Toast.makeText(this, "回答正確！", Toast.LENGTH_SHORT).show()

            // 高亮正确答案为绿色
            clickedButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            clickedButton.setTextColor(ContextCompat.getColor(this, android.R.color.black)) // 确保文本可见
            clickedButton.strokeColor = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark) // 描边也变为深绿
            clickedButton.strokeWidth = dpToPx(2f) // <-- 确保这里是 dpToPx(Float) 返回 Int

            // SM-2 算法，答对分数根据掌握程度给 3-5，这里先给 4
            val updatedWord = SM2.updateWord(currentWord, 4)
            CoroutineScope(Dispatchers.IO).launch {
                WordRepository.updateWord(applicationContext, updatedWord)
                withContext(Dispatchers.Main) {
                    // 延遲 1.5 秒後返回結果並結束 Activity
                    handler.postDelayed({
                        returnUpdatedWord(updatedWord)
                    }, 1500) // 1.5 秒延遲
                }
            }
        } else {
            // 回答错误
            Toast.makeText(this, "回答錯誤，請再試試！", Toast.LENGTH_SHORT).show()

            // 高亮错误选项为红色
            clickedButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            clickedButton.setTextColor(ContextCompat.getColor(this, android.R.color.black)) // 确保文本可见
            clickedButton.strokeColor = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark) // 描边也变为深红
            clickedButton.strokeWidth = dpToPx(2f) // <-- 确保这里是 dpToPx(Float) 返回 Int

            // 高亮正确答案为绿色
            val correctButton = optionButtons.find { it.text.toString() == correctAnswerDefinition }
            correctButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            correctButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            correctButton?.strokeColor = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            correctButton?.strokeWidth = dpToPx(2f) // <-- 确保这里是 dpToPx(Float) 返回 Int


            // SM-2 算法，答错分数给 0 (完全忘记)
            val updatedWord = SM2.updateWord(currentWord, 0)
            CoroutineScope(Dispatchers.IO).launch {
                WordRepository.updateWord(applicationContext, updatedWord)
                withContext(Dispatchers.Main) {
                    // 颜色保持 2 秒后重置，然后按钮重新可用，让用户重新尝试
                    handler.postDelayed({
                        optionButtons.forEach { btn ->
                            // 重置为 MaterialButton 默认的描边样式
                            btn.backgroundTintList = null // 清除背景色
                            btn.setTextColor(ContextCompat.getColorStateList(this@DefinitionQuizActivity, android.R.color.white)) // 文本白色
                            btn.strokeColor = ContextCompat.getColorStateList(this@DefinitionQuizActivity, android.R.color.white) // 描边白色
                            btn.strokeWidth = dpToPx(2f) // <-- 确保这里是 dpToPx(Float) 返回 Int
                        }
                        setOptionButtonsEnabled(true) // 重新启用按钮
                    }, 2000) // 2 秒延迟
                }
            }
        }
    }

    // 辅助方法：统一设置选项按钮的可用状态
    private fun setOptionButtonsEnabled(enabled: Boolean) {
        optionButtons.forEach { it.isEnabled = enabled }
    }

    // 将更新后的单词返回给 ReviewActivity
    private fun returnUpdatedWord(word: Word) {
        val updatedWordJson = Gson().toJson(word)
        val resultIntent = Intent().apply {
            putExtra("updatedWordJson", updatedWordJson)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // 辅助方法：dp转px，用于设置描边宽度
    // 关键修正：将返回类型明确为 Int，并对 applyDimension 的结果进行 toInt() 转换
    private fun dpToPx(dp: Float): Int { // <-- 确保返回类型是 Int
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt() // <-- 确保将 Float 转换为 Int
    }

    // 处理用户按返回键的情况
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 如果用户在答题过程中按返回键，返回当前单词的状态，并设置 RESULT_CANCELED
        val resultIntent = Intent().apply {
            putExtra("updatedWordJson", Gson().toJson(currentWord))
        }
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null) // 清除所有待处理的延遲任務
        super.onDestroy()
    }
}