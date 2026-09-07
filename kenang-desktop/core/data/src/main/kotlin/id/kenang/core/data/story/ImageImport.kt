package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import java.io.File

/**
 * Import-time normalization (owner 2026-09-07: phone photos arrive as HEIC).
 * Formats the JVM's ImageIO can't decode — HEIC/HEIF, AVIF, WebP, TIFF — are
 * transparently converted to JPEG via the bundled ffmpeg (8.x decodes HEIF
 * through its mov demuxer + hevc decoder, AVIF via dav1d; verified live for
 * webp/tiff/avif round-trips). Conversion failure returns the original file
 * so the existing "file tidak bisa dibaca" message still surfaces.
 */
object ImageImport {

    /** Directly usable by ImageIO — no conversion needed. */
    val IMAGEIO_SAFE = setOf("jpg", "jpeg", "png", "bmp", "jfif")

    /** Convertible via ffmpeg at import. */
    val CONVERTIBLE = setOf("heic", "heif", "avif", "webp", "tif", "tiff")

    /** Everything the pickers/drops should accept. */
    val ACCEPTED: List<String> = (IMAGEIO_SAFE + CONVERTIBLE).toList()

    fun needsConversion(file: File): Boolean = file.extension.lowercase() in CONVERTIBLE

    /**
     * Returns a JPEG-safe file for [src]: the file itself when ImageIO can
     * read it, else an ffmpeg-converted JPEG in the temp dir. [ffmpegExe]
     * null (unstaged dev build) falls back to the original file.
     */
    fun normalize(src: File, ffmpegExe: File?): File {
        if (!needsConversion(src) || ffmpegExe == null) return src
        return runCatching {
            val out = File.createTempFile("kenang_import_", ".jpg")
            val proc = ProcessBuilder(
                ffmpegExe.absolutePath, "-y", "-hide_banner", "-loglevel", "error",
                "-i", src.absolutePath, "-frames:v", "1", "-q:v", "2", out.absolutePath,
            ).redirectErrorStream(true).start()
            val log = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (proc.exitValue() == 0 && out.length() > 0) {
                Napier.i("image import: converted ${src.name} -> jpeg (${out.length() / 1024} KB)")
                out
            } else {
                Napier.w("image import: conversion failed for ${src.name}: ${log.takeLast(200)}")
                out.delete()
                src
            }
        }.getOrDefault(src)
    }
}
