package me.anno.fonts

import me.anno.gpu.texture.FakeWhiteTexture
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D
import me.anno.ui.base.DefaultRenderingHints
import me.anno.utils.incrementTab
import me.anno.utils.joinChars
import org.apache.logging.log4j.LogManager
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.lang.StrictMath.round
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.streams.toList

// todo triangulation challenge: 😬
class AWTFont(val font: Font): XFont {

    fun prepareGraphics(g2d: Graphics2D){
        g2d.font = font
        g2d.setRenderingHints(DefaultRenderingHints.hints as Map<*,*>)
    }

    val unused = BufferedImage(1,1,1).graphics as Graphics2D
    init {
        prepareGraphics(unused)
    }

    val fontMetrics = unused.fontMetrics

    fun containsSpecialChar(text: String): Boolean {
        for(cp in text.codePoints()){
            if(cp > 127 || cp == '\t'.toInt()) return true
        }
        return false
    }

    fun String.countLines() = count { it == '\n' } + 1

    override fun generateTexture(text: String, fontSize: Float): ITexture2D? {

        if(text.isEmpty()) return null
        if(containsSpecialChar(text)) return generateTextureV3(text, fontSize)

        val width = fontMetrics.stringWidth(text) + (if(font.isItalic) max(2, (fontSize / 5f).roundToInt()) else 1)
        val lineCount = text.countLines()
        val spaceBetweenLines = (0.5f * fontSize).roundToInt()
        val fontHeight = fontMetrics.height
        val height = fontHeight * lineCount + (lineCount - 1) * spaceBetweenLines

        if(width < 1 || height < 1) return null
        if(text.isBlank()){
            // we need some kind of wrapper around texture2D
            // and return an empty/blank texture
            // that the correct size is returned is required by text input fields
            // (with whitespace at the start or end)
            return FakeWhiteTexture(width, height)
        }

        val texture = Texture2D(width, height, 1)
        texture.create {

            val image = BufferedImage(width, height, 1)
            val gfx = image.graphics as Graphics2D
            prepareGraphics(gfx)

            val x = 0
            val y = fontMetrics.ascent

            if(lineCount == 1){
                gfx.drawString(text, x, y)
            } else {
                val lines = text.split('\n')
                lines.forEachIndexed { index, line ->
                    gfx.drawString(line, x, y + index * (fontHeight + spaceBetweenLines))
                }
            }
            gfx.dispose()
            image

        }

        return texture

    }

    fun splitParts(text: String, fontSize: Float, relativeTabSize: Float): PartResult {

        val fonts = listOf(font, getFallback(fontSize))

        fun getSupportLevel(char: Int, lastSupportLevel: Int): Int {
            fonts.forEachIndexed { index, font ->
                if(font.canDisplay(char)) return index
            }
            return lastSupportLevel
        }

        val lines = text.split('\n')
        val result = ArrayList<StringPart>(lines.size * 2)
        val ctx = FontRenderContext(null, true, true)
        val exampleLayout = TextLayout("o", font, ctx)
        val tabSize = exampleLayout.advance * relativeTabSize
        var widthF = 0f
        var currentY = 0f
        val fontHeight = exampleLayout.ascent + exampleLayout.descent
        var startResultIndex = 0
        lines.forEach { line ->
            val cp = line.codePoints().toList()
            var startIndex = 0
            var index = 0
            var lastSupportLevel = 0
            var currentX = 1f
            fun display(){
                if(index > startIndex){
                    val substring = cp.subList(startIndex, index).joinChars()
                    val font = fonts[lastSupportLevel]
                    val layout = TextLayout(substring, font, ctx)
                    // val bounds = layout.bounds
                    result += StringPart(currentX, currentY, substring, font, 0f)
                    currentX += layout.advance
                    widthF = max(widthF, currentX)
                    startIndex = index
                }
            }
            while(index < cp.size){
                val char = cp[index]
                if(char == '\t'.toInt()){
                    display()
                    startIndex++ // skip \t too
                    currentX = incrementTab(currentX, tabSize, relativeTabSize)
                } else {
                    val supportLevel = getSupportLevel(char, lastSupportLevel)
                    if(supportLevel != lastSupportLevel){
                        display()
                        lastSupportLevel = supportLevel
                    }
                }
                index++
            }
            display()
            for(i in startResultIndex until result.size){
                result[i].lineWidth = currentX
            }
            startResultIndex = result.size
            currentY += fontHeight
        }

        return PartResult(result, widthF, currentY, exampleLayout)

    }

