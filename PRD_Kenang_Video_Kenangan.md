# PRD — "Kenang" (Working Title)
## Aplikasi Desktop Generator Video Kenangan dari Foto Lama

| | |
|---|---|
| **Dokumen** | Product Requirements Document (PRD) v1.1 — Model BYOK |
| **Produk** | Kenang — Hidupkan Kembali Kenangan dari Foto |
| **Platform** | Desktop (Windows prioritas; macOS menyusul) |
| **Studio** | Ciptara AI Studio |
| **Status** | Draft untuk review |
| **Tanggal** | Agustus 2026 |

> **Changelog v1.1 (BYOK):** Model bisnis diubah dari kredit-server menjadi **jual lisensi software desktop + Bring Your Own Key** (user memakai API key miliknya sendiri). Bagian yang direvisi: §1.4, §3–§5, F3/F4/F7, §7–§14.

---

## 1. Ringkasan Produk

### 1.1 Visi
Memberi setiap orang cara yang mudah dan bermakna untuk **menghidupkan kembali kenangan** — foto masa kecil, foto orang tersayang yang sudah tiada, foto hewan peliharaan yang sudah pergi — menjadi video sinematik pendek yang siap dibagikan, tanpa perlu keahlian editing sama sekali.

### 1.2 Masalah
Foto lama itu statis dan sering rusak/pudar. Orang ingin "merasakan kembali" momen itu, tapi:
- Tools AI video (Kling, Seedance, dsb.) terlalu teknis untuk orang awam: harus paham prompt, model, parameter.
- Workflow-nya terpecah: restore foto di satu tempat, animasi di tempat lain, tambah musik & narasi di editor video, lalu render manual.
- Aplikasi mobile sejenis umumnya template kaku (hanya "AI Hug"), bukan storyboard multi-adegan yang bisa dikustomisasi.

### 1.3 Solusi
Satu aplikasi desktop dengan alur 3 langkah: **Upload → Konfirmasi Storyboard → Buat Video**. AI menangani analisis foto, pembuatan storyboard multi-adegan, prompt gerakan, transformasi suasana (vibe), narasi suara, dan perakitan video final — user hanya mengarahkan dan mengonfirmasi.

### 1.4 Diferensiasi Utama
1. **Storyboard yang bisa diedit** — bukan black-box template. User melihat & menyetujui setiap adegan (keyframe + prompt gerakan) *sebelum* biaya generate video keluar.
2. **Multi-foto → satu narasi** — beberapa foto digabung jadi satu cerita utuh, termasuk menggabungkan orang dari foto berbeda ke dalam satu adegan (contoh: user dewasa "duduk bersama" almarhum ayah dari foto lama).
3. **Vibe transformation** — memindahkan subjek foto ke suasana baru (taman, pantai, ruang tamu) dengan wajah tetap konsisten.
4. **Lokal + siap pakai** — output MP4 final dengan musik, narasi, subtitle, rasio 9:16/16:9, langsung siap posting.
5. **BYOK — tanpa markup & privasi maksimal** — user memakai API key sendiri: biaya generate dibayar langsung ke provider tanpa markup, dan foto **tidak pernah melewati server penerbit**.

---

## 2. Latar Belakang & Validasi Pasar

- Kategori ini sudah terbukti viral secara global: tren "AI Hug" meledak sejak awal 2025 lewat fitur Kling AI, dengan jutaan video memeluk orang tua/kakek-nenek yang sudah meninggal membanjiri TikTok, Instagram, dan X. Layanan pendahulu seperti MyHeritage Deep Nostalgia juga membuktikan demand untuk menganimasikan foto almarhum.
- Teknologi ini bahkan mulai masuk ke industri pemakaman (funeral home) sebagai layanan tribute/memorial.
- Kompetitor kebanyakan berbentuk **aplikasi mobile template tunggal** (hug/dance) atau **web tool generik**. Celah pasar: aplikasi desktop dengan storyboard multi-adegan, narasi bahasa Indonesia, dan alur khusus "video kenangan" (bukan sekadar efek).
- Momen budaya Indonesia sangat mendukung use case ini: Lebaran/halalbihalal, Hari Ibu, wisuda, pernikahan (menghadirkan almarhum orang tua secara simbolis), haul/peringatan keluarga.

---

## 3. Target Pengguna & Persona

**P1 — "Anak yang Merindukan" (utama).** Usia 25–45, ingin membuat video tribute almarhum orang tua/kakek-nenek untuk dibagikan di grup keluarga atau media sosial. Tidak bisa editing. Sensitif secara emosional → UX harus lembut dan penuh empati.

**P2 — "Pemilik Hewan Kesayangan".** Ingin video kenangan hewan peliharaan yang sudah tiada. Volume tinggi di media sosial, tingkat sensitivitas kebijakan konten lebih rendah.

**P3 — "Pengarsip Keluarga / Content Creator".** Menghidupkan foto masa kecil, foto pernikahan orang tua, foto jadul keluarga besar. Butuh batch & kualitas lebih tinggi, kandidat pengguna paket premium.

