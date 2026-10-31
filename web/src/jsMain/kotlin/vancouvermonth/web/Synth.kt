package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.browser.window
import org.khronos.webgl.set

/** A note's pitch in hertz: MIDI 69 is the A at 440. */
internal fun hertz(pitch: Int): Double = 440.0 * 2.0.pow((pitch - 69) / 12.0)

/**
 * The sound's wiring, the same in the page and in a test that renders to a buffer. The melody and the chords each go
 * through a soft tone, then with the low notes through one music volume that follows the mood; a gentle echo gives
 * them room. The night's bed of soft noise, the game's small sounds and the town's own sounds (the rain, the passing
 * train and the campfire) join them, and all of it passes one quiet master volume, a fade for switching on and off, and
 * a limiter, so nothing ever clips.
 */
internal class Mixer(private val context: BaseAudioContext) {
    private val fade = context.createGain().also { it.gain.value = 0.0 }
    private val master = context.createGain().also { it.gain.value = MASTER }
    private val limiter = context.createDynamicsCompressor()
    private val music = context.createGain()
    private val melodyTone = lowpass(MELODY_BRIGHT)
    private val chordTone = lowpass(CHORD_BRIGHT)
    private val echo = context.createGain().also { it.gain.value = ECHO }
    private val bed = context.createGain().also { it.gain.value = 0.0 }
    private val small = context.createGain()
    private val bell = context.createGain()
    private var mood: Mood? = null

    // The town's own sounds, each silent until asked for, made the first time one is.
    private val rain = context.createGain().also { it.gain.value = 0.0 }
    private val rumble = context.createGain().also { it.gain.value = 0.0 }
    private val crackle = context.createGain().also { it.gain.value = 0.0 }
    private var townSoundsMade = false

    // The fade's own plan: from one level at one time to another level at a later time, in a straight line. The
    // level at any moment is worked out from this, never read back from the browser, which may not report a ramp.
    private var fadeFrom = 0.0
    private var fadeFromTime = 0.0
    private var fadeTo = 0.0
    private var fadeToTime = 0.0

    init {
        limiter.threshold.value = -12.0
        limiter.knee.value = 6.0
        limiter.ratio.value = 12.0
        limiter.attack.value = 0.003
        limiter.release.value = 0.25
        master.connect(fade)
        fade.connect(limiter)
        limiter.connect(context.destination)

        melodyTone.connect(music)
        chordTone.connect(music)
        music.connect(master)
        music.connect(echo)
        bed.connect(master)
        rain.connect(master)
        rumble.connect(master)
        crackle.connect(master)
        small.connect(master)
        bell.connect(small)
        bell.connect(echo)

        // The echo: a dotted eighth later, again and again, each time quieter and duller.
        val delay = context.createDelay(2.0)
        delay.delayTime.value = 3 * VillageTune.SECONDS_PER_STEP / 2
        val again = context.createGain().also { it.gain.value = ECHO_AGAIN }
        val dull = lowpass(ECHO_TONE)
        echo.connect(delay)
        delay.connect(dull)
        dull.connect(again)
        again.connect(delay)
        dull.connect(master)
    }

    private fun lowpass(hertz: Double): BiquadFilterNode = context.createBiquadFilter().also {
        it.type = "lowpass"
        it.frequency.value = hertz
        it.Q.value = 0.5
    }

    /** From [time], the music sounds as [mood] asks: how loud, how bright, and how much soft noise under it. */
    fun setMood(mood: Mood, time: Double) {
        if (mood == this.mood) return
        val first = this.mood == null
        this.mood = mood
        fun AudioParam.towards(value: Double) {
            if (first) setValueAtTime(value, time) else setTargetAtTime(value, time, MOOD_GLIDE)
        }
        music.gain.towards(mood.level)
        melodyTone.frequency.towards(MELODY_DARK + (MELODY_BRIGHT - MELODY_DARK) * mood.brightness)
        chordTone.frequency.towards(CHORD_DARK + (CHORD_BRIGHT - CHORD_DARK) * mood.brightness)
        bed.gain.towards(BED * mood.noise)
    }

    fun fadeIn(time: Double) = fade(fadeLevelAt(time), 1.0, time, FADE_IN)

