// MainActivity.kt
package com.example.wordmemorizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wordmemorizer.data.TodayReviewCache
import com.example.wordmemorizer.data.WordRepository
import com.example.wordmemorizer.data.ThemeRepository // 確保導入
import com.example.wordmemorizer.ui.AddWordActivity
import com.example.wordmemorizer.ui.ReviewModeSelectionActivity
import com.example.wordmemorizer.utils.ImportWordContract
import com.google.gson.Gson
import java.io.BufferedReader
import java.util.Calendar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val importWordLauncher = registerForActivityResult(ImportWordContract()) { uriString ->
        uriString?.let { uriStr ->
            lifecycleScope.launch {
                try {
                    val uri = Uri.parse(uriStr)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val json = inputStream.bufferedReader().use(BufferedReader::readText)
                        val result = WordRepository.importWordsFromJson(this@MainActivity, json)
                        Toast.makeText(
                            this@MainActivity,
                            "导入 ${result.total} 个单词，其中 ${result.withIPA} 个生成了音标，${result.failedIPA} 个失败。",
                            Toast.LENGTH_LONG
                        ).show()
                        // 导入成功后，重新初始化 WordRepository 和生成今天的填空列表，确保数据同步
                        WordRepository.init(this@MainActivity) // 重新初始化缓存
                        WordRepository.ensureFillInBlankWordsForToday(this@MainActivity) // 重新生成今天的填空列表
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_failed, e.localizedMessage),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonAdd = findViewById<Button>(R.id.buttonAdd)
        val buttonReview = findViewById<Button>(R.id.buttonReview)
        val buttonImport = findViewById<Button>(R.id.buttonImport)

        // 確保 WordRepository 在任何操作之前被初始化
        WordRepository.init(this)
        ThemeRepository.init(this) // 通常主題庫的初始化不依賴於單詞庫，可以獨立存在

        // 在 WordRepository 初始化之後，生成或檢查今天的填空單詞列表
        WordRepository.ensureFillInBlankWordsForToday(this)


        buttonAdd.setOnClickListener {
            startActivity(Intent(this, AddWordActivity::class.java))
        }

        buttonReview.setOnClickListener {
            val allWords = WordRepository.getWords(this) // 從持久化存儲獲取所有單詞
            val today = Calendar.getInstance().timeInMillis

            val todayWords = allWords.filter { it.nextReviewDate <= today }

            // 將今日單詞暫存
            TodayReviewCache.setWords(todayWords)

            // 將 todayWords 列表序列化為 JSON 字符串
            val gson = Gson()
            val todayWordsJson = gson.toJson(todayWords)

            val intent = Intent(this, ReviewModeSelectionActivity::class.java)
            intent.putExtra("todayWords", todayWordsJson)
            startActivity(intent)
        }

        buttonImport.setOnClickListener {
            importWordLauncher.launch(Unit)
        }
    }
}