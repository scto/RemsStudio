package me.anno.utils

import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Clipping {

    fun check(v0: Vector4f, axis1: Vector4f, axis2: Vector4f, getValue: (Vector4f) -> Float): Vector4f? {
        val val0 = getValue(v0)
        if(val0 in -1f .. 1f) return v0 // it's fine
        val val1p = getValue(axis1)
        val val2p = getValue(axis2)
        // decide which one is better... is this important? idk...
        if((val0 > 1f && val1p > 1f) || (val0 < -1f && val1p < -1f)) return lerpMaybe(v0, axis2, val0, val2p, getValue)
        if((val0 > 1f && val2p > 1f) || (val0 < -1f && val2p < -1f)) return lerpMaybe(v0, axis1, val0, val1p, getValue)
        val v1IsBetter = abs(val1p - val0) > abs(val2p - val0)
        return if(v1IsBetter){
            lerpMaybe(v0, axis1, val0, val1p, getValue)
        } else {
            lerpMaybe(v0, axis2, val0, val2p, getValue)
        }
    }

    fun lerpMaybe(v0: Vector4f, v1: Vector4f, val0: Float, val1: Float, getValue: (Vector4f) -> Float): Vector4f? {
        if((val0 > 1f && val1 > 1f) || (val0 < -1f && val1 < -1f)) return null // impossible
        val cuttingPoint = if(val0 < 0f) -1f else 1f
        // linear combination, such that the new value is cuttingPoint
        val d1 = abs(val0 - cuttingPoint)
        val d2 = abs(val1 - cuttingPoint)
        return Vector4f(v0).lerp(v1, d1/(d1+d2))
        // println("${v0.print()} ${v1.print()} -> ${result.print()} ($val0 $val1 -> ${getValue(result)})")
    }

    fun getZ(p00: Vector4f, p01: Vector4f, p10: Vector4f, p11: Vector4f): Pair<Float, Float>? {

        var v00 = p00
        var v01 = p01
        var v10 = p10
        var v11 = p11

        fun checkAll(getValue: (Vector4f) -> Float): Boolean {

            // sort by resolvability ->
            // there must be at least one intersection with zero, or
            // a possible value
            val x00 = getValue(v00)
            val x01 = getValue(v01)
            val x10 = getValue(v10)
            val x11 = getValue(v11)

            if(x00 < -1f && x01 < -1f && x10 < -1f && x11 < -1f) return false
            if(x00 > +1f && x01 > +1f && x10 > +1f && x11 > +1f) return false

            // find, which value is crossing...
            if((x00 < -1f && x01 < -1f && x10 < -1f) || x00 > +1f && x01 > +1f && x10 > +1f){
                // we need to use x11, with any of the others
                v01 = check(v01, v00, v11, getValue) ?: return false
                v10 = check(v10, v00, v11, getValue) ?: return false
                v00 = check(v00, v01, v10, getValue) ?: return false
                v11 = check(v11, v10, v01, getValue) ?: return false
            } else {
                // we have no info about x11, just that correcting the triple should work
                // when it's corrected, hopefully the rest will work...
                v00 = check(v00, v01, v10, getValue) ?: return false
                v01 = check(v01, v00, v11, getValue) ?: return false
                v10 = check(v10, v00, v11, getValue) ?: return false
                v11 = check(v11, v10, v01, getValue) ?: return false
            }

            return true

        }

        if(!checkAll { it.x } || !checkAll { it.y } || !checkAll { it.z }) return null

        // aprintln("${v00.print()} ${v01.print()} ${v10.print()} ${v11.print()}")

        val minZ = min(min(v00.z, v01.z), min(v10.z, v11.z))
        val maxZ = max(min(v00.z, v01.z), min(v10.z, v11.z))

        return minZ to maxZ

    }


}