    fun fadeOut(time: Double) = fade(fadeLevelAt(time), 0.0, time, FADE_OUT)

    /** Back after a pause: up from silence quickly, so the sound does not start again mid-note at full level. */
    fun fadeBack(time: Double) = fade(0.0, 1.0, time, FADE_BACK)

    /** The fade's level at [time], from its plan. */
    private fun fadeLevelAt(time: Double): Double = when {
        time <= fadeFromTime -> fadeFrom
        time >= fadeToTime -> fadeTo
        else -> fadeFrom + (fadeTo - fadeFrom) * (time - fadeFromTime) / (fadeToTime - fadeFromTime)
    }

    /** From [time], a straight fade from [from] to [to] over [seconds], in place of whatever was planned after it. */
    private fun fade(from: Double, to: Double, time: Double, seconds: Double) {
        fade.gain.cancelScheduledValues(time)
        fade.gain.setValueAtTime(from, time)
        fade.gain.linearRampToValueAtTime(to, time + seconds)
        fadeFrom = from
        fadeFromTime = time
        fadeTo = to
        fadeToTime = time + seconds
    }

    /** A music-box note: a soft triangle with a quieter sine an octave up, struck and left to die away over [ring]. */
    fun pluck(time: Double, pitch: Int, velocity: Double, ring: Double, into: AudioNode = melodyTone, loud: Double = PLUCK) {
        voice("triangle", hertz(pitch), time, ring, loud * velocity, into)
        voice("sine", hertz(pitch) * 2, time, ring / 3, loud * velocity * 0.3, into)
    }

    /** A soft chord from [time] to [until]: two gently detuned triangles a note, swelling in and fading out. */
    fun chord(time: Double, until: Double, pitches: List<Int>, velocity: Double) {
        val peak = CHORD * velocity
        val swell = min(1.2, (until - time) / 2)
        for (pitch in pitches) for (cents in listOf(-7.0, 7.0)) {
            val tone = context.createOscillator()
            tone.type = "triangle"
            tone.frequency.setValueAtTime(hertz(pitch), time)
            tone.detune.value = cents
            val gain = context.createGain()
            gain.gain.setValueAtTime(0.0, time)
            gain.gain.linearRampToValueAtTime(peak, time + swell)
            gain.gain.setTargetAtTime(0.0, until, 0.5)
            tone.connect(gain)
            gain.connect(chordTone)
            tone.start(time)
            tone.stop(until + 3.0)
        }
    }

    /** A low, round note, plucked softly. */
    fun low(time: Double, pitch: Int, velocity: Double) =
        voice("sine", hertz(pitch), time, 2.0, BASS * velocity, music, attack = 0.02)

    /** A soft blip that rises a fourth, as a talk opens. */
    fun blip(time: Double) {
        val tone = context.createOscillator()
        tone.type = "sine"
        tone.frequency.setValueAtTime(hertz(79), time)
        tone.frequency.exponentialRampToValueAtTime(hertz(84), time + 0.08)
        val gain = context.createGain()
        gain.gain.setValueAtTime(0.0, time)
        gain.gain.linearRampToValueAtTime(BLIP, time + 0.01)
        gain.gain.setTargetAtTime(0.0, time + 0.01, 0.06)
        tone.connect(gain)
        gain.connect(small)
        tone.start(time)
        tone.stop(time + 0.5)
    }

    /** A very quiet, very short tick, for a letter typed out. */
    fun tick(time: Double) = voice("sine", hertz(96), time, 0.05, TICK, small, attack = 0.002)

    /** A gentle chime of three bell notes, F, A and C rising, at the change of day. */
    fun chime(time: Double) {
        listOf(77, 81, 84).forEachIndexed { index, pitch ->
            pluck(time + index * 0.13, pitch, 0.6, 2.4, into = bell, loud = CHIME)
        }
    }

