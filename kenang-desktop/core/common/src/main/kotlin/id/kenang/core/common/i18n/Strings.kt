package id.kenang.core.common.i18n

/**
 * User-facing UI strings — Indonesian only for now (MASTER_PROMPT_02 §Screens).
 * Keep every user-visible literal here so a future locale switch is mechanical.
 * Tone rules (MEMORY §7): empathetic; "hidupkan kenangannya", never "hidupkan orangnya".
 */
object Strings {
    // App
    const val APP_NAME = "Kenang (Beta)"

    /** Owner branding — shown prominently on Home and in About. */
    const val BRAND_TEXT = "Powered By Vibetool.Id - By Harindra Darmawan"

    // Home
    const val HOME_TITLE = "Proyek Kenangan"
    const val HOME_NEW_PROJECT = "Proyek Baru"
    const val HOME_EMPTY = "Belum ada proyek. Mulai hidupkan kenangan Anda dengan menekan \"Proyek Baru\"."
    const val HOME_DELETE_TITLE = "Hapus proyek?"
    const val HOME_DELETE_MESSAGE = "Semua foto, keyframe, dan video di proyek ini akan dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan."
    const val HOME_DELETE_CONFIRM = "Hapus"
    const val HOME_MONTHLY_SPEND_PREFIX = "Estimasi biaya API bulan ini: "

    // Motion Control (owner 2026-09-02)
    const val MOTION_TITLE = "Motion Control"
    const val MOTION_SUBTITLE = "Karakter di foto Anda bergerak mengikuti video referensi — memakai Kling 3.0 Pro Motion Control, model transfer gerakan terbaik di fal.ai."
    const val MOTION_PICK_PHOTO = "Pilih foto karakter"
    const val MOTION_PICK_VIDEO = "Pilih video referensi gerakan"
    const val MOTION_PHOTO_NOTE = "Karakter harus terlihat jelas (≥5% area foto, proporsi tubuh jelas)."
    const val MOTION_VIDEO_NOTE = "Video berisi gerakan yang ingin ditiru. Maks 200MB."
    const val MOTION_ORIENTATION = "Mode gerakan"
    const val MOTION_ORIENT_VIDEO = "Ikuti gerakan video (maks %1 dtk)"
    const val MOTION_ORIENT_VIDEO_DESC = "Terbaik untuk gerakan tubuh kompleks — tarian, gestur, aktivitas."
    const val MOTION_ORIENT_IMAGE = "Pertahankan framing foto (maks %1 dtk)"
    const val MOTION_ORIENT_IMAGE_DESC = "Terbaik untuk mempertahankan komposisi & kamera foto asli."
    const val MOTION_PROMPT_LABEL = "Tuntunan (opsional)"
    const val MOTION_PROMPT_HINT = "Contoh: gerakan lembut dan natural, suasana hangat…"
    const val MOTION_KEEP_SOUND = "Pertahankan suara dari video referensi"
    const val MOTION_START = "Buat Video Gerakan"
    const val MOTION_RUNNING = "Membuat video gerakan… proses ini bisa 3–10 menit, biarkan terbuka."
    const val MOTION_DONE_PREFIX = "Selesai! Video tersimpan di: "
    const val MOTION_OPEN_FOLDER = "Buka folder hasil"
    const val MOTION_PLAY = "Putar video"
    const val MOTION_DURATION_PREFIX = "Durasi referensi: "
    const val MOTION_DURATION_UNKNOWN = "durasi tidak terbaca — estimasi memakai batas maksimal"
    const val MOTION_DURATION_CAPPED = " (dipotong ke batas maks %1 dtk)"
    const val MOTION_NEED_BOTH = "Pilih foto dan video referensinya dulu."
    const val MOTION_RESULT_NOTE = "Hasil disimpan ke: %1"
    const val MOTION_DROP_HINT = "Tercepat: geser foto dan video dari Explorer, lepaskan di mana saja di layar ini — keduanya sekaligus juga bisa."
    const val MOTION_MODEL_LABEL = "Model"
    const val MOTION_MODEL_NOTE = "Opsi bertanda ◦ belum teruji penuh — hasil dan biaya bisa berbeda. Pilihan diingat untuk pemakaian berikutnya."
    const val SB_DROP_REPLACE_HINT = "Geser foto ke kartu adegan untuk mengganti gambarnya."

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

