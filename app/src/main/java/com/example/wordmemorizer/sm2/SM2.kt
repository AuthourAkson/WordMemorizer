package com.example.wordmemorizer.sm2

import com.example.wordmemorizer.data.Word
import java.util.concurrent.TimeUnit

object SM2 {
    fun updateWord(word: Word, quality: Int): Word {
        var ef = word.easinessFactor
        var interval = word.interval
        var repetitions = word.repetitions

        if (quality < 3) {
            repetitions = 0
            interval = 1
        } else {
            repetitions += 1
            when (repetitions) {
                1 -> interval = 1
                2 -> interval = 6
                else -> interval = (interval * ef).toInt()
            }
        }

        ef += 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
        if (ef < 1.3) ef = 1.3

        val nextReview = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(interval.toLong())

        return word.copy(
            easinessFactor = ef,
            interval = interval,
            repetitions = repetitions,
            nextReviewDate = nextReview
        )
    }
}
