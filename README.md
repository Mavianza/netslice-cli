# NetSlice

Mini program harian **29 Oktober 2026**: kalkulator subnet IPv4 dan perencana pembagian jaringan **VLSM** berbasis Java. Berguna untuk latihan jaringan komputer, menyusun pembagian IP laboratorium, dan memeriksa perhitungan subnet tanpa menghitung biner satu per satu.

Proyek sudah menyertakan **JAR siap dijalankan**, seluruh source code, build script, contoh kebutuhan jaringan, dan tes. Tidak memakai dependensi eksternal, Maven, atau Gradle. Semua perhitungan dilakukan lokal; program tidak menghubungi atau mengubah konfigurasi jaringan.

## Mulai cepat

1. Siapkan **Java 17 atau lebih baru**; periksa dengan `java -version`.
2. Ekstrak ZIP lalu masuk ke folder `netslice-cli`.
3. Jalankan JAR yang sudah tersedia:

```sh
java -jar dist/netslice.jar info 192.168.10.37/24
java -jar dist/netslice.jar plan 192.168.10.0/24 Lab=60 Kelas=30 Server=12
```

Perintah sama berlaku di Windows, Linux, dan macOS dengan Java yang sesuai. Pemeriksaan aktual proyek ini dilakukan di Linux dengan OpenJDK 17.0.20; pengujian pada Windows/macOS belum dilakukan.

## Fitur

- Informasi network CIDR, subnet mask, wildcard mask, broadcast, rentang host, dan jumlah alamat.
- Dukungan seluruh prefix IPv4 `/0` sampai `/32`, termasuk nilai alamat yang melebihi batas integer bertanda 32 bit.
- Pembagian VLSM berdasarkan kebutuhan host, dimulai dari blok terbesar; ukuran sama mengikuti urutan masukan.
- Penolakan rencana jika kapasitas kurang, tanpa menyimpan hasil parsial.
- Penampilan host cadangan di dalam setiap alokasi serta sisa jaringan induk sebagai blok CIDR.
- Input kebutuhan melalui argumen atau berkas teks UTF-8 dengan komentar.
- Laporan Markdown untuk dibaca/disalin ke dokumentasi, dan JSON untuk diolah program lain.
- Laporan yang sudah ada tidak ditimpa kecuali opsi `--force` diberikan. Berkas kebutuhan tetap dilindungi dari penimpaan oleh laporan.

## Menghitung satu subnet

```sh
java -jar dist/netslice.jar info 192.168.10.37/24
```

Bagian hasil:

| Properti | Nilai |
| --- | --- |
| Masukan | 192.168.10.37/24 |
| Network CIDR | 192.168.10.0/24 |
| Subnet mask | 255.255.255.0 |
| Wildcard mask | 0.0.0.255 |
| Broadcast | 192.168.10.255 |
| Host pertama | 192.168.10.1 |
| Host terakhir | 192.168.10.254 |
| Kapasitas host | 254 |

`info` menerima alamat host dan menjelaskan normalisasinya ke network address. `plan` mewajibkan network address yang sudah sejajar: `192.168.10.37/24` ditolak dengan saran `192.168.10.0/24`.

### Arti prefix khusus

