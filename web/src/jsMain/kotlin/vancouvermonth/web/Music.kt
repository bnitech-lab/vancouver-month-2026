package vancouvermonth.web

import kotlin.math.abs
import kotlin.random.Random

/**
 * How the village tune sounds at an hour of the player's own day: the whole tune by day, a softer and sparser one in
 * the evening, and at night only a quiet bed of soft noise under a few slow notes. [level] is how loud the notes are,
 * [noise] how loud the bed of soft noise is (0 to 1), and [brightness] how much of the notes' high end is kept.
 */
internal enum class Mood(val level: Double, val noise: Double, val brightness: Double) {
    DAY(1.0, 0.0, 1.0),
    EVENING(0.7, 0.35, 0.55),
    NIGHT(0.45, 1.0, 0.3),
}

/** The mood for an hour (0 to 23) on the player's clock: day from 6:00, evening from 18:00, night from 22:00. */
internal fun moodAt(hour: Int): Mood = when (hour) {
    in 6..17 -> Mood.DAY
    in 18..21 -> Mood.EVENING
    else -> Mood.NIGHT
}

/**
 * One note of a tune, or a rest when it has no [pitches]. It starts [step] eighth notes into the loop and lasts
 * [length] of them. A pitch is a MIDI number (69 is the A above middle C), and a chord has several. [velocity] is how
 * hard it is struck, from 0 to 1.
 */
internal data class Note(val step: Int, val length: Int, val pitches: List<Int>, val velocity: Double) {
    val isRest: Boolean
        get() = pitches.isEmpty()

    val end: Int
        get() = step + length
}

/**
 * One loop of the village tune in one mood: a music-box [melody], soft [chords] under it, and a low [bass]. Each part
 * is a run of notes and rests that fills the loop from its first step to its last.
 */
internal class Tune(val mood: Mood, val melody: List<Note>, val chords: List<Note>, val bass: List<Note>) {
    /** The three parts in one list (for tests). */
    val parts: List<List<Note>>
        get() = listOf(melody, chords, bass)

    /** Every note that sounds, in every part. */
    val notes: List<Note>
        get() = parts.flatten().filterNot { it.isRest }

    /**
     * A rough measure of how loud the loop is: each pitch counted by how hard it is struck and how long it lasts
     * (for tests).
     */
    val loudness: Double
        get() = mood.level * mood.level * notes.sumOf { it.velocity * it.velocity * it.length * it.pitches.size }
}

/**
 * The village's own tune, written by rules rather than copied from anywhere: F major pentatonic (F, G, A, C and D,
 * so no two notes ever clash), 70 beats a minute, a loop of 16 bars of four beats. The melody has the shape of a short
 * song, A A' B A'': a four-bar phrase, the same start with an ending that comes home, a higher middle, and the start
 * again with the home ending. Its notes come from a seed, and each pass through the loop changes a few of them a
 * little, so the loop never sounds quite the same twice.
 */
internal object VillageTune {
    const val BEATS_PER_MINUTE = 70
    const val STEPS_PER_BEAT = 2
    const val STEPS_PER_BAR = 4 * STEPS_PER_BEAT
    const val BARS = 16
    const val LOOP_STEPS = BARS * STEPS_PER_BAR
    const val SECONDS_PER_STEP = 60.0 / BEATS_PER_MINUTE / STEPS_PER_BEAT

    /** The seed of the theme the page plays, the village's own tune: of the themes read, the easiest to sing. */
    const val THEME_SEED = 1011

    /** F's place among the twelve notes of an octave (C is 0), and the scale as steps above F. */
    private const val KEY = 5
    private val SCALE = setOf(0, 2, 4, 7, 9)

    /** True when [pitch] is one of F, G, A, C or D, in any octave. */
    fun inScale(pitch: Int): Boolean = ((pitch - KEY) % 12 + 12) % 12 in SCALE

    /** The melody's notes, low to high, the range of a small music box: A4 up to F6. */
    private val LADDER = listOf(69, 72, 74, 77, 79, 81, 84, 86, 89)

    /** Where F5 and F6, the melody's home notes, are on the ladder. */
    private val HOME = listOf(3, 8)

    /** A chord: its low note, and three soft notes above it. */
    private class Chord(val bass: Int, val pad: List<Int>) {
        val tones: Set<Int> = (pad + bass).map { it % 12 }.toSet()
    }

    private val F = Chord(41, listOf(53, 57, 60))
    private val D_MINOR = Chord(38, listOf(53, 57, 62))
    private val G_OPEN = Chord(43, listOf(55, 60, 62))
    private val C_OPEN = Chord(48, listOf(55, 60, 62))
    private val A_OPEN = Chord(45, listOf(57, 60, 67))

