package com.example.wordmemorizer.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.ThemeRepository
import com.example.wordmemorizer.data.ThemeCategory
import com.example.wordmemorizer.data.Word
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson

class ThemeClassificationActivity : AppCompatActivity() {

    private lateinit var wordTextView: TextView
    private lateinit var definitionTextView: TextView
    private lateinit var exampleTextView: TextView
    private lateinit var themesContainer: ChipGroup
    private lateinit var submitButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var nextButton: Button

    private var currentWord: Word? = null
    private val selectedThemes = mutableSetOf<String>()
    private val TAG = "ThemeClassification"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_classification)

        wordTextView = findViewById(R.id.wordTextView)
        definitionTextView = findViewById(R.id.definitionTextView)
        exampleTextView = findViewById(R.id.exampleTextView)
        themesContainer = findViewById(R.id.themesContainer)
        submitButton = findViewById(R.id.submitButton)
        resultTextView = findViewById(R.id.resultTextView)
        nextButton = findViewById(R.id.nextButton)

        resultTextView.visibility = View.GONE
        nextButton.visibility = View.GONE

        val wordJson = intent.getStringExtra("currentWordJson")
        currentWord = Gson().fromJson(wordJson, Word::class.java)

        currentWord?.let { word ->
            wordTextView.text = word.word
            definitionTextView.text = word.definition
            exampleTextView.text = word.example?.joinToString("\n") ?: ""
            loadThemes(word)
        } ?: run {
            Toast.makeText(this, "無法加載單詞信息", Toast.LENGTH_SHORT).show()
            finish()
        }

        submitButton.setOnClickListener {
            checkClassification()
        }

        nextButton.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("updatedWordJson", Gson().toJson(currentWord))
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadThemes(word: Word) {
        themesContainer.removeAllViews()

        val allThemes = ThemeRepository.getAllThemes()
        val correctThemesForWord = allThemes.filter { themeCategory ->
            themeCategory.relatedWords.any { it.equals(word.word, ignoreCase = true) }
        }.toMutableSet()

        val otherThemes = allThemes.filter { themeCategory ->
            !correctThemesForWord.contains(themeCategory)
        }.shuffled()

        val themesToDisplay = mutableListOf<ThemeCategory>()
        themesToDisplay.addAll(correctThemesForWord)

        var count = themesToDisplay.size
        for (themeCategory in otherThemes) {
            if (count >= 8) break
            if (!themesToDisplay.contains(themeCategory)) {
                themesToDisplay.add(themeCategory)
                count++
            }
        }

        themesToDisplay.shuffle()

        for (themeCategory in themesToDisplay) {
            val themeChip = Chip(this).apply {
                text = themeCategory.theme
                tag = themeCategory.theme
                isCheckable = true

                // 初始颜色设置
                setChipBackgroundColor(
                    ContextCompat.getColorStateList(context, R.color.default_theme_chip_background)
                )
                setTextColor(
                    ContextCompat.getColorStateList(context, R.color.default_theme_chip_text)
                )
                setChipStrokeColorResource(R.color.default_theme_chip_stroke)
                chipStrokeWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    1f,
                    resources.displayMetrics
                )

                setOnCheckedChangeListener { chip, isChecked ->
                    if (isChecked) {
                        selectedThemes.add(themeCategory.theme)
                        setChipBackgroundColor(
                            ContextCompat.getColorStateList(context, R.color.selected_theme_background)
                        )
                        setTextColor(
                            ContextCompat.getColorStateList(context, R.color.selected_theme_chip_text)
                        )
                    } else {
                        selectedThemes.remove(themeCategory.theme)
                        setChipBackgroundColor(
                            ContextCompat.getColorStateList(context, R.color.default_theme_chip_background)
                        )
                        setTextColor(
                            ContextCompat.getColorStateList(context, R.color.default_theme_chip_text)
                        )
                    }
                }
            }
            themesContainer.addView(themeChip)
        }
    }

    private fun checkClassification() {
        currentWord?.let { word ->
            val correctThemes = ThemeRepository.getAllThemes().filter { themeCategory ->
                themeCategory.relatedWords.any { it.equals(word.word, ignoreCase = true) }
            }.map { it.theme }.toSet()

            val isCorrect = selectedThemes.containsAll(correctThemes) && correctThemes.containsAll(selectedThemes)

            if (isCorrect) {
                resultTextView.text = "分类正确！"
                resultTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                val missing = correctThemes.subtract(selectedThemes)
                val extra = selectedThemes.subtract(correctThemes)
                var feedback = "分类错误。\n"
                if (missing.isNotEmpty()) {
                    feedback += "缺少主题: ${missing.joinToString(", ")}\n"
                }
                if (extra.isNotEmpty()) {
                    feedback += "多余主题: ${extra.joinToString(", ")}\n"
                }
                feedback += "正确主题: ${correctThemes.joinToString(", ")}"
                resultTextView.text = feedback
                resultTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            resultTextView.visibility = View.VISIBLE
            submitButton.visibility = View.GONE
            nextButton.visibility = View.VISIBLE

            for (i in 0 until themesContainer.childCount) {
                val chip = themesContainer.getChildAt(i) as? Chip
                chip?.isEnabled = false
            }
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
