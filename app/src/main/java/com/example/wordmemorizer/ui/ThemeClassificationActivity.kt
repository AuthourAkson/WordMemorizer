package com.example.wordmemorizer.ui

import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.databinding.ActivityThemeClassificationBinding
import com.example.wordmemorizer.data.ThemeCategory
import com.example.wordmemorizer.data.ThemeRepository
import com.example.wordmemorizer.data.TodayReviewCache
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word // 確保 Word 數據類被正確導入

class ThemeClassificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeClassificationBinding
    private var themeIndex = 0
    private var matchedThemes: List<ThemeCategory> = emptyList()
    private var currentWordsInPool: MutableList<String> = mutableListOf() // 用於追蹤當前單詞池中的單詞

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeClassificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val allWordsForReview = TodayReviewCache.getWords()
        if (allWordsForReview.isEmpty()) {
            Toast.makeText(this, "沒有今日複習單詞", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 篩選出包含今日複習單詞的主題
        // 使用 toSet() 提高查找效率
        val allWordTexts = allWordsForReview.map { it.word }.toSet()
        matchedThemes = ThemeRepository.getAllThemes().filter { theme ->
            theme.relatedWords.any { relatedWord -> allWordTexts.contains(relatedWord) }
        }

        if (matchedThemes.isEmpty()) {
            Toast.makeText(this, "沒有匹配的主題", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始隱藏「下一組主題」按鈕
        binding.buttonNextTheme.visibility = View.GONE

        showNextThemes()

        binding.buttonNextTheme.setOnClickListener {
            // 檢查當前顯示的主題所涉及的單詞是否都已分類
            if (binding.wordPool.childCount == 0) { // 如果單詞池為空，說明當前批次單詞都已分類
                if (themeIndex < matchedThemes.size) { // 還有更多主題可以顯示
                    showNextThemes()
                } else {
                    // 沒有更多主題了，顯示完成消息
                    Toast.makeText(this, "沒有更多的主題可以分類了", Toast.LENGTH_LONG).show()
                    // 如果所有單詞都處理完了，並且沒有更多主題，可以考慮清空緩存並結束
                    if (TodayReviewCache.getWords().isEmpty()) {
                        Toast.makeText(this, "今日任務完成", Toast.LENGTH_SHORT).show()
                        TodayReviewCache.clear()
                    }
                    finish() // 結束 Activity
                }
            } else {
                Toast.makeText(this, "請先完成當前單詞的分類", Toast.LENGTH_SHORT).show()
            }
        }

        setupDragListeners()
    }

    private fun showNextThemes() {
        // 在顯示新主題前，確保「下一組主題」按鈕是隱藏的
        binding.buttonNextTheme.visibility = View.GONE

        val themesToShow = matchedThemes.drop(themeIndex).take(3)
        if (themesToShow.isEmpty()) {
            Toast.makeText(this, "沒有更多主題了", Toast.LENGTH_SHORT).show()
            if (TodayReviewCache.getWords().isEmpty()) {
                Toast.makeText(this, "今日任務完成", Toast.LENGTH_SHORT).show()
                TodayReviewCache.clear()
            }
            finish() // 結束 Activity
            return
        }

        // 更新 themeIndex 以便下次獲取下一組主題
        themeIndex += 3

        val themeLabels = listOf(binding.themeLabel1, binding.themeLabel2, binding.themeLabel3)
        val themeBoxes = listOf(binding.themeBox1, binding.themeBox2, binding.themeBox3)

        // 清空之前的內容和標籤，並重置 themeBoxes 的 tag
        themeBoxes.forEachIndexed { i, box ->
            // 保留 themeLabel 在 box 內部，只移除拖拽進來的單詞
            // 由於 themeLabel 已經在 XML 中定義為 themeBox 的第一個子元素，我們應該只移除 index > 0 的視圖
            val viewsToRemove = mutableListOf<View>()
            for (j in 1 until box.childCount) {
                viewsToRemove.add(box.getChildAt(j))
            }
            viewsToRemove.forEach { box.removeView(it) }

            // 確保每次都重置 themeLabel 的文本，防止舊數據殘留
            themeLabels[i].text = ""
            box.tag = null
        }
        binding.wordPool.removeAllViews() // 清空單詞池

        // 綁定新主題和顯示標籤
        themesToShow.forEachIndexed { i, theme ->
            if (i < themeLabels.size) { // 確保索引不越界
                themeLabels[i].text = theme.theme
                themeBoxes[i].tag = theme
            }
        }

        // 收集與當前顯示主題相關的今日複習單詞
        val wordsToDisplayInPool = mutableListOf<String>()
        val currentReviewWords = TodayReviewCache.getWords().map { it.word }.toSet() // 使用Set提高查找效率
        themesToShow.forEach { theme ->
            theme.relatedWords.forEach { relatedWord ->
                if (currentReviewWords.contains(relatedWord) && !wordsToDisplayInPool.contains(relatedWord)) {
                    wordsToDisplayInPool.add(relatedWord)
                }
            }
        }
        currentWordsInPool = wordsToDisplayInPool.toMutableList() // 更新當前單詞池追蹤列表

        // 將單詞添加到單詞池中
        // 在 showNextThemes() 方法中
        for (wordText in currentWordsInPool) {
            val tv = TextView(this).apply {
                text = wordText
                textSize = 20f
                // setPadding 已經在 draggable_word_background 中定義，可以選擇移除或保持
                // setPadding(16, 8, 16, 8)
                background = getDrawable(R.drawable.draggable_word_background) // 添加這行
                setOnLongClickListener {
                    it.startDragAndDrop(null, View.DragShadowBuilder(it), it, 0)
                    true
                }
            }
            binding.wordPool.addView(tv)
        }
    }

    private fun setupDragListeners() {
        val boxes = listOf(binding.themeBox1, binding.themeBox2, binding.themeBox3)

        for (box in boxes) {
            box.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        // 可以添加一些視覺反饋，例如改變可拖拽區域的背景
                        true
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        // 可以添加視覺反饋，表示進入了拖拽目標區域
                        true
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> {
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        // 移除視覺反饋
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        val draggedView = event.localState as? TextView ?: return@setOnDragListener true
                        val wordText = draggedView.text.toString()
                        val theme = view.tag as? ThemeCategory

                        if (theme?.relatedWords?.contains(wordText) == true) {
                            // **關鍵修改：在添加之前，先從原來的父視圖中移除**
                            val owner = draggedView.parent as? ViewGroup
                            owner?.removeView(draggedView)

                            (view as? ViewGroup)?.addView(draggedView)
                            draggedView.visibility = View.VISIBLE // 確保拖拽後的視圖可見

                            // 從 TodayReviewCache 中移除已分類的單詞
                            val remainingWords = TodayReviewCache.getWords().filter { it.word != wordText }
                            TodayReviewCache.setWords(remainingWords)

                            // 從當前單詞池追蹤列表中移除
                            currentWordsInPool.remove(wordText)

                            Toast.makeText(this, "分類正確", Toast.LENGTH_SHORT).show()

                            // 檢查是否所有單詞都已分類完畢
                            checkClassificationCompletion()

                        } else {
                            draggedView.visibility = View.VISIBLE // 如果分類錯誤，將單詞返回到原位
                            Toast.makeText(this, "分類錯誤", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        val draggedView = event.localState as? TextView
                        if (!event.result) { // 如果拖拽沒有成功（例如沒有放下在有效區域）
                            draggedView?.visibility = View.VISIBLE // 確保單詞可見
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     检查单词池中的单词是否分类完毕
     */
    private fun checkClassificationCompletion() {
        if (binding.wordPool.childCount == 0) { // 當前頁面的單詞池清空
            if (TodayReviewCache.getWords().isEmpty()) {
                // 如果所有今日複習單詞都已處理完
                Toast.makeText(this, "今日任務完成", Toast.LENGTH_SHORT).show()
                TodayReviewCache.clear()
                finish() // 結束 Activity
            } else {
                // 如果當前頁面的單詞池清空，但 TodayReviewCache 中還有其他單詞，
                // 並且還有更多主題可以顯示，則顯示「下一組主題」按鈕
                if (themeIndex < matchedThemes.size) {
                    binding.buttonNextTheme.visibility = View.VISIBLE
                } else {
                    // 沒有更多主題了，但 TodayReviewCache 還有單詞，這表示有些單詞沒有匹配的主題
                    Toast.makeText(this, "沒有更多的主題可以分類了，部分單詞未匹配主題", Toast.LENGTH_LONG).show()
                    finish() // 結束 Activity
                }
            }
        } else {
            // 單詞池還有單詞，隱藏按鈕
            binding.buttonNextTheme.visibility = View.GONE
        }
    }
}