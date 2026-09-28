package com.kyron.scraper

import com.kyron.scraper.model.Product
import com.kyron.scraper.pipeline.MergePolicy
import kotlin.test.*

class MergePolicyTest {
    private fun product(source: String, confidence: Double, brand: String? = null, image: String? = null) =
        Product("8690504084686", "$source name", brand, image, source = source, confidence = confidence, updatedAt = 1)

    @Test fun lowerTrustFillsGapsWithoutOverwriting() {
        val trusted = product("off", 1.0, brand = "Trusted")
        val lower = product("sok", .7, brand = "Wrong", image = "https://img")
        val merged = MergePolicy.merge(trusted, lower)
        assertEquals("Trusted", merged.brand)
        assertEquals("https://img", merged.imageUrl)
        assertEquals("off", merged.source)
    }

    @Test fun mergeIsIndependentOfSourceOrder() {
        val a = product("off", 1.0, brand = "A")
        val b = product("sok", .7, image = "https://img")
        assertEquals(MergePolicy.merge(a, b).copy(updatedAt = 0), MergePolicy.merge(b, a).copy(updatedAt = 0))
    }
}
