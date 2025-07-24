package com.example.wordmemorizer.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.sm2.SM2
import com.example.wordmemorizer.data.WordNetUtils
import com.example.wordmemorizer.data.WordCluster
import java.util.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.view.View
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.Spannable
import android.text.method.LinkMovementMethod
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexWrap
import android.util.Log


class ReviewActivity : AppCompatActivity() {
    private lateinit var wordText: TextView
    private lateinit var definitionText: TextView
    private lateinit var exampleText: TextView
    private lateinit var buttonNext: Button
    private lateinit var ratingGroup: RadioGroup
    private lateinit var tts: TextToSpeech
    private lateinit var cardLayout: LinearLayout
    private lateinit var hintText: TextView
    private var cardRevealed = false
    //隐藏卡片内容和语义树的图谱
    private lateinit var semanticHintText: TextView
    private lateinit var semanticLayout: LinearLayout
    private lateinit var synonymsText: TextView
    private lateinit var rootText: TextView
    private lateinit var antonymsText: TextView
    private lateinit var selectedWordDefinition: TextView
    private lateinit var buttonAddToReview: Button
    private lateinit var semanticMapContainer: LinearLayout
    private lateinit var semanticResultContainer: LinearLayout

    //分语义簇的部分
    private val reviewQueue = LinkedList<Word>()
    private val shownWords = mutableSetOf<String>()
    private var currentCluster: List<Word> = emptyList()
    private var currentWordIndex = 0
    private var currentWord: Word? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        wordText = findViewById(R.id.wordText)
        definitionText = findViewById(R.id.definitionText)
        exampleText = findViewById(R.id.exampleText)
        buttonNext = findViewById(R.id.buttonNext)
        ratingGroup = findViewById(R.id.ratingGroup)
        cardLayout = findViewById(R.id.cardLayout)
        hintText = findViewById(R.id.hintText)

        semanticHintText = findViewById(R.id.semanticHintText)
        semanticLayout = findViewById(R.id.semanticLayout)
        synonymsText = findViewById(R.id.synonymsText)
        rootText = findViewById(R.id.rootText)
        antonymsText = findViewById(R.id.antonymsText)
        selectedWordDefinition = findViewById(R.id.selectedWordDefinition)
        buttonAddToReview = findViewById(R.id.buttonAddToReview)
        semanticMapContainer = findViewById(R.id.semanticMapContainer)
        semanticResultContainer = findViewById(R.id.semanticResultContainer)

        val buttonPlayPronunciation = findViewById<Button>(R.id.buttonPlayPronunciation)

        semanticHintText.setOnClickListener {
            semanticLayout.visibility = View.VISIBLE
            currentWord?.let {
                populateSemanticGraph(it)
                showSemanticMap(it)
            }
        }

