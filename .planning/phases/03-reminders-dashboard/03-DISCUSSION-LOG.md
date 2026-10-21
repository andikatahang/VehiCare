# Phase 3: Reminders & Dashboard - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-21
**Phase:** 3-Reminders & Dashboard
**Areas discussed:** Tempat mengatur reminder, Bentuk aturan & basis hitung, Notifikasi & advance notice, Isi kartu home & urutan

---

## Tempat mengatur reminder

### Q1 — Di mana user membuat dan mengubah reminder?

| Option | Description | Selected |
|--------|-------------|----------|
| Layar Reminders per kendaraan | Slot baru di vehicle Detail membuka layar Reminders per kendaraan (pola D-32) | ✓ |
| Inline di form service | Checkbox/field di form service entry | |
| Global di Settings | Satu daftar rule untuk semua kendaraan | |

**User's choice:** Layar Reminders per kendaraan
**Notes:** Rule memang milik kendaraan (interval motor vs mobil berbeda); mengikuti pola slot→layar Phase 2.

### Q2 — Bagaimana user menambah dan mengubah rule?

| Option | Description | Selected |
|--------|-------------|----------|
| FAB + editor satu layar | FAB membuka editor satu layar; tap row membuka editor yang sama | ✓ |
| Editor inline di list | Row mengembang jadi form di tempat | |
| Semua rule editable di satu layar | Semua rule tampil dengan field editable langsung | |

**User's choice:** FAB + editor satu layar
**Notes:** Mengikuti D-30/D-33/D-36; tidak memperkenalkan pola expand-in-place.

### Q3 — Apakah reminder bisa dinonaktifkan tanpa dihapus?

| Option | Description | Selected |
|--------|-------------|----------|
| Ya, toggle aktif/nonaktif | Kolom `enabled` + toggle di row | ✓ |
| Tidak, cukup hapus rule | Hanya add/edit/delete | |

**User's choice:** Ya, toggle aktif/nonaktif
**Notes:** Interval yang sudah diisi tidak hilang saat rule dibisukan.

### Q4 — Service type apa saja yang boleh dipilih saat membuat rule?

| Option | Description | Selected |
|--------|-------------|----------|
| Preset + tipe custom yang dipelajari | Chip group yang sama dengan form service (D-24/D-25) | ✓ |
| Enam preset saja | Hanya preset | |
| Free text | Ketik bebas | |

**User's choice:** Preset + tipe custom yang dipelajari
**Notes:** Satu vocabulary aplikasi; rule dijamin cocok dengan string tipe tersimpan (D-40).

### Q5 — Bagaimana slot Reminders muncul di vehicle Detail?

| Option | Description | Selected |
|--------|-------------|----------|
| Summary row dengan next due | "2 reminders · next due 12.000 km", tap membuka layar | ✓ |
| Baris navigasi polos | "Reminders ›" tanpa data | |

**User's choice:** Summary row dengan next due
**Notes:** Konsisten dengan `ServiceSummaryRow` (D-32).

### Q6 — Kapan izin notifikasi diminta ke user? *(konflik dokumen — lihat catatan)*

| Option | Description | Selected |
|--------|-------------|----------|
| Setelah Save rule pertama | Penjelasan in-app lalu prompt OS saat reminder pertama disimpan | ✓ (final) |
| Banner di layar Reminders | Banner non-blocking | |
| Prompt saat layar Reminders dibuka | Prompt saat layar pertama dibuka | |
| Prompt OS langsung saat pembukaan pertama | Usulan awal user: minta izin di pembukaan pertama via native API masing-masing OS | ✗ (dibatalkan) |

**User's choice:** awalnya "prompt OS langsung saat pembukaan pertama"; setelah konflik dijelaskan, user memilih **"Gate di reminder pertama"**.
**Notes:** **Konflik dokumen harus tercatat:** usulan pembukaan-pertama bertentangan dengan ROADMAP SC-2, STATE.md Key Decision ("A one-time iOS prompt denied on cold launch breaks reminders permanently") dan riset PITFALLS Pitfall 5. Konsekuensi teknisnya dijelaskan (prompt iOS hanya sekali; ditolak = reminder mati permanen sampai user mengubah Settings). User memilih mempertahankan SC-2, sehingga **tidak ada perubahan ROADMAP**.

---

## Bentuk aturan & basis hitung

### Q1 — Bisakah satu service type punya interval km dan bulan sekaligus?

| Option | Description | Selected |
|--------|-------------|----------|
| Boleh km dan bulan sekaligus | Satu rule, dua interval opsional, jatuh tempo pada yang lebih dulu | ✓ |
| Satu jenis per rule | Satu rule hanya km atau bulan | |
| Wajib keduanya | Kedua interval harus diisi | |

