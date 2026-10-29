# Root assessment — Blaupunkt Palm Bay 1000 (draft 1, 2026-10-30)

Verdict: probably doable in under a day, but nobody has published a Palm Bay 1000 root, so the exact method is unknown until we identify the platform. Do two Mac-only steps and one adb session first; they decide the route and the effort. Do NOT flash anything before a stock-firmware recovery has been proven.

## What root would buy (and what works without it)

| goal | without root (adb only) | needs root |
|------|-------------------------|-----------|
| install our launcher, make it HOME | yes (`adb install`, `cmd package set-home-activity`) | no |
| grant our app permissions, notification-listener | yes (`pm grant`, `cmd notification allow_listener`) | no |
| disable the stock launcher | yes (`pm disable-user --user 0 <pkg>`, reversible) | no |
| keep vendor Radio/Phone/Camera/CarPlay reachable | yes (explicit intents) | no |
| keep our app alive across ACC-off (vendor "whitelist" killers) | sometimes | usually: edit vendor whitelist files |
| read Canbox/vehicle data the vendor service already decodes | maybe (broadcast/Binder) | if permission-gated |
| read raw canbox frames | only if the vendor service forwards them | yes if it is a tty the MCU feeds |
| hide status/nav bars permanently, replace SystemUI bits | partly (immersive mode) | cleaner with root |
| re-skin vendor Radio/BT-phone/EQ screens | no | still requires reverse engineering |

Reading the MCU itself is out of reach for software root: it is a separate chip. The canbox UART ends at the MCU.

## Dependency checklist

| id | what | tool / source | notes |
|----|------|---------------|-------|
| D1 | Identify the platform (SoC family, MCU, Android build, partition layout, A/B, AVB) | `adb shell getprop` (ro.board.platform, ro.product.*, ro.build.fingerprint, ro.boot.verifiedbootstate, ro.boot.flash.locked, ro.debuggable) · OR crack open the firmware package on the Mac | One reviewer video tags the platform "A53C"; that string alone is not enough |
| D2 | adb access | developer options (hidden vendor unlock first), Wi-Fi/wireless debugging, or a USB "device role" port | on some families the port is picky (USB A-to-A cable + role switch) |
| D3 | Stock firmware for the exact variant (platform + 2/4 GB RAM + version) | vendor OTA app download (it lands on the unit/phone), Blaupunkt toll-free support/WhatsApp link, installers | Required for recovery AND usually for step D4. Keep two copies off the unit |
| D4 | boot.img | inside the firmware package, or `dd if=/dev/block/by-name/boot` from an existing root shell | Android 11 => boot.img (not init_boot) |
| D5 | Magisk patch | Magisk app v27+ on the unit or any phone: Install -> Select and Patch a File | patching needs no root. Mac tooling: unpackbootimg / magiskboot only for inspection |
| D6 | Flash route | (a) root-capable adbd: `dd` it back; (b) vendor USB-updater with the patched image inside the package (signature/check files may need fixing: family-specific); (c) fastboot if the unit has it and the bootloader is unlockable (rare on head units) | THIS is the step that varies per family |
| D7 | Post-root | persist wireless adb (`setprop persist.adb.tcp.port 5555`), Magisk auto-grant for our app, vendor whitelist entries, SELinux check | |
| D8 | Safety net | car 12 V stable (engine running or charger), never while driving, stock package + boot backup stored off-unit, USB stick (FAT32, <=32 GB) with the stock package tested once | |

## How tedious: three bands

1. Easy (~30-60 min): the unit already has a root-capable adb (`adb shell id` -> uid=0, or `adb root` works). Then D4+D6 are a `dd`. Many cheap Chinese head units behave this way.
2. Medium (~half a day): locked bootloader but the vendor USB updater accepts a package we repack (family-specific tricks, e.g. a boot-only OTA signed with the vendor's public test key). Public guides exist for the common families (FYT UIS7862, MTCB/8227L, some Allwinner/Unisoc), so the effort is mostly reading the right guide and being careful.
3. Hard / maybe not worth it: signature-enforced updater + locked bootloader + no known exploit for this exact firmware. Then the options are an exploit hunt, a hardware route (UART/eMMC access), or living without root. We can still ship the app without root.

Prior from the Blaupunkt family: the older Key Largo (Android 9, "alps 8227L") had no public root guide, installers describe the maker as a rebadged generic Chinese unit, USB firmware updates work by unzipping onto a FAT32 stick. Treat Palm Bay as unknown.

## Step zero, no risk, mostly on the Mac

0a. Settings -> About/System: write down model, system version string, MCU version, Android version, kernel (photo is fine).
0b. Get the firmware package for your exact unit (the same file the dealer videos use for the "hard update"). Unzip it on the Mac: file names identify the family immediately (for example an FYT unit ships `lsec6315update` + a `*_1.zip`; MediaTek ships scatter/`*.img`; Allwinner ships `*.img`/`update.zip`; Unisoc ships `*.pac`). Look for boot.img, vbmeta, an updater binary, signature files. From that: family, whether boot.img is plain, whether the updater verifies signatures.
0c. One adb session (parked, engine on): D1 getprop dump + `adb shell su -c id` + `adb root`. 60 seconds, read-only.

Decision after 0a-0c: band 1/2 -> proceed; band 3 -> park root, ship non-root.

## Plan if proceeding

1. Back up: pull boot.img (or use the one from the package), copy the full stock package to two places, verify the stock USB update works on a spare moment when we are not depending on the unit.
2. Patch with Magisk; write back by the family's route (D6); first boot test with the engine running.
3. Verify: `adb shell su -c id` -> uid=0, Magisk shows "Installed", unit still boots after ACC cycle, reverse camera/CarPlay/BT/steering keys still work.
4. Only then use root: whitelist our app, expose raw canbox frames if reachable.

Rollback: write the original boot.img back the same way, or reflash the stock package from USB.

## Risks

Brick risk is real on band 2 and 3 (bad boot image, half-finished update). Mitigation: proven stock recovery first, stable power, patched image only touches boot. Warranty (2 years) is void once flashed. Android security: a rooted head unit that is internet-connected is a bigger target; keep Magisk superuser prompts on "ask" except for our app.
