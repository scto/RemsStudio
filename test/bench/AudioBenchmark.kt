package bench

import me.anno.Engine
import me.anno.maths.Maths.mix
import me.anno.remsstudio.audio.AudioFXCache2
import me.anno.remsstudio.audio.AudioFXCache2.SPLITS
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import me.anno.utils.OS
import me.anno.utils.Sleep
import org.apache.logging.log4j.LogManager

fun main() {

    val loggerImpl = LogManager.getLogger("AudioBenchmark")

    // it requires twice the time to listen too ...
    // done optimize until it's at least 10x real time,
    // especially because it should be non-blocking

    // Thread.sleep(10000)

    val async = true

    val bufferSize = 1024

    val start = System.nanoTime()
    val audio = Video(OS.downloads.getChild("Bring Me The Horizon Dear Diary.mp3"))
    val camera = Camera()
    val t0 = 0.0
    val t1 = (2 * 60 + 45).toDouble() // 2 min 45
    val steps = 100000 / SPLITS
    var i0 = t0
    val identifier = audio.toString()
    for (i in 0 until steps) {
        val f1 = (i + 1).toDouble() / steps
        val i1 = mix(t0, t1, f1)
        if (async) {
            AudioFXCache2.getRange(bufferSize, i0, i1, identifier, audio, camera, true)
        } else {
            while (AudioFXCache2.getRange(bufferSize, i0, i1, identifier, audio, camera, false) == null) {
                Sleep.sleepShortly(true)
            }
        }
        i0 = i1
    }
    val end = System.nanoTime()
    val delta = (end - start) * 1e-9

    loggerImpl.info("Used $delta seconds")

    Engine.requestShutdown()

}