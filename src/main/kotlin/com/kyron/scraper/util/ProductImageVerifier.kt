package com.kyron.scraper.util

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import javax.imageio.ImageIO

/** Checks real image bytes before persistence; no rehosting or fabricated fallback images. */
class ProductImageVerifier(private val load: (String) -> ByteArray = ::download) {
    private val cache = object : LinkedHashMap<String, Boolean>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 4096
    }
    fun verify(url: String): Boolean {
        if (!ProductQuality.isHttpsUrl(url)) return false
        return cache.getOrPut(url) { runCatching {
            val bytes = load(url)
            require(bytes.size <= MAX_BYTES)
            ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
                require(input != null)
                val readers = ImageIO.getImageReaders(input)
                require(readers.hasNext())
                val reader = readers.next()
                try {
                    reader.input = input
                    val w = reader.getWidth(0); val h = reader.getHeight(0)
                    require(w > 0 && h > 0 && w.toLong() * h <= 40_000_000)
                    require(reader.read(0) != null)
                } finally { reader.dispose() }
            }
            true
        }.getOrDefault(false) }
    }
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private fun download(value: String): ByteArray {
            var uri = URI(value)
            repeat(4) {
                require(ProductQuality.isHttpsUrl(uri.toString()))
                val addresses = InetAddress.getAllByName(uri.host)
                require(addresses.isNotEmpty() && addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress ||
                    it.isSiteLocalAddress || it.isLinkLocalAddress || it.isMulticastAddress ||
                    (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) })
                val conn = uri.toURL().openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", com.kyron.scraper.config.Config.userAgent)
                    conn.connectTimeout = 5_000; conn.readTimeout = 10_000
                    val code = conn.responseCode
                    if (code in 300..399) {
                        uri = uri.resolve(conn.getHeaderField("Location") ?: error("Missing redirect location"))
                    } else {
                        check(code in 200..299) { "Image HTTP $code" }
                        return conn.inputStream.use { it.readNBytes(MAX_BYTES + 1) }.also { require(it.size <= MAX_BYTES) }
                    }
                } finally { conn.disconnect() }
            }
            error("Too many image redirects")
        }
    }
}
