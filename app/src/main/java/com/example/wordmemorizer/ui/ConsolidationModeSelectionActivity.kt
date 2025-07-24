package com.example.wordmemorizer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R

class ConsolidationModeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consolidation_mode_selection)

        // 任務一：句子填空
        findViewById<Button>(R.id.buttonSentenceCompletion).setOnClickListener {
            val intent = Intent(this, SentenceCompletionActivity::class.java)
            startActivity(intent)
        }

        // 任務二：同義替換 (現在啟用)
        findViewById<Button>(R.id.buttonSynonymReplacement).setOnClickListener {
            val intent = Intent(this, SynonymReplacementActivity::class.java) // <--- 解除註釋並啟用
            startActivity(intent)
            // Toast.makeText(this, "同义替换功能待開發", Toast.LENGTH_SHORT).show() // 移除這行
        }
    }
}