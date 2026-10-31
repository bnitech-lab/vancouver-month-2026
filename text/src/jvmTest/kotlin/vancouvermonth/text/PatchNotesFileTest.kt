package vancouvermonth.text

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PATCH_NOTES.md at the repository's root is the patch notes as [PatchNotes.markdown] writes them, so the file and the
 * game never say different things. After changing the notes, write the file again with
 * `UPDATE_PATCH_NOTES=1 ./gradlew :text:jvmTest --tests '*PatchNotesFileTest*' --rerun`.
 */
class PatchNotesFileTest {
    @Test
    fun `PATCH_NOTES md is the notes as the game has them`() {
        val repository = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val file = File(repository, "PATCH_NOTES.md")
        val expected = PatchNotes.markdown()
        if (System.getenv("UPDATE_PATCH_NOTES") == "1") file.writeText(expected)
        val written = if (file.exists()) file.readText() else ""
        assertEquals(
            expected,
            written,
            "PATCH_NOTES.md differs from PatchNotes.kt. Write it again with " +
                "UPDATE_PATCH_NOTES=1 ./gradlew :text:jvmTest --tests '*PatchNotesFileTest*' --rerun",
        )
    }
}
