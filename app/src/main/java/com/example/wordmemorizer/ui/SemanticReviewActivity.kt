// SemanticReviewActivity.kt
package com.example.wordmemorizer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wordmemorizer.R
import com.example.wordmemorizer.data.ThemeRepository
import com.example.wordmemorizer.utils.ImportThemeContract
import com.example.wordmemorizer.utils.ThemeImporter

class SemanticReviewActivity : AppCompatActivity() {

    // 導入主題的 Launcher，使用您提供的 ImportThemeContract
    private val importThemeLauncher = registerForActivityResult(ImportThemeContract()) { uri ->
        uri?.let {
            try {
                // 使用 ThemeImporter 從 Uri 導入主題
                val themes = ThemeImporter.importFromUri(this, it)
                // 將導入的主題添加到 ThemeRepository 中
                ThemeRepository.addThemes(themes,this)
                Toast.makeText(
                    this,
                    "成功導入 ${themes.size} 個主題分類",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "導入失敗: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } ?: Toast.makeText(this, "未選擇文件", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semantic_review)

        val buttonTheme = findViewById<Button>(R.id.buttonThemeClassification)
        val buttonSemantic = findViewById<Button>(R.id.buttonSemanticDrag)
        val buttonImportThemes = findViewById<Button>(R.id.buttonImportThemes)

        buttonTheme.setOnClickListener {
            // 在啟動 ThemeClassificationActivity 之前檢查是否已導入主題
            if (ThemeRepository.hasThemes()) {
                startActivity(Intent(this, ThemeClassificationActivity::class.java))
            } else {
                Toast.makeText(this, "請先導入主題分類", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSemantic.setOnClickListener {
            // 根據您的應用設計，SemanticDragActivity 可能也需要主題數據
            // 如果它不需要主題數據，這裡可以直接啟動。
            // 如果需要，也要檢查 ThemeRepository.hasThemes()
            startActivity(Intent(this, SemanticDragActivity::class.java))
        }

        buttonImportThemes.setOnClickListener {
            // 點擊按鈕時觸發文件選擇器
            importThemeLauncher.launch(Unit)
        }
    }
}