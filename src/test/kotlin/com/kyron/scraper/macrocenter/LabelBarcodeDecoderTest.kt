package com.kyron.scraper.macrocenter

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

class LabelBarcodeDecoderTest {
    private fun barcode(format: BarcodeFormat, text: String): BufferedImage = MatrixToImageWriter.toBufferedImage(
        MultiFormatWriter().encode(text, format, 400, 140),
    )

    @Test fun decodesEan13AndItsRotatedLabel() {
        val original = barcode(BarcodeFormat.EAN_13, "8690504084686")
        assertEquals("8690504084686", LabelBarcodeDecoder.decode(original))
        val rotated = BufferedImage(original.height, original.width, BufferedImage.TYPE_INT_RGB)
        rotated.createGraphics().apply {
            translate(rotated.width.toDouble(), 0.0); rotate(Math.PI / 2)
            drawImage(original, 0, 0, null); dispose()
        }
        assertEquals("8690504084686", LabelBarcodeDecoder.decode(rotated))
    }

    @Test fun readsBarcodeEmbeddedInLargerPackagingImage() {
        val label = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
        label.createGraphics().apply {
            color = Color.WHITE; fillRect(0, 0, 1200, 800)
            drawImage(barcode(BarcodeFormat.EAN_13, "8690504084686"), 720, 500, null); dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(label, "png", it) }.toByteArray()
        assertEquals("8690504084686", LabelBarcodeDecoder.decode(bytes))
    }

    @Test fun rejectsQrCodesAndNonImages() {
        assertNull(LabelBarcodeDecoder.decode(barcode(BarcodeFormat.QR_CODE, "8690504084686")))
        assertNull(LabelBarcodeDecoder.decode("not an image".toByteArray()))
    }
}
