package com.example.wordmemorizer.data

// WordNetUtils.kt
object WordNetUtils {
    // 分析词根（简化版，实际应用中应使用WordNet或词根分析库）
    fun extractRootWord(word: String): String {
        val suffixes = listOf("ize", "ing", "ed", "ly", "al", "tion", "s", "es")
        var root = word
        for (suffix in suffixes) {
            if (word.endsWith(suffix)) {
                root = word.substring(0, word.length - suffix.length)
                break
            }
        }
        return root.ifEmpty { word }
    }

    // 判断两个单词是否有相同词根
    fun shareRoot(word1: String, word2: String): Boolean {
        return extractRootWord(word1) == extractRootWord(word2)
    }

    // 简单的词性判断（实际应用中应使用NLP库）
    fun guessPartOfSpeech(word: String): String? {
        return when {
            word.endsWith("tion") || word.endsWith("sion") -> "noun"
            word.endsWith("ing") -> "verb"
            word.endsWith("ly") -> "adverb"
            word.endsWith("al") || word.endsWith("ous") -> "adjective"
            else -> null
        }
    }

    fun getSemanticMap(word: Word, allWords: List<Word>): Map<String, List<Word>> {
        val synonyms = allWords.filter {
            it.word != word.word && (
                    word.synonyms.contains(it.word) || it.synonyms.contains(word.word)
                    )
        }

        val antonyms = allWords.filter {
            it.word != word.word && (
                    word.antonyms.contains(it.word) || it.antonyms.contains(word.word)
                    )
        }

        val sameRoot = allWords.filter {
            it.word != word.word && word.rootWord != null && word.rootWord == it.rootWord
        }

        val samePOS = allWords.filter {
            it.word != word.word && word.partOfSpeech != null && word.partOfSpeech == it.partOfSpeech
        }

        return mapOf(
            "近义词" to synonyms,
            "反义词" to antonyms,
            "同词根" to sameRoot,
            "同词性" to samePOS
        )
    }
}