    // Error diagnostics (Stabilization: make failures visible)
    const val ERROR_DETAIL_PREFIX = "Detail teknis: "
    const val ERROR_OPEN_LOGS = "Buka folder log"

    // Settings
    const val SETTINGS_TITLE = "Pengaturan"
    const val SETTINGS_API_KEYS = "API Key"
    const val SETTINGS_OUTPUT_FOLDER = "Folder output"
    const val SETTINGS_LANGUAGE = "Bahasa"
    const val SETTINGS_LANGUAGE_ID = "Bahasa Indonesia"
    const val SETTINGS_TELEMETRY = "Kirim data pemakaian anonim (belum aktif)"
    const val SETTINGS_VERSION = "Versi aplikasi"
    const val SETTINGS_SPEND_PER_KEY = "Estimasi biaya bulan ini per key"
    const val SETTINGS_SHOW_SPEND = "Tampilkan biaya per key"
    const val SETTINGS_HIDE_SPEND = "Sembunyikan"

    // Settings → Model AI
    const val SETTINGS_MODELS_TITLE = "Model AI"
    const val SETTINGS_MODELS_DESC = "Pilih model untuk tiap tahap. \"Bawaan\" mengikuti pengaturan aplikasi; opsi bertanda ◦ belum teruji penuh — hasil dan biaya bisa berbeda."
    const val SETTINGS_MODEL_VIDEO = "1 · Generate Video"
    const val SETTINGS_MODEL_VIDEO_NOTE = "Menimpa model dari pilihan Kualitas (Hemat/Standar/Premium) saat membuat video."
    const val SETTINGS_MODEL_ANALYSIS = "2 · Analisa Foto"
    const val SETTINGS_MODEL_TTS = "3 · Suara Narasi — model"
    const val SETTINGS_MODEL_VOICE = "3 · Suara Narasi — jenis suara"

    // Kloning Suara (owner 2026-09-02)
    const val SETTINGS_CLONE_TITLE = "Kloning Suara"
    const val SETTINGS_CLONE_DESC = "Kloning suara orang tercinta dari rekaman audio, lalu pakai sebagai suara narasi. Memakai model kloning terbaik di fal.ai (MiniMax Voice Clone) yang tersambung langsung ke narasi Speech-02 HD. Sampel minimal 10 detik, makin panjang & jernih makin mirip (WAV/MP3/M4A)."
    const val SETTINGS_CLONE_LABEL = "Nama suara (mis. Suara Ayah)"
    const val SETTINGS_CLONE_BUTTON = "Pilih audio & kloning"
    const val SETTINGS_CLONE_PICK_TITLE = "Pilih rekaman suara"
    const val SETTINGS_CLONE_RUNNING = "Mengkloning suara… biasanya 1–2 menit, biarkan terbuka."
    const val SETTINGS_CLONE_DONE = "Suara berhasil dikloning: "
    const val SETTINGS_CLONE_EMPTY = "Belum ada suara kloning."
    const val SETTINGS_CLONE_LIST_TITLE = "Suara kloning tersimpan"
    const val SETTINGS_CLONE_KEY_NOTE = "Terikat ke akun API key pembuatnya — jangan hapus key itu selama suara ini dipakai."
    const val SETTINGS_CLONE_APPEARS = "Suara hasil kloning muncul otomatis di pilihan Suara narasi (wizard langkah 2) dengan tanda 🎤."
    const val SETTINGS_MODEL_DEFAULT = "Bawaan"
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
    const val KEYS_PRIORITY = "Prioritas"
    const val KEYS_CHECK_ALL_BALANCES = "Cek Saldo"
    const val KEYS_CHECKING_ALL = "Mengecek %1 key…"
    const val KEYS_BALANCE_TOTAL = "Total saldo: %1 — %2 key terbaca, %3 tidak terbaca (perlu key admin)"
    const val KEYS_BALANCE_PREFIX = "Sisa saldo: "
    const val KEYS_BALANCE_CHECKING = "cek saldo…"
    const val KEYS_BALANCE_NEEDS_ADMIN = "saldo perlu key admin"
    const val KEYS_BALANCE_UNAVAILABLE = "saldo tidak tersedia"
    const val KEYS_BALANCE_ADMIN_HINT = "Sisa saldo hanya bisa dibaca oleh key ber-scope Admin. Key biasa tetap berfungsi penuh untuk membuat video — fal menolak permintaan saldonya (403)."
    const val SETTINGS_BROWSE_FOLDER = "Pilih folder…"
    const val KEYS_COPY_LINK = "Salin"
    const val KEYS_LINK_COPIED = "Disalin ✓"
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
    const val ABOUT_MUSIC_PREFIX = "Musik bawaan: "
    const val ABOUT_PRIVACY_HEADLINE = "Foto dan video Anda tidak pernah menyentuh server Kenang. Semua pemrosesan AI berjalan langsung antara perangkat Anda dan penyedia AI pilihan Anda."