- `/0` sampai `/30`: kapasitas host LAN dihitung sebagai `2^(32-prefix) - 2`.
- `/31`: ditafsirkan sebagai sambungan **point-to-point**, dengan dua endpoint dan tanpa broadcast khusus, sesuai [RFC 3021 bagian 2.1](https://www.rfc-editor.org/rfc/rfc3021.html#section-2.1).
- `/32`: ditampilkan sebagai **host route** satu alamat, tanpa network/broadcast terpisah.

Perencana VLSM mengalokasikan **LAN konvensional hingga `/30`**. Kebutuhan satu atau dua host tetap memperoleh `/30`; tidak diam-diam diubah menjadi sambungan point-to-point. Untuk menganalisis `/31` atau `/32`, gunakan `info`.

## Membagi jaringan dengan VLSM

```sh
java -jar dist/netslice.jar plan 192.168.10.0/24 Lab=60 Kelas=30 Server=12
```

Hasil alokasi:

| Segmen | Host diminta | CIDR | Host pertama | Host terakhir | Broadcast | Host cadangan |
| --- | ---: | --- | --- | --- | --- | ---: |
| Lab | 60 | 192.168.10.0/26 | 192.168.10.1 | 192.168.10.62 | 192.168.10.63 | 2 |
| Kelas | 30 | 192.168.10.64/27 | 192.168.10.65 | 192.168.10.94 | 192.168.10.95 | 0 |
| Server | 12 | 192.168.10.96/28 | 192.168.10.97 | 192.168.10.110 | 192.168.10.111 | 2 |

Total 102 host diminta, 112 alamat blok dialokasikan, dengan kapasitas 106 host dan 4 host cadangan. Sisa **144 alamat** diringkas menjadi `192.168.10.112/28` dan `192.168.10.128/25`.

**Jumlah host harus sudah mencakup gateway**, access point, printer, dan perangkat lain yang membutuhkan alamat. Program tidak mencadangkan IP gateway secara otomatis. "Host cadangan" berarti slot host di dalam alokasi; "alamat belum dialokasikan" berarti alamat di luar seluruh alokasi. Keduanya berbeda.

Nama dengan spasi diberi tanda kutip:

```sh
java -jar dist/netslice.jar plan 10.20.0.0/24 "Lab Komputer=50" "Ruang Dosen=20"
```

### Kebutuhan dari berkas

`examples/kampus.plan` sudah berisi:

```text
# Jumlah host mencakup gateway jika diperlukan.
Laboratorium = 60
Kelas = 30
Server = 12
```

Jalankan:

```sh
java -jar dist/netslice.jar plan 192.168.10.0/24 --file examples/kampus.plan
```

Baris kosong dan komentar mulai `#` diabaikan. UTF-8, UTF-8 dengan BOM, LF, dan CRLF didukung. Pilih input argumen **atau** `--file`; mencampur keduanya ditolak.

## Menyimpan dan mengolah hasil

Markdown:

```sh
java -jar dist/netslice.jar plan 192.168.10.0/24 --file examples/kampus.plan --out reports/kampus.md
```

JSON:

```sh
java -jar dist/netslice.jar plan 192.168.10.0/24 --file examples/kampus.plan --json --out reports/kampus.json
java -jar dist/netslice.jar info 203.0.113.6/31 --json
```

Direktori tujuan dibuat jika belum ada. Hasil tetap dicetak ke stdout setelah penyimpanan berhasil. Pilihan format ditentukan oleh `--json`, bukan ekstensi nama berkas.

Jika laporan lama memang ingin diganti, tambahkan `--force`:

```sh
java -jar dist/netslice.jar info 10.0.0.0/16 --out reports/info.md --force
```

JSON memuat `schemaVersion: 1`. Pada `info`, properti `subnet` berisi rincian jaringan dan `normalized` menunjukkan perubahan alamat host ke network. Pada `plan`, tersedia `base`, `summary`, `allocations`, dan `freeCidrs`. `broadcast` bernilai `null` untuk `/31` dan `/32`.

Contoh laporan aktual yang dihasilkan dari berkas kampus disertakan dalam `examples/hasil-kampus.md` dan `examples/hasil-kampus.json`.

### Exit code

| Kode | Makna |
| ---: | --- |
| 0 | Berhasil. |
| 2 | Argumen, CIDR, atau kebutuhan segmen tidak valid. |
| 3 | Kapasitas jaringan induk tidak cukup. |
| 4 | Membaca/menulis berkas gagal, termasuk menimpa laporan tanpa `--force`. |

Kesalahan ditulis ke stderr. Bila operasi gagal, tidak ada laporan sukses di stdout.

## Build dan tes

Untuk memakai JAR yang disertakan, tidak perlu build. Untuk membangun dari source, gunakan **JDK 17+** dari direktori proyek:

```sh
java Build.java
java Build.java test
```

Build memakai compiler bawaan JDK dengan target Java 17, UTF-8, `-Xlint:all`, dan `-Werror`. Test harness memakai assertion yang selalu aktif, tidak bergantung pada opsi `-ea`. Build script tidak mengunduh dependensi.

Pengujian mencakup batas `/0`, `/31`, `/32`, validasi input, ukuran blok minimal, alokasi tanpa overlap, 300 rencana acak, parsing berkas, perlindungan laporan, dan exit code CLI. Ringkasan hasil aktual ada pada `verification/RESULTS.md`.

Pemeriksaan silang tambahan, **opsional**, membutuhkan Python 3.9+:

```sh
python3 tools/verify_with_python.py
```

Skrip ini menjalankan JAR sesungguhnya dan membandingkan 100 kasus informasi subnet serta 40 rencana VLSM dengan modul standar `ipaddress`. Tidak diperlukan untuk menjalankan NetSlice sehari-hari.

## Batas ruang lingkup

- IPv4 desimal bertitik; IPv6 dan nama DNS tidak diproses. Nol di depan oktet atau jumlah host ditolak agar tidak ambigu.
- Satu jaringan induk yang belum mempunyai alokasi lain. Reservasi manual, subnet yang sudah terpakai, dan DHCP pool tidak dimodelkan.
- 1-256 segmen. Nama segmen 1-40 karakter huruf/angka/spasi/`_`/`.`/`-`, diawali huruf atau angka, dan harus unik tanpa membedakan huruf besar/kecil.
- Kebutuhan 1-4294967294 host per segmen; rencana tetap harus muat dalam jaringan induk. Berkas kebutuhan maksimum 64 KiB.
- Jumlah host merupakan kapasitas matematis sesuai model di atas, bukan jaminan alamat boleh digunakan atau dapat dirutekan. Rentang khusus, kebijakan jaringan, dan dukungan perangkat tidak diperiksa.

Referensi konsep: [RFC 4632 — CIDR](https://www.rfc-editor.org/rfc/rfc4632.html) dan [RFC 3021 — IPv4 /31](https://www.rfc-editor.org/rfc/rfc3021.html).

## Struktur proyek

```text
dist/netslice.jar            Program siap dijalankan
Build.java                   Build dan test runner tanpa dependensi
src/netslice/Subnet.java     Perhitungan IPv4 dan CIDR
src/netslice/Planner.java    Alokasi VLSM dan ringkasan blok sisa
src/netslice/PlanFile.java   Pembaca kebutuhan UTF-8
src/netslice/Reports.java    Laporan Markdown dan JSON
src/netslice/Main.java       CLI dan operasi berkas
test/netslice/               Tes Java mandiri
tools/verify_with_python.py  Pemeriksaan silang independen
examples/                   Masukan dan hasil aktual
verification/               Ringkasan pengujian
```

Lisensi MIT, lihat `LICENSE`.
