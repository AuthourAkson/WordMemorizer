package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color // 不再直接使用 Color.BLACK，改为 ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.ClozeTestEntry
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.sm2.SM2
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// 导入 Material Design 组件
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.card.MaterialCardView // 导入 MaterialCardView


class ClozeTestActivity : AppCompatActivity() {

    private lateinit var textViewClozeSentence: TextView
    private lateinit var textViewClozeSentenceTranslation: TextView
    private lateinit var flexboxLayoutOptions: FlexboxLayout
    private lateinit var textViewSelectedAnswers: TextView
    private lateinit var buttonCheckAnswer: MaterialButton // 修改为 MaterialButton
    private lateinit var textViewFeedback: TextView
    private lateinit var textViewCorrectAnswers: TextView
    private lateinit var ratingGroup: RadioGroup // 保持 RadioGroup，内部是 MaterialRadioButton
    private lateinit var buttonNextWord: MaterialButton

    private lateinit var textViewRatingPrompt: TextView
    private lateinit var feedbackCard: MaterialCardView // 反馈卡片

    private var currentWord: Word? = null
    private var currentClozeTestEntry: ClozeTestEntry? = null
    private var selectedAnswerButtons: LinkedList<MaterialButton> = LinkedList() // 存储用户选择的答案按钮，类型改为 MaterialButton
    private var currentSelectedAnswers: LinkedList<String> = LinkedList()

    private val TAG = "ClozeTestActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloze_test)

        textViewClozeSentence = findViewById(R.id.textViewClozeSentence)
        textViewClozeSentenceTranslation = findViewById(R.id.textViewClozeSentenceTranslation) // 初始化翻译TextView
        flexboxLayoutOptions = findViewById(R.id.flexboxLayoutOptions)
        textViewSelectedAnswers = findViewById(R.id.textViewSelectedAnswers)
        buttonCheckAnswer = findViewById(R.id.buttonCheckAnswer)
        textViewFeedback = findViewById(R.id.textViewFeedback)
        textViewCorrectAnswers = findViewById(R.id.textViewCorrectAnswers)
        ratingGroup = findViewById(R.id.ratingGroup)
        buttonNextWord = findViewById(R.id.buttonNextWord)

        textViewRatingPrompt = findViewById(R.id.textViewRatingPrompt)
        feedbackCard = findViewById(R.id.feedbackCard)

        val wordJson = intent.getStringExtra("currentWordJson")
        currentWord = Gson().fromJson(wordJson, Word::class.java)