    // ------------------- Phase 03: wizard -------------------
    const val WIZARD_TITLE = "Proyek Baru"
    const val WIZARD_STEP1 = "Foto"
    const val WIZARD_STEP2 = "Cerita"
    const val WIZARD_STEP3 = "Musik"
    const val WIZARD_STEP4 = "Format & Suasana"
    // %1 = limits.max_photos from config — never hardcode the number here.
    const val WIZARD_ADD_PHOTOS = "Pilih foto (1–%1)"
    const val WIZARD_DROP_HINT = "Atau geser & lepaskan foto ke area ini"
    const val WIZARD_DROP_ACTIVE = "Lepaskan foto di sini…"
    const val WIZARD_PHOTO_LIMIT = "Maksimal %1 foto per proyek."
    const val WIZARD_BADGE_BAGUS = "Bagus"
    const val WIZARD_BADGE_CUKUP = "Cukup"
    const val WIZARD_BADGE_KURANG = "Kurang"
    // Storyboard scene-count picker (step 1, any photo count)
    const val WIZARD_SINGLE_PHOTO_TITLE = "Storyboard dari 1 foto"
    const val WIZARD_SINGLE_PHOTO_DESC = "Berapa adegan yang ingin dibuat dari foto ini? Setiap adegan menghasilkan satu foto baru berdasarkan foto pertama, dengan wajah dan penampilan yang sama (±%1/foto). Setelah foto-fotonya jadi di storyboard, proses lanjut seperti biasa."
    const val WIZARD_SCENES_TITLE = "Jumlah adegan storyboard"
    const val WIZARD_SCENES_DESC = "Berapa adegan yang ingin dibuat dari %1 foto ini? Jika adegan lebih banyak dari foto, adegan tambahan dibuat dari foto-foto pilihanmu dengan aktivitas berbeda-beda, wajah dan penampilan tetap sama (±%2/foto). Otomatis = mengikuti jumlah foto."
    const val WIZARD_GUIDANCE_LABEL = "Tuntunan adegan (opsional)"
    const val WIZARD_GUIDANCE_HINT = "Arahkan isi adegannya — contoh: bermain layang-layang di sawah, minum teh di teras sore hari, tertawa bersama di bawah pohon, berjalan bergandengan di jalan kampung…"
    const val WIZARD_SINGLE_PHOTO_AUTO = "Otomatis"
    const val WIZARD_SINGLE_PHOTO_SCENES_SUFFIX = " adegan"

    const val WIZARD_NARRATION_LABEL = "Narasi (opsional)"
    const val WIZARD_NARRATION_SHUFFLE = "Ganti contoh narasi"
    const val WIZARD_NARRATION_TEMPLATE_NOTE = "Ini hanya contoh — silakan ubah sesuai cerita Anda."

