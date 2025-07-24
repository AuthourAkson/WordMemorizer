package com.example.wordmemorizer.data


data class SynonymReplacementEntry(
    val originalSentence: String,
    val originalTranslation: String,
    val recommendedReplacementSentence: String
)

// Word.kt
data class Word(
    val word: String = "",
    val definition: String = "",
    val example: List<String> = emptyList(), // 保持 List<String> 不變
    val fillInTheBlankExamples: List<String> = emptyList(), // 保持 List<String> 不變
    val relatedWords: List<String> = emptyList(),
    val synsets: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val rootWord: String? = null,
    val similarWords: List<String> = emptyList(),
    val partOfSpeech: String? = null,
    var easinessFactor: Double = 2.5,
    var interval: Int = 1,
    var repetitions: Int = 0,
    var nextReviewDate: Long = System.currentTimeMillis(),
    var pronunciation: String = "",
    // 新增字段來追蹤填空題練習的狀態
    var fillInBlankReviewCount: Int = 0,
    var lastFillInBlankReviewDate: Long = 0L,
    val synonymReplacementExamples: List<SynonymReplacementEntry> = emptyList()
) {
    fun isSemanticallyRelated(other: Word): Boolean {
        return this.word == other.word ||
                this.rootWord == other.rootWord ||
                this.synsets.intersect(other.synsets).isNotEmpty() ||
                this.synonyms.contains(other.word) ||
                other.synonyms.contains(this.word)
    }
}