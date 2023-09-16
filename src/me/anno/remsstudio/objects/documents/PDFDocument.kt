package me.anno.remsstudio.objects.documents

import me.anno.animation.Type
import me.anno.cache.instances.PDFCache
import me.anno.cache.instances.PDFCache.getTexture
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFX.viewportHeight
import me.anno.gpu.GFX.viewportWidth
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.documents.SiteSelection.parseSites
import me.anno.remsstudio.objects.lists.Element
import me.anno.remsstudio.objects.lists.SplittableElement
import me.anno.studio.Inspectable
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.utils.Clipping
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.lists.Lists.median
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.*

// todo different types of lists (x list, y list, grid, linear particle system, random particle system, ...)
// todo different types of iterators (pdf pages, parts of images, )
// todo re-project UV textures onto stuff to animate an image exploding (gets UVs from first frame, then just is a particle system or sth else)
// todo interpolation between lists and sets? could be interesting :)

open class PDFDocument(var file: FileReference, parent: Transform?) : GFXTransform(parent), SplittableElement {

    constructor() : this(InvalidRef, null)

    var selectedSites = ""

    var padding = AnimatedProperty.float()

    var direction = AnimatedProperty.rotY()

    var editorQuality = 3f
    var renderQuality = 3f

    override val defaultDisplayName: String
        get() {
            // file can be null
            return if (file == InvalidRef || file.name.isBlank2()) "PDF"
            else file.name
        }

    override val className get() = "PDFDocument"
    override val symbol get() = "\uD83D\uDDCE"

    fun getSelectedSitesList() = parseSites(selectedSites)

    val meta get() = getMeta(file, true)
    val forcedMeta get() = getMeta(file, false)!!

    fun getMeta(src: FileReference, async: Boolean): PDFCache.AtomicCountedDocument? {
        if (!src.exists) return null
        return PDFCache.getDocumentRef(src, src.inputStreamSync(), true, async)
    }

    // rather heavy...
    override fun getRelativeSize(): Vector3f {
        val ref = forcedMeta
        val doc = ref.doc
        val pageCount = doc.numberOfPages
        val referenceScale = (0 until min(10, pageCount)).map {
            doc.getPage(it).mediaBox.run {
                if (viewportWidth > viewportHeight) height else width
            }
        }.median(0f)
        if (pageCount < 1) {
            ref.returnInstance()
            return Vector3f(1f)
        }
        val firstPage = getSelectedSitesList().firstOrNull()?.first ?: return Vector3f(1f)
        val size = doc.getPage(firstPage).mediaBox.run { Vector3f(width / referenceScale, height / referenceScale, 1f) }
        ref.returnInstance()
        return size
    }

    fun getQuality() = if (isFinalRendering) renderQuality else editorQuality

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val file = file
        val ref = meta
        if (ref == null) {
            super.onDraw(stack, time, color)
            return checkFinalRendering()
        }

        val doc = ref.doc
        val quality = getQuality()
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        val direction = -direction[time].toRadians()
        // find reference scale: median height (or width, or height if height > width?)
        val referenceScale = getReferenceScale(doc, numberOfPages)
        var wasDrawn = false
        val padding = padding[time]
        val cos = cos(direction)
        val sin = sin(direction)
        val normalizer = 1f / max(abs(cos), abs(sin))
        val scale = (1f + padding) * normalizer / referenceScale
        stack.next {
            pages.forEach {
                for (pageNumber in max(it.first, 0)..min(it.last, numberOfPages - 1)) {
                    val mediaBox = doc.getPage(pageNumber).mediaBox
                    val w = mediaBox.width * scale
                    val h = mediaBox.height * scale
                    if (wasDrawn) {
                        stack.translate(cos * w, sin * h, 0f)
                    }
                    val x = w / h
                    // only query page, if it's visible
                    if (isVisible(stack, x)) {
                        var texture = getTexture(file, doc, quality, pageNumber)
                        if (texture == null) {
                            checkFinalRendering()
                            texture = colorShowTexture
                        }
                        // find out the correct size for the image
                        // find also the correct y offset...
                        if (texture === colorShowTexture) {
                            stack.next {
                                stack.scale(x, 1f, 1f)
                                GFXx3Dv2.draw3DVideo(
                                    this, time, stack, texture, color,
                                    Filtering.NEAREST, Clamping.CLAMP, null, UVProjection.Planar
                                )
                            }
                        } else {
                            GFXx3Dv2.draw3DVideo(
                                this, time, stack, texture, color,
                                Filtering.LINEAR, Clamping.CLAMP, null, UVProjection.Planar
                            )
                        }
                    }
                    wasDrawn = true
                    stack.translate(cos * w, sin * h, 0f)
                }
            }
        }

