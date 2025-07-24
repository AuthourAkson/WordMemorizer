package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.google.android.flexbox.FlexboxLayout
import com.example.wordmemorizer.data.TodayReviewCache
import java.util.*
import kotlin.collections.HashMap

class SemanticDragActivity : AppCompatActivity() {

    private lateinit var bubbleContainer: FlexboxLayout
    private lateinit var dropZoneSynonym: LinearLayout
    private lateinit var dropZoneAntonym: LinearLayout
    private lateinit var dropZoneSimilar: LinearLayout
    private lateinit var targetWordText: TextView
    private lateinit var nextButton: Button

    private lateinit var currentWord: Word
    private val expectedAnswers = HashMap<String, String>()
    private val answeredCorrectly = mutableSetOf<String>()

    // 🔧 新增：遍歷控制用變量
    private lateinit var semanticWords: MutableList<Word>
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semantic_drag)

        bubbleContainer = findViewById(R.id.bubbleContainer)
        dropZoneSynonym = findViewById(R.id.dropZoneSynonym)
        dropZoneAntonym = findViewById(R.id.dropZoneAntonym)
        dropZoneSimilar = findViewById(R.id.dropZoneSimilar)
        targetWordText = findViewById(R.id.targetWordText)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        nextButton = Button(this).apply {
            text = "下一个单词"
            visibility = View.GONE
            setOnClickListener {
                loadNextWord() // 🔧 改成 loadNextWord，不再 recreate()
            }
        }

        rootLayout.addView(nextButton)

        semanticWords = TodayReviewCache.getWords().toMutableList() // 🔧 用局部副本控制流程

        if (semanticWords.isEmpty()) {
            Toast.makeText(this, "今日暂无可复习的单词", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            loadNextWord()
        }
    }

    // 🔧 每次加载一个词
    private fun loadNextWord() {
        answeredCorrectly.clear()
        expectedAnswers.clear()
        bubbleContainer.removeAllViews()

        // ***重要：清除拖拽區域的子視圖，以確保每次加載新詞時清空之前的內容***
        dropZoneSynonym.removeAllViews()
        dropZoneAntonym.removeAllViews()
        dropZoneSimilar.removeAllViews()

        nextButton.visibility = View.GONE

        if (currentIndex >= semanticWords.size) {
            Toast.makeText(this, "语义复习完成！", Toast.LENGTH_SHORT).show()
            TodayReviewCache.clear()
            finish()
            return
        }

        currentWord = semanticWords[currentIndex]
        currentIndex++

        targetWordText.text = "目标词：${currentWord.word}"

        val options = mutableListOf<Pair<String, String>>()

        currentWord.synonyms.forEach {
            options.add(it to "synonym")
            expectedAnswers[it] = "synonym"
        }

        currentWord.antonyms.forEach {
            options.add(it to "antonym")
            expectedAnswers[it] = "antonym"
        }

        currentWord.similarWords.forEach {
            options.add(it to "similar")
            expectedAnswers[it] = "similar"
        }

        if (options.isEmpty()) {
            Toast.makeText(this, "当前单词没有可拖拽词，已跳过", Toast.LENGTH_SHORT).show()
            loadNextWord() // 跳过当前词
            return
        }

        options.shuffle()
        for ((word, category) in options) {
            val btn = Button(this).apply {
                text = word
                tag = category
                setBackgroundColor(Color.parseColor("#EEEEEE"))
                setPadding(24, 16, 24, 16)
                setOnLongClickListener {
                    val data = ClipData.newPlainText("wordCategory", "$word|$category")
                    val shadow = View.DragShadowBuilder(this)
                    it.startDragAndDrop(data, shadow, it, 0)
                    true
                }
            }
            bubbleContainer.addView(btn)
        }

        // 設置拖拽區域的監聽器
        setDropZone(dropZoneSynonym, "synonym")
        setDropZone(dropZoneAntonym, "antonym")
        setDropZone(dropZoneSimilar, "similar")

        // *** 新增：動態添加標籤 TextView ***
        addCategoryLabelToDropZone(dropZoneSynonym, "同义词")
        addCategoryLabelToDropZone(dropZoneAntonym, "反义词")
        addCategoryLabelToDropZone(dropZoneSimilar, "形近词")
    }

    // 新增一個輔助函數來添加標籤
    private fun addCategoryLabelToDropZone(dropZone: LinearLayout, labelText: String) {
        val labelTextView = TextView(this).apply {
            text = labelText
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD // *** 這裡進行了修正 ***
            setTextColor(Color.BLACK) // 確保文字顏色是黑色
            // 可以根據需要添加 padding 或 margin
            // layoutParams = LinearLayout.LayoutParams(
            //     LinearLayout.LayoutParams.WRAP_CONTENT,
            //     LinearLayout.LayoutParams.WRAP_CONTENT
            // ).apply {
            //     bottomMargin = 16 // 設置底部間距，讓拖入的詞語不會緊貼標籤
            // }
        }
        dropZone.addView(labelTextView, 0) // 添加到最前面，確保總是在頂部
    }

    @SuppressLint("SetTextI18n")
    private fun setDropZone(layout: LinearLayout, expectedCategory: String) {
        // 保存原始背景色
        val originalBackgroundColor = when (expectedCategory) {
            "synonym" -> Color.parseColor("#CCEEFF")
            "antonym" -> Color.parseColor("#FFD6D6")
            "similar" -> Color.parseColor("#FFF9C4")
            else -> Color.WHITE // 默認值
        }

        // 首次設置背景色
        layout.setBackgroundColor(originalBackgroundColor) // 確保在設置監聽器時就應用原始顏色

        layout.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true

                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.setBackgroundColor(Color.LTGRAY)
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    view.setBackgroundColor(originalBackgroundColor)
                    true
                }

                DragEvent.ACTION_DROP -> {
                    view.setBackgroundColor(originalBackgroundColor)
                    val item = event.clipData.getItemAt(0).text.toString()
                    val (word, draggedCategory) = item.split("|")

                    if (expectedCategory == draggedCategory) {
                        if (answeredCorrectly.contains(word)) {
                            Toast.makeText(this, "$word 已拖拽完成", Toast.LENGTH_SHORT).show()
                            return@setOnDragListener true
                        }

                        val textView = TextView(this).apply {
                            text = word
                            textSize = 16f
                            setPadding(12, 6, 12, 6)
                            setTextColor(Color.BLACK) // 确保拖入的单词是黑色
                        }
                        layout.addView(textView) // 直接添加到 LinearLayout 的末尾

                        answeredCorrectly.add(word)
                        bubbleContainer.removeView(findButtonByText(word))

                        checkIfAllCorrect()
                    } else {
                        val tip = getRootDifferenceTip(currentWord, word)
                        Toast.makeText(this, "拖错了：$word 不属于该类别\n$tip", Toast.LENGTH_SHORT).show()
                    }

                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    // 无论拖拽结果如何，拖拽结束后都恢复到原始背景色
                    view.setBackgroundColor(originalBackgroundColor)
                    true
                }
                else -> false
            }
        }
    }

    private fun findButtonByText(text: String): View? {
        for (i in 0 until bubbleContainer.childCount) {
            val view = bubbleContainer.getChildAt(i)
            if ((view as? Button)?.text == text) return view
        }
        return null
    }

    private fun checkIfAllCorrect() {
        if (answeredCorrectly.size == expectedAnswers.size) {
            Toast.makeText(this, "恭喜，全部分类正确！", Toast.LENGTH_SHORT).show()
            nextButton.visibility = View.VISIBLE
        }
    }

    private fun getRootDifferenceTip(word: Word, wrongWord: String): String {
        val rootA = word.rootWord ?: ""
        val rootB = WordRepository.getWords(this).find { it.word.equals(wrongWord, true) }?.rootWord ?: "未知"

        return if (rootA != rootB)
            "词根不同：${word.word}（$rootA） vs $wrongWord（$rootB）"
        else
            "词义相关但分类错误"
    }
}
