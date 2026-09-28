package com.kyron.scraper.marketfiyati

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class MarketFiyatiParserTest {
    private val json = Json
    @Test fun acceptsExplicitBarcode() {
        val obj = json.parseToJsonElement("""{"id":"8690504084686","barcode":"8690504084686"}""").jsonObject
        assertEquals("8690504084686", MarketFiyatiParser.extractBarcode(obj))
    }
    @Test fun rejectsGenericIds() {
        val obj = json.parseToJsonElement("""{"id":"8690504084686","productId":"8690504084686"}""").jsonObject
        assertNull(MarketFiyatiParser.extractBarcode(obj))
    }
}