    /**
     * Prefill suggestions for the narration box. The wizard picks one at
     * random per project and never repeats the previous project's pick
     * (owner 2026-09-01: a single fixed template made every project sound the
     * same). Different subjects and tones on purpose — grandparent, parent,
     * whole family, childhood home, gratitude, short and poetic.
     */
    val WIZARD_NARRATION_TEMPLATES: List<String> = listOf(
        "Waktu boleh berlalu, namun senyum dan kasih sayangmu tetap tinggal di hati kami. Di setiap sudut rumah ini, kami masih merasakan kehangatanmu — tawa yang menenangkan, nasihat yang menguatkan, dan doa-doa yang tak pernah putus untuk kami. Terima kasih untuk setiap pengorbanan yang tak sempat kami balas, untuk cinta yang kau berikan tanpa pamrih, dan untuk setiap kenangan indah yang pernah kita bagi bersama. Meski kini kita tak lagi bersama, engkau tetap hidup dalam setiap cerita yang kami kisahkan dan dalam setiap doa yang kami panjatkan.",

        "Ada rindu yang tak pernah selesai, dan namamu selalu ada di dalamnya. Kami menyimpan suaramu, cara tertawamu, juga sabarmu yang tak pernah habis menghadapi kami. Setiap foto ini membawa kami pulang ke hari-hari yang dulu terasa biasa saja, padahal ternyata itulah hari-hari paling berharga. Terima kasih sudah menjadi bagian terindah dari perjalanan hidup kami. Doa kami selalu menyertaimu, di mana pun engkau berada sekarang.",

        "Dari tangan yang selalu sibuk bekerja, tumbuh kami yang hari ini bisa berdiri. Engkau jarang mengeluh, jarang meminta, tetapi selalu memberi lebih dari yang kami butuhkan. Kami belajar arti sabar dari caramu menjalani hidup, dan arti kasih dari caramu memaafkan. Semoga setiap tetes keringatmu menjadi cahaya yang menerangi jalanmu. Kami akan menjaga apa yang telah kau tanam, dan meneruskannya kepada anak-anak kami.",

        "Rumah ini pernah begitu ramai. Ada suara di dapur, ada langkah kaki di ruang tamu, ada panggilan lembut saat waktu makan tiba. Kini suasananya berbeda, tetapi kenangannya tidak pernah pergi. Kami masih menyimpan semua tawa itu, semua cerita sebelum tidur, semua nasihat yang dulu kami dengar sambil lalu dan baru kami pahami sekarang. Terima kasih untuk rumah yang engkau bangun dengan cinta.",

        "Kami berkumpul lagi hari ini, membuka album lama, dan tiba-tiba semua terasa dekat kembali. Wajah-wajah ini, momen-momen ini, adalah bukti bahwa kita pernah begitu bahagia bersama. Ada yang sudah pergi, ada yang kini jauh, tetapi ikatan ini tidak pernah putus. Semoga kenangan ini terus hidup, diceritakan turun-temurun, agar generasi berikutnya tahu betapa hangatnya keluarga ini.",

        "Terima kasih untuk setiap doa yang kau panjatkan diam-diam. Kami tidak selalu melihatnya, tetapi kami merasakan hasilnya di sepanjang hidup kami. Untuk semua kesabaran menghadapi kenakalan kami, untuk semua pelukan saat kami gagal, dan untuk semua senyum yang menyembunyikan lelahmu — kami mohon maaf dan kami berterima kasih. Semoga kebaikanmu dibalas dengan tempat terbaik.",

        "Masa kecil kami penuh dengan suaramu. Sepeda di halaman, hujan sore hari, dan cerita yang tidak pernah membosankan meski diulang berkali-kali. Kami dulu tidak tahu bahwa masa itu akan begitu kami rindukan. Sekarang, setiap kali mengingatnya, kami tersenyum lebih dulu sebelum air mata jatuh. Terima kasih sudah membuat hari-hari sederhana itu terasa begitu istimewa.",

        "Engkau mengajari kami berjalan, lalu melepas kami berlari, dan tetap menunggu di rumah setiap kali kami pulang. Tidak pernah ada syarat dalam kasih sayangmu. Hari ini kami ingin mengatakan apa yang dulu terlalu jarang kami ucapkan: kami sayang padamu, kami bangga padamu, dan kami bersyukur pernah menjadi bagian dari hidupmu.",

        "Kenangan tidak pernah benar-benar pergi. Ia hanya berpindah tempat, dari mata ke hati, dari hari ini ke selamanya. Dalam setiap foto ini ada cerita yang tidak cukup diceritakan dengan kata-kata. Biarlah wajah-wajah ini yang bicara, tentang cinta yang pernah nyata, dan tentang rindu yang masih terus tinggal.",

        "Hari ini kami mengenangmu dengan syukur, bukan hanya dengan air mata. Karena hidupmu meninggalkan begitu banyak kebaikan yang masih kami rasakan sampai sekarang. Setiap nasihatmu masih kami pegang, setiap teladanmu masih kami tiru. Selamat beristirahat dengan tenang. Kami akan baik-baik saja, seperti yang selalu engkau doakan.",

        "Ada orang-orang yang kehadirannya membuat hidup terasa lebih ringan, dan engkau salah satunya. Kami merindukan obrolan panjang tanpa tujuan, candaan yang hanya kita yang mengerti, dan kebersamaan yang dulu terasa akan selamanya. Terima kasih untuk waktu yang pernah kita punya. Kenangan ini kami simpan baik-baik.",

        "Untuk semua yang pernah kita lalui bersama — suka, duka, tawa, dan air mata — terima kasih. Perjalanan ini tidak akan sama tanpamu. Foto-foto ini bukan sekadar gambar, melainkan potongan hidup yang ingin kami jaga selamanya. Semoga setiap kali video ini diputar, kehangatan itu kembali hadir di tengah kita.",
    )

