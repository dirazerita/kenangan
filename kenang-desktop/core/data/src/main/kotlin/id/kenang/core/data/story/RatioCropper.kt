package id.kenang.core.data.story

import java.io.File

/**
 * Center-crops an image file IN PLACE to a project ratio ("9:16" | "16:9") —
 * needed wherever a user photo becomes a keyframe directly, or the video step
 * would crop people out arbitrarily (D-022). Platform-implemented: AWT on
 * desktop, Bitmap on Android; both must be failure-silent (unreadable formats
 * leave the file untouched).
 */
interface RatioCropper {
    fun cropToRatio(file: File, ratio: String)

    /**
     * Writes [source] centred on a canvas of [ratio] to [out], with flat
     * mid-grey bands where the scene has to be extended (owner 2026-09-17:
     * asked to restore AND reframe to 9:16 in one go, the edit model
     * re-staged the whole photo - the people came back doubled, a bench
     * vanished; with the bands drawn in advance it only has to fill them).
     * Null when the ratio already matches or the image cannot be read.
     */
    fun padToRatio(source: File, ratio: String, out: File): File?
}
