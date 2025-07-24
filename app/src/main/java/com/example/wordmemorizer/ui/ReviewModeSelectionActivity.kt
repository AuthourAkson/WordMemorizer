package com.example.wordmemorizer.ui

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R

class ReviewModeSelectionActivity : AppCompatActivity() {

    private lateinit var todayWordsJson: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        // 接收从 MainActivity 传来的 JSON 数据
        todayWordsJson = intent.getStringExtra("todayWords") ?: "[]"

        findViewById<Button>(R.id.buttonStandardReview).setOnClickListener {
            val intent = Intent(this, ReviewActivity::class.java)
            intent.putExtra("todayWords", todayWordsJson)
            startActivity(intent)
        }

        findViewById<Button>(R.id.buttonSemanticReview).setOnClickListener {
            val intent = Intent(this, SemanticReviewActivity::class.java)
            intent.putExtra("todayWords", todayWordsJson)
            startActivity(intent)
        }

        findViewById<Button>(R.id.buttonDefinitionQuiz).setOnClickListener {
            val intent = Intent(this, DefinitionQuizActivity::class.java)
            // 不需要传递 todayWordsJson，DefinitionQuizActivity 会直接从 TodayReviewCache 获取
            startActivity(intent)
        }

        // 新增的“巩固应用”按钮的点击事件
        findViewById<Button>(R.id.buttonConsolidationMode).setOnClickListener {
            val intent = Intent(this, ConsolidationModeSelectionActivity::class.java)
            // 如果 ConsolidationModeSelectionActivity 需要 todayWordsJson，也可以傳遞
            // intent.putExtra("todayWords", todayWordsJson)
            startActivity(intent)
        }
    }
}