    /** Kept for compatibility; the wizard uses the randomized list above. */
    val WIZARD_NARRATION_TEMPLATE: String get() = WIZARD_NARRATION_TEMPLATES.first()
    const val WIZARD_NO_NARRATION = "Tanpa narasi"
    const val WIZARD_NO_NARRATION_HINT = "Video akan dibuat tanpa suara narasi dan tanpa subtitle — hanya musik (jika dipilih di langkah berikutnya)."
    const val WIZARD_NARRATION_HINT = "Tulis pesan atau kenangan singkat — akan dibacakan sebagai narasi."
    const val WIZARD_VOICE_LABEL = "Suara narasi"
    const val WIZARD_VOICE_PREVIEW = "Dengar contoh"
    const val WIZARD_VOICE_COST = "biaya ±$0.001"
    const val WIZARD_MUSIC_BUNDLED = "Musik bawaan (bebas royalti)"
    const val WIZARD_MUSIC_BUNDLED_EMPTY = "Koleksi musik bawaan akan hadir di pembaruan berikutnya."
    const val WIZARD_MUSIC_UPLOAD = "Pilih file musik sendiri (MP3/WAV)"
    const val WIZARD_MUSIC_COPYRIGHT_TITLE = "Perhatian hak cipta"
    const val WIZARD_MUSIC_COPYRIGHT_BODY = "Pastikan Anda memiliki hak untuk menggunakan musik ini. Video berisi musik berhak-cipta bisa dibisukan atau dihapus oleh platform tempat Anda membagikannya."
    const val WIZARD_MUSIC_COPYRIGHT_ACK = "Saya memahami dan memiliki hak atas musik ini"
    const val WIZARD_MUSIC_NONE = "Tanpa musik"
    const val WIZARD_RATIO_LABEL = "Rasio video"
    const val WIZARD_VIBE_LABEL = "Suasana (vibe)"
    const val WIZARD_VIBE_CUSTOM = "Suasana kustom"
    const val WIZARD_VIBE_CUSTOM_DESC = "Tulis suasanamu sendiri"
    const val WIZARD_VIBE_CUSTOM_LABEL = "Deskripsi suasana kustom"
    const val WIZARD_VIBE_CUSTOM_HINT = "Contoh: di puncak gunung bersalju saat matahari terbit, dengan kabut tipis dan cahaya keemasan…"
    const val WIZARD_DURATION_LABEL = "Durasi per adegan"
    const val WIZARD_RESTORE_LABEL = "Restorasi foto lama — perbaiki goresan, warna pudar & ketajaman"
    const val WIZARD_NAME_LABEL = "Nama proyek"
    const val WIZARD_FINISH = "Mulai Analisis"

