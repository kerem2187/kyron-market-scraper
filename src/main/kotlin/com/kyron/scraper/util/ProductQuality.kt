package com.kyron.scraper.util

import com.kyron.scraper.model.RawProduct
import java.net.URI
import java.util.Locale

/** Never invent a GTIN, use SKU as barcode, or persist records without barcode + name. Image is optional but must be HTTPS if present. */
object ProductQuality {
    private val LETTER_REGEX = Regex("[A-Za-zÇĞİÖŞÜçğıöşü]")
    private val PLACEHOLDER_NAMES = setOf(
        "test", "unknown", "null", "none", "n/a",
        "product", "ürün", "yok", "loading"
    )

    fun normalized(raw: RawProduct): RawProduct? {
        val code = raw.barcode?.takeIf { it.matches(Regex("[0-9]{8}|[0-9]{12,13}")) }
            ?.let(Ean::normalize) ?: return null
        val name = TextNormalizer.cleanName(raw.name).takeIf(::isMeaningfulName) ?: return null
        val image = raw.sourceImageUrl?.trim()?.takeIf(::isHttpsUrl)
        return raw.copy(name = name, barcode = code, sourceImageUrl = image)
    }

    fun isMeaningfulName(name: String): Boolean {
        val cleaned = TextNormalizer.cleanName(name)
        if (cleaned.length < 3) return false
        if (cleaned.lowercase(Locale.ROOT) in PLACEHOLDER_NAMES) return false
        val distinctLetters = LETTER_REGEX.findAll(cleaned)
            .map { it.value.lowercase(Locale.ROOT) }
            .toSet()
        return distinctLetters.size >= 2
    }

    fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
}