    /** One chord a bar, the same in every mood, so a change of mood at any bar stays in tune. */
    private val PROGRESSION = listOf(
        F, D_MINOR, G_OPEN, C_OPEN,
        F, D_MINOR, G_OPEN, F,
        D_MINOR, A_OPEN, G_OPEN, C_OPEN,
        F, D_MINOR, C_OPEN, F,
    )

    /** Rhythms for a bar, in eighth notes, and for the last bar of a phrase, which holds its last note. */
    private val RHYTHMS = listOf(
        listOf(2, 2, 2, 2), listOf(3, 1, 2, 2), listOf(2, 1, 1, 4), listOf(4, 2, 2),
        listOf(2, 2, 4), listOf(3, 3, 2), listOf(1, 1, 2, 2, 2), listOf(2, 2, 1, 1, 2),
    )
    private val CADENCES = listOf(listOf(4, 4), listOf(2, 6), listOf(3, 5), listOf(8))

    /** The loop in [mood], from the theme of [seed], on its [pass] through (0 is the theme itself). */
    fun compose(seed: Int, mood: Mood, pass: Int = 0): Tune {
        val day = dayMelody(seed, pass)
        return when (mood) {
            Mood.DAY -> Tune(mood, day, chords(0.5), dayBass())
            Mood.EVENING -> Tune(mood, thin(day), chords(0.4), eveningBass())
            Mood.NIGHT -> Tune(mood, slowNotes(seed, pass), listOf(rest(0, LOOP_STEPS)), nightBass())
        }
    }

    private fun rest(step: Int, length: Int) = Note(step, length, emptyList(), 0.0)

    private fun chordAt(bar: Int) = PROGRESSION[bar % BARS]

    /** The day's melody: the theme of [seed] in its song shape, varied a little on each [pass]. */
    private fun dayMelody(seed: Int, pass: Int): List<Note> {
        val random = Random(seed)
        val opening = bars(random, from = 0, until = 4, startAt = 4, endHome = false)
        // A': the opening's first two bars again (the chords there are the same), then an ending that comes home.
        val again = shifted(opening, fromBar = 0, bars = 2, toBar = 4)
        val againEnd = bars(random, from = 6, until = 8, startAt = lastRung(again), endHome = true)
        val middle = bars(random, from = 8, until = 12, startAt = 6, endHome = false)
        val close = shifted(opening, fromBar = 0, bars = 2, toBar = 12)
        val closeEnd = bars(random, from = 14, until = 16, startAt = lastRung(close), endHome = true)
        val theme = opening + again + againEnd + middle + close + closeEnd
        return varied(theme, Random(seed * 31 + pass * 7919 + 1), pass)
    }

    /**
     * Bars [from] to [until] (not included) of melody, walking the ladder from rung [startAt]. Each bar opens on a
     * note of its chord, and the last bar holds its last note: home (F) when [endHome], or a note of the chord.
     */
    private fun bars(random: Random, from: Int, until: Int, startAt: Int, endHome: Boolean): List<Note> {
        val notes = mutableListOf<Note>()
        var rung = startAt
        for (bar in from until until) {
            val last = bar == until - 1
            val rhythm = (if (last) CADENCES else RHYTHMS).random(random)
            var step = bar * STEPS_PER_BAR
            rhythm.forEachIndexed { index, length ->
                val downbeat = index == 0
                val final = last && index == rhythm.lastIndex
                rung = when {
                    final && endHome -> HOME.minBy { abs(it - rung) }
                    downbeat || final -> nearestChordTone(walk(rung, random), chordAt(bar))
                    else -> walk(rung, random)
                }
                val resting = !downbeat && !final && random.nextDouble() < 0.12
                notes += if (resting) {
                    rest(step, length)
                } else {
                    Note(step, length, listOf(LADDER[rung]), if (downbeat) 0.8 else 0.62)
                }
                step += length
            }
        }
        return notes
    }

    /** A step along the ladder: mostly to a neighbour, sometimes a skip or the same note, never off either end. */
    private fun walk(rung: Int, random: Random): Int {
        val move = listOf(-2, -1, -1, -1, 0, 1, 1, 1, 2).random(random)
        val next = rung + move
        return if (next in LADDER.indices) next else rung - move
    }

    /** The rung nearest [rung] whose note belongs to [chord], the lower one on a tie. */
    private fun nearestChordTone(rung: Int, chord: Chord): Int =
        LADDER.indices.filter { LADDER[it] % 12 in chord.tones }.minBy { abs(it - rung) * 2 + if (it > rung) 1 else 0 }