**P4 — "Jasa" (B2B ringan).** Studio foto, jasa cetak, penyedia jasa tribute/memorial, MUA/WO yang menjual video kenangan sebagai layanan tambahan. Butuh output tanpa watermark & lisensi komersial multi-perangkat. **Dalam model BYOK, segmen ini adalah pembeli inti**: bayar lisensi sekali, tanggung biaya API per pesanan klien, jual jasanya berkali-kali.

> Catatan BYOK: P1/P2 (non-teknis) rentan drop di tahap setup API key → mitigasi: wizard berpanduan + target *single-key* (cukup satu akun fal) — lihat §8 dan Fase 0.

---

## 4. Ruang Lingkup

### 4.1 In Scope (MVP)
1. Input multi-foto (1–15 foto), narasi teks opsional, musik latar, rasio 9:16/16:9, pilihan vibe.
2. Analisis foto otomatis + pembuatan storyboard otomatis (keyframe per adegan + prompt gerakan per adegan).
3. Edit prompt per adegan, regenerate keyframe per adegan, atur urutan & durasi adegan.
4. Generate video per adegan via API (dengan 3 tier kualitas), lalu perakitan final lokal (musik + narasi TTS + subtitle + transisi).
5. Ekspor MP4 (720p/1080p) siap posting; simpan/muat proyek; riwayat hasil.
6. Aktivasi lisensi software (online, dengan masa tenggang offline) + **Manajer API Key** milik user (BYOK) + cost tracker estimasi biaya provider.

### 4.2 Out of Scope (MVP) — masuk backlog
- Voice cloning suara almarhum (sensitif; butuh gerbang etika khusus — lihat §10).
- Lip-sync narasi ke wajah subjek (talking photo).
- Editor timeline penuh (trim/cut manual ala CapCut).
- Mobile app, kolaborasi multi-user, cloud render 4K.
- "Managed mode" (proxy server + sistem kredit) — disimpan sebagai opsi masa depan bila ada demand dari persona non-teknis.

---

## 5. User Flow (End-to-End)

```
[1. INPUT]                [2. ANALISIS AI]           [3. STORYBOARD]
Upload 1-10 foto   --->   Deteksi subjek, era,  ---> Grid adegan (keyframe +
Narasi (opsional)         mood, kualitas foto        prompt gerakan editable)
Pilih musik               Restorasi ringan           User edit / regenerate /
Rasio 9:16 / 16:9         (opsional)                 hapus / urutkan adegan
Pilih vibe                                                |
                                                          v
[6. SELESAI]              [5. PERAKITAN LOKAL]      [4. GENERATE VIDEO]
Preview + tombol   <---   FFmpeg: gabung klip, <--- Konfirmasi & estimasi
Share/Save/Open           transisi, musik, TTS,     biaya --> API I2V per
folder                    subtitle, watermark       adegan (progress bar)
```

Prinsip penting: **semua biaya besar (generate video) baru terjadi setelah user mengonfirmasi storyboard.** Tahap analisis + keyframe murah dan bisa diulang-ulang; ini mengendalikan pengeluaran API user sekaligus mengurangi kekecewaan hasil.

---

## 6. Persyaratan Fungsional

### F1 — Modul Input

| ID | Requirement | Prioritas |
|---|---|---|
| F1.1 | Upload 1–15 foto (JPG/PNG/WebP/HEIC), drag-and-drop, maks 20 MB/foto. | P0 |
| F1.2 | Validasi otomatis: resolusi minimal (sisi terpendek ≥ 512 px), deteksi wajah terlalu kecil/blur → tampilkan peringatan kualitas dengan skor per foto. | P0 |
| F1.3 | Kolom narasi opsional (maks 500 karakter) + pilihan suara TTS (pria/wanita, 2–3 gaya, preview 1 klik). | P0 |
| F1.4 | Musik latar: (a) pilih dari library bawaan bebas royalti (8–12 track sesuai mood: haru, hangat, ceria, khidmat), atau (b) upload file MP3/WAV sendiri (disertai peringatan hak cipta). | P0 |
| F1.5 | Pilihan rasio: 9:16 (default, untuk Reels/TikTok/Shorts) atau 16:9. Rasio dipropagasikan ke semua tahap (keyframe & video). | P0 |
| F1.6 | Pilihan vibe (preset, extensible dari server): Original, Taman, Ruang Tamu, Pantai, Studio Klasik, Golden Hour, Pegunungan, Kafe Hangat. Tiap vibe punya thumbnail contoh + deskripsi singkat. | P0 |
| F1.7 | Pilihan durasi target per adegan: 5s (default) atau 10s. | P1 |
| F1.8 | Toggle "Restorasi foto lama" (perbaikan warna/noise/goresan pada keyframe untuk foto rusak/pudar/BW). | P1 |

### F2 — Modul Analisis AI

| ID | Requirement | Prioritas |
|---|---|---|
| F2.1 | Setiap foto dianalisis oleh vision LLM: jumlah & deskripsi subjek (usia perkiraan, pakaian), relasi antar-subjek jika terlihat, latar, era/estetika foto (BW, 90-an, dsb.), mood, orientasi, dan skor kualitas. Output JSON terstruktur. | P0 |
| F2.2 | Dari hasil analisis + vibe + narasi, LLM menyusun **rencana cerita**: jumlah adegan (default = jumlah foto, maks 6 adegan MVP), urutan naratif (kronologis/emosional), dan apakah perlu adegan gabungan multi-foto. | P0 |
| F2.3 | Deteksi konten terlarang di input (NSFW, kekerasan) → tolak dengan pesan sopan sebelum ada biaya API besar. | P0 |
| F2.4 | Analisis berjalan paralel per foto; total waktu tahap ini < 30 detik untuk 5 foto. | P1 |

