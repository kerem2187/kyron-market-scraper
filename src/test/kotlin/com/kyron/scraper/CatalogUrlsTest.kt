package com.kyron.scraper
import com.kyron.scraper.util.CatalogUrls
import org.jsoup.Jsoup
import kotlin.test.*
class CatalogUrlsTest {
    @Test fun categoryDiscoveryIncludesSubcategoriesAndDeduplicates() {
        val doc=Jsoup.parse("""<a href="/milk-c-123?a=1">Milk</a><a href="/milk-c-123#x">Milk</a><a href="/tea-c-456">Tea</a>""","https://shop.test")
        assertEquals(listOf("https://shop.test/milk-c-123","https://shop.test/tea-c-456"),CatalogUrls.links(doc,"shop.test","-c-"))
    }
    @Test fun rejectsForeignHostsCredentialsAndHttp() {
        val doc=Jsoup.parse("""<a href="https://evil.test/a-c-1"></a><a href="https://user@shop.test/a-c-2"></a><a href="http://shop.test/a-c-3"></a>""","https://shop.test")
        assertTrue(CatalogUrls.links(doc,"shop.test","-c-").isEmpty())
    }
}
