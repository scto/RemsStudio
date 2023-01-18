package me.anno.remsstudio.audio.effects.impl

import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.min
import me.anno.maths.Maths.mix
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Audio
import me.anno.remsstudio.objects.Camera
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.FloatInput
import me.anno.ui.style.Style
import me.anno.utils.LOGGER
import me.anno.video.ffmpeg.FFMPEGStream.Companion.getAudioSequence
import kotlin.concurrent.thread
import kotlin.math.sqrt

class NoiseSuppressionEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    private val noiseLevel = AnimatedProperty.floatPlus() // maybe should be in dB...

    override fun getStateAsImmutableKey(source: Audio, destination: Camera, time0: Time, time1: Time): Any {
        return noiseLevel.toString()
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray, // is this stereo?
        source: Audio,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {

        // create running average :)
        // based on that average, mute or not the audio :)
        val sm1 = getDataSrc(-1)
        val src = getDataSrc(+0)
        val sp1 = getDataSrc(+1)

        var accu = 0f
        for (x in sm1) {
            accu += x * x
        }

        for (x in src) {
            accu += x * x
        }

        val accuFactor = sqrt(0.5f / bufferSize) / 32e3f

        val noiseLevel = noiseLevel
        if (!noiseLevel.isAnimated && noiseLevel.drivers[0] == null) {
            val noiseLevelI = noiseLevel[time0.localTime]
            val invNoiseLevelI = 1f / noiseLevelI
            for (i in 0 until bufferSize) {
                val ratio = sqrt(accu) * accuFactor * invNoiseLevelI
                dataDst[i] = src[i] * clamp(ratio - 1f)
                val x0 = sm1[i]
                val x1 = sp1[i]
                accu += x1 * x1 - x0 * x0
            }
        } else {
            val t0 = time0.localTime
            val t1 = time1.localTime
            for (i in 0 until bufferSize) {
                val ratio = sqrt(accu) * accuFactor / noiseLevel[mix(t0, t1, i.toDouble() / bufferSize)]
                dataDst[i] = src[i] * clamp(ratio - 1f)
                val x0 = sm1[i]
                val x1 = sp1[i]
                accu += x1 * x1 - x0 * x0
            }
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        val nlp = audio.vi("Noise Level", "All audio below this relative level will be silenced", noiseLevel, style)
        list += nlp
        list += TextButton("Detect Noise Level", false, style)
            .apply {
                tooltip = "Scans the first 10 seconds of audio for the lowest volume"
                addLeftClickListener {
                    // auto noise level detection
                    val meta = audio.forcedMeta
                    if (meta != null) {
                        // make this async to prevent lag
                        thread(name = "Noise Level") {
                            val duration = min(10.0, meta.audioDuration)
                            val numSamples = min((duration * meta.audioSampleRate).toLong(), meta.audioSampleCount)
                            // if audio is short, subdivide buffers, so we get more decision freedom on histogram
                            val subdivisions = min(1000, numSamples / 200).toInt()
                            val histogram = IntArray(subdivisions)
                            // average values inside buffer
                            val sampleRate = meta.audioSampleRate
                            val stereoData = getAudioSequence(audio.file, 0.0, duration, sampleRate).soundBuffer?.data
                            if (stereoData != null) {
                                var k0 = 0
                                for (j in 0 until subdivisions) {
                                    var sum = 0L
                                    val k1 = (j + 1) * stereoData.capacity() / subdivisions
                                    for (k in k0 until k1) {
                                        val vk = stereoData[k]
                                        sum += vk * vk // should fit into an integer :)
                                    }
                                    histogram[j] = sqrt(sum / (k1 - k0).toDouble()).toInt()
                                    k0 = k1
                                }

                                histogram.sort()

                                val percentileLevel = histogram[(subdivisions + 3) / 5] / 32767f
                                addEvent {
                                    (nlp as FloatInput)
                                        .setValue(percentileLevel, true)
                                }

                            } else LOGGER.warn("Data is null")
                        }
                    } else LOGGER.warn("Metadata is null")
                }
            }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "noiseLevel", noiseLevel)
    }

    override fun readObject(name: String, value: ISaveable?) {
        if (name == "noiseLevel") noiseLevel.copyFrom(value)
        else super.readObject(name, value)
    }

    override val displayName get() = "Noise Suppression"
    override val description get() = "Removes noise; only works if noise is pretty quiet"
    override val className get() = "NoiseSuppressionEffect"

}