### F3 — Modul Storyboard

| ID | Requirement | Prioritas |
|---|---|---|
| F3.1 | Untuk vibe **Original**: keyframe = foto asli (setelah restorasi ringan jika diaktifkan), di-crop/outpaint cerdas ke rasio target. | P0 |
| F3.2 | Untuk vibe lain: keyframe digenerate oleh model image editing dengan instruksi "pindahkan subjek ke [vibe], pertahankan wajah, tubuh, dan pakaian persis sama" + parameter rasio. | P0 |
| F3.3 | **Adegan gabungan**: model image editing menggabungkan subjek dari ≥2 foto berbeda ke satu keyframe (multi-image fusion), mis. "subjek foto A dan subjek foto B duduk bersebelahan di ruang tamu". Maksimal 4 subjek per adegan (batas kualitas konsistensi). | P0 |
| F3.4 | Untuk tiap adegan, LLM menghasilkan **prompt gerakan** (bahasa Inggris, 1–3 kalimat) dari template gerakan aman: micro-expression (senyum, kedipan), gerakan lembut (menoleh, melambai, berpelukan), gerakan kamera (slow push-in, gentle pan). Ditampilkan juga versi ringkasan bahasa Indonesia agar user awam paham. | P0 |
| F3.5 | UI storyboard: grid kartu adegan berisi keyframe, prompt (editable), durasi, tombol Regenerate keyframe, Hapus, dan drag untuk mengurutkan. | P0 |
| F3.6 | Regenerate keyframe tanpa batas (biaya API ±$0.04/gambar ditanggung user) — tampilkan chip estimasi biaya kecil di tombol regenerate. | P1 |
| F3.7 | **Estimasi biaya provider** (USD + Rp, berlabel "estimasi — tagihan riil ada di akun provider Anda"), dihitung dari tabel tarif remote config, selalu terlihat dan ter-update saat user mengubah adegan/durasi/tier. Tombol **"Buat Video"** menampilkan konfirmasi rincian estimasi. | P0 |

### F4 — Modul Generate Video

| ID | Requirement | Prioritas |
|---|---|---|
| F4.1 | Tiap adegan dikirim ke API image-to-video (keyframe + prompt gerakan + rasio + durasi) melalui backend. Antrian async dengan progress per adegan (Queued → Generating → Done/Failed). | P0 |
| F4.2 | 3 tier kualitas yang memetakan ke model berbeda (lihat §9): **Hemat**, **Standar** (default), **Premium**. Tier memengaruhi estimasi biaya API (ditanggung user) & estimasi waktu. | P0 |
| F4.3 | Retry otomatis 1× hanya untuk gagal teknis (5xx/timeout); kegagalan kebijakan konten tidak di-retry, ditampilkan dengan pesan yang bisa dipahami + saran perbaikan (mis. ganti prompt). | P0 |
| F4.4 | Biaya API dibayar user langsung ke provider sesuai kebijakan billing provider. Semua panggilan dicatat di cost tracker lokal; app jujur memberi tahu bahwa sebagian provider dapat tetap menagih percobaan yang gagal. | P0 |
| F4.5 | Klip hasil diunduh langsung dari CDN provider ke mesin user (hemat bandwidth backend). | P1 |

### F5 — Modul Audio

| ID | Requirement | Prioritas |
|---|---|---|
| F5.1 | Narasi di-generate via TTS (bahasa Indonesia natural; mendukung juga EN untuk pasar ekspansi) menjadi satu track penuh. | P0 |
| F5.2 | Mixing otomatis: musik latar dengan **ducking** (volume musik turun saat narasi berbunyi), fade-in/out di awal/akhir video. | P0 |
| F5.3 | Jika total durasi narasi > durasi video, tawarkan: tambah durasi adegan, potong narasi, atau percepat tempo TTS ±10%. | P1 |
| F5.4 | Subtitle otomatis dari teks narasi (karena teks sudah diketahui, tanpa perlu ASR), gaya font/posisi mengikuti rasio; bisa dimatikan. | P1 |

### F6 — Modul Perakitan & Ekspor

| ID | Requirement | Prioritas |
|---|---|---|
| F6.1 | Perakitan final dilakukan **lokal** dengan FFmpeg (dibundel dalam installer): concat klip, transisi crossfade/dip-to-black (0,5–1 dtk), audio mixing, subtitle burn-in, normalisasi loudness. | P0 |
| F6.2 | Output MP4 H.264 + AAC, 720p atau 1080p, 24/30 fps, metadata "AI-generated". | P0 |
| F6.3 | Watermark kecil "Dibuat dengan Kenang" pada paket gratis/hemat; tanpa watermark untuk paket berbayar penuh — label "AI-generated" tetap ada di metadata semua output. | P0 |
| F6.4 | Layar hasil: preview player, tombol Simpan Sebagai, Buka Folder, dan Salin path. (Integrasi share sheet OS = P2.) | P0 |
| F6.5 | Ekspor ulang dengan rasio berbeda dari proyek yang sama tanpa regenerate adegan bila memungkinkan (crop cerdas), atau tawarkan regenerate dengan diskon. | P2 |

