# Smoker Timer

Aplikasi Android sederhana untuk mencatat waktu merokok dan menghitung kapan smoking berikutnya boleh lagi.

## Fitur

- Log history merokok: tanggal, jam, dan keterangan opsional
- Filter history:
  - hari ini
  - 7 hari terakhir
  - bulan ini
  - semua history
- Timer `allowed smoking` berdasarkan interval terakhir
- Notifikasi pengingat ketika interval selesai
- Rekap jumlah smoking hari ini

## Build APK

Jalankan:

```bash
./gradlew assembleDebug
```

APK debug akan muncul di:

```text
app/build/outputs/apk/debug/app-debug.apk
```
