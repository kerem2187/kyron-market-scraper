package com.kyron.scraper

import com.kyron.scraper.util.Ean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EanTest {
    @Test
    fun validEan13() {
        assertTrue(Ean.isValidEan13("8690504084686")) // sample TR product barcode
        assertEquals("8690504084686", Ean.normalize("8690504084686"))
    }

    @Test
    fun upcAtoEan13() {
        // 12-digit UPC-A gets a leading zero and is revalidated.
        val normalized = Ean.normalize("036000291452")
        assertEquals("0036000291452", normalized)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Ean.normalize("not-a-barcode"))
        assertNull(Ean.normalize("123"))
        assertNull(Ean.normalize("8690504084680")) // bad check digit
    }
}
