package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The village tune, as notes: no sound is made here. */
class MusicTest {
    private val seeds = listOf(1, 7, 1011, VillageTune.THEME_SEED, 99_999)

    @Test
    fun `the same seed, mood and pass always give the same tune`() {
        for (seed in seeds) for (mood in Mood.entries) for (pass in 0..2) {
            val one = VillageTune.compose(seed, mood, pass)
            val two = VillageTune.compose(seed, mood, pass)
            assertEquals(one.parts, two.parts, "seed $seed, $mood, pass $pass")
        }
        assertNotEquals(VillageTune.compose(1, Mood.DAY).melody, VillageTune.compose(2, Mood.DAY).melody)
    }

    @Test
    fun `every note of every part is in the scale`() {
        for (seed in seeds) for (mood in Mood.entries) for (pass in 0..3) {
            for (note in VillageTune.compose(seed, mood, pass).notes) for (pitch in note.pitches) {
                assertTrue(VillageTune.inScale(pitch), "$pitch in seed $seed, $mood, pass $pass")
            }
        }
        // F, G, A, C and D are in; the other seven are not.
        assertEquals(listOf(65, 67, 69, 72, 74), (60..71).map { it + 5 }.filter { VillageTune.inScale(it) })
    }

    @Test
    fun `each part fills the loop exactly, note after note with no gap and no overlap`() {
        assertEquals(128, VillageTune.LOOP_STEPS)
        for (seed in seeds) for (mood in Mood.entries) for (pass in 0..3) {
            for (part in VillageTune.compose(seed, mood, pass).parts) {
                var at = 0
                for (note in part) {
                    assertEquals(at, note.step, "seed $seed, $mood, pass $pass: $part")
                    assertTrue(note.length > 0)
                    at = note.end
                }
                assertEquals(VillageTune.LOOP_STEPS, at, "seed $seed, $mood, pass $pass")
            }
        }
    }

    @Test
    fun `the evening is sparser than the day, and the night sparser still`() {
        for (seed in seeds) for (pass in 0..3) {
            val day = VillageTune.compose(seed, Mood.DAY, pass)
            val evening = VillageTune.compose(seed, Mood.EVENING, pass)
            val night = VillageTune.compose(seed, Mood.NIGHT, pass)
            assertTrue(evening.melody.count { !it.isRest } < day.melody.count { !it.isRest }, "seed $seed, pass $pass")
            assertTrue(evening.notes.size < day.notes.size)
            assertTrue(night.notes.size < evening.notes.size)
            // At night: no chords, and only a few slow notes.
            assertTrue(night.chords.all { it.isRest })
            assertTrue(night.notes.size <= 12, "${night.notes.size} notes at night")
            assertTrue(night.notes.all { it.length >= 2 * VillageTune.STEPS_PER_BAR })
        }
    }

    @Test
    fun `the night is the quietest, then the evening, then the day`() {
        assertTrue(Mood.NIGHT.level < Mood.EVENING.level && Mood.EVENING.level < Mood.DAY.level)
        assertTrue(Mood.NIGHT.brightness < Mood.EVENING.brightness && Mood.EVENING.brightness < Mood.DAY.brightness)
        for (seed in seeds) for (pass in 0..3) {
            val loudness = Mood.entries.map { VillageTune.compose(seed, it, pass).loudness }
            assertTrue(loudness[2] < loudness[1] && loudness[1] < loudness[0], "seed $seed, pass $pass: $loudness")
            val softest = Mood.entries.map { mood -> VillageTune.compose(seed, mood, pass).notes.maxOf { it.velocity } }
            assertTrue(softest[2] <= softest[1] && softest[1] <= softest[0], "$softest")
        }
    }

    @Test
    fun `the melody is a song: phrases open on their chord, the middle and the end come home`() {
        for (seed in seeds) {
            val melody = VillageTune.compose(seed, Mood.DAY).melody
            // Every bar opens with a note, never a rest.
            for (bar in 0 until VillageTune.BARS) {
                assertTrue(melody.any { it.step == bar * VillageTune.STEPS_PER_BAR && !it.isRest }, "bar $bar, seed $seed")
            }
            // The eighth and the last bar end on F.
            for (bar in listOf(7, 15)) {
                val last = melody.last { it.step < (bar + 1) * VillageTune.STEPS_PER_BAR }
                assertEquals(5, last.pitches.single() % 12, "bar $bar, seed $seed")
            }
            // A' and A'' open with the same two bars as A.
            val opening = melody.filter { it.step < 16 }.map { it.pitches }
            assertEquals(opening, melody.filter { it.step in 32 until 48 }.map { it.pitches })
            assertEquals(opening, melody.filter { it.step in 96 until 112 }.map { it.pitches })
        }
    }

    @Test
    fun `each pass varies the theme a little, never the whole of it`() {
        val theme = VillageTune.compose(VillageTune.THEME_SEED, Mood.DAY, 0).melody
        val passes = (1..6).map { VillageTune.compose(VillageTune.THEME_SEED, Mood.DAY, it).melody }
        assertTrue(passes.any { it.map { n -> n.pitches } != theme.map { n -> n.pitches } }, "No pass ever changes a note")
        for (melody in passes) {
            val sameStart = melody.count { note -> theme.any { it.step == note.step && it.pitches == note.pitches } }
            assertTrue(sameStart >= theme.size * 3 / 4, "A pass changed too much: $sameStart of ${theme.size}")
        }
    }

    @Test
    fun `the hour picks the mood`() {
        assertEquals(Mood.NIGHT, moodAt(0))
        assertEquals(Mood.NIGHT, moodAt(5))
        assertEquals(Mood.DAY, moodAt(6))
        assertEquals(Mood.DAY, moodAt(17))
        assertEquals(Mood.EVENING, moodAt(18))
        assertEquals(Mood.EVENING, moodAt(21))
        assertEquals(Mood.NIGHT, moodAt(22))
    }

    @Test
    fun `seventy beats a minute, so a loop of sixteen bars lasts about 55 seconds`() {
        val seconds = VillageTune.LOOP_STEPS * VillageTune.SECONDS_PER_STEP
        assertTrue(seconds in 54.8..54.9, "$seconds")
    }
}
