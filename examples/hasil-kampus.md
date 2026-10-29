# NetSlice - Rencana VLSM

Jaringan induk: **192.168.10.0/24**. Alokasi dari blok terbesar; ukuran sama mengikuti urutan masukan.

| Segmen | Host diminta | CIDR | Subnet mask | Host pertama | Host terakhir | Broadcast | Host cadangan |
| --- | ---: | --- | --- | --- | --- | --- | ---: |
| Laboratorium | 60 | 192.168.10.0/26 | 255.255.255.192 | 192.168.10.1 | 192.168.10.62 | 192.168.10.63 | 2 |
| Kelas | 30 | 192.168.10.64/27 | 255.255.255.224 | 192.168.10.65 | 192.168.10.94 | 192.168.10.95 | 0 |
| Server | 12 | 192.168.10.96/28 | 255.255.255.240 | 192.168.10.97 | 192.168.10.110 | 192.168.10.111 | 2 |

Host diminta: **102**. Kapasitas host dialokasikan: **106**. Host cadangan di dalam alokasi: **4**.

Alamat blok terpakai: **112** dari **256**. Alamat belum dialokasikan: **144**.

## Blok yang belum dialokasikan

- 192.168.10.112/28 (16 alamat)
- 192.168.10.128/25 (128 alamat)

Jumlah host diminta harus sudah mencakup gateway dan perangkat infrastruktur. Tidak ada gateway yang dicadangkan otomatis.