    /**
     * Starts the bed of soft noise, heard only as much as the mood asks: brown noise (made here, from [seed]) under a
     * low tone, rising and falling slowly like the sea.
     */
    fun startBed(time: Double, seed: Int) {
        val rate = 22_050.0
        val length = (BED_SECONDS * rate).toInt()
        val overlap = (0.5 * rate).toInt()
        val random = Random(seed)
        var brown = 0.0
        val walk = DoubleArray(length + overlap) {
            brown = (brown + 0.02 * random.nextDouble(-1.0, 1.0)) / 1.02
            brown * 3.5
        }
        // Brown noise wanders off zero; taking its average away keeps the bed centred.
        val middle = walk.average()
        val noise = DoubleArray(walk.size) { walk[it] - middle }
        // The loop's end runs on into its start, so the seam cannot be heard.
        val buffer = context.createBuffer(1, length, rate)
        val data = buffer.getChannelData(0)
        for (i in 0 until length) {
            val sample = if (i < overlap) {
                val w = i.toDouble() / overlap
                noise[i] * w + noise[length + i] * (1 - w)
            } else {
                noise[i]
            }
            data[i] = sample.toFloat()
        }
        val source = context.createBufferSource()
        source.buffer = buffer
        source.loop = true
        val soft = lowpass(BED_TONE)
        val sea = context.createGain().also { it.gain.value = 0.7 }
        val tide = context.createOscillator()
        tide.type = "sine"
        tide.frequency.value = 0.08
        val depth = context.createGain().also { it.gain.value = 0.3 }
        tide.connect(depth)
        depth.connect(sea.gain)
        source.connect(soft)
        soft.connect(sea)
        sea.connect(bed)
        source.start(time)
        tide.start(time)
    }

    /** From [time], the soft bed of rain comes in gently when [on], and goes as gently when not. */
    fun setRain(on: Boolean, time: Double) {
        if (on) makeTownSounds(time)
        rain.gain.setTargetAtTime(if (on) RAIN else 0.0, time, RAIN_GLIDE)
    }

    /**
     * From [time], the train rumbles as loud as [train] says and the campfire crackles as loud as [fire] says, each
     * from 0.0 (silent) to 1.0, gliding there over a moment.
     */
    fun setNearby(train: Double, fire: Double, time: Double) {
        if (train > 0.0 || fire > 0.0) makeTownSounds(time)
        rumble.gain.setTargetAtTime(RUMBLE * train.coerceIn(0.0, 1.0), time, NEARBY_GLIDE)
        crackle.gain.setTargetAtTime(CRACKLE * fire.coerceIn(0.0, 1.0), time, NEARBY_GLIDE)
    }

    /**
     * Starts the town's own sounds at [time], all from noise made here, each a loop of a few seconds whose end runs on
     * into its start: rain as a gentle hiss with soft drops in it; the train as a low, dull rumble with a hum under it;
     * the fire as soft pops and snaps over a faint breath. They are made once, and sound only as their volumes allow.
     */
    private fun makeTownSounds(time: Double) {
        if (townSoundsMade) return
        townSoundsMade = true
        val random = Random(TOWN_SOUNDS_SEED)
        val rate = 22_050.0

        val rainLoop = DoubleArray(((LOOP_SECONDS + 0.5) * rate).toInt()) { random.nextDouble(-0.5, 0.5) }
        repeat((LOOP_SECONDS * DROPS_A_SECOND).toInt()) {
            // A drop: a short high note that dies at once.
            val at = random.nextInt(rainLoop.size - 400)
            val pitch = 1_800 + 2_400 * random.nextDouble()
            val loud = 0.15 + 0.3 * random.nextDouble()
            for (i in 0 until 400) rainLoop[at + i] += loud * sin(2 * PI * pitch * i / rate) * exp(-i / 40.0)
        }
        play(rainLoop, rate, time, rain, lowCut = RAIN_LOW, highCut = RAIN_HIGH)

        var brown = 0.0
        val rumbleLoop = DoubleArray(((LOOP_SECONDS + 0.5) * rate).toInt()) {
            brown = (brown + 0.02 * random.nextDouble(-1.0, 1.0)) / 1.02
            brown * 3.5
        }
        play(rumbleLoop, rate, time, rumble, highCut = RUMBLE_TONE)
        val hum = context.createOscillator()
        hum.type = "triangle"
        hum.frequency.value = RUMBLE_HUM
        val humLevel = context.createGain().also { it.gain.value = 0.25 }
        hum.connect(humLevel)
        humLevel.connect(rumble)
        hum.start(time)

        val fireLoop = DoubleArray(((LOOP_SECONDS + 0.5) * rate).toInt()) { random.nextDouble(-0.02, 0.02) }
        repeat((LOOP_SECONDS * POPS_A_SECOND).toInt()) {
            // A pop: a burst of noise a few milliseconds long, sometimes with a smaller snap or two after it.
            var at = random.nextInt(fireLoop.size - 2_000)
            var loud = 0.3 + 0.7 * random.nextDouble()
            repeat(1 + random.nextInt(3)) {
                val fade = 8.0 + 30 * random.nextDouble()
                for (i in 0 until 300) fireLoop[at + i] += loud * random.nextDouble(-1.0, 1.0) * exp(-i / fade)
                at += 150 + random.nextInt(400)
                loud *= 0.5
            }
        }
        play(fireLoop, rate, time, crackle, lowCut = CRACKLE_LOW)
    }