**User's choice:** Boleh km dan bulan sekaligus
**Notes:** Riset FEATURES menyebut kombinasi ini gold standard; biaya hanya dua kolom nullable.

### Q2 — Interval dihitung dari mana?

| Option | Description | Selected |
|--------|-------------|----------|
| Service terakhir bertipe sama | Km + tanggal dari entry bertipe itu | ✓ |
| Entry terakhir apa pun | Baseline odometer terbaru | |
| Dikunci saat rule dibuat | Baseline tetap sampai user update manual | |

**User's choice:** Service terakhir bertipe sama
**Notes:** Sesuai REM-01 ("from last service"); tiap tipe punya interval berbeda.

### Q3 — Apa yang terjadi pada rule saat user mencatat service baru?

| Option | Description | Selected |
|--------|-------------|----------|
| Otomatis bergeser | Baseline mengikuti service terbaru, tanpa langkah tambahan | ✓ |
| Konfirmasi tiap service | Aplikasi menanyakan perbarui/buat ulang | |
| Tandai selesai, aktifkan ulang manual | Rule ditandai done, harus diaktifkan lagi | |

**User's choice:** Otomatis bergeser
**Notes:** Melindungi alur cepat 30 detik (D-31).

### Q4 — Bagaimana kalau rule dibuat untuk tipe yang belum pernah dicatat?

| Option | Description | Selected |
|--------|-------------|----------|
| Pakai kondisi kendaraan saat ini | Baseline km entry terakhir kendaraan + tanggal rule; "belum ada data km" bila belum ada entry | ✓ |
| Status menunggu log pertama | Tidak dihitung sampai ada entry bertipe itu | |
| Tolak sampai tipe pernah dicatat | Rule tidak boleh dibuat | |

**User's choice:** Pakai kondisi kendaraan saat ini
**Notes:** Menghindari kesan "reminder rusak", dan tidak pernah mengarang nilai odometer (riset melarang ekstrapolasi).

---

## Notifikasi & advance notice

### Q1 — Apakah reminder memberi tahu lebih awal sebelum jatuh tempo (Q3 di STATE.md)?

| Option | Description | Selected |
|--------|-------------|----------|
| Per rule, opsional | Kolom `lead_km` / `lead_days`, kosong = tepat saat due | ✓ |
| Global di Settings | Satu nilai lead untuk semua | |
| Tanpa advance notice | Hanya saat due | |

**User's choice:** Per rule, opsional
**Notes:** **Melampaui huruf REM-01/02** (P1 di riset FEATURES, bukan P0) — disetujui user dan dicatat eksplisit di CONTEXT.md (D-50) agar tidak dibaca sebagai scope creep.

### Q2 — Kalau rule sudah jatuh tempo dan belum diservis, seberapa sering ia memberi tahu?

| Option | Description | Selected |
|--------|-------------|----------|
| Sekali per siklus | Diam sampai baseline bergeser atau rule diedit | ✓ |
| Ulangi tiap 7 hari | Pengingat berkala | |
| Setiap evaluasi | Tiap evaluasi harian / tiap buka app | |

**User's choice:** Sekali per siklus
**Notes:** Sesuai anti-feature riset ("aggressive notifications" = keluhan utama review kompetitor).

### Q3 — Bagaimana reminder waktu dikirim di Android (Q7 di STATE.md)?

| Option | Description | Selected |
|--------|-------------|----------|
| WorkManager untuk keduanya | Mileage + waktu lewat scheduled work; tanpa izin exact-alarm, tanpa receiver reboot | ✓ |
| AlarmManager exact untuk waktu | `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM` + `BOOT_COMPLETED` | |
| Hybrid | Waktu via AlarmManager, mileage via WorkManager | |

**User's choice:** WorkManager untuk keduanya
**Notes:** Trade-off diterima: waktu bisa meleset beberapa jam di Doze dan itu harus tercermin di copy UI. iOS tetap tepat waktu via `UNCalendarNotificationTrigger` tanpa biaya tambahan.

### Q4 — Ke mana notifikasi mengarah saat diketuk?

| Option | Description | Selected |
|--------|-------------|----------|
| Vehicle Detail kendaraan terkait | Payload `vehicleId` → Detail (hub kedua summary row) | ✓ |
| Layar Reminders kendaraan itu | Langsung ke daftar rule | |
| Cukup buka aplikasi | Perilaku default OS | |

**User's choice:** Vehicle Detail kendaraan terkait
**Notes:** Riset Notif 4: mendarat di home generik dibaca user sebagai bug.

### (Ditanyakan, dijawab di area berikutnya) — status izin ditolak

Pertanyaan ini dipindahkan ke area "Isi kartu home & urutan" karena menyangkut permukaan UI home, dan diputuskan di sana (D-61).

