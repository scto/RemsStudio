package me.anno.gpu.texture

import me.anno.cache.data.ICacheData
import me.anno.gpu.GFX
import me.anno.gpu.buffer.Buffer
import me.anno.gpu.framebuffer.TargetType
import org.lwjgl.opengl.ARBDepthBufferFloat.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer

// can be used e.g. for game engine for environment & irradiation maps
// todo multi-sampled environment maps, because some gpus may handle them just fine :3

class CubemapTexture(
    var size: Int, val samples: Int
) : ICacheData, ITexture2D {

    var isCreated = false
    var isDestroyed = false
    var pointer = -1

    var locallyAllocated = 0L

    override var w: Int
        get() = size
        set(value) {
            size = value
        }

    override var h: Int
        get() = size
        set(_) {}

    private val tex2D = GL_TEXTURE_CUBE_MAP

    private fun ensurePointer() {
        if (isDestroyed) throw RuntimeException("Texture was destroyed")
        if (pointer < 0) {
            GFX.check()
            pointer = Texture2D.createTexture()
            // many textures can be created by the console log and the fps viewer constantly xD
            // maybe we should use allocation free versions there xD
            GFX.check()
        }
        if (pointer <= 0) throw RuntimeException("Could not allocate texture pointer")
    }

    private fun bindBeforeUpload() {
        if (pointer == -1) throw RuntimeException("Pointer must be defined")
        Texture2D.bindTexture(tex2D, pointer)
    }

    private fun checkSize(channels: Int, size0: Int) {
        if (size0 < size * size * channels) throw IllegalArgumentException("Incorrect size, ${size * size * channels} vs ${size0}!")
    }

    private fun beforeUpload(channels: Int, size: Int) {
        if (isDestroyed) throw RuntimeException("Texture is already destroyed, call reset() if you want to stream it")
        checkSize(channels, size)
        GFX.check()
        ensurePointer()
        bindBeforeUpload()
        GFX.check()
    }

    fun createRGB(sides: List<ByteArray>) {
        beforeUpload(6 * 3, sides[0].size)
        val size = size
        val byteBuffer = Texture2D.byteBufferPool[size * size * 3, false]
        for (i in 0 until 6) {
            byteBuffer.position(0)
            byteBuffer.put(sides[i])
            byteBuffer.position(0)
            glTexImage2D(
                getTarget(i), 0, GL_RGB8,
                size, size, 0, GL_RGB, GL_UNSIGNED_BYTE, byteBuffer
            )
        }
        Texture2D.byteBufferPool.returnBuffer(byteBuffer)
        afterUpload(6 * 3)
    }

    fun createRGBA(sides: List<ByteArray>) {
        beforeUpload(6 * 4, sides[0].size)
        val size = size
        val byteBuffer = Texture2D.byteBufferPool[size * size * 4, false]
        for (i in 0 until 6) {
            byteBuffer.position(0)
            byteBuffer.put(sides[i])
            byteBuffer.position(0)
            glTexImage2D(
                getTarget(i), 0, GL_RGBA8,
                size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer
            )
        }
        Texture2D.byteBufferPool.returnBuffer(byteBuffer)
        afterUpload(6 * 4)
    }

    fun create(type: TargetType) {
        beforeUpload(0, 0)
        val size = size
        Buffer.bindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0)
        for (i in 0 until 6) {
            glTexImage2D(
                getTarget(i), 0, type.type0, size, size,
                0, type.type1, type.fillType, null as ByteBuffer?
            )
        }
        afterUpload(type.bytesPerPixel)
    }

    private fun getTarget(side: Int) = GL_TEXTURE_CUBE_MAP_POSITIVE_X + side

    fun createDepth(lowQuality: Boolean = false) {
        ensurePointer()
        bindBeforeUpload()
        val size = size
        val format = if (lowQuality) GL_DEPTH_COMPONENT16 else GL_DEPTH_COMPONENT32F
        for (side in 0 until 6) {
            glTexImage2D(
                getTarget(side), 0, format, size, size,
                0, GL_DEPTH_COMPONENT, GL_FLOAT, 0
            )
        }
        afterUpload(if (lowQuality) 2 * 6 else 4 * 6)
    }

    private fun afterUpload(bytesPerPixel: Int) {
        GFX.check()
        locallyAllocated = Texture2D.allocate(locallyAllocated, size * size * bytesPerPixel.toLong())
        isCreated = true
        filtering(filtering)
        clamping()
        GFX.check()
        if (isDestroyed) destroy()
    }

    private fun isBoundToSlot(slot: Int): Boolean {
        return Texture2D.boundTextures[slot] == pointer
    }

    override fun bind(index: Int, nearest: GPUFiltering, clamping: Clamping): Boolean {
        if (pointer > 0 && isCreated) {
            if (isBoundToSlot(index)) return false
            Texture2D.activeSlot(index)
            val result = Texture2D.bindTexture(tex2D, pointer)
            ensureFilterAndClamping(nearest, clamping)
            return result
        } else throw IllegalStateException("Cannot bind non-created texture!")
    }

    private fun clamping() {
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
    }

    var hasMipmap = false
    var filtering: GPUFiltering = GPUFiltering.TRULY_NEAREST

    private fun filtering(nearest: GPUFiltering) {
        if (!hasMipmap && nearest.needsMipmap) {
            GL30.glGenerateMipmap(tex2D)
            hasMipmap = true
            if (GFX.supportsAnisotropicFiltering) {
                val anisotropy = GFX.anisotropy
                glTexParameteri(tex2D, GL30.GL_TEXTURE_LOD_BIAS, 0)
                glTexParameterf(tex2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy)
            }
            glTexParameteri(tex2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        }
        glTexParameteri(tex2D, GL_TEXTURE_MIN_FILTER, nearest.min)
        glTexParameteri(tex2D, GL_TEXTURE_MAG_FILTER, nearest.mag)
        this.filtering = nearest
    }

    private fun ensureFilterAndClamping(nearest: GPUFiltering, clamping: Clamping) {
        // ensure being bound?
        if (nearest != this.filtering) filtering(nearest)
    }

    fun reset() {
        isDestroyed = false
    }

    override fun destroy() {
        isCreated = false
        isDestroyed = true
        val pointer = pointer
        if (pointer > -1) {
            if (!GFX.isGFXThread()) {
                GFX.addGPUTask(1) {
                    Texture2D.invalidateBinding()
                    locallyAllocated = Texture2D.allocate(locallyAllocated, 0L)
                    Texture2D.texturesToDelete.add(pointer)
                }
            } else {
                Texture2D.invalidateBinding()
                locallyAllocated = Texture2D.allocate(locallyAllocated, 0L)
                Texture2D.texturesToDelete.add(pointer)
            }
        }
        this.pointer = -1
    }

}