### F7 — Proyek, Lisensi & API Key

| ID | Requirement | Prioritas |
|---|---|---|
| F7.1 | Simpan/muat proyek lokal (SQLite + folder aset): input, hasil analisis, storyboard, status generate, path klip. Crash-safe (bisa resume). | P0 |
| F7.2 | **Aktivasi lisensi**: masukkan license key → aktivasi online (device binding), status lisensi di Settings, masa tenggang offline 30 hari, tombol deaktivasi untuk pindah perangkat. Mode Trial: semua fitur + watermark + maksimal 3 ekspor. *(Urutan implementasi: fitur lisensi dikerjakan **paling akhir**, setelah aplikasi stabil — lihat §13; selama pengembangan aplikasi berjalan penuh tanpa gembok via LicenseGate stub.)* | P0 |
| F7.3 | **Manajer API Key (BYOK)**: penyimpanan terenkripsi (Windows Credential Manager), field ter-masking, tombol "Tes koneksi" per provider, dan wizard onboarding berpanduan (tautan pembuatan akun/key + langkah bergambar). Minimum: 1 key fal; Google & ElevenLabs opsional untuk kualitas analisis/suara lebih tinggi. | P0 |
| F7.4 | **Cost tracker lokal**: estimasi biaya API per proyek & rekap bulanan (tarif remote config × pemakaian aktual), selalu berlabel estimasi. | P1 |
| F7.5 | Riwayat video (thumbnail + tanggal + estimasi biaya). | P1 |
| F7.6 | Mode offline: proyek & hasil tetap bisa dibuka; fitur AI menampilkan status offline; lisensi tetap valid dalam masa tenggang. | P1 |

---

## 7. Persyaratan Non-Fungsional

**Performa.** Analisis 5 foto < 30 dtk; keyframe per adegan < 25 dtk; generate video per adegan 1–4 mnt (tergantung tier); perakitan final lokal < 60 dtk untuk video 30 dtk 1080p. UI tidak pernah freeze (semua kerja berat di background thread/coroutine).

**Keamanan.** API key milik user disimpan lokal terenkripsi (Windows Credential Manager; fallback file AES-GCM), tidak pernah dikirim ke server penerbit, dan tidak pernah masuk log. Lisensi divalidasi via tanda tangan digital (Ed25519, public key tertanam di aplikasi) + device binding, heartbeat berkala, dan masa tenggang offline. Binary di-obfuscate (ProGuard) — dipahami bahwa proteksi client-side bersifat deteren, bukan mutlak.

**Privasi.** Foto & narasi **tidak pernah melewati server penerbit** — data mengalir langsung dari komputer user ke provider AI di bawah akun user sendiri (ini klaim privasi utama produk, sangat relevan untuk konten duka). Penyimpanan permanen hanya di mesin user; retensi di sisi provider mengikuti kebijakan masing-masing provider dan diungkap di dokumen bantuan. Backend penerbit hanya menerima data lisensi & telemetry non-konten (opt-out).

**Reliabilitas.** Semua job generate idempotent & resumable; kegagalan sebagian tidak menggagalkan seluruh proyek; refund kredit otomatis untuk kegagalan permanen.

**Skalabilitas biaya.** Penerbit tidak menanggung COGS per video (BYOK). Remote config menyajikan slug model + tabel tarif indikatif provider agar estimator akurat dan endpoint bisa di-hotfix tanpa rilis aplikasi.

**Kompatibilitas.** Windows 10/11 x64 (MVP). Ukuran installer target < 300 MB (termasuk FFmpeg). Tidak butuh GPU — semua inferensi AI di cloud.

---

## 8. Arsitektur Teknis


```
┌──────────────────────────── DESKTOP APP (Windows) ────────────────────────────┐
│  Kotlin + Compose Multiplatform (Desktop)                                     │
│  • UI wizard & storyboard editor      • SQLite (proyek, cost tracker)         │
│  • Key Vault (Credential Manager)     • core/providers: fal / Gemini / TTS    │
│  • FFmpeg bundled (perakitan final)   • License client (verifikasi Ed25519)   │
└───────┬───────────────────────────────────────────────┬───────────────────────┘
        │ HTTPS langsung (API key milik USER)           │ HTTPS (kecil & jarang)
┌───────▼───────────────────────────────┐   ┌───────────▼───────────────────────┐
│ PROVIDER AI (akun user sendiri)       │   │ BACKEND MINI (Laravel — Ciptara)  │
│ • fal.ai: keyframe (Nano Banana),     │   │ • Aktivasi/validasi lisensi       │
│   I2V (Kling/Wan/Seedance), TTS,      │   │ • Remote config (slug model,      │
│   VLM analisis (mode single-key)      │   │   tarif indikatif, vibes, versi)  │
│ • Google Gemini (opsional): analisis  │   │ • Toko lisensi (Midtrans) + email │
│ • ElevenLabs (opsional): suara premium│   │ • Update check, telemetry opt-out │
└───────────────────────────────────────┘   └───────────────────────────────────┘
```

