package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.ThemeRepository
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordNetUtils
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.sm2.SM2
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexWrap
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import androidx.core.view.isEmpty
import androidx.core.view.isGone

class ReviewActivity : AppCompatActivity() {

    private lateinit var wordText: TextView
    private lateinit var ipaText: TextView // 新增音标 TextView
    private lateinit var definitionText: TextView
    private lateinit var exampleText: TextView
    private lateinit var buttonNext: MaterialButton // 改为 MaterialButton
    private lateinit var ratingGroup: RadioGroup
    private lateinit var tts: TextToSpeech
    private lateinit var cardLayout: MaterialCardView // 类型改为 MaterialCardView
    private lateinit var hintText: TextView

    private var cardRevealed = false

    private lateinit var semanticHintText: TextView
    private lateinit var semanticLayout: LinearLayout
    private lateinit var synonymsText: TextView
    private lateinit var rootText: TextView
    private lateinit var antonymsText: TextView
    private lateinit var selectedWordDefinition: TextView
    private lateinit var buttonAddToReview: MaterialButton // 改为 MaterialButton
    private lateinit var semanticMapContainer: LinearLayout
    private lateinit var semanticResultContainer: LinearLayout
    private lateinit var buttonPlayPronunciation: MaterialButton // 改为 MaterialButton

    private val regularReviewQueue = LinkedList<Word>()
    private val specialModeWords = LinkedList<Word>()

    private val shownWords = mutableSetOf<String>()
    private var currentWord: Word? = null

    private var isTtsInitialized = false
    private val TAG = "ReviewActivity"