    // Consent gate (MEMORY §7)
    const val CONSENT_TITLE = "Sebelum melanjutkan"
    const val CONSENT_BODY = "Dengan melanjutkan, Anda menyatakan memiliki hak atas foto-foto ini dan izin dari orang yang ada di dalamnya (atau keluarganya). Kenang dirancang untuk menghidupkan kenangan — video yang dihasilkan adalah interpretasi AI dari fotomu, bukan rekaman nyata."
    const val CONSENT_ACCEPT = "Saya menyetujui"

    // Analysis progress
    const val ANALYSIS_TITLE = "Menghidupkan kenanganmu…"
    const val ANALYSIS_UPLOADING = "Menyiapkan foto…"
    const val ANALYSIS_MODERATING = "Memeriksa foto…"
    const val ANALYSIS_READING = "Membaca foto…"
    const val ANALYSIS_PLANNING = "Menyusun cerita…"
    const val ANALYSIS_SAVING = "Menyimpan storyboard…"
    const val ANALYSIS_ELAPSED_PREFIX = "Berjalan "
    const val ANALYSIS_BLOCKED_TITLE = "Ada foto yang tidak dapat diproses"
    const val ANALYSIS_BLOCKED_BODY = "Penyedia AI menolak memproses salah satu foto. Ini kadang terjadi pada foto tertentu — coba hapus atau ganti foto yang ditandai, lalu ulangi."
    const val ANALYSIS_BLOCKED_REMOVE = "Hapus foto ini & ulangi"
    const val ANALYSIS_BLOCKED_BACK = "Kembali ke pemilihan foto"

    // Storyboard
    const val SB_TITLE_FALLBACK = "Storyboard"
    const val SB_CREATE_VIDEO = "Buat Video"
    const val SB_EDIT_PROMPT = "Ubah gerakan"
    const val SB_REGEN_KEYFRAME = "Buat ulang gambar"
    const val SB_REPLACE_IMAGE = "Ganti dengan foto sendiri (gratis)"
    const val SB_ADD_SCENE = "Tambah adegan sendiri"
    const val SB_ADD_SCENE_TITLE = "Tambah adegan dari foto sendiri"
    const val SB_ADD_SCENE_PICK = "Pilih foto"
    const val SB_ADD_SCENE_DESC = "Deskripsi adegan"
    const val SB_ADD_SCENE_DESC_HINT = "Contoh: Beliau duduk di teras sambil menikmati teh hangat, lalu tersenyum menatap halaman…"
    const val SB_ADD_SCENE_NOTE = "Gratis — foto Anda langsung dipakai sebagai gambar adegan."
    const val SB_ADD_SCENE_NEED_PHOTO = "Pilih foto dulu."
    const val SB_ADD_SCENE_DROP_HERE = "Geser & lepaskan foto ke sini — atau klik untuk memilih."
    const val SB_ADD_AI_SCENE = "Adegan baru dengan AI"
    const val SB_ADD_AI_SCENE_NOTE = "AI melanjutkan cerita dengan aktivitas baru dari foto proyek ini."
    const val SB_ADD_AI_NO_SOURCE = "Belum ada adegan berbasis foto untuk dilanjutkan AI."
    const val SB_ADD_AIREF = "Adegan AI dari foto referensi"
    const val SB_ADD_AIREF_NOTE = "Pakai foto baru sebagai sumber adegan — AI fokus ke sosok yang terlihat jelas."
    const val SB_ADD_AIREF_TITLE = "Adegan AI dari foto referensi"
    const val SB_ADD_AIREF_DESC = "Deskripsi adegan (opsional)"
    const val SB_ADD_AIREF_DESC_HINT = "Contoh: Beliau tersenyum tenang di kursi roda, keluarga mendampingi…"
    const val SB_ADD_AIREF_FACE_NOTE = "Orang yang wajahnya terpotong atau tidak terlihat jelas di foto TIDAK akan diikutkan — supaya AI tidak mengarang wajah orang tak dikenal."
    const val SB_ADD_AIREF_PHOTO_JOIN_NOTE = "Foto referensi ikut tersimpan sebagai foto proyek."
    const val SB_DELETE_SCENE = "Hapus"
    const val SB_DELETE_LAST_SCENE = "Minimal satu adegan harus tersisa."
    const val SB_FUSION_BADGE = "Gabungan"
    const val SB_KEYFRAME_FAILED = "Gambar gagal dibuat"
    const val SB_RETRY = "Coba lagi"
    const val SB_ESTIMATE_LABEL = "Estimasi — tagihan riil ada di akun provider Anda"
    const val SB_ESTIMATE_UNKNOWN = "biaya tidak diketahui"
    const val SB_PREVIEW = "🔍 Lihat"
    const val SB_PHOTOS_READY = "Foto storyboard: %1/%2 siap"
    const val SB_KEYFRAME_ETA = "±%1 dtk lagi"
    const val SB_KEYFRAME_ALMOST = "hampir selesai…"
    const val SB_MOTION_CATEGORY = "Gerakan"
    const val SB_MOTION_CAMERA = "Kamera"
    const val SB_MOTION_ADJECTIVES = "Kata sifat tambahan (maks 8 kata, opsional)"
    const val SB_SCENE_DURATION = "Durasi"

