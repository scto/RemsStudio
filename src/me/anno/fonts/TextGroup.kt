package me.anno.fonts

import me.anno.fonts.mesh.TextMesh
import me.anno.fonts.mesh.TextMeshGroup.Companion.getAlignments
import me.anno.fonts.mesh.TextRepBase
import me.anno.utils.Lists.accumulate
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import kotlin.streams.toList

/**
 * custom character-character alignment maps by font for faster calculation
 * */
abstract class TextGroup(
    val font: Font, val text: String,
    val charSpacing: Float
) : TextRepBase() {

    val alignment = getAlignments(font)
    val ctx = FontRenderContext(null, true, true)

    val codepoints = text.codePoints().toList()

    val offsets = (codepoints.mapIndexed { index, secondCodePoint ->
        if (index > 0) {
            val firstCodePoint = codepoints[index - 1]
            charSpacing + getOffset(firstCodePoint, secondCodePoint)
        } else 0.0
    } + getOffset(codepoints.last(), 32)).accumulate() // space

    val baseScale: Float

    init {
        if ('\t' in text || '\n' in text) throw RuntimeException("\t and \n are not allowed in FontMesh2!")
        val layout = TextLayout(".", font, ctx)
        baseScale = TextMesh.DEFAULT_LINE_HEIGHT / (layout.ascent + layout.descent)
        minX = 0f
        maxX = 0f
    }

    fun getOffset(previous: Int, current: Int): Double {
        val map = alignment.charDistance
        val key = previous to current
        val characterLengthCache = alignment.charSize
        fun getLength(str: String): Double {
            return TextLayout(str, font, ctx).bounds.maxX
        }

        fun getCharLength(char: Int): Double {
            var value = characterLengthCache[char]
            if (value != null) return value
            value = getLength(String(Character.toChars(char)))
            characterLengthCache[char] = value
            return value
        }
        synchronized(alignment) {
            var offset = map[key]
            if (offset != null) return offset
            // val aLength = getCharLength(previous)
            val bLength = getCharLength(current)
            val abLength = getLength(String(Character.toChars(previous) + Character.toChars(current)))
            // ("$abLength = $aLength + $bLength ? (${aLength + bLength})")
            offset = abLength - bLength
            //(abLength - (bLength + aLength) * 0.5)
            // offset = (abLength - aLength)
            // offset = aLength
            map[key] = offset
            return offset
        }
    }

    override fun destroy() {

    }

}