    fun generateTextureV3(text: String, fontSize: Float): Texture2D? {

        val parts = splitParts(text, fontSize, 4f)
        val result = parts.parts
        val exampleLayout = parts.exampleLayout

        val width = ceil(parts.width)
        val height = ceil(parts.height)

        // println("$width for ${result.size} parts")

        val texture = Texture2D(width, height, 1)
        texture.create {
            val image = BufferedImage(width, height, 1)
            if(result.isNotEmpty()){
                val gfx = image.graphics as Graphics2D
                prepareGraphics(gfx)

                val x = (image.width - width) * 0.5f
                val y = (image.height - height) * 0.5f + exampleLayout.ascent

                result.forEach {
                    gfx.font = it.font
                    gfx.drawString(it.text, it.xPos + x, it.yPos + y)
                }

                gfx.dispose()
            }
            image
        }

        return texture

    }

    /*fun generateTexture2(text: String, fontSize: Float): Texture2D? {

        val withIcons = createFallbackString(text, font, getFallback(fontSize))
        val layout = TextLayout(withIcons.iterator, unused.fontRenderContext)
        val bounds = layout.bounds

        val width = ceil(bounds.width)
        val height = ceil(layout.ascent + layout.descent)

        val texture = Texture2D(width, height, 1)
        texture.create {
            val image = BufferedImage(width, height, 1)
            val gfx = image.graphics as Graphics2D
            prepareGraphics(gfx)

            val x = (image.width - width) * 0.5f
            val y = (image.height - height) * 0.5f + layout.ascent

            gfx.drawString(withIcons.iterator, x, y)

            gfx.dispose()
            image
        }

        return texture

    }

    private fun createFallbackString(
        text: String,
        mainFont: Font,
        fallbackFont: Font
    ): AttributedString {
        val result = AttributedString(text)
        val textLength = text.length
        result.addAttribute(TextAttribute.FONT, mainFont, 0, textLength)
        var fallback = false
        var fallbackBegin = 0
        val codePoints = text.codePoints().toList()
        for (i in codePoints.indices) {
            // 😉
            val inQuestion = codePoints[i]
            val curFallback = !mainFont.canDisplay(inQuestion)
            if(curFallback){
                LOGGER.info("[AWTFont] ${String(Character.toChars(inQuestion))}, $inQuestion needs fallback, supported? ${fallbackFont.canDisplay(inQuestion)}")
            }
            if (curFallback != fallback) {
                fallback = curFallback
                if (fallback) {
                    fallbackBegin = i
                } else {
                    result.addAttribute(TextAttribute.FONT, fallbackFont, fallbackBegin, i)
                }
            }
        }
        return result
    }*/

    fun ceil(f: Float) = round(f + 0.5f)
    fun ceil(f: Double) = round(f + 0.5).toInt()

    companion object {

        val LOGGER = LogManager.getLogger(AWTFont::class)!!

        // val staticGfx = BufferedImage(1,1, BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
        // val staticMetrics = staticGfx.fontMetrics
        // val staticFontRenderCTX = staticGfx.fontRenderContext

        var fallbackFont0 = Font("Segoe UI Emoji", Font.PLAIN, 25)
        val fallbackFonts = HashMap<Float, Font>()
        fun getFallback(size: Float): Font {
            val cached = fallbackFonts[size]
            if(cached != null) return cached
            val font = fallbackFont0.deriveFont(size)
            fallbackFonts[size] = font
            return font
        }
    }
}