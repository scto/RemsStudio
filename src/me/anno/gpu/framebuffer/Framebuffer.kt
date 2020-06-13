package me.anno.gpu.framebuffer

import me.anno.gpu.GFX
import me.anno.gpu.texture.Texture2D
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*
import java.lang.RuntimeException
import java.util.*

class Framebuffer(var w: Int, var h: Int, val targetCount: Int, val fpTargets: Boolean, val createDepthBuffer: DepthBufferType){

    // multiple targets, layout=x require shader version 330+
    // use glBindFragDataLocation instead

    enum class DepthBufferType {
        NONE,
        INTERNAL,
        TEXTURE
    }

    var samples = 4
    val isMultisampled get() = samples > 1

    var pointer = -1
    var depthRenderBuffer = -1
    var colorRenderBuffer = -1
    var depthTexture: Texture2D? = null

    lateinit var textures: Array<Texture2D>

    fun bind(){
        if(pointer < 0) create()
        currentFramebuffer = this
        glBindFramebuffer(GL_FRAMEBUFFER, pointer)
        glViewport(0,0, w, h)
    }

    fun bind(newWidth: Int, newHeight: Int){
        if(newWidth != w || newHeight != h){
            w = newWidth
            h = newHeight
            GFX.check()
            destroy()
            GFX.check()
            create()
            GFX.check()
        }
        GFX.check()
        bind()
        GFX.check()
    }

    fun create(){
        GFX.check()
        pointer = glGenFramebuffers()
        if(pointer < 0) throw RuntimeException()
        glBindFramebuffer(GL_FRAMEBUFFER, pointer)
        GFX.check()
        textures = Array(targetCount){
            val texture = Texture2D(w, h)
            if(fpTargets) texture.createFP32()
            else texture.create()
            GFX.check()
            texture.filtering(true)
            texture.clamping(false)
            GFX.check()
            texture
        }
        GFX.check()
        textures.forEachIndexed { index, texture ->
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, GL11.GL_TEXTURE_2D, texture.pointer, 0)
        }
        GFX.check()
        if(targetCount > 1){// skip array alloc otherwise
            glDrawBuffers(textures.indices.map { it + GL_COLOR_ATTACHMENT0 }.toIntArray())
        } else glDrawBuffer(GL_COLOR_ATTACHMENT0)
        GFX.check()
        when(createDepthBuffer){
            DepthBufferType.NONE -> {}
            DepthBufferType.INTERNAL -> createDepthBuffer()
            DepthBufferType.TEXTURE -> {
                val depthTexture = Texture2D(w, h)
                depthTexture.createDepth()
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture.pointer, 0)
            }
        }
        GFX.check()
        check()
    }

    fun createColorBuffer(){
        if(!isMultisampled) throw RuntimeException()
        val renderBuffer = glGenRenderbuffers()
        colorRenderBuffer = renderBuffer
        if(renderBuffer < 0) throw RuntimeException()
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderBuffer)
    }

    fun createDepthBuffer(){
        val renderBuffer = glGenRenderbuffers()
        depthRenderBuffer = renderBuffer
        if(renderBuffer < 0) throw RuntimeException()
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
        if(isMultisampled){
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT, w, h)
        } else glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, w, h)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderBuffer)
    }

    fun resolveTo(target: Framebuffer?){
        if(pointer < 0) throw RuntimeException()
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target?.pointer ?: 0)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, pointer)
        // if(target == null) glDrawBuffer(GL_BACK)?
        glBlitFramebuffer(
            0, 0, w, h,
            0, 0, target?.w ?: GFX.width, target?.h ?: GFX.height,
            if(target == null) GL_COLOR_BUFFER_BIT else GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT,
            GL11.GL_NEAREST)
    }

    fun check(){
        val state = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if(state != GL_FRAMEBUFFER_COMPLETE){
            throw RuntimeException("framebuffer is incomplete: $state")
        }
    }

    fun bindTexture0(offset: Int = 0, nearest: Boolean){
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + offset)
        textures[0].bind(nearest)
    }

    fun bindTextures(offset: Int = 0, nearest: Boolean){
        textures.forEachIndexed { index, texture ->
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + offset + index)
            texture.bind(nearest)
        }
    }

    fun destroy(){
        if(pointer > -1){
            glDeleteFramebuffers(pointer)
            pointer = -1
            textures.forEach {
                it.destroy()
            }
        }
        if(depthRenderBuffer > -1){
            glDeleteRenderbuffers(depthRenderBuffer)
            depthRenderBuffer = -1
        }
    }

    companion object {
        private var currentFramebuffer: Framebuffer? = null
        val stack = Stack<Framebuffer>()
        fun bindNull(){
            currentFramebuffer = null
        }
        fun bindNullTemporary(){
            stack.push(currentFramebuffer)
            bindNull()
        }
        fun unbindNull(){
            if(stack.isEmpty()) throw RuntimeException("No framebuffer was found!")
            stack.pop().bind()
        }
        fun Framebuffer?.bind(w: Int, h: Int){
            if(this == null){
                bindNull()
                glBindFramebuffer(GL_FRAMEBUFFER, 0)
            } else bind(w, h)
        }
    }

    fun bindTemporary(newWidth: Int, newHeight: Int){
        stack.push(currentFramebuffer)
        bind(newWidth, newHeight)
    }

    fun bindTemporary(){
        stack.push(currentFramebuffer)
        bind()
    }

    fun unbind(){
        if(stack.isEmpty()) throw RuntimeException("No framebuffer was found!")
        stack.pop().bind()
    }



}