---

## Isi kartu home & urutan

### Q1 — Bagaimana urutan kendaraan di home saat ada reminder jatuh tempo?

| Option | Description | Selected |
|--------|-------------|----------|
| Urutkan berdasarkan urgensi | Overdue/due terdekat di atas; tanpa reminder di bawah — mengubah D-16 | ✓ |
| Tetap newest-first + badge | D-16 tidak disentuh, urgensi lewat badge | |
| Pisahkan section | Section "Needs attention" di atas | |

**User's choice:** Urutkan berdasarkan urgensi
**Notes:** **D-16 (newest-first) di-supersede eksplisit**, dicatat di CONTEXT.md (D-57) dengan perlakuan yang sama seperti D-26 di Phase 2.

### Q2 — Apa yang ditampilkan pada kartu kendaraan di home?

| Option | Description | Selected |
|--------|-------------|----------|
| Kartu yang ada + baris status | `VehicleCard` + satu baris status service | ✓ |
| Kartu dashboard khusus | Komponen kartu baru dengan angka besar | |
| Ringkasan di header saja | Ringkasan di atas daftar, kartu tidak berubah | |

**User's choice:** Kartu yang ada + baris status
**Notes:** Satu komponen untuk satu entitas (D-14); DSH-01 terpenuhi karena next due melekat di kartu.

### Q3 — Kalau satu kendaraan punya beberapa rule, apa isi baris statusnya?

| Option | Description | Selected |
|--------|-------------|----------|
| Paling mendesak + penghitung | Satu baris + "+1 due" | ✓ |
| Tampilkan beberapa rule | 2-3 baris per kartu | |
| Jumlah saja | "2 due, 1 upcoming" | |

**User's choice:** Paling mendesak + penghitung
**Notes:** Tinggi kartu tetap seragam dan daftar tetap mudah dipindai.

### Q4 — Bagaimana status due ditampilkan secara visual?

| Option | Description | Selected |
|--------|-------------|----------|
| Upcoming / due soon / overdue | Tiga keadaan, selalu berteks; hanya overdue memakai `Danger` | ✓ |
| Normal vs overdue | Dua keadaan | |
| Tanpa warna | Netral semua | |

**User's choice:** Upcoming / due soon / overdue
**Notes:** Sesuai kontrak aksesibilitas 01-UI-SPEC: warna tidak boleh jadi satu-satunya pembawa makna.

### Q5 — Di mana status "notifikasi dimatikan" ditampilkan?

| Option | Description | Selected |
|--------|-------------|----------|
| Banner di home + status di Reminders | Banner tipis di home (bila ada rule aktif & izin belum ada) + baris status di layar Reminders | ✓ |
| Hanya di layar Reminders | Home tetap bersih | |
| Hanya di home | Banner saja | |

**User's choice:** Banner di home + status di Reminders
**Notes:** Riset Notif 3 mewajibkan status izin dicek ulang setiap app dibuka; tap banner mengarah ke layar Reminders, atau ke Settings sistem bila ditolak permanen.

---

## the agent's Discretion

Tidak ada area di mana user berkata "you decide" secara eksplisit. Discretion dicatat di CONTEXT.md § "the agent's Discretion", dibatasi oleh keputusan dan requirement:

- Kolom `reminder_rule` di luar yang sudah diputuskan (bentuk penanda notifikasi, index, representasi `created_at`)
- Bentuk query dashboard (satu agregat, tanpa N+1) dan cara membandingkan urgensi antar sumbu km dan waktu
- Cadence WorkManager Android, constraint set, dan pembagian `:shared` `androidMain` vs `:androidApp` (worker, channel, manifest)
- Channel notifikasi Android (id/nama/importance, dibuat di `Application.onCreate`) dan titik penetapan delegate iOS
- Layout editor, batas validasi interval dan pesannya
- Penulisan judul/isi notifikasi, empty state, copy banner, dan frasa "+N due"
- Apakah row rule menampilkan status due-nya sendiri, dan tie-break antar kendaraan yang sama mendesak

## Deferred Ideas

- Snooze / acknowledge langsung dari notifikasi — P1 riset, butuh permukaan aksi notifikasi di kedua platform
- Prediksi tanggal jatuh tempo dari laju km/hari — P2 riset, butuh ≥2 entry per tipe
- Reminder untuk dokumen (pajak, asuransi, STNK) — wilayah DOC-01/v2
- Suara notifikasi per rule / channel per service type — tidak perlu di v1
- Riwayat notifikasi (apa yang dikirim, kapan, dibuka atau tidak) — belum ada kebutuhan user di v1
- Quiet hours / jendela do-not-disturb — penyempurnaan lanjutan dari D-51
- Widget layar utama menampilkan next due — tidak ada requirement di belakangnya
