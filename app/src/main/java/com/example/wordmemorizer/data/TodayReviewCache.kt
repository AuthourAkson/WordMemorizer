package com.example.wordmemorizer.data

object TodayReviewCache {
    private var todayWords: List<Word> = emptyList()

    fun setWords(words: List<Word>) {
        todayWords = words
    }

    fun getWords(): List<Word> = todayWords

    fun clear() {
        todayWords = emptyList()
    }
}
