package com.manhunt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomCodeTest {

    @Test
    fun defaultLengths() {
        assertEquals(6, RandomCode.generateCode().length)
        assertEquals(4, RandomCode.generateNumericCode().length)
    }

    @Test
    fun joinCodesAreUppercaseLettersOnly() {
        repeat(500) {
            val code = RandomCode.generateCode(6)
            assertTrue("bad join code $code", code.all { it in 'A'..'Z' })
        }
    }

    @Test
    fun catchPinsAreDigitsOnlyAndKeepLeadingZeros() {
        var sawLeadingZero = false
        repeat(2_000) {
            val pin = RandomCode.generateNumericCode(4)
            assertEquals(4, pin.length)
            assertTrue("bad pin $pin", pin.all { it in '0'..'9' })
            if (pin.startsWith("0")) sawLeadingZero = true
        }
        assertTrue("leading zeros should occur (about 10% of PINs)", sawLeadingZero)
    }

    @Test
    fun everyDigitAndEveryLetterIsReachable() {
        val digits = (1..200).joinToString("") { RandomCode.generateNumericCode(20) }.toSet()
        assertEquals(10, digits.size)
        val letters = (1..200).joinToString("") { RandomCode.generateCode(20) }.toSet()
        assertEquals(26, letters.size)
    }

    @Test
    fun nonPositiveLengthYieldsEmptyString() {
        assertEquals("", RandomCode.generateCode(0))
        assertEquals("", RandomCode.generateNumericCode(-3))
    }

    @Test
    fun codesDoNotRepeatTrivially() {
        val pins = (1..50).map { RandomCode.generateCode(12) }.toSet()
        assertTrue(pins.size > 45)
    }
}