Keputusan arsitektur kunci:

1. **BYOK penuh** — desktop memanggil provider AI langsung dengan API key milik user; server penerbit tidak pernah menyentuh foto, prompt, maupun key user.
2. **Target onboarding single-key** — seluruh pipeline (keyframe, I2V, TTS, bahkan analisis via VLM yang dihosting fal) diarahkan lewat **satu akun fal** bila kualitasnya lolos uji Fase 0; Gemini & ElevenLabs menjadi jalur opsional untuk kualitas lebih tinggi.
3. **Backend menyusut jadi layanan mini** (lisensi + remote config + toko + update-check) — tanpa antrian, tanpa penyimpanan media; sangat ringan untuk shared hosting.
4. **Desktop polls antrian fal langsung** (submit → status → result) dengan backoff; resume-safe dari SQLite.
5. **Remote config tetap tuas kendali** — slug endpoint, routing tier, dan tabel tarif indikatif bisa di-hotfix dari server saat provider berubah, tanpa rilis aplikasi.
6. **Perakitan final tetap lokal (FFmpeg)** — cepat, gratis, privat.

---

## 9. Analisis & Rekomendasi AI Stack

> Catatan: harga bersifat indikatif (medio 2026, mayoritas via fal.ai/agregator) dan **berubah cepat** — wajib re-verifikasi saat implementasi. Kurs asumsi Rp16.000–16.500/USD.

### 9.1 Layer 1 — Analisis Foto & Penyusun Storyboard/Prompt (Vision LLM)

Kebutuhan: memahami isi foto (subjek, relasi, era, mood), menyusun rencana cerita, menulis instruksi keyframe & prompt gerakan dalam JSON terstruktur. Volume panggilan tinggi tapi murah.

| Kandidat | Nilai | Catatan |
|---|---|---|
| **Gemini Flash (rekomendasi kualitas)** | ★★★★★ | Vision kuat & sangat murah. Untuk mode single-key, alternatifnya VLM yang dihosting fal (diverifikasi Fase 0) agar user cukup punya satu akun. |
| Claude Haiku/Sonnet | ★★★★ | Kualitas penalaran naratif sangat baik; cocok sebagai penulis "rencana cerita" bila ingin narasi lebih halus. |
| GPT-4o mini | ★★★★ | Setara; pilih jika sudah ada relasi billing OpenAI. |

**Estimasi biaya: < $0.01 per proyek** (dapat diabaikan dalam COGS).

### 9.2 Layer 2 — Keyframe Storyboard: Vibe Transformation & Multi-Photo Fusion (Image Editing)

Ini layer paling menentukan kualitas produk: wajah **harus** tetap dikenali setelah subjek "dipindahkan" ke taman/pantai/ruang tamu atau digabung dengan subjek dari foto lain.

| Kandidat | Kekuatan untuk use case ini | Harga indikatif |
|---|---|---|
| **Nano Banana / Gemini Flash Image (rekomendasi default)** | Edit berbasis bahasa natural, terkenal justru karena character consistency & multi-image fusion; cepat & murah — ideal untuk iterasi/regenerate keyframe. | ~$0.039/gambar |
| **Nano Banana Pro / Gemini 3 Pro Image (tier Premium)** | Konsistensi hingga ~5 karakter & sampai 14 gambar referensi dalam satu komposisi, output 2K–4K — ideal untuk adegan gabungan yang kompleks dan restorasi kelas atas. | ~$0.13–0.15/gambar (1–2K) |
| Seedream (ByteDance) | Alternatif kuat untuk komposisi sinematik multi-figur; kandidat fallback. | Setara kelas |
| FLUX Kontext | Editing bagus, tapi keunggulan fusion multi-subjek kalah dari dua di atas untuk kebutuhan kita. | Setara kelas |

**Keputusan desain:** pipeline berbasis **keyframe** (bukan langsung foto→video) dipilih karena (a) spek produk mensyaratkan storyboard yang bisa dipreview & diedit sebelum biaya besar, (b) membuat Layer 3 model-agnostic, dan (c) biaya iterasi murah ($0.04 vs $1+ per percobaan video).

### 9.3 Layer 3 — Image-to-Video (Jantung Produk)

Kebutuhan spesifik "video kenangan": **fidelitas wajah manusia & gerakan emosional yang natural** (senyum, pelukan, menoleh) lebih penting daripada physics/aksi kompleks. Klip pendek 5–10 dtk per adegan, rasio 9:16 & 16:9.

