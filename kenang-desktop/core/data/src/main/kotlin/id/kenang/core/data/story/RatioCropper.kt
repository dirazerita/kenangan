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
}