    /**
     * Plays [samples] at [rate] from [time] into [into], round and round: the last half second runs on into the start,
     * so the seam cannot be heard; the average is taken away, so the loop stays centred. Where given, everything below
     * [lowCut] hertz and above [highCut] is softened away.
     */
    private fun play(
        samples: DoubleArray,
        rate: Double,
        time: Double,
        into: AudioNode,
        lowCut: Double? = null,
        highCut: Double? = null,
    ) {
        val overlap = (0.5 * rate).toInt()
        val length = samples.size - overlap
        val middle = samples.average()
        val buffer = context.createBuffer(1, length, rate)
        val data = buffer.getChannelData(0)
        for (i in 0 until length) {
            val sample = if (i < overlap) {
                val w = i.toDouble() / overlap
                samples[i] * w + samples[length + i] * (1 - w)
            } else {
                samples[i]
            }
            data[i] = (sample - middle).toFloat()
        }
        val source = context.createBufferSource()
        source.buffer = buffer
        source.loop = true
        var end: AudioNode = source
        if (lowCut != null) {
            val filter = context.createBiquadFilter()
            filter.type = "highpass"
            filter.frequency.value = lowCut
            filter.Q.value = 0.5
            end.connect(filter)
            end = filter
        }
        if (highCut != null) {
            val filter = lowpass(highCut)
            end.connect(filter)
            end = filter
        }
        end.connect(into)
        source.start(time)
    }

    /** One oscillator struck at [time]: up to [peak] in [attack] seconds, then dying away over about [ring] seconds. */
    private fun voice(type: String, hertz: Double, time: Double, ring: Double, peak: Double, into: AudioNode, attack: Double = 0.006) {
        val tone = context.createOscillator()
        tone.type = type
        tone.frequency.setValueAtTime(hertz, time)
        val gain = context.createGain()
        gain.gain.setValueAtTime(0.0, time)
        gain.gain.linearRampToValueAtTime(peak, time + attack)
        gain.gain.setTargetAtTime(0.0, time + attack, ring / 5)
        tone.connect(gain)
        gain.connect(into)
        tone.start(time)
        tone.stop(time + attack + ring * 1.2)
    }

    companion object {
        /** Everything together is never louder than this, before the limiter. */
        const val MASTER = 0.5
        const val FADE_IN = 1.5
        const val FADE_OUT = 0.6
        const val FADE_BACK = 0.3
        const val MOOD_GLIDE = 1.5

        const val PLUCK = 0.22
        const val CHORD = 0.03
        const val BASS = 0.2
        const val BED = 0.1
        const val BED_SECONDS = 4.0
        const val BED_TONE = 500.0
        const val BLIP = 0.12
        const val TICK = 0.025
        const val CHIME = 0.12

        /** The town's own sounds at their loudest: the rain's bed, the train's rumble and the fire's crackle. */
        const val RAIN = 0.06
        const val RUMBLE = 0.16
        const val CRACKLE = 0.18
        const val RAIN_GLIDE = 1.2
        const val NEARBY_GLIDE = 0.15
        const val RAIN_LOW = 500.0
        const val RAIN_HIGH = 5_000.0
        const val DROPS_A_SECOND = 22.0
        const val RUMBLE_TONE = 600.0
        const val RUMBLE_HUM = 110.0
        const val CRACKLE_LOW = 700.0
        const val POPS_A_SECOND = 7.0
        const val LOOP_SECONDS = 4.0
        const val TOWN_SOUNDS_SEED = 2026

        const val ECHO = 0.25
        const val ECHO_AGAIN = 0.3
        const val ECHO_TONE = 1_800.0

        const val MELODY_BRIGHT = 4_500.0
        const val MELODY_DARK = 1_200.0
        const val CHORD_BRIGHT = 1_100.0
        const val CHORD_DARK = 450.0
    }
}

