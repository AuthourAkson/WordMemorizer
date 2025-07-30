package com.example.wordmemorizer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.Word
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.sm2.SM2
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SemanticDragActivity : AppCompatActivity() {

    private lateinit var wordTextView: TextView
    private lateinit var definitionTextView: TextView
    private lateinit var draggableWordsContainer: FlexboxLayout
    private lateinit var synonymDropZone: FlexboxLayout
    private lateinit var antonymDropZone: FlexboxLayout
    private lateinit var similarDropZone: FlexboxLayout
    private lateinit var submitButton: MaterialButton
    private lateinit var resultTextView: TextView

    private lateinit var ratingGroup: RadioGroup
    private lateinit var buttonFinishReview: MaterialButton

    // Add colors for chip background/stroke when marking answers
    private var correctChipBackgroundColor: Int = 0
    private var wrongChipBackgroundColor: Int = 0
    private var correctChipStrokeColor: Int = 0
    private var wrongChipStrokeColor: Int = 0
    private var dragEnteredColor: Int = 0


    private var currentWord: Word? = null
    private val TAG = "SemanticDragActivity"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semantic_drag)

        // Initialize colors from resources
        correctChipBackgroundColor = ContextCompat.getColor(this, R.color.green_500)
        wrongChipBackgroundColor = ContextCompat.getColor(this, R.color.red_500)
        correctChipStrokeColor = ContextCompat.getColor(this, R.color.green_500)
        wrongChipStrokeColor = ContextCompat.getColor(this, R.color.red_500)
        dragEnteredColor = ContextCompat.getColor(this, R.color.purple_200)

        wordTextView = findViewById(R.id.wordTextView)
        definitionTextView = findViewById(R.id.definitionTextView)
        draggableWordsContainer = findViewById(R.id.draggableWordsContainer)
        synonymDropZone = findViewById(R.id.synonymDropZone)
        antonymDropZone = findViewById(R.id.antonymDropZone)
        similarDropZone = findViewById(R.id.similarDropZone)
        submitButton = findViewById(R.id.submitButton)
        resultTextView = findViewById(R.id.resultTextView)

        ratingGroup = findViewById(R.id.ratingGroup)
        buttonFinishReview = findViewById(R.id.buttonFinishReview)

        // Ensure MaterialRadioButtons are used
        findViewById<MaterialRadioButton>(R.id.score1)
        findViewById<MaterialRadioButton>(R.id.score2)
        findViewById<MaterialRadioButton>(R.id.score3)
        findViewById<MaterialRadioButton>(R.id.score4)
        findViewById<MaterialRadioButton>(R.id.score5)


        resultTextView.visibility = View.GONE
        ratingGroup.visibility = View.GONE
        buttonFinishReview.visibility = View.GONE

        val wordJson = intent.getStringExtra("currentWordJson")
        currentWord = Gson().fromJson(wordJson, Word::class.java)

        currentWord?.let { word ->
            wordTextView.text = word.word
            definitionTextView.text = word.definition
            setupDragAndDrop(word)
        } ?: run {
            Toast.makeText(this, "無法加載單詞信息", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        submitButton.setOnClickListener {
            checkDragAndDropResult()
        }

        buttonFinishReview.setOnClickListener {
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
                            Toast.makeText(this@SemanticDragActivity, "${updated.word} 评分成功！", Toast.LENGTH_SHORT).show()
                            val resultIntent = Intent()
                            resultIntent.putExtra("updatedWordJson", Gson().toJson(updated))
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "请先选择评分", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDragAndDrop(word: Word) {
        // Clear existing views in drop zones for fresh setup
        synonymDropZone.removeAllViews()
        antonymDropZone.removeAllViews()
        similarDropZone.removeAllViews()

        val allRelatedWords = mutableListOf<String>()
        allRelatedWords.addAll(word.synonyms)
        allRelatedWords.addAll(word.antonyms)
        allRelatedWords.addAll(word.similarWords)
        allRelatedWords.shuffle()

        draggableWordsContainer.removeAllViews()
        for (relatedWord in allRelatedWords) {
            val draggableChip = Chip(this).apply {
                // Ensure LayoutParams are compatible with FlexboxLayout
                layoutParams = FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                text = relatedWord
                // Initial Material Chip styling
                chipBackgroundColor = ContextCompat.getColorStateList(context, android.R.color.white)
                chipStrokeColor = ContextCompat.getColorStateList(context, R.color.purple_200)
                chipStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, resources.displayMetrics)
                setTextColor(ContextCompat.getColorStateList(context, R.color.black))
                isCheckable = false
                isCloseIconVisible = false
                isClickable = true
                isLongClickable = true

                setOnLongClickListener { v ->
                    val item = ClipData.Item(v.tag as? CharSequence)
                    val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    val dragData = ClipData(v.tag.toString(), mimeTypes, item)
                    val shadowBuilder = View.DragShadowBuilder(v)
                    v.startDragAndDrop(dragData, shadowBuilder, v, 0)
                    v.visibility = View.INVISIBLE
                    true
                }
            }
            draggableChip.tag = relatedWord
            draggableWordsContainer.addView(draggableChip)
        }

        setupDropZone(synonymDropZone)
        setupDropZone(antonymDropZone)
        setupDropZone(similarDropZone)
    }

    private fun setupDropZone(dropZone: FlexboxLayout) {
        val defaultDropZoneBackground = ContextCompat.getDrawable(this, R.drawable.drop_zone_background)
        dropZone.background = defaultDropZoneBackground

        dropZone.setOnDragListener { v, event ->
            val draggableItem = event.localState as? Chip // This is the dragged chip
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    (v as? FlexboxLayout)?.background?.setColorFilter(dragEnteredColor, PorterDuff.Mode.SRC_IN)
                    v.invalidate()
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    (v as? FlexboxLayout)?.background?.clearColorFilter()
                    v.invalidate()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    (v as? FlexboxLayout)?.background?.clearColorFilter()
                    v.invalidate()

                    if (draggableItem != null) {
                        val owner = draggableItem.parent as ViewGroup
                        owner.removeView(draggableItem)
                        (v as FlexboxLayout).addView(draggableItem)
                        draggableItem.visibility = View.VISIBLE
                        true
                    } else {
                        false
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    (v as? FlexboxLayout)?.background?.clearColorFilter()
                    v.invalidate()
                    // If the drop was not successful and the item exists
                    if (event.result == false && draggableItem != null) {
                        // Re-add the chip to the draggableWordsContainer if it doesn't have a parent
                        // This prevents crashes if the chip was removed from its original parent
                        // but not added to a new one (e.g., dragged outside a drop zone)
                        if (draggableItem.parent == null) {
                            draggableWordsContainer.addView(draggableItem) // Corrected: use draggableItem
                        }
                        draggableItem.visibility = View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }
    }


    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun checkDragAndDropResult() {
        currentWord?.let { word ->
            var correctCount = 0
            var totalCount = 0

            val synonymCorrect = word.synonyms.toSet()
            val antonymCorrect = word.antonyms.toSet()
            val similarCorrect = word.similarWords.toSet()

            val synonymDropped = getDroppedWordsFromChips(synonymDropZone)
            val antonymDropped = getDroppedWordsFromChips(antonymDropZone)
            val similarDropped = getDroppedWordsFromChips(similarDropZone)

            // Evaluate synonyms
            totalCount += synonymCorrect.size
            for (w in synonymDropped) {
                if (synonymCorrect.contains(w)) {
                    correctCount++
                    markChipInZone(synonymDropZone, w, true)
                } else {
                    markChipInZone(synonymDropZone, w, false)
                }
            }
            // Mark correct synonyms that were NOT dropped in the synonym zone (i.e., still in draggable or wrong zone)
            for (w in synonymCorrect) {
                if (!synonymDropped.contains(w)) {
                    markChipInAllContainers(w, false, synonymCorrect, antonymCorrect, similarCorrect)
                }
            }


            // Evaluate antonyms
            totalCount += antonymCorrect.size
            for (w in antonymDropped) {
                if (antonymCorrect.contains(w)) {
                    correctCount++
                    markChipInZone(antonymDropZone, w, true)
                } else {
                    markChipInZone(antonymDropZone, w, false)
                }
            }
            // Mark correct antonyms that were NOT dropped in the antonym zone
            for (w in antonymCorrect) {
                if (!antonymDropped.contains(w)) {
                    markChipInAllContainers(w, false, synonymCorrect, antonymCorrect, similarCorrect)
                }
            }


            // Evaluate similar words
            totalCount += similarCorrect.size
            for (w in similarDropped) {
                if (similarCorrect.contains(w)) {
                    correctCount++
                    markChipInZone(similarDropZone, w, true)
                } else {
                    markChipInZone(similarDropZone, w, false)
                }
            }
            // Mark correct similar words that were NOT dropped in the similar zone
            for (w in similarCorrect) {
                if (!similarDropped.contains(w)) {
                    markChipInAllContainers(w, false, synonymCorrect, antonymCorrect, similarCorrect)
                }
            }

            // Mark any chips that are still in draggableWordsContainer and are incorrect
            for (i in 0 until draggableWordsContainer.childCount) {
                val child = draggableWordsContainer.getChildAt(i) as? Chip
                if (child != null) {
                    val wordText = child.text.toString()
                    val isCorrectInAnyCategory =
                        synonymCorrect.contains(wordText) || antonymCorrect.contains(wordText) || similarCorrect.contains(wordText)
                    if (isCorrectInAnyCategory) {
                        markChipInZone(draggableWordsContainer, wordText, false)
                    }
                }
            }

            val accuracy = if (totalCount > 0) (correctCount.toDouble() / totalCount) * 100 else 0.0
            resultTextView.text = "正确率: ${String.format("%.2f", accuracy)}% ($correctCount/$totalCount)"
            resultTextView.visibility = View.VISIBLE

            ratingGroup.visibility = View.VISIBLE
            buttonFinishReview.visibility = View.VISIBLE
            submitButton.visibility = View.GONE

            disableAllChips()
        }
    }

    private fun getDroppedWordsFromChips(dropZone: FlexboxLayout): List<String> {
        val words = mutableListOf<String>()
        for (i in 0 until dropZone.childCount) {
            val child = dropZone.getChildAt(i) as? Chip
            child?.let {
                words.add(it.text.toString())
            }
        }
        return words
    }

    private fun markChipInZone(container: ViewGroup, word: String, isCorrect: Boolean) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? Chip
            if (child != null && child.text.toString() == word) {
                child.chipBackgroundColor = ContextCompat.getColorStateList(this, if (isCorrect) R.color.green_500 else R.color.red_500)
                child.chipStrokeColor = ContextCompat.getColorStateList(this, if (isCorrect) R.color.green_500 else R.color.red_500)
                child.chipStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
                child.setTextColor(ContextCompat.getColorStateList(this, android.R.color.white))

                break
            }
        }
    }

    private fun markChipInAllContainers(
        word: String,
        isCorrect: Boolean,
        synonymCorrect: Set<String>,
        antonymCorrect: Set<String>,
        similarCorrect: Set<String>
    ) {
        // Check draggable container
        for (i in 0 until draggableWordsContainer.childCount) {
            val child = draggableWordsContainer.getChildAt(i) as? Chip
            if (child != null && child.text.toString() == word) {
                markChipInZone(draggableWordsContainer, word, false)
                return
            }
        }

        // Check if a correct word was dropped in the WRONG zone
        val allDropZones = listOf(synonymDropZone, antonymDropZone, similarDropZone)
        for (dropZone in allDropZones) {
            for (i in 0 until dropZone.childCount) {
                val child = dropZone.getChildAt(i) as? Chip
                if (child != null && child.text.toString() == word) {
                    val isCorrectPlacement =
                        (dropZone == synonymDropZone && synonymCorrect.contains(word)) ||
                                (dropZone == antonymDropZone && antonymCorrect.contains(word)) ||
                                (dropZone == similarDropZone && similarCorrect.contains(word))

                    if (!isCorrectPlacement) {
                        markChipInZone(dropZone, word, false)
                    }
                    return
                }
            }
        }
    }

    private fun disableAllChips() {
        for (i in 0 until draggableWordsContainer.childCount) {
            val chip = draggableWordsContainer.getChildAt(i) as? Chip
            chip?.isEnabled = false
        }
        for (i in 0 until synonymDropZone.childCount) {
            val chip = synonymDropZone.getChildAt(i) as? Chip
            chip?.isEnabled = false
        }
        for (i in 0 until antonymDropZone.childCount) {
            val chip = antonymDropZone.getChildAt(i) as? Chip
            chip?.isEnabled = false
        }
        for (i in 0 until similarDropZone.childCount) {
            val chip = similarDropZone.getChildAt(i) as? Chip
            chip?.isEnabled = false
        }
    }


    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val resultIntent = Intent()
        resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }
}