| Model | Kecocokan untuk video kenangan | Harga indikatif (per detik) | Peran di produk |
|---|---|---|---|
| **Kling 3.0 (Standard/Pro)** | **Terbaik di kelasnya untuk wajah manusia, gestur & momen emosional** — model yang sama yang memviralkan tren "AI Hug" (memeluk almarhum) sejak 2025; track record kebijakan yang permisif untuk animasi wajah orang nyata. | Standard ~$0.084 (tanpa audio); Pro ~$0.112; Pro+audio ~$0.168 | **Tier Standar (default) & Premium** |
| **Wan 2.6/2.7 (Alibaba)** | Kualitas baik dengan harga termurah di kelas produksi; cocok untuk volume & user sensitif harga. | ~$0.05 | **Tier Hemat** |
| **Seedance 2.0 (ByteDance)** | Peringkat #1 leaderboard I2V (Artificial Analysis, medio 2026); fitur unik `@reference` hingga 9 gambar — jalur alternatif adegan gabungan langsung tanpa keyframe. Mahal untuk default. | Mini ~$0.07–0.155; Standard ~$0.30 (720p) | Opsi Premium / eksperimen fitur gabungan |
| Hailuo 2.3 (MiniMax) | Look sinematik, cepat; kandidat fallback tier Standar. | Kelas menengah | Fallback |
| Veo 3.1 (Google) | Kualitas & audio premium, tapi paling mahal dan kontrol kebijakan "person generation" lebih ketat — kurang cocok jadi tulang punggung use case wajah orang nyata. | ~$0.20–0.40 (Lite ~$0.05) | Tidak dipakai di MVP |
| Sora 2 (OpenAI) | Kualitas tinggi, **tetapi kebijakan uploadnya membatasi wajah manusia fotorealistik** — praktis gugur untuk produk ini. | ~$0.10–0.50 | Tidak dipakai |

**Rekomendasi routing tier:**

- **Hemat** → Wan 2.6/2.7 (720p, tanpa audio native)
- **Standar (default)** → Kling 3.0 Standard 720p
- **Premium** → Kling 3.0 Pro 1080p (opsional +ambience audio native) — Seedance 2.0 sebagai A/B test

Audio native model **dimatikan secara default** (musik & narasi kita kontrol sendiri di Layer 4–5; menghemat 30–50% biaya video).

### 9.4 Layer 4 — Narasi Suara (TTS)

Kebutuhan: bahasa Indonesia yang natural & hangat (konteks emosional), durasi pendek (≤500 karakter).

| Kandidat | Catatan | Harga indikatif |
|---|---|---|
| **ElevenLabs Multilingual/v3 (rekomendasi)** | Kualitas & emosi terbaik di pasar; voice library punya banyak suara Indonesia natural (pria/wanita, termasuk aksen lokal). | ~$0.10/1.000 karakter (Flash ~$0.05) |
| OpenAI gpt-4o-mini-tts | Jauh lebih murah, kualitas cukup; kandidat suara tier Hemat. | ~$0.015/1.000 karakter |
| Google Cloud TTS | Sangat murah tapi terdengar kaku untuk konten emosional. | ~$0.004–0.016/1.000 karakter |

Narasi 300 karakter ≈ $0.03 (ElevenLabs) — sangat kecil, jadi kualitas boleh diutamakan. Untuk mode single-key, kandidat TTS yang dihosting fal (mis. keluarga MiniMax Speech) diuji di Fase 0 sebagai default; ElevenLabs menjadi opsi premium bila user menambahkan key-nya.

### 9.5 Layer 5 — Perakitan

**FFmpeg lokal (gratis).** Concat + xfade, amix + sidechain ducking, drawtext/ASS subtitle, loudnorm. Tidak ada alternatif yang perlu dipertimbangkan.

### 9.6 Estimasi Biaya API per Video (3 adegan × 5 dtk = 15 dtk) — dibayar user langsung ke provider, tanpa markup

| Komponen | Hemat | Standar | Premium |
|---|---|---|---|
| Analisis (LLM) | ~$0.005 | ~$0.005 | ~$0.01 |
| Keyframe ×3 (+buffer regenerate) | $0.18 (NB) | $0.18 (NB) | $0.60 (NB Pro) |
| Video 15 dtk | $0.75 (Wan) | $1.26 (Kling Std) | $2.10 (Kling Pro 1080p) |
| TTS 300 karakter | $0.005 (OpenAI) | $0.03 (ElevenLabs) | $0.03 (ElevenLabs) |
| **Total biaya API** | **≈ $0.94 ≈ Rp15.500** | **≈ $1.47 ≈ Rp24.000** | **≈ $2.74 ≈ Rp45.000** |
| + buffer retry/gagal 15% | ≈ Rp17.800 | ≈ Rp27.600 | ≈ Rp51.700 |

### 9.7 Model Monetisasi: Jual Software (Lisensi) + BYOK

Prinsipnya: **penerbit menjual software, user membayar API-nya sendiri**. Penerbit bebas COGS per video dan bebas risiko fluktuasi harga provider; user mendapat harga API tanpa markup (video Standar ±Rp24–28rb — bandingkan layanan berbasis kredit yang umum menjual 2–3× lipat).

| SKU (usulan awal — final di Open Questions) | Harga | Isi |
|---|---|---|
| Trial | Gratis | Semua fitur, watermark "Kenang Trial", maksimal 3 ekspor |
| Personal | Rp249.000 (sekali bayar) | 1 perangkat, tanpa watermark, non-komersial, update 12 bulan |
| Studio | Rp699.000 (sekali bayar) | 3 perangkat, lisensi komersial (jasa video kenangan), prioritas dukungan |
| Update Pass | Rp99.000/tahun (opsional, setelah 12 bulan) | Lanjutan update fitur & config; tanpa perpanjangan aplikasi tetap berfungsi |