        cardLayout.setOnClickListener {
            if (!cardRevealed) {
                hintText.visibility = View.GONE
                definitionText.visibility = View.VISIBLE
                exampleText.visibility = View.VISIBLE
                cardRevealed = true
            }
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS语言不支持", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "TTS初始化失败", Toast.LENGTH_SHORT).show()
            }
        }

        buttonPlayPronunciation.setOnClickListener {
            currentWord?.let {
                tts.speak(it.word, TextToSpeech.QUEUE_FLUSH, null, null)
            } ?: Toast.makeText(this, "暂无单词可发音", Toast.LENGTH_SHORT).show()
        }

        loadAndClusterWords()

        buttonNext.setOnClickListener {
            val selectedId = ratingGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val score = when (selectedId) {
                    R.id.score1 -> 1
                    R.id.score2 -> 2
                    R.id.score3 -> 3
                    R.id.score4 -> 4
                    R.id.score5 -> 5
                    else -> 3
                }

                currentWord?.let {
                    val updated = SM2.updateWord(it, score)
                    WordRepository.updateWord(this, updated)
                }

                ratingGroup.clearCheck()
                showNextWord()
            } else {
                Toast.makeText(this, "请先选择评分", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateSemanticGraph(word: Word) {
        fun makeClickableList(words: List<String>, label: TextView) {
            if (words.isEmpty()) {
                label.text = ""
                return
            }

            val spannable = SpannableString(words.joinToString(" | "))
            var start = 0

            words.forEach { w ->
                val end = start + w.length
                val clickable = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val allWords = WordRepository.getWords(this@ReviewActivity)
                        val matched = allWords.find { it.word.equals(w, ignoreCase = true) }

                        if (matched != null) {
                            selectedWordDefinition.text = "${matched.word}：${matched.definition}"
                            buttonAddToReview.visibility = View.VISIBLE
                            buttonAddToReview.setOnClickListener {
                                if (shownWords.add(matched.word.lowercase())) {
                                    reviewQueue.add(matched)
                                    Toast.makeText(this@ReviewActivity, "${matched.word} 已加入复习序列", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@ReviewActivity, "该单词已在复习队列中", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            selectedWordDefinition.text = "$w：暂无释义"
                            buttonAddToReview.visibility = View.GONE
                        }
                    }
                }

                spannable.setSpan(clickable, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = end + 3
            }

            label.text = spannable
            label.movementMethod = LinkMovementMethod.getInstance()

        }

        makeClickableList(word.synonyms, synonymsText)
        rootText.text = "词根：${word.rootWord ?: "无"}，词性：${word.partOfSpeech ?: "无"}"
        makeClickableList(word.antonyms, antonymsText)
    }

    private fun showSemanticMap(word: Word) {
        val allWords = WordRepository.getWords(this)
        val map = WordNetUtils.getSemanticMap(word, allWords)

        semanticMapContainer.removeAllViews()

        for ((label, words) in map) {
            if (words.isEmpty()) continue

            val title = TextView(this).apply {
                text = "$label："
                textSize = 16f
                setPadding(0, 16, 0, 8)
            }

            semanticMapContainer.addView(title)

            val flowLayout = FlexboxLayout(this).apply {
                flexWrap = FlexWrap.WRAP
                visibility = View.GONE  // 默认折叠
            }

            words.forEach { related ->
                val btn = Button(this).apply {
                    text = related.word
                    setOnClickListener {
                        showDefinitionUnderMap(related)
                    }
                }
                flowLayout.addView(btn)
            }

            if (label == "同词性" || label == "同词根") {
                val toggleButton = Button(this).apply {
                    text = "展开${label}单词 ▼"
                    setOnClickListener {
                        if (flowLayout.visibility == View.GONE) {
                            flowLayout.visibility = View.VISIBLE
                            text = "收起${label}单词 ▲"
                        } else {
                            flowLayout.visibility = View.GONE
                            text = "展开${label}单词 ▼"
                        }
                    }
                }
                semanticMapContainer.addView(toggleButton)
            }

            semanticMapContainer.addView(flowLayout)
        }

    }


    private fun showDefinitionUnderMap(word: Word) {
        semanticResultContainer.removeAllViews()

        val defView = TextView(this).apply {
            text = "${word.word} 的释义：\n${word.definition}"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        semanticResultContainer.addView(defView)

        val addButton = Button(this).apply {
            text = "加入今日复习"
            setOnClickListener {
                if (shownWords.add(word.word.lowercase())) {
                    reviewQueue.add(word)
                    Toast.makeText(this@ReviewActivity, "已加入复习队列", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ReviewActivity, "该词已在复习中", Toast.LENGTH_SHORT).show()
                }
            }
        }
        semanticResultContainer.addView(addButton)
    }

    private fun loadAndClusterWords() {
        val now = System.currentTimeMillis()
        val dueWords = WordRepository.getWords(this)
            .filter { it.nextReviewDate <= now }
            .sortedBy { it.word }

        if (dueWords.isEmpty()) {
            Toast.makeText(this, "没有需要复习的单词！", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val clusters = mutableListOf<WordCluster>()
        val remainingWords = dueWords.toMutableList()

        while (remainingWords.isNotEmpty()) {
            val currentWord = remainingWords.removeAt(0)
            val rootWord = WordNetUtils.extractRootWord(currentWord.word)

            var foundCluster = clusters.find { cluster ->
                cluster.rootWord == rootWord ||
                        currentWord.isSemanticallyRelated(cluster.words.first())
            }

            if (foundCluster == null) {
                foundCluster = WordCluster(rootWord, mutableListOf(currentWord))
                clusters.add(foundCluster)
            } else {
                foundCluster.addWord(currentWord)
            }
        }

        val pairedClusters = mutableListOf<WordCluster>()
        val unpairedClusters = clusters.toMutableList()

        while (unpairedClusters.isNotEmpty()) {
            val current = unpairedClusters.removeAt(0)
            val antonymCluster = unpairedClusters.find { cluster ->
                cluster.words.any { word ->
                    word.antonyms.contains(current.rootWord) ||
                            current.words.any { cw -> cw.antonyms.contains(word.word) }
                }
            }?.apply {
                isAntonymCluster = true
            }

            when (antonymCluster) {
                null -> pairedClusters.add(current)
                else -> {
                    unpairedClusters.remove(antonymCluster)
                    pairedClusters.add(current)
                    pairedClusters.add(antonymCluster)
                }
            }
        }

        pairedClusters.forEach { cluster ->
            val mainWord = cluster.words.firstOrNull { it.word.equals(cluster.rootWord, ignoreCase = true) }
                ?: cluster.words.first()

            if (shownWords.add(mainWord.word.lowercase())) {
                reviewQueue.add(mainWord)
            }

            cluster.words.forEach { word ->
                if (word != mainWord && shownWords.add(word.word.lowercase())) {
                    reviewQueue.add(word)
                }
            }
        }

        showNextWord()
    }

    private fun showNextWord() {
        if (reviewQueue.isEmpty()) {
            Toast.makeText(this, "复习完成！", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        cardRevealed = false
        hintText.visibility = View.VISIBLE
        definitionText.visibility = View.GONE
        exampleText.visibility = View.GONE

        currentWord = reviewQueue.poll()
        val ipa = currentWord?.pronunciation?.takeIf { it.isNotBlank() } ?: ""
        wordText.text = if (ipa.isNotEmpty())
            "${currentWord?.word}  [$ipa]"
        else
            currentWord?.word

        definitionText.text = "释义：${currentWord?.definition}"
        exampleText.text = "例句：\n${currentWord?.example?.joinToString("\n")}"


        currentWord?.let { word ->
            if (word.relatedWords.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val allWords = WordRepository.getWords(this)

                word.relatedWords.forEach { related ->
                    allWords.find {
                        it.word.equals(related, ignoreCase = true) &&
                                it.nextReviewDate <= now &&
                                shownWords.add(it.word.lowercase())
                    }?.let { relatedWord ->
                        reviewQueue.add(relatedWord)
                    }
                }
            }
        }

        semanticLayout.visibility = View.GONE
        semanticMapContainer.removeAllViews()
        semanticResultContainer.removeAllViews()
        selectedWordDefinition.text = ""
        buttonAddToReview.visibility = View.GONE
        synonymsText.text = ""
        antonymsText.text = ""
        semanticHintText.visibility = View.VISIBLE

        currentWord?.let {
            tts.speak(it.word, TextToSpeech.QUEUE_FLUSH, null, null)
        }
        // 清空旧的同义词、反义词按钮布局
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
