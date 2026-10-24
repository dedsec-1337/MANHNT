package com.manhunt.app.util

import kotlin.random.Random

/**
 * Short codes for room join + hider catch PINs.
 */
object RandomCode {
    private val letters = ('A'..'Z').toList()
    private val digits = ('0'..'9').toList()

    fun generateCode(length: Int = 6): String {
        return (1..length).map { letters.random() }.joinToString("")
    }

    fun generateNumericCode(length: Int = 4): String {
        return (1..length).map { digits.random() }.joinToString("")
    }
}
