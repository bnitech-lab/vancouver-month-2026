package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.TodaysRun

/** Columns as a phone's narrow card fills them: two for a Hangul syllable, one for anything else. */
private fun columns(line: String): Int = line.sumOf { if (it in '가'..'힣') 2 else 1 }

/** At most two rows of a 375-point phone's notes card at 15px, so no line runs on. */
private const val MAX_LINE_COLUMNS = 72

/** One row of the card's version heading. */
private const val MAX_HEADLINE_COLUMNS = 32

class PatchNotesTest {
    @Test
    fun `the newest entry comes first and is the current version`() {
        assertEquals(PatchNotes.CURRENT, PatchNotes.ALL.first().version)
        assertEquals("v${PatchNotes.CURRENT}", PatchNotes.versionLabel(PatchNotes.CURRENT))
    }

    @Test
    fun `versions go strictly down the list, each newer than the one below it`() {
        for ((newer, older) in PatchNotes.ALL.zipWithNext()) {
            assertTrue(PatchNotes.isNewer(newer.version, older.version), "${newer.version} is not after ${older.version}")
        }
        assertEquals(PatchNotes.ALL.size, PatchNotes.ALL.map { it.version }.toSet().size)
    }

    @Test
    fun `every date is a real one, and no entry is dated before the one below it`() {
        for (note in PatchNotes.ALL) {
            assertNotNull(TodaysRun.parse(note.date), "${note.version}: ${note.date}")
            assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(note.date), "${note.version}: write ${note.date} as 2026-10-31")
        }
        for ((newer, older) in PatchNotes.ALL.zipWithNext()) assertTrue(newer.date >= older.date, newer.version)
        assertEquals("2026년 10월 31일", PatchNotes.dateLabel("2026-10-31"))
        assertEquals("2026년 9월 2일", PatchNotes.dateLabel("2026-09-02"))
    }

    @Test
    fun `every entry has a headline and at least one line, in short lines`() {
        for (note in PatchNotes.ALL) {
            assertTrue(note.headline.isNotBlank(), note.version)
            assertTrue(columns(note.headline) <= MAX_HEADLINE_COLUMNS, "Headline too long: ${note.headline}")
            assertTrue(note.sections.isNotEmpty(), "${note.version} says nothing")
            for ((name, lines) in note.sections) {
                for (line in lines) {
                    assertTrue(line.isNotBlank(), "${note.version} $name has an empty line")
                    assertTrue(columns(line) <= MAX_LINE_COLUMNS, "Too long for a phone (${columns(line)}): $line")
                }
            }
        }
    }

    @Test
    fun `the words have no em dash and nothing written for the makers`() {
        val words = PatchNotes.ALL.flatMap { note -> listOf(note.headline) + note.sections.flatMap { it.second } }
        val makersOnly = listOf("PR", "#", "SwiftShader", "WebGL", "Kotlin", ".kt", "commit", "merge", "instanced")
        for (line in words) {
            assertFalse('—' in line, "Em dash in: $line")
            for (word in makersOnly) assertFalse(word in line, "\"$word\" in: $line")
        }
        assertFalse('—' in PatchNotes.markdown())
    }

    @Test
    fun `the sections keep the order players expect, and an empty one is left out`() {
        val note = PatchNote("9.9", "2026-11-01", "시험", added = listOf("가"), fixed = listOf("다"), known = listOf("라"))
        assertEquals(listOf("새로운 기능", "수정", "알려진 문제"), note.sections.map { it.first })
        assertEquals(listOf("새로운 기능", "개선", "수정", "알려진 문제"),
            listOf(PatchNotes.ADDED, PatchNotes.IMPROVED, PatchNotes.FIXED, PatchNotes.KNOWN))
    }

    @Test
    fun `a newer version is told by its numbers, part by part`() {
        assertTrue(PatchNotes.isNewer("0.10", "0.9"))
        assertTrue(PatchNotes.isNewer("0.11", "0.10"))
        assertTrue(PatchNotes.isNewer("1.0", "0.99"))
        assertTrue(PatchNotes.isNewer("0.11.1", "0.11"))
        assertFalse(PatchNotes.isNewer("0.11", "0.11"))
        assertFalse(PatchNotes.isNewer("0.11", "0.11.0"))
        assertFalse(PatchNotes.isNewer("0.9", "0.10"))
        // Nothing opened yet, or a kept value that does not read, is older than any version.
        assertTrue(PatchNotes.isNewer("0.11", null))
        assertTrue(PatchNotes.isNewer("0.11", "seen"))
        assertTrue(PatchNotes.isNewer("0.11", ""))
        assertNull(PatchNotes.parts("0.011"))
        assertNull(PatchNotes.parts("v0.11"))
        assertEquals(listOf(0, 11), PatchNotes.parts("0.11"))
    }

    @Test
    fun `the markdown has every version, newest first, with its date, headline and lines`() {
        val text = PatchNotes.markdown()
        val headings = text.lines().filter { it.startsWith("## ") }
        assertEquals(PatchNotes.ALL.map { "## v${it.version} · ${PatchNotes.dateLabel(it.date)}" }, headings)
        for (note in PatchNotes.ALL) {
            assertTrue("**${note.headline}**" in text, note.headline)
            for ((_, lines) in note.sections) for (line in lines) assertTrue("- $line\n" in text, line)
        }
        assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"))
    }
}
