package id.kenang.core.data.media

import kotlin.test.Test
import kotlin.test.assertEquals

/** "Foto berbicara dari MP3" (owner 2026-09-18): what happens to a recording before it goes to the provider. */
class AudioIntakeTest {

    @Test
    fun `mp3 and wav within the ceiling go as they are, others are re-encoded`() {
        assertEquals(AudioIntake.Action.COPY, AudioIntake.plan("mp3", 12_000, 60.0, canTranscode = true))
        assertEquals(AudioIntake.Action.COPY, AudioIntake.plan("WAV", 59_990, 60.0, canTranscode = true))
        assertEquals(AudioIntake.Action.TRANSCODE, AudioIntake.plan("m4a", 12_000, 60.0, canTranscode = true))
        assertEquals(AudioIntake.Action.REJECT, AudioIntake.plan("m4a", 12_000, 60.0, canTranscode = false))
    }

    @Test
    fun `a recording over the ceiling is cut where ffmpeg exists and refused where it does not`() {
        assertEquals(AudioIntake.Action.TRIM, AudioIntake.plan("mp3", 95_000, 60.0, canTranscode = true))
        assertEquals(AudioIntake.Action.REJECT, AudioIntake.plan("mp3", 95_000, 60.0, canTranscode = false))
        assertEquals(AudioIntake.Action.REJECT, AudioIntake.plan("mp3", null, 60.0, canTranscode = true), "unreadable")
        assertEquals(AudioIntake.Action.REJECT, AudioIntake.plan("mp3", 0, 60.0, canTranscode = true), "empty")
    }

    @Test
    fun `billing follows the recording up to the ceiling`() {
        assertEquals(12.5, AudioIntake.billableSeconds(12_500, 60.0), 1e-9)
        assertEquals(60.0, AudioIntake.billableSeconds(95_000, 60.0), 1e-9)
        assertEquals(1.0, AudioIntake.billableSeconds(300, 60.0), 1e-9, "never under a second")
    }
}
