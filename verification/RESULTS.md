# Hasil verifikasi NetSlice

Proyek harian **29 Oktober 2026**. Seluruh hasil di bawah berasal dari eksekusi aktual pada proyek ini.

## Lingkungan dan build

- Linux, OpenJDK **17.0.20**.
- Perintah build dan tes: `java Build.java test`.
- Source utama dan tes berhasil dikompilasi dengan `--release 17 -encoding UTF-8 -Xlint:all -Werror`.
- JAR memuat 9 class aplikasi, tanpa class tes dan tanpa dependensi eksternal. Seluruh class aplikasi memiliki classfile major version **61** (Java 17).
- `java --limit-modules java.base -jar dist/netslice.jar --version` berhasil dengan keluaran `1.0.0`, sehingga program tidak memerlukan modul compiler saat dijalankan.

## Tes Java

**31 dari 31 tes lulus; 0 gagal.** Test harness memakai assertion yang selalu aktif.

Yang diperiksa:

- Network, subnet mask, wildcard, broadcast, dan rentang host.
- Batas unsigned IPv4, `/0`, `/30`, `/31`, dan `/32`.
- Alamat/prefix invalid, oktet ambigu, kebutuhan host invalid, nama duplikat, dan batas jumlah segmen.
- Contoh VLSM manual, pengurutan stabil, blok paling kecil yang mencukupi, kapasitas pas, kapasitas kurang, dan batas maksimum `/0`.
- **300 rencana acak** dalam satu tes berbasis properti: alignment, kapasitas, blok minimal, tidak ada overlap/gap, serta seluruh sisa CIDR menutup jaringan induk secara tepat.
- Berkas UTF-8/BOM/CRLF/komentar, nomor baris kesalahan, ukuran maksimum, encoding rusak, dan berkas hilang.
- CLI, format laporan, penyimpanan, perlindungan berkas masukan, penolakan penimpaan tanpa `--force`, serta exit code `0/2/3/4`.

## Pemeriksaan silang independen

Perintah aktual:

```sh
python3 tools/verify_with_python.py
```

Oracle: modul standar Python **ipaddress**, Python **3.12.14**. Skrip menjalankan JAR sesungguhnya dan membaca JSON hasil program.

- **100 kasus info subnet** lulus, mencakup seluruh prefix `/0` sampai `/32`.
- **40 rencana VLSM** lulus: 17 rencana valid cocok dengan oracle, 23 rencana tidak cukup kapasitas ditolak dengan benar menggunakan exit code `3`.
- Total **140 kasus** lulus. Seed acak tetap `20261029`.

Keluaran JSON aktual disimpan di `python-crosscheck.json`.

## Contoh end-to-end

Kedua perintah berikut dijalankan dengan exit code `0`:

```sh
java -jar dist/netslice.jar plan 192.168.10.0/24 --file examples/kampus.plan --out examples/hasil-kampus.md
java -jar dist/netslice.jar plan 192.168.10.0/24 --file examples/kampus.plan --json --out examples/hasil-kampus.json
```

Hasil: tiga segmen memakai 112 alamat blok, menyediakan 106 slot host untuk 102 host yang diminta, dan menyisakan 144 alamat dalam dua blok CIDR. Kedua laporan aktual disertakan pada folder `examples/`.

## Status GitHub

Catatan akses di bawah mencatat keadaan saat pembuatan arsip awal. Publikasi berikutnya ditangani oleh `Mavianza/daily-app-publisher`; identitas paket dan commit penerbit tercatat pada `.daily-app-publisher.json` di repo hasil.

Belum ada commit atau push GitHub untuk NetSlice. Koneksi saat ini terautentikasi sebagai `Mavianza`, tetapi tidak menyediakan aksi membuat repositori baru. Pemeriksaan `Mavianza/netslice-cli` dan `Mavianza/daily-app-publisher` mengembalikan `404` (tidak ditemukan atau tidak dapat diakses). GitHub CLI dan token CLI tidak tersedia di lingkungan ini.

Arsip menyertakan seluruh source code dan JAR siap pakai sebagai hasil yang dapat diunduh. Tidak ada pengujian Windows/macOS atau perubahan konfigurasi jaringan nyata yang diklaim.
