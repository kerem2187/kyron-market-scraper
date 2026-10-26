package com.kyron.scraper.util

import com.kyron.scraper.model.RawProduct
import java.net.URI

/** Never invent a GTIN, use SKU as barcode, or persist incomplete catalog records. */
object ProductQuality {
    fun normalized(raw: RawProduct): RawProduct? {
        val code = raw.barcode?.takeIf { it.matches(Regex("[0-9]{8}|[0-9]{12,13}")) }
            ?.let(Ean::normalize) ?: return null
        val name = TextNormalizer.cleanName(raw.name).takeIf { it.isNotBlank() } ?: return null
        val image = raw.sourceImageUrl?.trim()?.takeIf(::isHttpsUrl) ?: return null
        return raw.copy(name = name, barcode = code, sourceImageUrl = image)
    }

    fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
}
