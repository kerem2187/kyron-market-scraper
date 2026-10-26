package com.kyron.scraper
import com.kyron.scraper.util.ProductImageVerifier
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
class ProductImageVerifierTest {
    @Test fun decodesActualImageAndCachesResult() {
        val bytes=ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",it) }.toByteArray()
        var downloads=0; val verifier=ProductImageVerifier { downloads++;bytes }
        repeat(2) { assertTrue(verifier.verify("https://example.test/product.png")) }; assertEquals(1,downloads)
    }
    @Test fun rejectsHtmlEvenWhenUrlLooksLikeImage() {
        assertFalse(ProductImageVerifier { "<html>access denied</html>".toByteArray() }.verify("https://example.test/image.jpg"))
    }
    @Test fun rejectsNonHttpsWithoutFetching() {
        assertFalse(ProductImageVerifier { error("Must not fetch") }.verify("http://example.test/image.png"))
    }
    @Test fun rejectsOversizedDownload() {
        assertFalse(ProductImageVerifier { ByteArray(ProductImageVerifier.MAX_BYTES+1) }.verify("https://example.test/image.jpg"))
    }
}
