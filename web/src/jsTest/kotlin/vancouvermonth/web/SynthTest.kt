package vancouvermonth.web

import kotlin.js.Promise
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.khronos.webgl.get

/**
 * The village tune and the game's small sounds, rendered for real by the test browser's Web Audio into a buffer, as
 * fast as it can and heard by no one, through the same mixer and band the page plays live.
 */
class SynthTest {
    private val rate = 44_100.0

    /**
     * [seconds] of the tune in [mood], faded in from the start, with whatever [more] adds, rendered to a buffer.
     * Notes due before [late] are left out, as after a stall. With [inSteps], the bars are planned a quarter of a
     * second at a time, as the page's timer does, instead of all at once.
     */
    private fun render(
        seconds: Double,
        mood: Mood,
        late: Double = Double.NEGATIVE_INFINITY,
        inSteps: Boolean = false,
        more: (Mixer) -> Unit = {},
    ): Promise<AudioBuffer> {
        val context = OfflineAudioContext(2, (rate * seconds).toInt(), rate)
        val mixer = Mixer(context)
        mixer.fadeIn(0.0)
        val band = Band(mixer, VillageTune.THEME_SEED) { mood }
        band.start(0.05)
        if (inSteps) {
            for (quarter in 1..(seconds * 4).toInt()) band.scheduleUntil(quarter * 0.25, late)
        }
        band.scheduleUntil(seconds, late)
        more(mixer)
        return context.startRendering()
    }

    /** The loudest sample and the root mean square of [buffer] from [from] seconds up to [until], on every channel. */
    private fun levels(buffer: AudioBuffer, from: Double = 0.0, until: Double = Double.MAX_VALUE): Pair<Double, Double> {
        var peak = 0.0
        var squares = 0.0
        var count = 0
        val first = (from * rate).toInt()
        val last = if (until == Double.MAX_VALUE) buffer.length else minOf(buffer.length, (until * rate).toInt())
        for (channel in 0 until buffer.numberOfChannels) {
            val data = buffer.getChannelData(channel)
            for (i in first until last) {
                val sample = data[i].toDouble()
                peak = max(peak, abs(sample))
                squares += sample * sample
                count += 1
            }
        }
        return peak to sqrt(squares / max(count, 1))
    }

    @Test
    fun `a few seconds of the day's tune and every small sound are heard, and stay under full scale`(): Promise<Unit> =
        render(6.0, Mood.DAY) { mixer ->
            mixer.chime(1.5)
            mixer.blip(2.5)
            for (i in 0 until 20) mixer.tick(3.0 + i * 0.11)
        }.then { buffer ->
            val (peak, rms) = levels(buffer)
            assertTrue(peak > 0.02, "Silent: the loudest sample is $peak")
            assertTrue(peak < 1.0, "Clipped: the loudest sample is $peak")
            assertTrue(rms > 0.003, "Too quiet to hear: $rms")
        }

    @Test
    fun `the night is quieter than the evening, and the evening than the day, yet none is silent`(): Promise<Unit> =
        Promise.all(Mood.entries.map { render(8.0, it) }.toTypedArray()).then { buffers ->
            val rms = buffers.map { levels(it, from = 2.0).second }
            assertTrue(rms[2] < rms[1] && rms[1] < rms[0], "Day, evening and night: $rms")
            assertTrue(rms[2] > 0.0005, "The night is silent: $rms")
            for (buffer in buffers) assertTrue(levels(buffer).first < 1.0)
        }

    @Test
    fun `switching off fades the sound out to silence, echo and all, not in one cut`(): Promise<Unit> =
        render(5.0, Mood.DAY) { mixer -> mixer.fadeOut(2.0) }.then { buffer ->
            val (before, _) = levels(buffer, from = 1.0, until = 2.0)
            val (after, _) = levels(buffer, from = 2.0 + Mixer.FADE_OUT + 0.05)
            assertTrue(before > 0.02, "Nothing played before the fade: $before")
            assertTrue(after < 1e-4, "Still heard after the fade: $after")
            // Just after the fade starts the sound is still well heard, and it is quieter later in the fade.
            val justBefore = levels(buffer, from = 1.85, until = 2.0).second
            val justAfter = levels(buffer, from = 2.0, until = 2.15).second
            val later = levels(buffer, from = 2.45, until = 2.6).second
            assertTrue(justAfter > 0.4 * justBefore, "A cut, not a fade: $justBefore then $justAfter")
            assertTrue(later < justAfter, "The fade does not go down: $justAfter then $later")
        }

    @Test
    fun `back after a pause the sound comes up from silence over a moment, not at once`(): Promise<Unit> =
        Promise.all(arrayOf(render(4.0, Mood.DAY), render(4.0, Mood.DAY) { mixer -> mixer.fadeBack(2.0) })).then {
            val (steady, back) = it
            val ratio = { from: Double, until: Double -> levels(back, from, until).second / levels(steady, from, until).second }
            // The same music both times: just after coming back it is much quieter, and a moment later it is the same.
            val start = ratio(2.0, 2.0 + Mixer.FADE_BACK / 3)
            val later = ratio(2.0 + Mixer.FADE_BACK + 0.05, 3.0)
            assertTrue(start < 0.4, "Back at once, not faded: $start of the level")
            assertTrue(later in 0.95..1.05, "Not back to the full level: $later")
        }

    @Test
    fun `after a stall, notes already due are left out instead of all starting at once`(): Promise<Unit> =
        render(4.0, Mood.DAY, late = 2.0).then { buffer ->
            val (overdue, _) = levels(buffer, 0.0, 1.95)
            val (played, _) = levels(buffer, 2.0, 4.0)
            assertTrue(overdue < 1e-4, "Overdue notes were played: $overdue")
            assertTrue(played > 0.02, "Nothing was played after the stall: $played")
        }

    @Test
    fun `planning a quarter of a second at a time plays each bar once, as planning it all at once does`(): Promise<Unit> {
        val plans = arrayOf(render(8.0, Mood.DAY), render(8.0, Mood.DAY, inSteps = true))
        return Promise.all(plans).then { (once, inSteps) ->
            var largest = 0.0
            for (channel in 0 until once.numberOfChannels) {
                val a = once.getChannelData(channel)
                val b = inSteps.getChannelData(channel)
                for (i in 0 until once.length) largest = max(largest, abs(a[i] - b[i]).toDouble())
            }
            assertTrue(largest < 1e-6, "The two plans sound different, by up to $largest")
            assertTrue(levels(once).first > 0.02)
        }
    }
}
