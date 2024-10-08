package me.anno.remsstudio.objects.particles.forces.impl

import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.pow
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.inspectable.InspectableAnimProperty
import me.anno.remsstudio.objects.models.ArrowModel.arrowLineModel
import me.anno.remsstudio.objects.particles.Particle
import me.anno.remsstudio.objects.particles.ParticleState
import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.sin

@Suppress("MemberVisibilityCanBePrivate")
class TornadoField : ForceField(
    "Tornado",
    "Circular motion around center", "tornado"
) {

    val exponent = AnimatedProperty.float(-1f)

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {

        val direction = getDirection(time)
        val strength = strength[time]
        val delta = state.position - position[time]
        val l = delta.length()
        return delta.cross(direction) * pow(l, -(exponent[time] + 1f)) * strength

    }

    override fun listProperties(): List<InspectableAnimProperty> {
        return super.listProperties() + listOf(
            InspectableAnimProperty(
                exponent, NameDesc(
                    "Exponent",
                    "How quickly the force declines with distance",
                    "obj.effect.gravityExponent"
                )
            )
        )
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "exponent", exponent)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "exponent" -> exponent.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)
        // draw a tornado of arrows
        for (i in 1 until 5) {
            val distance = i / 2f
            val arrowCount = i * 5
            for (j in 0 until arrowCount) {
                val angle = j * 6.2830f / arrowCount
                val pos = Vector3f(cos(angle) * distance, 0f, sin(angle) * distance)
                val force = pow(distance, -exponent[time])
                stack.next {
                    stack.translate(pos)
                    stack.scale(visualForceScale * force)
                    stack.rotateY(-angle - 1.57f)
                    Grid.drawLineMesh(null, stack, Vector4f(1f), arrowLineModel)
                }
            }
        }
    }

    override val className get() = "TornadoField"

}