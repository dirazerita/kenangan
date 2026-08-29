# Kenang — Android

Versi Android dari Kenang (aplikasi desktop di `../kenang-desktop`). Alur dan
logikanya sama: BYOK (pakai API key fal.ai milik pengguna sendiri), foto lama →
storyboard → video kenangan, seluruh teks antarmuka Bahasa Indonesia.

## Membuka di Android Studio

1. **File → Open…** lalu pilih folder `ANDROID` ini (bukan folder induknya).
2. Tunggu Gradle sync selesai.
3. Pilih perangkat (HP lewat USB debugging, atau emulator), tekan **Run ▶**.

Kalau Android Studio memakai JDK bawaannya, itu sudah cukup. Dari terminal:

```
cd ANDROID
gradlew.bat :app:assembleDebug      # APK debug
gradlew.bat :app:installDebug       # langsung pasang ke perangkat aktif
```

APK debug ada di `app/build/outputs/apk/debug/app-debug.apk` (±98 MB;
sebagian besar musik bawaan dan pustaka video).

* `local.properties` berisi lokasi Android SDK dan **tidak** ikut ke Git — Android
  Studio membuatnya otomatis di komputer lain.
* Minimal Android 10 (API 29), target API 36.

## Cara pakai pertama kali

Sama seperti desktop: buka **Pengaturan (ikon gerigi di kanan atas) → API Key**,
tempel key fal.ai, lalu **Tes koneksi**. Tanpa key, aplikasi tidak bisa membuat
apa pun.

Hasil video otomatis masuk ke **Galeri: `Movies/Kenang/<nama proyek>/`**, dan
klip per adegan ke subfolder `Adegan/`. Tidak ada izin penyimpanan yang diminta
(MediaStore, API 29+).

## Hubungan dengan proyek desktop

Kode inti (`id.kenang.core.*`: config, prompt, template gerakan, klien fal,
analisis, storyboard, orkestrasi video) **disalin** dari `kenang-desktop`, bukan
modul bersama. Alasannya: proyek desktop adalah Kotlin/JVM biasa; mengubahnya
jadi Kotlin Multiplatform akan membongkar build yang sudah stabil dan sedang
dipakai harian.

Konsekuensinya: **perbaikan pada logika inti harus diterapkan di dua tempat.**
Berkas yang sengaja berbeda (versi Android ditulis ulang):

| Berkas | Desktop | Android |
| --- | --- | --- |
| `AppDirs` | `%APPDATA%/Kenang` | `filesDir/Kenang` (privat aplikasi) |
| `ImageQuality`, `UploadPrep` | `javax.imageio` | `BitmapFactory` + downsampling |
| `DatabaseFactory` | JDBC SQLite + pembungkus notifikasi | `AndroidSqliteDriver` |
| `KeyVault`/`AesGcmFileStore` | Windows Credential Manager | file AES-GCM, kunci diturunkan dari perangkat |
| `TtsPreviewService.play` | JLayer | `MediaPlayer` |
| `AssemblyService` + perakit video | FFmpeg bawaan | **Media3 Transformer** |
| Pemilih berkas | Swing `JFileChooser` | Android Photo Picker |
| Folder output | folder pilihan pengguna | Galeri (MediaStore) |

## Perbedaan hasil video (penting)

Perakitan video Android memakai Media3 Transformer karena tidak ada FFmpeg di
HP. Dua efek desktop **belum ada** di Android:

1. **Transisi xfade 0,6 detik antar adegan** — di Android adegan berpindah
   dengan potongan tegas (hard cut). Akibatnya durasi video Android = jumlah
   durasi semua adegan (desktop sedikit lebih pendek karena tumpang tindih).
2. **Subtitle yang dibakar ke video** — pilihan subtitle diabaikan di Android.

Yang tetap sama: urutan adegan, narasi (termasuk perbaikan tempo F5.3), musik
latar yang diulang di bawah narasi, rasio 9:16 / 16:9 (scale + crop), ekspor
klip per adegan, dan fitur "Buat versi rasio lain".

## Status uji

Sudah diverifikasi di emulator (Android 14, x86_64): aplikasi terpasang dan
berjalan, onboarding → Home → wizard → Pengaturan tampil benar, database dan
penyimpanan key berfungsi.

**Belum diuji end-to-end dengan API berbayar** — analisis foto sampai video jadi
di Android belum pernah dijalankan sekali pun (lihat `docs/KNOWN_ISSUES.md`
KI-019). Jalur yang paling perlu diperhatikan saat uji pertama adalah perakitan
Media3 pada `VideoAssembler`.
