package me.anno.objects.effects

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.ShaderPlus
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.GFXTransform
import me.anno.objects.Transform
import me.anno.objects.animation.AnimatedProperty
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Frame
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*

abstract class MaskLayerBase(parent: Transform? = null): GFXTransform(parent){

    // just a little expensive...
    // todo enable multisampling
    val samples = 1

    lateinit var mask: Framebuffer
    lateinit var masked: Framebuffer

    // limit to [0,1]?
    // nice effects can be created with values outside of [0,1], so while [0,1] is the valid range,
    // numbers outside [0,1] give artists more control
    val useMaskColor = AnimatedProperty.float()
    val blurThreshold = AnimatedProperty.float()

    // not animated, because it's not meant to be transitioned, but instead to be a little helper
    var isInverted = false

    // ignore the bounds of this objects xy-plane?
    var isFullscreen = false

    // for user-debugging
    var showMask = false
    var showMasked = false

    override fun getSymbol() = DefaultConfig["ui.symbol.mask", "\uD83D\uDCA5"]

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val showResult = isFinalRendering || (!showMask && !showMasked)
        if(children.size >= 2 && showResult){// else invisible

            mask = FBStack["mask", GFX.windowWidth, GFX.windowHeight, samples, true]
            masked = FBStack["masked", GFX.windowWidth, GFX.windowHeight, samples, true]

            BlendDepth(null, false){

                // (low priority)
                // to do calculate the size on screen to limit overhead
                // to do this additionally requires us to recalculate the transform

                BlendMode.DEFAULT.apply()

                drawMask(stack, time, color)

                BlendMode.DEFAULT.apply()

                drawMasked(stack, time, color)
            }

            drawOnScreen(stack, time, color)

        } else super.onDraw(stack, time, color)

        if(showMask) drawChild(stack, time, color, children.getOrNull(0))
        if(showMasked) drawChild(stack, time, color, children.getOrNull(1))

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // forced, because the default value might be true instead of false
        writer.writeBool("showMask", showMask, true)
        writer.writeBool("showMasked", showMasked, true)
        writer.writeBool("isFullscreen", isFullscreen, true)
        writer.writeBool("isInverted", isInverted, true)
        writer.writeObject(this, "useMaskColor", useMaskColor)
        writer.writeObject(this, "blurThreshold", blurThreshold)
    }

    override fun readBool(name: String, value: Boolean) {
        when(name){
            "showMask" -> showMask = value
            "showMasked" -> showMasked = value
            "isFullscreen" -> isFullscreen = value
            "isInverted" -> isInverted = value
            else -> super.readBool(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "useMaskColor" -> useMaskColor.copyFrom(value)
            "blurThreshold" -> blurThreshold.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun createInspector(list: PanelListY, style: Style, getGroup: (title: String, id: String) -> SettingCategory) {
        super.createInspector(list, style, getGroup)
        val mask = getGroup("Mask Settings", "mask")
        mask += VI("Invert Mask", "Changes transparency with opacity", null, isInverted, style){ isInverted = it }
        mask += VI("Use Color / Transparency", "Should the color influence the masked?", useMaskColor, style)
        mask += VI("Blur Threshold", "", blurThreshold, style)
        // todo expand plane to infinity if fullscreen -> depth works then, idk...
        // infinite bounds doesn't mean that it's actually filling the whole screen
        // (infinite horizon isn't covering both roof and floor)
        mask += VI("Fullscreen", "if not, the borders are clipped by the quad shape", null, isFullscreen, style){ isFullscreen = it }
        /*list += SpacePanel(0, 1, style)
            .setColor(style.getChild("deep").getColor("background", black))*/
        val editor = getGroup("Editor", "editor")
        editor += VI("Show Mask", "for debugging purposes; shows the stencil", null, showMask, style){ showMask = it }
        editor += VI("Show Masked", "for debugging purposes", null, showMasked, style){ showMasked = it }
    }

    override fun drawChildrenAutomatically() = false


    fun drawMask(stack: Matrix4fArrayList, time: Double, color: Vector4f){

        Frame(GFX.windowWidth, GFX.windowHeight, false, mask){

            Frame.currentFrame!!.bind()

            val child = children.getOrNull(0)
            if(child?.getClassName() == "Transform" && child.children.isEmpty()){

                glClearColor(1f, 1f, 1f, 1f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            } else {

                glClearColor(0f, 0f, 0f, 0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                val oldDrawMode = GFX.drawMode
                if(oldDrawMode == ShaderPlus.DrawMode.COLOR_SQUARED) GFX.drawMode = ShaderPlus.DrawMode.COLOR

                drawChild(stack, time, color, child)

                GFX.drawMode = oldDrawMode

            }
        }

    }

    fun drawMasked(stack: Matrix4fArrayList, time: Double, color: Vector4f){

        Frame(GFX.windowWidth, GFX.windowHeight, false, masked){

            Frame.currentFrame!!.bind()

            val oldDrawMode = GFX.drawMode
            if(oldDrawMode == ShaderPlus.DrawMode.COLOR_SQUARED) GFX.drawMode = ShaderPlus.DrawMode.COLOR

            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            drawChild(stack, time, color, children.getOrNull(1))

            GFX.drawMode = oldDrawMode

        }

    }

    fun drawOnScreen(stack: Matrix4fArrayList, time: Double, color: Vector4f){

        val localTransform = if(isFullscreen){
            // todo better solution...
            // todo scaling by 1000 is no option, because it causes artifacts, because of fp precision issues
            stack.scale(5f)
            stack
        } else {
            stack
        }

        drawOnScreen2(localTransform, time, color)

    }

    abstract fun drawOnScreen2(localTransform: Matrix4fArrayList, time: Double, color: Vector4f)

}