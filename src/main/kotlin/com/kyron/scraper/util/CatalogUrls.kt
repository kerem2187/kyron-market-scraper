package com.kyron.scraper.util
import org.jsoup.nodes.Document
import java.net.URI
object CatalogUrls {
    fun links(doc: Document, host: String, marker: String): List<String> = doc.select("a[href]")
        .mapNotNull { anchor -> runCatching {
            val uri=URI(doc.baseUri()).resolve(anchor.attr("href")).normalize()
            if (uri.scheme != "https" || uri.host != host || uri.userInfo != null ||
                uri.port !in listOf(-1,443) || !uri.path.contains(marker)) null
            else URI(uri.scheme,uri.authority,uri.path,null,null).toASCIIString()
        }.getOrNull() }.distinct()
}
