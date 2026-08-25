package id.kenang.core.common.i18n

/**
 * User-facing UI strings — Indonesian only for now (MASTER_PROMPT_02 §Screens).
 * Keep every user-visible literal here so a future locale switch is mechanical.
 * Tone rules (MEMORY §7): empathetic; "hidupkan kenangannya", never "hidupkan orangnya".
 */
object Strings {
    // App
    const val APP_NAME = "Kenang (Beta)"

    // Home
    const val HOME_TITLE = "Proyek Kenangan"
    const val HOME_NEW_PROJECT = "Proyek Baru"
    const val HOME_EMPTY = "Belum ada proyek. Mulai hidupkan kenangan Anda dengan menekan \"Proyek Baru\"."
    const val HOME_DELETE_TITLE = "Hapus proyek?"
    const val HOME_DELETE_MESSAGE = "Semua foto, keyframe, dan video di proyek ini akan dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan."
    const val HOME_DELETE_CONFIRM = "Hapus"
    const val HOME_MONTHLY_SPEND_PREFIX = "Estimasi biaya API bulan ini: "

    // Generic
    const val CANCEL = "Batal"
    const val SAVE = "Simpan"
    const val CLOSE = "Tutup"
    const val RETRY = "Coba lagi"
    const val NEXT = "Lanjut"
    const val BACK = "Kembali"
    const val SKIP = "Lewati dulu"
    const val DONE = "Selesai"
    const val ESTIMATE_LABEL = "estimasi"

    // Status chips
    const val STATUS_DRAFT = "Draf"
    const val STATUS_PROCESSING = "Diproses"
    const val STATUS_DONE = "Selesai"
    const val STATUS_FAILED = "Gagal"

    // Offline
    const val OFFLINE_BANNER = "Anda sedang offline — fitur AI tidak tersedia. Proyek tetap bisa dilihat."

    // Settings
    const val SETTINGS_TITLE = "Pengaturan"
    const val SETTINGS_API_KEYS = "API Key"
    const val SETTINGS_OUTPUT_FOLDER = "Folder output"
    const val SETTINGS_LANGUAGE = "Bahasa"
    const val SETTINGS_LANGUAGE_ID = "Bahasa Indonesia"
    const val SETTINGS_TELEMETRY = "Kirim data pemakaian anonim (belum aktif)"
    const val SETTINGS_VERSION = "Versi aplikasi"
    const val SETTINGS_SPEND_PER_KEY = "Estimasi biaya bulan ini per key"
    const val SETTINGS_REOPEN_ONBOARDING = "Buka panduan awal lagi"

    // API Key manager
    const val KEYS_FAL_TITLE = "Key fal.ai (wajib)"
    const val KEYS_FAL_DESC = "Semua fitur AI utama berjalan lewat akun fal.ai Anda. Anda bisa menambahkan lebih dari satu key — aplikasi otomatis beralih ke key berikutnya saat saldo habis."
    const val KEYS_FAL_ADD = "Tambah key fal"
    const val KEYS_FAL_LABEL_HINT = "Label (mis. \"Utama\" atau nama klien)"
    const val KEYS_KEY_HINT = "Tempel API key di sini"
    const val KEYS_GEMINI_TITLE = "Google Gemini (opsional)"
    const val KEYS_GEMINI_DESC = "Analisis foto lebih tajam. Gratis dibuat di Google AI Studio."
    const val KEYS_EL_TITLE = "ElevenLabs (opsional)"
    const val KEYS_EL_DESC = "Suara narasi premium. Perlu paket berbayar ElevenLabs untuk suara Indonesia."
    const val KEYS_TEST = "Tes koneksi"
    const val KEYS_TEST_COST_NOTE = "biaya tes < $0.001"
    const val KEYS_TEST_OK = "Terhubung"
    const val KEYS_TEST_FAIL = "Gagal terhubung"
    const val KEYS_STATUS_ACTIVE = "aktif"
    const val KEYS_STATUS_BACKUP = "cadangan"
    const val KEYS_STATUS_EXHAUSTED = "saldo habis"
    const val KEYS_REMOVE = "Hapus"
    const val KEYS_MOVE_UP = "Naikkan"
    const val KEYS_MOVE_DOWN = "Turunkan"
    const val KEYS_OPEN_FAL = "Buka halaman pembuatan key fal.ai"
    const val KEYS_OPEN_GEMINI = "Buka Google AI Studio"
    const val KEYS_OPEN_EL = "Buka dashboard ElevenLabs"
    const val KEYS_SWITCHED_TOAST = "Saldo key '%1' habis — beralih ke '%2'"
    const val KEYS_ALL_EXHAUSTED_CTA = "Top up / kelola key"

    // Onboarding
    const val ONBOARD_TITLE = "Selamat datang di Kenang"
    const val ONBOARD_SUBTITLE = "Tiga langkah singkat untuk mulai menghidupkan kenangan Anda."
    const val ONBOARD_STEP1_TITLE = "1 · Buat akun fal.ai"
    const val ONBOARD_STEP1_BODY = "Kenang memakai akun AI milik Anda sendiri (bukan milik kami), sehingga foto dan biaya sepenuhnya di tangan Anda. Buat akun gratis di fal.ai, lalu buka halaman API Keys."
    const val ONBOARD_STEP2_TITLE = "2 · Tempel API key Anda"
    const val ONBOARD_STEP2_BODY = "Salin key dari halaman fal.ai, tempel di bawah ini, lalu jalankan tes koneksi."
    const val ONBOARD_STEP3_TITLE = "3 · Siap digunakan"
    const val ONBOARD_STEP3_BODY = "Key opsional (Google Gemini untuk analisis lebih tajam, ElevenLabs untuk suara premium) bisa ditambahkan kapan saja lewat Pengaturan."
    const val ONBOARD_BYOK_NOTICE = "Catatan penting: semua pemakaian AI ditagihkan langsung ke akun penyedia milik Anda dan tunduk pada ketentuan layanan masing-masing penyedia. Kenang tidak menambahkan biaya apa pun."
    const val ONBOARD_ANTI_FARM_NOTICE = "Fitur multi-key hanya untuk key yang benar-benar Anda miliki (mis. key cadangan atau akun per klien). Membuat banyak akun fal demi kredit gratis melanggar ketentuan fal.ai dan berisiko diblokir."

    // About
    const val ABOUT_TITLE = "Tentang Kenang"
    const val ABOUT_PRIVACY = "Kebijakan Privasi"
    const val ABOUT_TERMS = "Ketentuan Layanan"
    const val ABOUT_LICENSES = "Lisensi pihak ketiga"
    const val ABOUT_FFMPEG = "Aplikasi ini menyertakan FFmpeg (lisensi GPL/LGPL) — https://ffmpeg.org"
    const val ABOUT_PRIVACY_HEADLINE = "Foto dan video Anda tidak pernah menyentuh server Kenang. Semua pemrosesan AI berjalan langsung antara perangkat Anda dan penyedia AI pilihan Anda."

    // New project placeholder (Phase 03 owns the real wizard)
    const val NEW_PROJECT_PLACEHOLDER = "Alur pembuatan proyek akan hadir di fase berikutnya."
}
