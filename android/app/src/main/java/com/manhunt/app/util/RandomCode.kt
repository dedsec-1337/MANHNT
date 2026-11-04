package com.manhunt.app.util

import java.security.SecureRandom

/**
 * Short codes for room join + hider catch PINs.
 *
 * Uses [SecureRandom] so codes cannot be predicted from earlier ones. Note the search space is
 * still small (26^6 join codes, 10^4 PINs): unpredictability is not brute-force resistance, which
 * must come from server-side rate limiting (see the Firebase rules).
 */
object RandomCode {
    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private val rng = SecureRandom()

    fun generateCode(length: Int = 6): String = generate(LETTERS, length)

    fun generateNumericCode(length: Int = 4): String = generate(DIGITS, length)

    private fun generate(alphabet: String, length: Int): String = buildString(length.coerceAtLeast(0)) {
        repeat(length) { append(alphabet[rng.nextInt(alphabet.length)]) }
    }
}