/**
 * Plays the village tune into a [Mixer], one bar at a time, each bar in the mood of the hour it starts in. Each pass
 * through the loop varies the theme of [seed] a little. Bars are timed on the mixer's own clock, so the same band
 * plays live, a little ahead of time, or renders to a buffer for a test.
 */
internal class Band(private val mixer: Mixer, private val seed: Int, private val mood: () -> Mood) {
    private var started = false
    private var bar = 0
    private var pass = 0
    private var barTime = 0.0
    private val tunes = mutableMapOf<Mood, Tune>()

    /** Starts the first bar at [time]. Later calls change nothing. */
    fun start(time: Double) {
        if (started) return
        started = true
        barTime = time
        mixer.startBed(time, seed)
    }

    /**
     * Plays every bar that starts before [horizon], in seconds on the mixer's clock. A note due before [now] is left
     * out: after the page stalls for a moment, the overdue notes would otherwise all start together.
     */
    fun scheduleUntil(horizon: Double, now: Double = Double.NEGATIVE_INFINITY) {
        while (started && barTime < horizon) playBar(now)
    }

    private fun playBar(now: Double) {
        val mood = mood()
        mixer.setMood(mood, barTime)
        val tune = tunes.getOrPut(mood) { VillageTune.compose(seed, mood, pass) }
        val first = bar * VillageTune.STEPS_PER_BAR
        val at = { step: Int -> barTime + (step - first) * VillageTune.SECONDS_PER_STEP }
        val inBar = { note: Note ->
            !note.isRest && note.step in first until first + VillageTune.STEPS_PER_BAR && at(note.step) >= now
        }
        // Darker moods let each note ring longer.
        val ring = RING * (2 - mood.brightness)
        for (note in tune.melody.filter(inBar)) mixer.pluck(at(note.step), note.pitches.single(), note.velocity, ring)
        for (note in tune.chords.filter(inBar)) mixer.chord(at(note.step), at(note.end), note.pitches, note.velocity)
        for (note in tune.bass.filter(inBar)) mixer.low(at(note.step), note.pitches.single(), note.velocity)
        barTime += VillageTune.STEPS_PER_BAR * VillageTune.SECONDS_PER_STEP
        bar += 1
        if (bar == VillageTune.BARS) {
            bar = 0
            pass += 1
            tunes.clear()
        }
    }

    private companion object {
        /** How long a music-box note rings by day, in seconds. */
        const val RING = 1.8
    }
}

/** What makes the sound once the player turns it on. A test hands in one that only notes what it is asked. */
internal interface Speaker {
    /**
     * Fades the music in, waking the sound if it was paused, and calls [whenPlaying] once the browser really plays it.
     * A browser may refuse until the player's own tap; then [whenPlaying] is never called, and a later call tries again.
     */
    fun on(whenPlaying: () -> Unit)

    /** Fades the music out, then pauses the sound. */
    fun off()

    /** Pauses the sound at once, as when the page is hidden. */
    fun pause()

    /** Wakes the sound again if it was on, as when the page is shown, and calls [whenPlaying] once it really plays. */
    fun resume(whenPlaying: () -> Unit)

    /** A soft blip, as a talk opens. */
    fun blip()

    /** A very quiet tick, for a letter of a line typed out. */
    fun tick()

    /** A gentle chime, at the change of day. */
    fun chime()

    /** A soft bed of rain while [rain] is true, under the tune; none when it is false. */
    fun weather(rain: Boolean)