    // Confirm dialog
    const val CONFIRM_TITLE = "Siap membuat video?"
    const val CONFIRM_TIER_LABEL = "Kualitas"
    const val CONFIRM_TIER_HEMAT = "Hemat — paling murah, kualitas standar (belum tersedia)"
    const val CONFIRM_TIER_STANDAR = "Standar — pilihan terbaik untuk kebanyakan video"
    const val CONFIRM_TIER_PREMIUM = "Premium — detail terbaik, biaya lebih tinggi"
    const val CONFIRM_FIRST_TIME_NOTE = "Biaya ditagih penyedia AI langsung ke akun Anda — Kenang tidak menambahkan biaya."
    const val CONFIRM_DISCLAIMER = "Video adalah interpretasi AI dari fotomu."
    const val CONFIRM_GO = "Buat Video"

    // ------------------- Phase 04: generation -------------------
    const val GEN_TITLE = "Membuat video kenanganmu…"
    const val GEN_SUBTITLE = "Setiap adegan dibuat oleh AI lewat akun Anda. Ini bisa memakan beberapa menit."
    const val GEN_PROGRESS = "%1 dari %2 adegan selesai"
    const val GEN_SCENE_PREFIX = "Adegan "
    const val GEN_STATUS_QUEUED = "Antre"
    const val GEN_STATUS_RUNNING = "Diproses"
    const val GEN_STATUS_DONE = "Selesai"
    const val GEN_STATUS_FAILED = "Gagal"
    const val GEN_ELAPSED_PREFIX = "Berjalan "
    const val GEN_EDIT_MOTION_CTA = "Ubah gerakan di storyboard"
    const val GEN_OPEN_KEYS_CTA = "Buka Pengaturan Key"
    const val GEN_RETRY_SCENE = "Coba lagi adegan ini"
    const val GEN_BILLING_NOTE = "Catatan: percobaan yang gagal tetap bisa ditagih oleh penyedia AI. Setiap akun fal menagih pemakaiannya masing-masing."
    const val GEN_PARTIAL_TITLE = "Sebagian adegan belum berhasil"
    const val GEN_PARTIAL_BODY = "Beberapa adegan gagal dibuat. Anda bisa melanjutkan video hanya dengan adegan yang berhasil, atau mencoba lagi adegan yang gagal."
    const val GEN_PARTIAL_CONTINUE = "Lanjutkan dengan adegan yang berhasil"
    const val GEN_PARTIAL_RETRY = "Coba lagi yang gagal"
    const val GEN_PAUSED_BALANCE_TITLE = "Pembuatan dijeda — saldo habis"
    const val GEN_PAUSED_BALANCE_BODY = "Saldo semua key fal Anda habis. Adegan yang sudah selesai tetap tersimpan; lanjutkan kapan saja setelah top up."
    const val GEN_RESUME = "Lanjutkan pembuatan"
    const val GEN_TTS_RUNNING = "Membuat narasi suara…"
    const val GEN_SUBS_BUILDING = "Menyusun subtitle…"
    const val GEN_ASSEMBLING = "Merangkai video di perangkat ini…"
    const val GEN_ASSEMBLING_NOTE = "Langkah ini berjalan lokal — tidak ada biaya API."
    const val GEN_SUBS_TOGGLE = "Sertakan subtitle"
    const val GEN_FFMPEG_MISSING = "Komponen perakit video (FFmpeg) tidak ditemukan. Pasang ulang aplikasi untuk memperbaikinya."

