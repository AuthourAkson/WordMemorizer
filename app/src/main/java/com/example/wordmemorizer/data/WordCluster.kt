package com.example.wordmemorizer.data

data class WordCluster(
    val rootWord: String,          // 簇的基础词
    val words: MutableList<Word>,  // 簇中的单词
    var isAntonymCluster: Boolean = false // 是否是反义词簇
) {
    fun addWord(word: Word) {
        words.add(word)
    }
}