Kanal penjualan: landing page Ciptara + Midtrans (margin penuh) sebagai utama; alternatif marketplace produk digital lokal (Lynk.id/Mayar) untuk kemudahan operasional, dan Gumroad untuk pasar global. Ilustrasi: 100 lisensi Personal/bulan = Rp24,9 juta pendapatan **tanpa COGS API sama sekali** — biaya berjalan hanya hosting backend mini.

---

## 10. Kebijakan Konten, Etika & Kepatuhan

Produk ini menyentuh area paling sensitif dari AI generatif — wajah orang nyata, termasuk yang sudah meninggal. Kebijakan ini bagian dari produk, bukan pelengkap.

1. **Pernyataan hak & persetujuan.** Sebelum generate pertama, user menyetujui pernyataan bahwa ia memiliki hak atas foto dan izin yang wajar dari keluarga/subjek; dilarang menggunakan foto figur publik, orang asing, atau untuk menipu/mempermalukan/memfitnah.
2. **Batasan konten.** Blokir input & prompt bermuatan seksual, kekerasan, dan penghinaan. Prompt gerakan dibatasi ke template "gerakan bermartabat" (senyum, pelukan, melambai, menoleh) — tidak ada kategori bebas yang bisa disalahgunakan untuk deepfake merugikan.
3. **Transparansi AI.** Metadata "AI-generated" di semua output; watermark visual pada tier gratis/hemat. Ini juga selaras arah regulasi pelabelan konten sintetis (termasuk diskursus PSE/UU ITE di Indonesia soal konten manipulatif).
4. **Kepekaan duka & budaya.** Copywriting UX empatik (hindari kata "hidupkan kembali orangnya"; gunakan "hidupkan kenangannya"). Disclaimer lembut bahwa video adalah interpretasi AI, bukan rekaman nyata. Hormati sensitivitas religius/kultural sebagian pengguna Indonesia terhadap penggambaran almarhum — posisi produk: alat kenangan/tribute, keputusan penggunaan sepenuhnya di tangan user & keluarganya.
5. **Privasi data.** Lihat §7 — retensi singkat, tanpa training, alur pihak ketiga diungkap di privacy policy.
6. **Risiko kebijakan provider.** Setiap provider punya aturan berbeda soal wajah orang nyata dan bisa berubah sewaktu-waktu → mitigasi: multi-provider routing via agregator + Fase 0 wajib menguji kebijakan tiap kandidat model dengan foto uji nyata (lihat §13).
7. **Backlog bergerbang etika.** Voice cloning suara almarhum hanya akan dipertimbangkan dengan gerbang persetujuan eksplisit ahli waris + review kebijakan tersendiri.
8. **Tanggung jawab akun BYOK.** Onboarding menampilkan bahwa pemakaian API tunduk pada ToS masing-masing provider dan ditagih ke akun user; guardrail konten ditegakkan di sisi aplikasi (validator template + moderasi pra-panggilan) dengan safety filter provider sebagai lapisan kedua — tanpa klaim bahwa filter mustahil ditembus.

---

## 11. Metrik Sukses

| Metrik | Target 90 hari pasca-launch |
|---|---|
| Aktivasi: user baru → video pertama selesai | ≥ 40% |
| Completion rate: proyek dimulai → video jadi | ≥ 70% |
| Regenerate keyframe rata-rata per adegan | ≤ 2 (indikator kualitas prompt otomatis) |
| Tingkat kegagalan generate (setelah retry) | < 5% |
| Konversi Trial → lisensi berbayar | ≥ 8% |
| Share/save rate dari layar hasil | ≥ 60% |
| Organik: video ber-watermark yang tayang publik | dilacak sebagai saluran akuisisi utama |

---

## 12. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Provider mengetatkan kebijakan animasi wajah orang nyata | Fitur inti mati | Multi-provider routing (Kling/Wan/Seedance/Hailuo) via agregator; uji kebijakan di Fase 0; abstraksi Layer 3 sejak awal |
| Harga API provider berubah | Estimator meleset → keluhan user | Tabel tarif via remote config (hotfix tanpa rilis); label "estimasi" eksplisit; cost tracker berbasis pemakaian aktual |
| Wajah hasil tidak mirip ("uncanny") | Kekecewaan emosional user — fatal untuk produk duka | Storyboard preview sebelum bayar; skor kualitas foto di input; guardrail prompt; fitur regenerate; ekspektasi diseting di UX |
| Musik upload user melanggar hak cipta saat diposting | Takedown di platform sosial | Library bebas royalti sebagai default + peringatan jelas untuk upload sendiri |
| Penyalahgunaan (deepfake merugikan) | Reputasi & hukum | §10: template gerakan terbatas, moderasi input, pernyataan hak, metadata AI |
| Kompetitor mobile besar (template hug) | Tekanan harga | Diferensiasi: storyboard multi-adegan + narasi ID + vibe + B2B jasa |
| Pembajakan software desktop | Pendapatan hilang | Lisensi bertanda tangan (Ed25519) + device binding + ProGuard; nilai berkelanjutan lewat update & remote config; diterima bahwa proteksi klien tidak mutlak |
| Friksi setup API key (persona non-teknis) | Drop-off onboarding | Wizard berpanduan + tombol tes koneksi; target single-key (cukup fal); video tutorial; fokus penjualan ke segmen Jasa (P4); "managed mode" disimpan sebagai opsi masa depan |