    private val REQUEST_CODE_THEME_CLASSIFICATION = 1
    private val REQUEST_CODE_SEMANTIC_DRAG = 2
    private val REQUEST_CODE_DEFINITION_QUIZ = 3
    private val REQUEST_CODE_SENTENCE_COMPLETION = 4
    private val REQUEST_CODE_SYNONYM_REPLACEMENT = 5
    private val REQUEST_CODE_CLOZE_TEST = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        // 应用进入动画
        val mainLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.review_main_layout)
        val animation = AnimationUtils.loadAnimation(this, R.anim.fade_in_from_bottom)
        mainLayout.startAnimation(animation)

        // UI 元素初始化，重点修改类型
        wordText = findViewById(R.id.wordText)
        ipaText = findViewById(R.id.ipaText) // 初始化音标 TextView
        definitionText = findViewById(R.id.definitionText)
        exampleText = findViewById(R.id.exampleText)
        buttonNext = findViewById(R.id.buttonNext) // 确保类型是 MaterialButton
        ratingGroup = findViewById(R.id.ratingGroup)
        cardLayout = findViewById(R.id.cardLayout) // 类型改为 MaterialCardView
        hintText = findViewById(R.id.hintText)

        semanticHintText = findViewById(R.id.semanticHintText)
        semanticLayout = findViewById(R.id.semanticLayout)
        synonymsText = findViewById(R.id.synonymsText)
        rootText = findViewById(R.id.rootText)
        antonymsText = findViewById(R.id.antonymsText)
        selectedWordDefinition = findViewById(R.id.selectedWordDefinition)
        buttonAddToReview = findViewById(R.id.buttonAddToReview) // 确保类型是 MaterialButton
        semanticMapContainer = findViewById(R.id.semanticMapContainer)
        semanticResultContainer = findViewById(R.id.semanticResultContainer)
        buttonPlayPronunciation = findViewById(R.id.buttonPlayPronunciation) // 确保类型是 MaterialButton

        // 初始可见性设置
        semanticLayout.visibility = View.GONE
        // ratingGroup 和 buttonNext 的可见性由 cardLayout.setOnClickListener 控制
        ratingGroup.visibility = View.GONE
        buttonNext.visibility = View.GONE
        definitionText.visibility = View.GONE
        exampleText.visibility = View.GONE


        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS语言不支持", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "TTS 语言不支持或缺少数据")
                    isTtsInitialized = false
                } else {
                    isTtsInitialized = true
                    Log.d(TAG, "TTS 初始化成功")
                }
            } else {
                Toast.makeText(this, "TTS初始化失败", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "TTS 初始化失败, status: $status")
                isTtsInitialized = false
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS 播放开始: $utteranceId")
                runOnUiThread {
                    buttonPlayPronunciation.isEnabled = false
                }
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS 播放完成: $utteranceId")
                runOnUiThread {
                    buttonPlayPronunciation.isEnabled = true
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS 播放错误 (Deprecated): $utteranceId")
                runOnUiThread {
                    buttonPlayPronunciation.isEnabled = true
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS 播放错误: $utteranceId, code: $errorCode")
                runOnUiThread {
                    buttonPlayPronunciation.isEnabled = true
                }
            }
        })


        buttonPlayPronunciation.setOnClickListener {
            currentWord?.let { word ->
                speakWord(word.word)
            } ?: Toast.makeText(this, "暂无单词可发音", Toast.LENGTH_SHORT).show()
        }


        semanticHintText.setOnClickListener {
            semanticLayout.visibility = View.VISIBLE
            currentWord?.let {
                // 如果语义文本内容为空，则填充
                if (synonymsText.text.isBlank() && antonymsText.text.isBlank() && rootText.text.isBlank()) {
                    populateSemanticGraph(it)
                }
                // 如果语义图谱容器为空，则显示图谱
                if (semanticMapContainer.isEmpty()) { // 使用 childCount == 0 检查是否为空
                    showSemanticMap(it)
                }
            }
        }

        // cardLayout 现在是 MaterialCardView
        cardLayout.setOnClickListener {
            if (!cardRevealed) {
                hintText.visibility = View.GONE
                definitionText.visibility = View.VISIBLE
                exampleText.visibility = View.VISIBLE
                cardRevealed = true
                ratingGroup.visibility = View.VISIBLE
                buttonNext.visibility = View.VISIBLE
            }
        }

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

                currentWord?.let { word ->
                    val updated = SM2.updateWord(word, score)
                    CoroutineScope(Dispatchers.IO).launch {
                        WordRepository.updateWord(applicationContext, updated)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ReviewActivity, "${updated.word} 評分成功！", Toast.LENGTH_SHORT).show()
                            ratingGroup.clearCheck()
                            showNextReviewStage()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "请先选择评分", Toast.LENGTH_SHORT).show()
            }
        }

        val wordsToReviewJson = intent.getStringExtra("todayWordsJson")
        loadAndClusterWords(wordsToReviewJson)
    }

    private fun speakWord(word: String) {
        if (isTtsInitialized) {
            tts.stop()
            val result = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak error for: $word")
                Toast.makeText(this, "语音播放失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "TTS 未初始化，无法播放语音: $word")
            Toast.makeText(this, "语音功能初始化中或不可用", Toast.LENGTH_SHORT).show()
        }
    }


    private fun parseWordsFromJson(json: String?): List<Word> {
        return try {
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Word>>() {}.type
                Gson().fromJson(json, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 JSON 失敗: ${e.message}")
            emptyList()
        }
    }

    private fun loadAndClusterWords(wordsToReviewJson: String?) {
        val allDueWords = parseWordsFromJson(wordsToReviewJson)

        if (allDueWords.isEmpty()) {
            Toast.makeText(this, "沒有需要复习的单词！", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        regularReviewQueue.clear()
        specialModeWords.clear()
        shownWords.clear()

        val level3WordsTemp = LinkedList<Word>()
        val level4WordsTemp = LinkedList<Word>()
        val level5WordsTemp = LinkedList<Word>()

        for (word in allDueWords) {
            if (shownWords.contains(word.word.lowercase())) {
                continue
            }

            if (word.interval >= 40) {
                if (word.clozeTestExamples.isNotEmpty()) {
                    Log.d(TAG, "Word ${word.word} added to Level 5 (Cloze Test) queue. Interval: ${word.interval}")
                    level5WordsTemp.add(word)
                    shownWords.add(word.word.lowercase())
                } else {
                    Log.d(TAG, "Word ${word.word} qualifies for Level 5 interval, but no cloze test. Adding to regular queue.")
                    regularReviewQueue.add(word)
                    shownWords.add(word.word.lowercase())
                }
            }
            else if (word.interval in 20..39) { // Level 4
                val hasFillInBlank = word.fillInTheBlankExamples.isNotEmpty()
                val hasSynonymReplacement = word.synonymReplacementExamples.isNotEmpty()
                if (hasFillInBlank || hasSynonymReplacement) {
                    Log.d(TAG, "Word ${word.word} added to Level 4 (Sentence Completion/Synonym Replacement) queue. Interval: ${word.interval}")
                    level4WordsTemp.add(word)
                    shownWords.add(word.word.lowercase())
                } else {
                    Log.d(TAG, "Word ${word.word} qualifies for Level 4 interval, but no special tasks. Adding to regular queue.")
                    regularReviewQueue.add(word)
                    shownWords.add(word.word.lowercase())
                }
            }
            else if (word.interval in 10..19 && word.definition.isNotBlank()) { // Level 3
                Log.d(TAG, "Word ${word.word} added to Level 3 (Definition Quiz) queue. Interval: ${word.interval}")
                level3WordsTemp.add(word)
                shownWords.add(word.word.lowercase())
            }
            else if (word.interval in 5..9) { // Level 2
                val hasRelatedThemes = ThemeRepository.getAllThemes().any { theme ->
                    theme.relatedWords.any {
                        it.equals(word.word, ignoreCase = true)
                    }
                }
                val hasSemanticRelatedWords = word.synonyms.isNotEmpty() || word.antonyms.isNotEmpty() || word.similarWords.isNotEmpty()

                if (hasRelatedThemes || hasSemanticRelatedWords) {
                    Log.d(TAG, "Word ${word.word} added to Level 2 (Special Modes) queue. Has themes: $hasRelatedThemes, Has semantic: $hasSemanticRelatedWords, Interval: ${word.interval}")
                    specialModeWords.add(word)
                    shownWords.add(word.word.lowercase())
                } else {
                    Log.d(TAG, "Word ${word.word} qualifies for Level 2, but no special tasks. Adding to regular queue. Interval: ${word.interval}")
                    regularReviewQueue.add(word)
                    shownWords.add(word.word.lowercase())
                }
            }
            else { // Level 1 (or other intervals)
                Log.d(TAG, "Word ${word.word} added to regular review queue. Interval: ${word.interval}")
                regularReviewQueue.add(word)
                shownWords.add(word.word.lowercase())
            }
        }

        // 优先级：Level 5 -> Level 4 -> Level 3 -> Level 2 -> Level 1 (Regular)
        specialModeWords.addAll(0, level3WordsTemp)
        specialModeWords.addAll(0, level4WordsTemp)
        specialModeWords.addAll(0, level5WordsTemp) // <<--- 将 Level 5 放在最前面

        Log.d(TAG, "Initial queues: specialModeWords.size=${specialModeWords.size}, regularReviewQueue.size=${regularReviewQueue.size}")

        showNextReviewStage()
    }

    private fun showNextReviewStage() {
        if (specialModeWords.isNotEmpty()) {
            currentWord = specialModeWords.poll()
            if (currentWord != null) {
                if (currentWord!!.interval >= 40) { // <<--- Level 5 逻辑
                    if (currentWord!!.clozeTestExamples.isNotEmpty()) {
                        Log.d(TAG, "Starting Level 5 (Cloze Test) for ${currentWord!!.word}")
                        startClozeTest(currentWord!!)
                    } else {
                        Log.w(TAG, "Word ${currentWord!!.word} qualifies for Level 5 interval, but no cloze test. Adding to regular queue.")
                        regularReviewQueue.add(currentWord!!)
                        showNextReviewStage()
                    }
                }
                else if (currentWord!!.interval in 20..39) {
                    if (currentWord!!.fillInTheBlankExamples.isNotEmpty()) {
                        Log.d(TAG, "Starting Level 4 (Sentence Completion) for ${currentWord!!.word}")
                        startSentenceCompletion(currentWord!!)
                    } else if (currentWord!!.synonymReplacementExamples.isNotEmpty()) {
                        Log.d(TAG, "Word ${currentWord!!.word} has no fill-in-blank, proceeding to Level 4 (Synonym Replacement).")
                        startSynonymReplacement(currentWord!!)
                    } else {
                        Log.w(TAG, "Word ${currentWord!!.word} qualifies for Level 4 interval, but no special tasks. Adding to regular queue.")
                        regularReviewQueue.add(currentWord!!)
                        showNextReviewStage()
                    }
                }
                else if (currentWord!!.interval in 10..19 && currentWord!!.definition.isNotBlank()) {
                    Log.d(TAG, "Starting Level 3 (Definition Quiz) for ${currentWord!!.word}")
                    startDefinitionQuiz(currentWord!!)
                } else if (currentWord!!.interval in 5..9) {
                    val hasRelatedThemes = ThemeRepository.getAllThemes().any { theme ->
                        theme.relatedWords.any { it.equals(currentWord!!.word, ignoreCase = true) }
                    }
                    if (hasRelatedThemes) {
                        Log.d(TAG, "Starting Level 2 (Theme Classification) for ${currentWord!!.word}")
                        startThemeClassification(currentWord!!)
                    } else {
                        Log.d(TAG, "Word ${currentWord!!.word} has no themes, skipping Theme Classification, proceeding to Semantic Drag.")
                        startSemanticDrag(currentWord!!)
                    }
                } else {
                    Log.w(TAG, "Unexpected word in specialModeWords: ${currentWord!!.word} with interval ${currentWord!!.interval}. Adding to regular queue.")
                    regularReviewQueue.add(currentWord!!)
                    showNextReviewStage()
                }
            } else {
                showNextReviewStage() // currentWord is null, try next
            }
        } else if (regularReviewQueue.isNotEmpty()) {
            Log.d(TAG, "Starting Regular Review. Remaining: ${regularReviewQueue.size}")
            showNextWord()
        } else {
            Toast.makeText(this, "所有到期单词都已复习完成！", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startDefinitionQuiz(word: Word) {
        val intent = Intent(this, DefinitionQuizActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_DEFINITION_QUIZ)
    }

    private fun startThemeClassification(word: Word) {
        val intent = Intent(this, ThemeClassificationActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_THEME_CLASSIFICATION)
    }

    private fun startSemanticDrag(word: Word) {
        val intent = Intent(this, SemanticDragActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_SEMANTIC_DRAG)
    }

    private fun startSentenceCompletion(word: Word) {
        val intent = Intent(this, SentenceCompletionActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_SENTENCE_COMPLETION)
    }

    private fun startSynonymReplacement(word: Word) {
        val intent = Intent(this, SynonymReplacementActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_SYNONYM_REPLACEMENT)
    }

    // 新增：启动完形填空活动
    private fun startClozeTest(word: Word) {
        val intent = Intent(this, ClozeTestActivity::class.java)
        intent.putExtra("currentWordJson", Gson().toJson(word))
        startActivityForResult(intent, REQUEST_CODE_CLOZE_TEST)
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val updatedWordJson = data?.getStringExtra("updatedWordJson")
        val processedWord: Word? = if (updatedWordJson != null) {
            Gson().fromJson(updatedWordJson, Word::class.java)
        } else {
            currentWord
        }

        if (processedWord == null) {
            Log.e(TAG, "onActivityResult: received null word for requestCode $requestCode. Proceeding to next stage.")
            showNextReviewStage()
            return
        }

        when (requestCode) {
            REQUEST_CODE_DEFINITION_QUIZ -> {
                Log.d(TAG, "Definition Quiz finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                showNextReviewStage()
            }
            REQUEST_CODE_THEME_CLASSIFICATION -> {
                Log.d(TAG, "Theme Classification finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                if (resultCode == Activity.RESULT_OK) {
                    val hasSemanticRelatedWords = processedWord.synonyms.isNotEmpty() || processedWord.antonyms.isNotEmpty() || processedWord.similarWords.isNotEmpty()
                    if (processedWord.interval in 5..9 && hasSemanticRelatedWords) {
                        Log.d(TAG, "Proceeding to Semantic Drag for ${processedWord.word}.")
                        startSemanticDrag(processedWord)
                    } else {
                        Log.d(TAG, "${processedWord.word} doesn't need Semantic Drag (interval ${processedWord.interval}, hasSemantic: $hasSemanticRelatedWords). Proceeding to next stage.")
                        showNextReviewStage()
                    }
                } else {
                    Log.d(TAG, "Theme Classification for ${processedWord.word} was cancelled. Proceeding to next stage.")
                    showNextReviewStage()
                }
            }
            REQUEST_CODE_SEMANTIC_DRAG -> {
                Log.d(TAG, "Semantic Drag finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                showNextReviewStage()
            }
            REQUEST_CODE_SENTENCE_COMPLETION -> {
                Log.d(TAG, "Sentence Completion finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                if (resultCode == Activity.RESULT_OK) {
                    if (processedWord.interval in 20..39 && processedWord.synonymReplacementExamples.isNotEmpty()) {
                        Log.d(TAG, "Proceeding to Synonym Replacement for ${processedWord.word}.")
                        startSynonymReplacement(processedWord)
                    } else {
                        Log.d(TAG, "${processedWord.word} doesn't need Synonym Replacement (interval ${processedWord.interval}, has examples: ${processedWord.synonymReplacementExamples.isNotEmpty()}). Proceeding to next stage.")
                        showNextReviewStage()
                    }
                } else {
                    Log.d(TAG, "Sentence Completion for ${processedWord.word} was cancelled. Proceeding to next stage.")
                    showNextReviewStage()
                }
            }
            REQUEST_CODE_SYNONYM_REPLACEMENT -> {
                Log.d(TAG, "Synonym Replacement finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                showNextReviewStage()
            }
            REQUEST_CODE_CLOZE_TEST -> { // 新增：处理完形填空的结果
                Log.d(TAG, "Cloze Test finished for ${processedWord.word}. Result: ${if (resultCode == Activity.RESULT_OK) "OK" else "CANCELED"}")
                // 完形填空活动负责更新SM2参数，所以直接进入下一个复习阶段
                showNextReviewStage()
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun populateSemanticGraph(word: Word) {
        fun makeClickableList(words: List<String>, label: TextView) {
            if (words.isEmpty()) {
                label.text = "无"
                return
            }

            val spannable = SpannableString(words.joinToString(" | "))
            var start = 0

            words.forEach { w ->
                val end = start + w.length
                val clickable = object : ClickableSpan() {
                    @SuppressLint("SetTextI18n")
                    override fun onClick(widget: View) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val allWords = WordRepository.getWords(this@ReviewActivity)
                            val matched = allWords.find { it.word.equals(w, ignoreCase = true) }
                            withContext(Dispatchers.Main) {
                                if (matched != null) {
                                    selectedWordDefinition.text = "${matched.word}：${matched.definition}"
                                    buttonAddToReview.visibility = View.VISIBLE
                                    buttonAddToReview.setOnClickListener {
                                        if (shownWords.add(matched.word.lowercase())) {
                                            regularReviewQueue.add(matched)
                                            Toast.makeText(this@ReviewActivity, "${matched.word} 已加入复习队列", Toast.LENGTH_SHORT).show()
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
                    }
                }
                spannable.setSpan(clickable, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = end + 3
            }
            label.text = spannable
            label.movementMethod = LinkMovementMethod.getInstance()
        }

        makeClickableList(currentWord?.synonyms ?: emptyList(), synonymsText)
        rootText.text = "词根：${currentWord?.rootWord ?: "无"}，词性：${currentWord?.partOfSpeech ?: "無"}"
        makeClickableList(currentWord?.antonyms ?: emptyList(), antonymsText)
    }

    @SuppressLint("SetTextI18n")
    private fun showSemanticMap(word: Word) {
        CoroutineScope(Dispatchers.IO).launch {
            val allWords = WordRepository.getWords(this@ReviewActivity)
            val map = WordNetUtils.getSemanticMap(word, allWords)

            withContext(Dispatchers.Main) {
                semanticMapContainer.removeAllViews()

                for ((label, words) in map) {
                    if (words.isEmpty()) continue

                    val title = TextView(this@ReviewActivity).apply {
                        text = "$label："
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        // setPadding(0, dpToPx(16), 0, dpToPx(8)) // XML已设置，这里可以移除
                    }

                    semanticMapContainer.addView(title)

                    val flowLayout = FlexboxLayout(this@ReviewActivity).apply {
                        flexWrap = FlexWrap.WRAP
                        visibility = View.GONE
                    }

                    words.forEach { related ->
                        // 使用 MaterialButton 代替 Button
                        val btn = MaterialButton(this@ReviewActivity).apply {
                            text = related.word
                            // 设置 MaterialButton 样式，例如 TextButton
                            // style = com.google.android.material.R.style.Widget_Material3_Button_TextButton
                            setOnClickListener {
                                showDefinitionUnderMap(related)
                            }
                        }
                        flowLayout.addView(btn)
                    }

                    if (label == "同词性" || label == "同词根") {
                        // 使用 MaterialButton 代替 Button
                        val toggleButton = MaterialButton(this@ReviewActivity).apply {
                            text = "展开${label}单词 ▼"
                            // style = com.google.android.material.R.style.Widget_Material3_Button_TextButton
                            setOnClickListener {
                                if (flowLayout.isGone) {
                                    flowLayout.visibility = View.VISIBLE
                                    text = "收起${label}单词 ▲"
                                } else {
                                    flowLayout.visibility = View.GONE
                                    text = "展开${label}单词 ▼"
                                }
                            }
                        }
                        semanticMapContainer.addView(toggleButton)
                    } else {
                        flowLayout.visibility = View.VISIBLE
                    }

                    semanticMapContainer.addView(flowLayout)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showDefinitionUnderMap(word: Word) {
        semanticResultContainer.removeAllViews()

        val defView = TextView(this).apply {
            text = "${word.word} 的释义：\n${word.definition}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            // setPadding(0, dpToPx(16), 0, dpToPx(8)) // XML已设置，这里可以移除
        }
        semanticResultContainer.addView(defView)

        // 使用 MaterialButton 代替 Button
        val addButton = MaterialButton(this).apply {
            text = "加入今日复习"
            setOnClickListener {
                if (shownWords.add(word.word.lowercase())) {
                    regularReviewQueue.add(word)
                    Toast.makeText(this@ReviewActivity, "已加入复习队列", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ReviewActivity, "该词已在复习队列当中", Toast.LENGTH_SHORT).show()
                }
            }
        }
        semanticResultContainer.addView(addButton)
    }

    @SuppressLint("SetTextI18n")
    private fun showNextWord() {
        if (regularReviewQueue.isEmpty()) {
            showNextReviewStage()
            return
        }

        cardRevealed = false
        hintText.visibility = View.VISIBLE
        definitionText.visibility = View.GONE
        exampleText.visibility = View.GONE
        ratingGroup.visibility = View.GONE
        buttonNext.visibility = View.GONE

        currentWord = regularReviewQueue.poll()
        currentWord?.let { word ->
            val ipa = word.pronunciation.takeIf { it.isNotBlank() } ?: ""
            wordText.text = word.word
            ipaText.text = if (ipa.isNotEmpty()) "[$ipa]" else "" // 更新音标 TextView

            definitionText.text = "释义：${word.definition}"
            exampleText.text = "例句：\n${word.example?.joinToString("\n")}"

            semanticLayout.visibility = View.GONE
            semanticMapContainer.removeAllViews()
            semanticResultContainer.removeAllViews()
            selectedWordDefinition.text = ""
            buttonAddToReview.visibility = View.GONE
            synonymsText.text = ""
            antonymsText.text = ""
            rootText.text = ""
            semanticHintText.visibility = View.GONE // 初始隐藏语义图谱提示，待需要时再显示
            // 重新显示 semanticHintText 只有在需要的时候
            if (word.synonyms.isNotEmpty() || word.antonyms.isNotEmpty() || word.similarWords.isNotEmpty() || !word.rootWord.isNullOrBlank() || !word.partOfSpeech.isNullOrBlank()) {
                semanticHintText.visibility = View.VISIBLE
            }


            speakWord(word.word)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val resultIntent = Intent()
        resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }
}