    /** [bars] bars of [notes] starting at bar [fromBar], moved to start at bar [toBar]. */
    private fun shifted(notes: List<Note>, fromBar: Int, bars: Int, toBar: Int): List<Note> {
        val first = fromBar * STEPS_PER_BAR
        val until = first + bars * STEPS_PER_BAR
        val by = (toBar - fromBar) * STEPS_PER_BAR
        return notes.filter { it.step in first until until }.map { it.copy(step = it.step + by) }
    }

    /** The rung of the last note in [notes] that sounds. */
    private fun lastRung(notes: List<Note>): Int = LADDER.indexOf(notes.last { !it.isRest }.pitches.single())

    /**
     * The theme on a pass through the loop: every note struck a shade harder or softer, and after the first pass a few
     * notes moved to a neighbour on the ladder or turned into two quick notes. Bar openings and phrase endings stay.
     */
    private fun varied(theme: List<Note>, random: Random, pass: Int): List<Note> = theme.flatMap { note ->
        val velocity = if (note.isRest) 0.0 else (note.velocity + random.nextDouble(-0.06, 0.06)).coerceIn(0.3, 0.95)
        val fixed = note.step % STEPS_PER_BAR == 0 || note.length >= 4 || note.isRest
        val rung = if (note.isRest) -1 else LADDER.indexOf(note.pitches.single())
        val roll = random.nextDouble()
        when {
            pass == 0 || fixed -> listOf(note.copy(velocity = velocity))
            roll < 0.1 -> {
                val moved = walk(rung, random).let { if (it == rung) rung else it }
                listOf(note.copy(pitches = listOf(LADDER[moved]), velocity = velocity))
            }
            roll < 0.16 && note.length == 2 -> {
                val neighbour = if (rung + 1 < LADDER.size) rung + 1 else rung - 1
                listOf(
                    note.copy(length = 1, velocity = velocity),
                    Note(note.step + 1, 1, listOf(LADDER[neighbour]), velocity * 0.85),
                )
            }
            else -> listOf(note.copy(velocity = velocity))
        }
    }

    /**
     * The evening's melody: only the day's notes that open a bar or fall on its third beat, each left to ring until
     * the next, a little softer.
     */
    private fun thin(day: List<Note>): List<Note> {
        val kept = day.filter { !it.isRest && it.step % (2 * STEPS_PER_BEAT) == 0 }
        val notes = mutableListOf<Note>()
        if (kept.first().step > 0) notes += rest(0, kept.first().step)
        kept.forEachIndexed { index, note ->
            val next = kept.getOrNull(index + 1)?.step ?: LOOP_STEPS
            notes += note.copy(length = next - note.step, velocity = note.velocity * 0.8)
        }
        return notes
    }

    /** A soft chord every bar, struck at [velocity]. */
    private fun chords(velocity: Double): List<Note> =
        (0 until BARS).map { Note(it * STEPS_PER_BAR, STEPS_PER_BAR, chordAt(it).pad, velocity) }

    /** By day, the chord's low note on the first beat and a fifth (or the octave) on the third. */
    private fun dayBass(): List<Note> = (0 until BARS).flatMap { bar ->
        val low = chordAt(bar).bass
        val answer = if (inScale(low + 7)) low + 7 else low + 12
        val half = STEPS_PER_BAR / 2
        listOf(Note(bar * STEPS_PER_BAR, half, listOf(low), 0.7), Note(bar * STEPS_PER_BAR + half, half, listOf(answer), 0.55))
    }

    /** In the evening, only the chord's low note, once a bar. */
    private fun eveningBass(): List<Note> =
        (0 until BARS).map { Note(it * STEPS_PER_BAR, STEPS_PER_BAR, listOf(chordAt(it).bass), 0.55) }

    /** At night, a low note every four bars, very soft. */
    private fun nightBass(): List<Note> =
        (0 until BARS step 4).map { Note(it * STEPS_PER_BAR, 4 * STEPS_PER_BAR, listOf(chordAt(it).bass), 0.4) }

    /** At night, a few slow notes: one every two bars, or a rest, from the chord there, low on the ladder. */
    private fun slowNotes(seed: Int, pass: Int): List<Note> {
        val random = Random(seed * 17 + pass * 101 + 3)
        val slot = 2 * STEPS_PER_BAR
        return (0 until BARS step 2).map { bar ->
            val chord = chordAt(bar)
            if (bar > 0 && random.nextDouble() < 0.35) {
                rest(bar * STEPS_PER_BAR, slot)
            } else {
                val rung = (0..5).filter { LADDER[it] % 12 in chord.tones }.random(random)
                Note(bar * STEPS_PER_BAR, slot, listOf(LADDER[rung]), 0.42)
            }
        }
    }
}
