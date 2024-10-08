package me.anno.remsstudio.audio.effects.impl

import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import java.util.*
import kotlin.math.log2
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
class EchoEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    var offset = AnimatedProperty.floatPlus(0.1f) // seconds
    var falloff = AnimatedProperty.float01exp(0.4f)

    override fun getStateAsImmutableKey(source: Video, destination: Camera, time0: Time, time1: Time): Any {
        return offset.toString() + falloff.toString()
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        list += audio.vi(
            "Offset", "Distance of 1st echo in seconds",
            "echo.offset", offset, style
        )
        list += audio.vi(
            "Falloff", "How much is reflected, the less, the faster the echo fades away",
            "echo.falloff", falloff, style
        )
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "falloff", falloff)
        writer.writeObject(this, "offset", offset)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "falloff" -> falloff.copyFrom(value)
            "offset" -> offset.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    companion object {

        private const val maxEchoes = 64
        private val randomizedOffsets: FloatArray
        private const val minRelativeAmplitude = 0.0001f

        init {
            val random = Random(1234)
            randomizedOffsets = FloatArray(maxEchoes) { (it + 1) * (0.8f + 0.4f * random.nextFloat()) }
        }

    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {

        // todo this this mixing stereo channels randomly???

        val bufferSize = dataDst.size

        val time = (time0.localTime + time1.localTime) * 0.5
        val falloff = falloff[time]
        val echoes = min(maxEchoes, (log2(minRelativeAmplitude) / log2(falloff)).toInt())
        val offset0 = offset[time] * bufferSize / (time1.localTime - time0.localTime)

        getDataSrc(0).copyInto(dataDst)

        if (echoes > 0 && offset0 > 0.5) {

            var multiplier = 1f
            for (echo in 0 until echoes) {
                multiplier *= falloff
                val offset = (offset0 * randomizedOffsets[echo]).toInt()
                lateinit var lastSrc: FloatArray
                var lastIndex = 0
                for (i in dataDst.indices) {
                    val i2 = i - offset
                    val thisIndex = Math.floorDiv(i2, bufferSize)
                    if (thisIndex != lastIndex || i == 0) {
                        lastSrc = getDataSrc(thisIndex)
                        lastIndex = thisIndex
                    }
                    var i3 = i2 % bufferSize
                    if (i3 < 0) i3 += bufferSize
                    dataDst[i] += multiplier * lastSrc[i3]
                }
            }

            var totalMultiplier = 1f
            multiplier = 1f
            for (echo in 0 until echoes) {
                multiplier *= falloff
                totalMultiplier += multiplier
            }

            totalMultiplier = 1f / totalMultiplier

            for (i in 0 until bufferSize) {
                dataDst[i] *= totalMultiplier
            }
        }
    }

    override fun clone(): SoundEffect {
        val clone = EchoEffect()
        clone.offset = offset
        clone.falloff = falloff
        return clone
    }

    override val displayName get() = "Echo Effect"
    override val description get() = "Repeats the sound"
    override val className get() = "EchoEffect"

}