---

## 13. Roadmap

**Fase 0 — PoC & Uji Kebijakan (1–2 minggu).** Tanpa UI. Uji 10–15 foto nyata (foto BW, foto pudar, foto keluarga multi-orang, foto hewan) terhadap: Nano Banana (vibe & fusion), Kling 3.0 vs Wan 2.6 vs Seedance Mini (kualitas wajah, tingkat penolakan kebijakan, latensi, biaya aktual), ElevenLabs suara ID. Tambahan BYOK: verifikasi kelayakan **single-key** — TTS bahasa Indonesia yang dihosting fal (mis. MiniMax Speech) dan analisis foto via VLM di fal — sebagai pengganti key Google/ElevenLabs terpisah. **Gate keputusan:** wajah konsisten ≥ 8/10 kasus & tingkat penolakan < 10% pada model terpilih.

**Fase 1 — MVP tanpa lisensi (4–6 minggu).** Desktop Windows full-fitur (F1–F6 + F7 non-lisensi) berjalan **sempurna** dengan config lokal (`app-config.json`): Manajer API Key + wizard onboarding, 6 vibe, 3 tier, library musik 8 track, LicenseGate stub (tanpa watermark/batasan). Diakhiri gate Stabilisasi: 5 proyek nyata E2E mulus, seminggu dogfooding tanpa crash, uji clean-VM.

**Fase 1.5 — Lisensi & peluncuran.** Backend mini di **website lisensi khusus** yang sudah disiapkan (aktivasi Ed25519 + remote config + toko Midtrans), aktifkan Trial/watermark lewat penukaran LicenseGate stub, ProGuard, lalu beta tertutup 20–30 user (komunitas + jasa foto).

**Fase 2 — Peluncuran & Growth (setelah MVP stabil).** Fitur P1 (restorasi, subtitle, jatah regenerate), template momen Indonesia (Pelukan Rindu, Sungkeman Lebaran, Wisuda Bersama, Hadir di Pernikahan), paket lisensi Studio untuk jasa foto/tribute, halaman landing + funnel TikTok organik dari output ber-watermark.

**Fase 3 — Ekspansi.** macOS; talking photo/lip-sync; ekspor ulang multi-rasio; evaluasi ulang model (leaderboard I2V bergeser tiap kuartal); voice cloning bergerbang etika.

---

## 14. Open Questions

1. Nama final produk & posisi brand terhadap Ciptara AI Studio (produk mandiri vs fitur platform web?).
2. Harga & SKU lisensi final (angka §9.7 masih usulan) — cukup sekali bayar, atau tambah Update Pass tahunan?
3. Batas maksimal adegan (6?) dan durasi total (60 dtk?) untuk MVP.
4. Perlukah mode "hanya restorasi + animasi halus" (tanpa vibe) sebagai produk entry-level super murah?
5. Kanal penjualan utama: landing page + Midtrans vs Lynk.id/Mayar vs Gumroad (global)?
6. Kebijakan Trial: watermark + 3 ekspor (default) vs trial 7 hari fitur penuh?

---

## Lampiran A — Kontrak Data Inti (untuk pipeline agent)

```json
// PhotoAnalysis (output Layer 1, per foto)
{
  "photo_id": "p1",
  "subjects": [{"id": "s1", "desc": "elderly woman, ~70s, batik dress", "face_quality": 0.86}],
  "setting": "living room, 1990s aesthetic",
  "era_style": "faded color print",
  "mood": "warm, familial",
  "quality_score": 0.74,
  "issues": ["slight blur", "color fading"]
}

// Scene (unit storyboard)
{
  "scene_id": "sc2",
  "source_photos": ["p1", "p3"],
  "type": "fusion",              // single | fusion
  "vibe": "taman",
  "keyframe_prompt_en": "...",   // instruksi image editing
  "keyframe_url": "...",
  "motion_prompt_en": "She turns slightly and smiles warmly; gentle slow push-in.",
  "motion_summary_id": "Beliau menoleh pelan lalu tersenyum hangat; kamera mendekat perlahan.",
  "duration_s": 5,
  "regen_count": 1,
  "status": "confirmed"          // draft | confirmed | generating | done | failed
}
```

## Lampiran B — Template Prompt Gerakan (guardrailed)

Kategori yang diizinkan: `smile`, `blink`, `slight_head_turn`, `wave`, `hug`, `hold_hands`, `walk_slowly`, `look_at_camera`, `laugh_softly`, `pet_animal`. Setiap kategori punya template EN + parameter kamera (`slow push-in`, `gentle pan left/right`, `static`). LLM hanya boleh memilih & mengisi template, tidak mengarang aksi bebas.

---

*Dokumen ini siap dipecah menjadi MASTER_PROMPTS per fase (agent pipeline): 00-poc-gate, 01-license-config-backend, 02-desktop-shell, 03-storyboard-engine, 04-video-pipeline, 05-licensing-launch.*
