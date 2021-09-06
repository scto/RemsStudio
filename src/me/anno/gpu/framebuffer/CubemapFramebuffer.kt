package me.anno.gpu.framebuffer

import me.anno.gpu.GFX
import me.anno.gpu.RenderState.useFrame
import me.anno.gpu.shader.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.CubemapTexture
import me.anno.gpu.texture.GPUFiltering
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30.*

class CubemapFramebuffer(
    var name: String, var size: Int,
    val targets: Array<TargetType>,
    val depthBufferType: DepthBufferType
) : IFramebuffer {

    constructor(
        name: String, size: Int,
        targetCount: Int,
        fpTargets: Boolean,
        depthBufferType: DepthBufferType
    ) : this(
        name, size, if (fpTargets)
            Array(targetCount) { TargetType.FloatTarget4 } else
            Array(targetCount) { TargetType.UByteTarget4 }, depthBufferType
    )

    // multiple targets, layout=x require shader version 330+
    // use glBindFragDataLocation instead

    override var pointer = -1
    var depthRenderBuffer = -1
    override var depthTexture: CubemapTexture? = null

    override val w: Int get() = size
    override val h: Int get() = size

    lateinit var textures: Array<CubemapTexture>

    override fun ensure() {
        if (pointer < 0) create()
    }

    override fun bindDirectly(viewport: Boolean) = bind(viewport)
    override fun bindDirectly(w: Int, h: Int, viewport: Boolean) {
        bindDirectly(w, viewport)
    }

    fun bindDirectly(size: Int, viewport: Boolean) = bind(size, viewport)

    private fun bind(viewport: Boolean) {
        if (pointer < 0) create()
        glBindFramebuffer(GL_FRAMEBUFFER, pointer)
        Frame.lastPtr = pointer
        if (viewport) glViewport(0, 0, size, size)
        GL11.glDisable(GL13.GL_MULTISAMPLE)
    }

    private fun bind(newSize: Int, viewport: Boolean = true) {
        if (newSize != size) {
            size = newSize
            GFX.check()
            destroy()
            GFX.check()
            create()
            if (viewport) {
                // not done by create...
                glViewport(0, 0, newSize, newSize)
            }
            GFX.check()
        } else {
            GFX.check()
            bind(viewport)
            GFX.check()
        }
    }

    private fun create() {
        Frame.invalidate()
        // LOGGER.info("w: $w, h: $h, samples: $samples, targets: $targetCount x fp32? $fpTargets")
        GFX.check()
        pointer = glGenFramebuffers()
        if (pointer < 0) throw RuntimeException()
        glBindFramebuffer(GL_FRAMEBUFFER, pointer)
        Frame.lastPtr = pointer
        //stack.push(this)
        GFX.check()
        textures = Array(targets.size) { index ->
            val texture = CubemapTexture(size)
            texture.create(targets[index])
            GFX.check()
            texture
        }
        GFX.check()
        val textures = textures
        for (index in textures.indices) {
            val texture = textures[index]
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index,
                GL_TEXTURE_CUBE_MAP_POSITIVE_X, texture.pointer, 0
            )
        }
        GFX.check()
        when (targets.size) {
            0 -> glDrawBuffer(GL_NONE)
            1 -> glDrawBuffer(GL_COLOR_ATTACHMENT0)
            else -> glDrawBuffers(textures.indices.map { it + GL_COLOR_ATTACHMENT0 }.toIntArray())
        }
        GFX.check()
        when (depthBufferType) {
            DepthBufferType.NONE -> {
            }
            DepthBufferType.INTERNAL -> createDepthBuffer()
            DepthBufferType.TEXTURE -> {
                val depthTexture = CubemapTexture(size)
                depthTexture.createDepth()
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X, depthTexture.pointer, 0
                )
                this.depthTexture = depthTexture
            }
        }
        GFX.check()
        check()
    }

    /*fun createColorBuffer(){
        if(!withMultisampling) throw RuntimeException()
        val renderBuffer = glGenRenderbuffers()
        colorRenderBuffer = renderBuffer
        if(renderBuffer < 0) throw RuntimeException()
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderBuffer)
    }*/

    private fun createDepthBuffer() {
        val renderBuffer = glGenRenderbuffers()
        depthRenderBuffer = renderBuffer
        if (renderBuffer < 0) throw RuntimeException()
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, size, size)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderBuffer)
    }

    private fun check() {
        val state = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (state != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is incomplete: $state")
        }
    }

    fun bindTexture0(offset: Int = 0, nearest: GPUFiltering, clamping: Clamping) {
        bindTextureI(0, offset, nearest, clamping)
    }

    fun bindTextureI(index: Int, offset: Int) {
        bindTextureI(index, offset, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
    }

    fun bindTextureI(index: Int, offset: Int, nearest: GPUFiltering, clamping: Clamping) {
        textures[index].bind(offset, nearest, clamping)
    }

    fun bindTextures(offset: Int = 0, nearest: GPUFiltering, clamping: Clamping) {
        GFX.check()
        for ((index, texture) in textures.withIndex()) {
            texture.bind(offset + index, nearest, clamping)
        }
        GFX.check()
    }

    override fun destroy() {
        if (pointer > -1) {
            glDeleteFramebuffers(pointer)
            Frame.invalidate()
            pointer = -1
            for (it in textures) {
                it.destroy()
            }
            depthTexture?.destroy()
        }
        if (depthRenderBuffer > -1) {
            glDeleteRenderbuffers(depthRenderBuffer)
            depthRenderBuffer = -1
        }
    }

    fun updateAttachments(face: Int) {
        val tex2D = GL_TEXTURE_CUBE_MAP_POSITIVE_X + face
        val textures = textures
        for (index in textures.indices) {
            val texture = textures[index]
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index,
                tex2D, texture.pointer, 0
            )
        }
        GFX.check()
        when (targets.size) {
            0 -> glDrawBuffer(GL_NONE)
            1 -> glDrawBuffer(GL_COLOR_ATTACHMENT0)
            else -> glDrawBuffers(textures.indices.map { it + GL_COLOR_ATTACHMENT0 }.toIntArray())
        }
        GFX.check()
        if (depthBufferType == DepthBufferType.TEXTURE) {
            val depthTexture = depthTexture!!
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                tex2D, depthTexture.pointer, 0
            )
        }
        GFX.check()
    }

    fun draw(renderer: Renderer, render: (side: Int) -> Unit) {
        useFrame(this, renderer) {
            Frame.bind()
            for (side in 0 until 6) {
                // update all attachments, updating the framebuffer texture targets
                updateAttachments(side)
                val status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)
                if (status != GL_FRAMEBUFFER_COMPLETE) throw IllegalStateException("Framebuffer incomplete $status")
                render(side)
            }
        }
    }

    override fun toString(): String =
        "FBCubemap[n=$name, i=$pointer, size=$size t=${targets.joinToString()} d=$depthBufferType]"

}