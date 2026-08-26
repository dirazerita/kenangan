package id.kenang.core.common

/**
 * Maps every [AppError] to empathetic Indonesian copy (MEMORY §7).
 * UI must ALWAYS go through this — no raw exception text ever reaches a user.
 */
object ErrorTranslator {

    data class UiError(
        val title: String,
        val message: String,
        /** Optional call-to-action label, e.g. "Top up / kelola key". */
        val ctaLabel: String? = null,
        /** Optional URL the CTA opens. */
        val ctaUrl: String? = null,
    )

    fun translate(error: AppError): UiError = when (error) {
        is AppError.ContentBlocked -> UiError(
            title = "Konten tidak dapat diproses",
            message = "Maaf, penyedia AI menolak memproses foto atau teks ini. " +
                "Coba gunakan foto lain, atau ubah deskripsi gerakan menjadi lebih sederhana. " +
                "Kenangan Anda tetap aman di perangkat ini.",
        )
        is AppError.InvalidKey -> UiError(
            title = "API key salah/tidak aktif",
            message = when (error.provider) {
                Provider.FAL -> "Key fal.ai" + (error.keyLabel?.let { " '$it'" } ?: "") +
                    " tidak valid atau sudah dinonaktifkan. Periksa kembali di dashboard fal.ai."
                Provider.GEMINI -> "Key Google Gemini tidak valid. Periksa kembali di Google AI Studio."
                Provider.ELEVENLABS -> "Key ElevenLabs tidak valid. Periksa kembali di dashboard ElevenLabs."
            },
            ctaLabel = "Buka Pengaturan Key",
        )
        is AppError.ProviderBalance -> UiError(
            title = "Saldo habis",
            message = when (error.provider) {
                Provider.FAL -> "Saldo semua key fal Anda habis. Silakan top up di fal.ai atau tambahkan key lain."
                else -> "Saldo/kuota penyedia habis. Silakan periksa akun Anda."
            },
            ctaLabel = "Top up / kelola key",
            ctaUrl = if (error.provider == Provider.FAL) "https://fal.ai/dashboard/billing" else null,
        )
        is AppError.RateLimited -> UiError(
            title = "Terlalu banyak permintaan",
            message = "Penyedia AI sedang membatasi permintaan. Tunggu sebentar lalu coba lagi.",
        )
        is AppError.ProviderFailed -> UiError(
            title = "Penyedia AI bermasalah",
            message = "Terjadi gangguan di sisi penyedia AI. Ini bukan kesalahan Anda — coba lagi beberapa saat lagi.",
        )
        is AppError.Timeout -> UiError(
            title = "Koneksi terlalu lama",
            message = "Permintaan melebihi batas waktu. Periksa koneksi internet Anda lalu coba lagi.",
        )
        AppError.Offline -> UiError(
            title = "Tidak ada koneksi",
            message = "Anda sedang offline. Proyek tetap bisa dibuka, tetapi fitur AI membutuhkan koneksi internet.",
        )
        is AppError.AssemblyFailed -> UiError(
            title = "Perakitan video terkendala",
            message = "Terjadi kendala saat merangkai video di perangkat ini. Tidak ada biaya API untuk langkah ini. " +
                "Coba lagi; jika berlanjut, pastikan ruang penyimpanan cukup lalu mulai ulang aplikasi.",
        )
        is AppError.Unknown -> UiError(
            title = "Terjadi kesalahan",
            message = "Terjadi kesalahan yang tidak terduga. Coba lagi; jika berlanjut, mulai ulang aplikasi.",
        )
    }
}