        if (currentWord == null) {
            Toast.makeText(this, "无法加载单词信息，请重试。", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        buttonCheckAnswer.setOnClickListener {
            checkAnswer()
        }

        buttonNextWord.setOnClickListener {
            submitRatingAndFinish()
        }

        displayClozeTest()
    }

    @SuppressLint("SetTextI18n")
    private fun displayClozeTest() {
        resetUI()

        currentWord?.let { word ->
            val availableEntries = word.clozeTestExamples
            if (availableEntries.isEmpty()) {
                Toast.makeText(this, "${word.word} 没有可用的完形填空题，已跳过。", Toast.LENGTH_SHORT).show()
                val resultIntent = Intent()
                resultIntent.putExtra("updatedWordJson", Gson().toJson(word))
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                return
            }

            currentClozeTestEntry = availableEntries.random()
            val entry = currentClozeTestEntry!!

            // TODO: 这里需要处理 clozedSentence 中的下划线，例如 "_____"
            // 将 clozedSentence 中的占位符替换为下划线，使其可见
            val sentenceWithBlanks = entry.clozeSentence.replace("____", "_____") // 确保是下划线
            textViewClozeSentence.text = sentenceWithBlanks
            textViewClozeSentenceTranslation.text = entry.clozeSentenceTranslation

            flexboxLayoutOptions.removeAllViews()
            val shuffledOptions = entry.options.shuffled()
            shuffledOptions.forEach { option ->
                val button = MaterialButton(this).apply { // 使用 MaterialButton
                    text = option
                    tag = option
                    setOnClickListener { onOptionSelected(it as MaterialButton) } // 强制转换为 MaterialButton
                    // 设置 MaterialButton 的样式
                    cornerRadius = 20.dpToPx() // 设置圆角，需要一个扩展函数
                    setStrokeWidth(2.dpToPx()) // 设置描边宽度
                    setStrokeColorResource(R.color.purple_200) // 默认描边颜色
                    setTextColor(ContextCompat.getColor(context, R.color.black)) // 默认文本颜色
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.white)) // 默认背景颜色
                    setPadding(24.dpToPx(), 12.dpToPx(), 24.dpToPx(), 12.dpToPx()) // 内边距

                    val params = FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        FlexboxLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx()) // 间距
                    layoutParams = params
                }
                flexboxLayoutOptions.addView(button)
            }
        } ?: run {
            Toast.makeText(this, "没有单词信息可显示。", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    // dp转px的扩展函数
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun resetUI() {
        flexboxLayoutOptions.removeAllViews()
        textViewSelectedAnswers.text = "你选择的答案: "
        selectedAnswerButtons.clear()
        currentSelectedAnswers.clear()

        feedbackCard.visibility = View.GONE // 隐藏整个反馈卡片
        textViewRatingPrompt.visibility = View.GONE // 隐藏评分提示
        ratingGroup.visibility = View.GONE
        buttonNextWord.visibility = View.GONE
        buttonCheckAnswer.visibility = View.VISIBLE
        enableOptionButtons(true) // 确保选项按钮可点击

        ratingGroup.clearCheck()
    }

    private fun onOptionSelected(button: MaterialButton) { // 参数类型改为 MaterialButton
        val selectedText = button.text.toString()
        val currentEntry = currentClozeTestEntry ?: return

        // 如果用户点击的是已经选择的答案，则取消选择
        if (selectedAnswerButtons.contains(button)) {
            val indexToRemove = selectedAnswerButtons.indexOf(button)
            if (indexToRemove != -1) {
                selectedAnswerButtons.removeAt(indexToRemove)
                currentSelectedAnswers.removeAt(indexToRemove)
                // 恢复按钮的默认背景和描边颜色
                button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                button.setStrokeColorResource(R.color.purple_200)
                button.setTextColor(ContextCompat.getColor(this, R.color.black))
                updateSelectedAnswersDisplay()
            }
            return
        }

        if (currentSelectedAnswers.size >= currentEntry.blankAnswers.size) {
            Toast.makeText(this, "已选择所有填空答案，请点击'检查答案'。", Toast.LENGTH_SHORT).show()
            return
        }

        selectedAnswerButtons.add(button)
        currentSelectedAnswers.add(selectedText)

        // 改变按钮背景以示选中
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500)) // 选中时背景色
        button.setStrokeColorResource(R.color.purple_500) // 选中时描边色
        button.setTextColor(ContextCompat.getColor(this, R.color.white)) // 选中时文本色

        updateSelectedAnswersDisplay()
    }

    private fun updateSelectedAnswersDisplay() {
        val displayString = StringBuilder("你选择的答案: ")
        currentSelectedAnswers.forEachIndexed { index, answer ->
            if (index > 0) displayString.append(", ")
            displayString.append(answer)
        }
        textViewSelectedAnswers.text = displayString.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun checkAnswer() {
        val entry = currentClozeTestEntry ?: return
        if (currentSelectedAnswers.size != entry.blankAnswers.size) {
            Toast.makeText(this, "请选择所有填空！", Toast.LENGTH_SHORT).show()
            return
        }

        var correctCount = 0
        var allCorrectInOrder = true

        // 比较用户选择的答案与正确答案
        for (i in entry.blankAnswers.indices) {
            if (i < currentSelectedAnswers.size) {
                if (currentSelectedAnswers[i].equals(entry.blankAnswers[i], ignoreCase = true)) {
                    correctCount++
                } else {
                    allCorrectInOrder = false
                }
            } else {
                allCorrectInOrder = false
                break
            }
        }

        if (allCorrectInOrder) {
            textViewFeedback.text = "回答完美！"
            textViewFeedback.setTextColor(ContextCompat.getColor(this, R.color.green_500)) // 使用自定义绿色
            ratingGroup.check(R.id.score5)
        } else if (correctCount > 0) {
            textViewFeedback.text = "部分正确！你答对了 ${correctCount} 个填空。\n请参考正确答案。"
            textViewFeedback.setTextColor(ContextCompat.getColor(this, R.color.orange_500)) // 使用自定义橙色
            ratingGroup.check(R.id.score3)
        } else {
            textViewFeedback.text = "回答错误！\n请参考正确答案。"
            textViewFeedback.setTextColor(ContextCompat.getColor(this, R.color.red_500)) // 使用自定义红色
            ratingGroup.check(R.id.score1)
        }

        feedbackCard.visibility = View.VISIBLE // 显示整个反馈卡片
        textViewCorrectAnswers.text = "正确答案: ${entry.blankAnswers.joinToString(", ")}"
        textViewCorrectAnswers.visibility = View.VISIBLE

        buttonCheckAnswer.visibility = View.GONE
        textViewRatingPrompt.visibility = View.VISIBLE // 显示评分提示
        ratingGroup.visibility = View.VISIBLE
        buttonNextWord.visibility = View.VISIBLE
        enableOptionButtons(false) // 禁用选项按钮
        highlightCorrectAndIncorrectAnswers(entry.blankAnswers) // 调用新的高亮方法
    }

    private fun enableOptionButtons(enable: Boolean) {
        for (i in 0 until flexboxLayoutOptions.childCount) {
            val child = flexboxLayoutOptions.getChildAt(i)
            if (child is MaterialButton) { // 类型检查改为 MaterialButton
                child.isEnabled = enable
                if (enable) {
                    // 如果是启用状态，并且该按钮没有被选中，恢复默认颜色和描边
                    if (!selectedAnswerButtons.contains(child)) {
                        child.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                        child.setStrokeColorResource(R.color.purple_200) // 恢复默认描边色
                        child.setTextColor(ContextCompat.getColor(this, R.color.black))
                    }
                } else {
                    // 禁用状态下的未选中按钮显示为灰色
                    if (!selectedAnswerButtons.contains(child)) {
                        child.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                        child.setStrokeColorResource(R.color.light_gray) // 禁用时描边也变为灰色
                        child.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }
                }
            }
        }
    }

    // 新增方法：高亮显示正确和错误的选项按钮
    private fun highlightCorrectAndIncorrectAnswers(correctAnswers: List<String>) {
        for (i in 0 until flexboxLayoutOptions.childCount) {
            val child = flexboxLayoutOptions.getChildAt(i)
            if (child is MaterialButton) { // 类型检查改为 MaterialButton
                val optionText = child.text.toString()

                if (correctAnswers.contains(optionText) && selectedAnswerButtons.contains(child)) {
                    // 用户选中且是正确答案 -> 深绿色背景
                    child.setBackgroundColor(ContextCompat.getColor(this, R.color.green_500))
                    child.setStrokeColorResource(R.color.green_500)
                    child.setTextColor(ContextCompat.getColor(this, R.color.white))
                } else if (!correctAnswers.contains(optionText) && selectedAnswerButtons.contains(child)) {
                    // 用户选中但不是正确答案 -> 红色背景
                    child.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
                    child.setStrokeColorResource(R.color.red_500)
                    child.setTextColor(ContextCompat.getColor(this, R.color.white))
                } else if (correctAnswers.contains(optionText) && !selectedAnswerButtons.contains(child)) {
                    // 是正确答案但用户未选中 -> 浅绿色背景 (提示)
                    child.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    child.setStrokeColorResource(android.R.color.holo_green_light)
                    child.setTextColor(ContextCompat.getColor(this, R.color.black)) // 浅色背景用深色文本
                } else {
                    // 未选中且非正确答案 -> 灰色背景
                    child.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                    child.setStrokeColorResource(R.color.light_gray)
                    child.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                }
            }
        }
    }


    private fun submitRatingAndFinish() {
        val selectedId = ratingGroup.checkedRadioButtonId
        if (selectedId != -1) {
            val score = when (selectedId) {
                R.id.score1 -> 1
                R.id.score2 -> 2
                R.id.score3 -> 3
                R.id.score4 -> 4
                R.id.score5 -> 5
                else -> 3 // 默认给3分
            }

            currentWord?.let { word ->
                val updated = SM2.updateWord(word, score)
                CoroutineScope(Dispatchers.IO).launch {
                    WordRepository.updateWord(applicationContext, updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ClozeTestActivity, "${updated.word} 评分成功！", Toast.LENGTH_SHORT).show()
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