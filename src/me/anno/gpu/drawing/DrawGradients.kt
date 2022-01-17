package me.anno.gpu.drawing

import me.anno.gpu.GFX
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.texture.TextureLib.bindWhite
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.video.formats.gpu.GPUFrame
import org.joml.Vector4fc

object DrawGradients {

    fun drawRectGradient(x: Int, y: Int, w: Int, h: Int, leftColor: Vector4fc, rightColor: Vector4fc) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient.value
        shader.use()
        bindWhite(0)
        GFXx2D.posSize(shader, x, y, w, h)
        shader.v4f("lColor", leftColor)
        shader.v4f("rColor", rightColor)
        shader.v1i("code", -1)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(x: Int, y: Int, w: Int, h: Int, leftColor: Int, rightColor: Int) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient.value
        shader.use()
        bindWhite(0)
        GFXx2D.posSize(shader, x, y, w, h)
        shader.v4f("lColor", leftColor)
        shader.v4f("rColor", rightColor)
        shader.v1i("code", -1)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(
        x: Int, y: Int, w: Int, h: Int, leftColor: Vector4fc, rightColor: Vector4fc,
        frame: GPUFrame, uvs: Vector4fc
    ) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient.value
        shader.use()
        frame.bind(0, GPUFiltering.TRULY_LINEAR, Clamping.CLAMP)
        GFXx2D.posSize(shader, x, y, w, h)
        shader.v4f("lColor", leftColor)
        shader.v4f("rColor", rightColor)
        shader.v4f("uvs", uvs)
        shader.v1i("code", frame.code)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(
        x: Int, y: Int, w: Int, h: Int, leftColor: Int, rightColor: Int,
        frame: GPUFrame, uvs: Vector4fc
    ) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient.value
        shader.use()
        frame.bind(0, GPUFiltering.TRULY_LINEAR, Clamping.CLAMP)
        GFXx2D.posSize(shader, x, y, w, h)
        shader.v4f("lColor", leftColor)
        shader.v4f("rColor", rightColor)
        shader.v4f("uvs", uvs)
        shader.v1i("code", frame.code)
        GFX.flat01.draw(shader)
        GFX.check()
    }

}