        ref.returnInstance()

        if (!wasDrawn) {
            super.onDraw(stack, time, color)
        }

    }

    private fun isVisible(matrix: Matrix4f, x: Float): Boolean {
        return Clipping.isPlaneVisible(matrix, x, 1f)
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<PDFDocument>()
        val doc = getGroup("Document", "", "docs")
        doc += vi(inspected, "Path", "", null, file, style) { for (x in c) x.file = it }
        doc += vi(inspected, "Pages", "", null, selectedSites, style) { for (x in c) x.selectedSites = it }
        doc += vis(c, "Padding", "", c.map { it.padding }, style)
        doc += vis(c, "Direction", "Top-Bottom/Left-Right in Degrees", c.map { it.direction }, style)
        doc += vi(inspected, "Editor Quality", "", Type.FLOAT_PLUS, editorQuality, style) {
            for (x in c) x.editorQuality = it
        }
        doc += vi(inspected, "Render Quality", "", Type.FLOAT_PLUS, renderQuality, style) {
            for (x in c) x.renderQuality = it
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeObject(this, "padding", padding)
        writer.writeString("selectedSites", selectedSites)
        writer.writeObject(this, "direction", direction)
        writer.writeFloat("editorQuality", editorQuality)
        writer.writeFloat("renderQuality", renderQuality)
    }

    override fun readFloat(name: String, value: Float) {
        when (name) {
            "editorQuality" -> editorQuality = value
            "renderQuality" -> renderQuality = value
            else -> super.readFloat(name, value)
        }
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "file" -> file = value?.toGlobalFile() ?: InvalidRef
            "selectedSites" -> selectedSites = value ?: ""
            else -> super.readString(name, value)
        }
    }

    override fun readFile(name: String, value: FileReference) {
        when (name) {
            "file" -> file = value
            else -> super.readFile(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "padding" -> padding.copyFrom(value)
            "direction" -> direction.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    fun getReferenceScale(doc: PDDocument, numberOfPages: Int): Float {
        return (0 until min(10, numberOfPages)).map {
            doc.getPage(it).mediaBox.run {
                height//if (windowWidth > windowHeight) height else width
            }
        }.median(0f)
    }

    override fun getSplitElement(mode: String, index: Int): Element {
        val ref = forcedMeta
        val doc = ref.doc
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        var remainingIndex = index
        var pageNumber = 0
        for (it in pages) {
            val start = max(it.first, 0)
            val end = min(it.last + 1, numberOfPages)
            val length = max(0, end - start)
            if (remainingIndex < length) {
                pageNumber = start + remainingIndex
                break
            }
            remainingIndex -= length
        }
        val child = clone() as PDFDocument
        child.selectedSites = "$pageNumber"
        val mediaBox = doc.getPage(pageNumber).mediaBox
        val scale = 1f / getReferenceScale(doc, numberOfPages)
        ref.returnInstance()
        val w = mediaBox.width * scale
        val h = mediaBox.height * scale
        return Element(w, h, 0f, child)
    }

    override fun getSplitLength(mode: String): Int {
        val ref = meta!!
        val doc = ref.doc
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        var sum = 0
        for (it in pages) {
            val start = max(it.first, 0)
            val end = min(it.last + 1, numberOfPages)
            val length = max(0, end - start)
            sum += length
        }
        ref.returnInstance()
        return sum
    }

    companion object {
        init {
            // spams the output with it's drawing calls; using the error stream...
            LogManager.disableLogger("GlyphRenderer")
        }
    }

}