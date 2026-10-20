package com.kyron.scraper.macrocenter

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kyron.scraper.util.Ean
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Reads the actual bars, never OCR/guesses digits. Ignores QR codes on packaging. */
object LabelBarcodeDecoder {
    private val hints = mapOf<DecodeHintType, Any>(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A),
        DecodeHintType.TRY_HARDER to true,
    )

    fun decode(bytes: ByteArray): String? {
        if (bytes.size > 8 * 1024 * 1024) return null
        val image = runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width.toLong() * height > 20_000_000) return null
                    reader.read(0)
                } finally { reader.dispose() }
            }
        }.getOrNull() ?: return null
        return decode(image)
    }

    fun decode(image: BufferedImage): String? {
        if (image.width.toLong() * image.height > 20_000_000) return null
        decodeAtResolution(image)?.let { return it }
        // Real labels can have sub-two-pixel bars. Bicubic upsampling helps ZXing
        // distinguish these without inventing digits or depending on OCR.
        if (image.width.toLong() * image.height * 4 > 20_000_000) return null
        val enlarged = BufferedImage(image.width * 2, image.height * 2, BufferedImage.TYPE_INT_RGB)
        enlarged.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(image, 0, 0, enlarged.width, enlarged.height, null)
            dispose()
        }
        return decodeAtResolution(enlarged)
    }

    private fun decodeAtResolution(image: BufferedImage): String? {
        var source: LuminanceSource = BufferedImageLuminanceSource(image)
        repeat(4) {
            for (candidate in listOf(source, source.invert())) {
                val code = runCatching {
                    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text
                }.getOrNull()
                Ean.normalize(code)?.let { return it }
            }
            if (!source.isRotateSupported) return null
            source = source.rotateCounterClockwise()
        }
        return null
    }
}