    // Duration rule dialog (F5.3)
    const val DUR_TITLE = "Narasi lebih panjang dari video"
    const val DUR_BODY = "Durasi narasi melebihi total durasi video. Pilih cara menyesuaikannya:"
    const val DUR_OPT_EXTEND = "Perpanjang durasi adegan"
    const val DUR_OPT_EXTEND_DISABLED = "Perpanjang durasi adegan (tidak tersedia — adegan sudah dibuat)"
    const val DUR_OPT_TRIM = "Persingkat teks narasi"
    const val DUR_OPT_TEMPO = "Percepat narasi 10%"
    const val DUR_RECOMMENDED_SUFFIX = " (disarankan)"

    // Result screen
    const val RESULT_TITLE = "Video kenanganmu siap"
    const val RESULT_PLAY = "Putar"
    const val RESULT_SAVE_AS = "Simpan Sebagai…"
    const val RESULT_OPEN_FOLDER = "Buka Folder"
    const val RESULT_COPY_PATH = "Salin Lokasi"
    const val RESULT_COPIED_TOAST = "Lokasi file disalin."
    const val RESULT_SAVED_TOAST = "Video disimpan."
    const val RESULT_DURATION = "Durasi"
    const val RESULT_SIZE = "Ukuran file"
    const val RESULT_TIER = "Kualitas"
    const val RESULT_COST = "Estimasi biaya proyek"
    const val RESULT_OTHER_RATIO = "Buat versi %1"
    const val RESULT_OTHER_RATIO_NOTE = "Dirakit ulang dari klip yang sama — tanpa biaya API. Klip akan dipotong (crop) mengikuti rasio baru."
    const val RESULT_OTHER_RATIO_RUNNING = "Merakit versi %1… %2%"
    const val RESULT_OTHER_RATIO_DONE = "Versi %1 selesai."
    const val RESULT_AI_NOTE = "Video ini adalah interpretasi AI dari fotomu."

    // Upscale & restore tool (owner 2026-09-01)
    const val UPSCALE_TITLE = "Upscale & Perbaiki Foto"
    const val UPSCALE_INTRO = "Perbesar resolusi foto dan perbaiki foto lama yang rusak menjadi sangat detail. Bisa banyak foto sekaligus."
    const val UPSCALE_PICK = "Pilih foto"
    const val UPSCALE_MODEL_LABEL = "Pilih model"
    const val UPSCALE_START = "Mulai (%1 foto) — estimasi ±$%2"
    const val UPSCALE_RUNNING = "Memproses %1/%2 foto…"
    const val UPSCALE_ADD_MORE = "Tambah foto"
    const val UPSCALE_CLEAR = "Kosongkan daftar"
    const val UPSCALE_OPEN_OUTPUT = "Buka folder hasil"
    const val UPSCALE_STATUS_QUEUED = "Antre"
    const val UPSCALE_STATUS_RUNNING = "Proses…"
    const val UPSCALE_STATUS_DONE = "Selesai"
    const val UPSCALE_STATUS_FAILED = "Gagal"
    const val UPSCALE_RETRY_FAILED = "Ulangi yang gagal"
    const val UPSCALE_DONE_TOAST = "Selesai: %1 berhasil, %2 gagal. Hasil ada di folder Upscale."
    const val UPSCALE_RESULT_NOTE = "Hasil disimpan ke: %1"
    const val UPSCALE_COST_PER_PHOTO = "±$%1 / foto"
    const val UPSCALE_COMPARE_BEFORE = "Sebelum"
    const val UPSCALE_COMPARE_AFTER = "Sesudah"
    const val UPSCALE_EMPTY = "Belum ada foto. Pilih foto lama yang ingin diperbesar atau diperbaiki."
    const val UPSCALE_NEED_KEY = "Tambahkan API key fal dulu di Pengaturan."
    const val UPSCALE_DROP_HINT = "Atau geser & lepaskan foto ke sini"
    const val UPSCALE_CHANGE_FOLDER = "Ubah folder…"
    const val UPSCALE_FOLDER_RESET = "Bawaan"
}