    /**
     * How loud the town's own sounds are where the player is, each from 0.0 to 1.0: the passing [train], how near the
     * campfire the player is ([fire]), and how dark the town's own night is ([night]). The fire crackles only after
     * dark, in the town or on the player's own clock.
     */
    fun nearby(train: Double, fire: Double, night: Double)
}

/**
 * The sound the player hears, in a live [AudioContext]: the village tune, played a little ahead of time in the mood of
 * the player's [hour], and the game's small sounds at once.
 */
internal class WebAudioSpeaker(private val context: AudioContext, seed: Int, private val hour: () -> Int) : Speaker {
    private val mixer = Mixer(context)
    private val band = Band(mixer, seed) { moodAt(hour()) }

    /** The train's and the fire's loudness as last sent to the mixer, so a small change is not sent again. */
    private var trainSent = 0.0
    private var fireSent = 0.0
    private var playing = false
    private var timer: Int? = null
    private var pausing: Int? = null

    override fun on(whenPlaying: () -> Unit) {
        playing = true
        pausing?.let { window.clearTimeout(it) }
        pausing = null
        wake(whenPlaying)
        val now = context.currentTime
        band.start(now + LEAD)
        mixer.fadeIn(now)
        band.scheduleUntil(now + AHEAD, now)
        if (timer == null) timer = window.setInterval({ plan() }, TIMER_MILLIS)
    }

    override fun off() {
        playing = false
        mixer.fadeOut(context.currentTime)
        // Once the fade is over, nothing more is planned and the sound sleeps.
        pausing = window.setTimeout({
            pausing = null
            timer?.let { window.clearInterval(it) }
            timer = null
            sleep()
        }, ((Mixer.FADE_OUT + 0.1) * 1000).toInt())
    }

    override fun pause() = sleep()

    override fun resume(whenPlaying: () -> Unit) {
        if (!playing) return
        mixer.fadeBack(context.currentTime)
        wake(whenPlaying)
    }

    override fun blip() {
        if (playing) mixer.blip(context.currentTime)
    }

    override fun tick() {
        if (playing) mixer.tick(context.currentTime)
    }

    override fun chime() {
        if (playing) mixer.chime(context.currentTime)
    }

    override fun weather(rain: Boolean) = mixer.setRain(rain, context.currentTime)

    override fun nearby(train: Double, fire: Double, night: Double) {
        val crackle = Campfire.crackle(fire, max(night, darkness(moodAt(hour()))))
        // The town says this on every beat of its life; only a change that can be heard goes on to the mixer.
        val heard = abs(train - trainSent) >= NEARBY_STEP || abs(crackle - fireSent) >= NEARBY_STEP
        val silenced = (train == 0.0 && trainSent != 0.0) || (crackle == 0.0 && fireSent != 0.0)
        if (!heard && !silenced) return
        trainSent = train
        fireSent = crackle
        mixer.setNearby(train, crackle, context.currentTime)
    }

    /** How dark the player's own clock is: not at all by day, half in the evening, all of it at night. */
    private fun darkness(mood: Mood): Double = when (mood) {
        Mood.DAY -> 0.0
        Mood.EVENING -> 0.5
        Mood.NIGHT -> 1.0
    }

    /** Plans the bars a little ahead of the sound's clock, leaving out any note already due. */
    private fun plan() {
        val now = context.currentTime
        band.scheduleUntil(now + AHEAD, now)
    }

    /** Asks the browser to play, and calls [whenPlaying] if it does. A refusal leaves the context held. */
    private fun wake(whenPlaying: () -> Unit) {
        context.resume().then { if (context.state == "running") whenPlaying() }.catch { }
    }

    private fun sleep() {
        context.suspend().catch { }
    }

    private companion object {
        /** The first bar starts this long after the switch, and bars are planned this far ahead, in seconds. */
        const val LEAD = 0.1
        const val AHEAD = 1.2
        const val TIMER_MILLIS = 250

        /** The smallest change in the train's or the fire's loudness worth sending on. */
        const val NEARBY_STEP = 0.02
    }
}

/** True where the browser has Web Audio. */
internal fun hasWebAudio(): Boolean = window.asDynamic().AudioContext != null
