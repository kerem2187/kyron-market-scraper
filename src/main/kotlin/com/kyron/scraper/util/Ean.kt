package com.kyron.scraper.util

/** EAN/UPC barcode normalization and validation. */
object Ean {

    /** Normalizes any raw barcode string to a valid 13-digit EAN, or null. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val digits = raw.filter { it.isDigit() }
        return when (digits.length) {
            13 -> if (isValidEan13(digits)) digits else null
            12 -> normalize("0$digits")            // UPC-A -> EAN-13
            8  -> if (isValidEan8(digits)) digits else null
            else -> null
        }
    }

    fun isValidEan13(code: String): Boolean {
        if (code.length != 13 || code.any { !it.isDigit() }) return false
        val sum = code.take(12).mapIndexed { i, c ->
            val d = c - '0'
            if (i % 2 == 0) d else d * 3
        }.sum()
        val check = (10 - (sum % 10)) % 10
        return check == (code[12] - '0')
    }

    fun isValidEan8(code: String): Boolean {
        if (code.length != 8 || code.any { !it.isDigit() }) return false
        val sum = code.take(7).mapIndexed { i, c ->
            val d = c - '0'
            if (i % 2 == 0) d * 3 else d
        }.sum()
        val check = (10 - (sum % 10)) % 10
        return check == (code[7] - '0')
    }
}
