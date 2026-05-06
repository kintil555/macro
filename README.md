# CustomMacro — Fabric Mod for Minecraft 1.21.11

Buat dan kelola macro in-game dengan mudah. Tekan tombol, jalankan command!

## Fitur
- **Tambah macro** dengan nama, key binding, dan aksi (command/chat)
- **GUI lengkap** di pause menu — pojok kiri atas, ikon merah (◉)
- **Overlay button** in-game (pojok kiri atas layar) untuk buka manager
- **Simpan otomatis** ke file `config/custommacro.json`
- **Eksekusi instan** — tekan key → command langsung terkirim
- Mendukung `/command` maupun chat biasa

## Cara Pakai
1. Install Fabric Loader 0.18.1 + Fabric API 0.141.3+1.21.11
2. Taruh JAR di folder `mods/`
3. Masuk game → tekan **ESC** (pause menu)
4. Klik tombol **◉** di **pojok kiri atas** pause menu
5. Klik **+ Add Macro**, isi:
   - **Name**: nama macro (contoh: `Go Home`)
   - **Action**: command atau teks (contoh: `/home`)
   - **Key**: klik tombol lalu tekan key (contoh: `` ` ``)
6. Klik **Save** → selesai!

## Contoh Macro
| Nama       | Key | Aksi        |
|------------|-----|-------------|
| Go Home    | \`  | /home       |
| Go Spawn   | -   | /spawn      |
| Hey All    | =   | Hello all!  |
| Back       | F9  | /back       |

## Build
```bash
./gradlew build
```
JAR tersedia di `build/libs/custommacro-*.jar`

## Kompatibilitas
- Minecraft: **1.21.11**
- Fabric Loader: **0.18.1+**
- Fabric API: **0.141.3+1.21.11**
- Java: **21**
