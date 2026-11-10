# HomeDash

A cheap Android photo frame turned into a wall display for the family dashboard ([FamilyPlanner](../FamilyPlanner)), set up so it can't harm the home network.

## The device

- **Humblestead 10.1" Digital Picture Frame** ([Amazon](https://www.amazon.co.uk/dp/B0GGB7LWX8)), 1280×800 IPS touchscreen, WiFi, ships with the "AiMOR" photo-sharing app.
- **Treat it as untrusted for its whole life**, even after cleaning.

### Confirmed on our unit (2026-11-04, over ADB, read-only)

| | |
|---|---|
| Model | PF1007 (About page). Reports itself as `AEEZO`, board `astar_m86_8723cs` |
| Chip | Allwinner A33 (platform `astar`) |
| Android | **6.0.1**, security patch **2016-07-05** |
| Firmware | `astar_m86_8723cs-eng 6.0.1 MOB30R 20260529 test-keys` |
| Build | `eng` build with test keys, `ro.secure=0`, `ro.debuggable=1`, so **ADB runs as root** |
| USB | One USB-C port only, for both power and data. Presents as `1f3a:1002`: mass storage plus ADB. No keyboard and no network interface |
| Camera | None (0 camera devices) |
| The hole in the bezel | LTR-C216R ambient **light sensor** (auto-brightness), harmless |
| Microphone | Probably none. The codec has a capture channel, but most do whether a mic is fitted or not. Community report PR #204 found no mic on this model |
| Treble / GSI | Not possible. Android 6 predates Treble |

A community project documents this exact model, PF1007L on Allwinner A33: [wesbos/photo-frame-dashboard, PF1007L start-here guide](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/pf1007l-start-here.md). Our firmware `20260529` matches its report that root ADB works out of the box ([issue #8](https://github.com/wesbos/photo-frame-dashboard/issues/8)).

### Connecting it to the laptop

- **USB-C to USB-C does not work, even with a good cable**: no power and no data. The frame lacks the USB-C resistors that tell the laptop to turn on power.
- **What works:** laptop USB-C port → **USB-C to USB-A adapter** → **USB-A to USB-C data cable** → frame. The laptop then powers the frame and carries ADB over the same cable. The cable is effectively the power switch.
- The first A-to-C cable tried was power-only: the frame powered on but the laptop saw no USB device at all. Use a cable known to carry data, e.g. one used for transferring files from a phone.
- At power-on the frame briefly appears as `1f3a:efe8` (Allwinner FEL boot mode) for about 2 seconds, then reappears as `1f3a:1002` with ADB once Android has booted (about 30 seconds). This is normal.
- Avoid hubs powered by the laptop. The guide reports dropouts with them.

### Factory test app

About → tap **Model** 7 times → **Open test app** → password `YHK88888888` → tap **IS CLOSED** so it reads **is open**. On our unit this was set before ADB was confirmed, so we don't know whether our firmware needed it. Setting it back to **IS CLOSED** turns that ADB access off.

### Security findings

- **AiMOR has a vendor remote root shell.** The stock app connects to a vendor server that can send root commands (outbound TCP 7806). It also runs a paired file service on TCP 8086. **The frame must not join any network until AiMOR is disabled and nothing connects on 7806.**
- **`/system/xbin/su` is setuid root (`-rwsr-sr-x`) with no prompt**, so any app can become root. Remove it or take away its setuid bit before the frame goes online, and install nothing untrusted.
- **AiMOR is also the launcher** (`/system/priv-app/launcher_aimor`). Install the kiosk app *before* disabling it, or the frame boots to nothing.
- Vendor packages to disable: `com.efercro.os.aimor` (AiMOR launcher), `com.yhk.qeota` (vendor over-the-air updates), `com.DeviceTest` (factory test app).
- Unknown system app: `com.android.gl2jni` at `/system/priv-app/tomas/tomas.apk`. That package name is normally an OpenGL sample app. Inspect it before deciding.
- The system WebView is as old as the OS. The kiosk needs a browser that still supports Android 6.
- During an AiMOR factory reset, stock recovery runs root ADB for about 13–20 seconds (from the guide).
- `vuid_cert.txt` is a device credential. Keep it, serial numbers and MAC addresses out of this repo.

## Threat model

| Risk | Mitigation |
|---|---|
| Vendor apps and telemetry phoning home (AiMOR cloud etc.) | Clean the OS (step 2). Optionally enforce a DNS allowlist (step 4) |
| Unpatched OS or browser compromised, then used to attack the home LAN | Network isolation (step 3) |
| Frame holds a session with full family data access | Dedicated low-privilege login and `display` role (see FamilyPlanner below) |
| Anyone at the touchscreen browses elsewhere or changes data | Kiosk lock to the planner domain, read-only role |
| Laptop exposed while connected over USB | Plug in only while working. Consider USBGuard. Run vendor flashing tools (Windows .exe) in a VM only. Turn ADB off afterwards |
| Factory ADB over TCP (port 5555) left open, often with root and no auth | Port-scan after setup (`nmap -p- <ip>`) and close anything open |
| Microphone or camera | Check whether it has one. If so, revoke the permission or cover it |

## Plan

### 1. Inspect it offline

- **Do not connect it to home WiFi.** Use USB to the laptop only, or a phone hotspot.
- Enable Developer options, then USB debugging.
- Gather facts:
  ```
  adb shell getprop ro.product.board
  adb shell getprop ro.board.platform
  adb shell getprop ro.build.version.release
  adb shell getprop ro.treble.enabled
  adb shell getprop ro.boot.verifiedbootstate
  adb shell pm list packages
  ```
- These answer: chipset, Android version, Treble support (needed for a GSI), whether the bootloader looks unlockable, and which vendor packages are installed.
- **Back up the stock firmware before changing anything.**

#### Backup notes (done 2026-11-04/05)

- Stored at `~/photoframe-backups/2026-11-04/` on the laptop, **deliberately outside this Dropbox folder and outside git**. The `bootloader` partition (a FAT filesystem) holds the device credentials `vuid_cert.txt`, `vuid.txt` and `device_uid.txt`. Copy the backup to an external drive. It has its own README with the partition layout.
- Contents: all named partitions plus both eMMC boot areas (each sha1-verified), and the full 15.76 GB eMMC as 59 verified 256 MiB chunks.
- **The frame's default `dd` (toolbox) wraps at 2 GiB because it uses 32-bit offsets.** Use `toybox dd` for anything past 2 GiB. The first full-disk attempt silently produced wrapped data because of this, which is why each chunk is now also size-checked.
- The USB link drops occasionally on long transfers. The chunked script retries automatically.

### 2. Clean it as far as the device allows

- **A GSI is ruled out** (Android 6, no Treble), and no LineageOS build exists for this board. So the route is **debloat over root ADB**.
- Do not sign into a Google account or the AiMOR app on the frame. Upload no photos to vendor clouds.

#### Done 2026-11-05, verified across reboots

| Change | How | To undo |
|---|---|---|
| **Kiosk app** (`kiosk/`, `com.homedash.kiosk`) is the home screen | `adb install` | `adb uninstall com.homedash.kiosk`, after re-enabling AiMOR |
| AiMOR (launcher and remote root shell), vendor updates, "tomas" factory test app | `pm disable com.efercro.os.aimor com.yhk.qeota com.android.gl2jni` | `pm enable <pkg>` |
| `com.DeviceTest` (factory test app, runs as the system user) | Moved `/system/priv-app/DeviceTest` to `/system/DeviceTest.disabled`. `pm disable` doesn't stick: something re-enables it on every boot | Move it back and reboot |
| `/system/xbin/su` no longer gives any app root | `chmod 0755` (was `06755`). ADB has root without it | `chmod 06755` |
| Time zone | `persist.sys.timezone=Europe/London`, `auto_time_zone=0` (was America/New_York) | |
| Boot splash "LOVE IS SHARING" → "HomeDash" | `splash/make_splash.py` builds all three files: U-Boot `bootlogo.bmp` (mount `/dev/block/by-name/bootloader` as vfat, overwrite in place), `/system/media/initlogo.rle` and `/system/media/bootanimation.zip` | Restore from the backup (`partitions/bootloader.img` and `system.img`) |

Result: the frame boots into the kiosk, AiMOR and the other vendor apps don't run, nothing listens on 8086 and nothing connects on 7806, and root ADB over USB still works (it comes from `persist.sys.usb.config` and `ro.adb.secure=0`, not from the test app).

#### Kiosk app

- **Engine:** GeckoView 143 (= Firefox 143.0.4, the last release for Android 5–7). Chrome stopped at 106 for Android 6, and the system WebView is version 44 (2015), too old for the planner and for Microsoft sign-in.
- **Only navigates to the planner host** (`Allowlist.java`, unit-tested): not Microsoft, not the SWA identity host. The frame is paired with a display key (FamilyPlanner S34), so it never signs in. No pop-ups or new tabs, and Back stays inside the kiosk.
- Accepts a **pairing link over USB** (`PairingLink.java`, unit-tested): only `https://<planner>/display/pair#k=<43-char key>`. The link is never logged by the kiosk. Full screen, screen kept on. Shows its own "Can't reach the planner" page and retries every 30 s, reloads every 6 h, and reopens the session if the engine crashes.
- Safe Browsing, remote debugging and `about:config` are off.
- The planner's address isn't in the repo: put `plannerHost=<your app>.azurestaticapps.net` in `~/.gradle/gradle.properties` (or pass `-PplannerHost=...`). The build stops if it's missing.
- Build: `cd kiosk && JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug testDebugUnitTest assembleDebug`. Output goes to `~/.cache/homedash-kiosk-build/` to keep it out of Dropbox. Install with `adb -d install -r ~/.cache/homedash-kiosk-build/app/outputs/apk/debug/app-debug.apk`.
- Signed with the Android Studio debug key (`~/.android/debug.keystore`). Updates must use the same key, so back it up.
- **The engine never gets security updates again.** That makes the network allowlist in step 4 more valuable.

#### Gotchas

- `pm disable` is written to `package-restrictions.xml` a few seconds later. Wait before rebooting, or the change is lost.
- The frame's clock starts in 1970 with no network. HTTPS fails until NTP sets it (`auto_time=1`), so allow NTP (UDP 123) on any firewall.
- `adb shell reboot` works on this unit (about 40 s to boot).
- **Installing over laptop USB works if the load is reduced first:** push the APK, then `settings put system screen_brightness_mode 0; settings put system screen_brightness 10; setprop dalvik.vm.dex2oat-threads 1`, then `pm install -r /data/local/tmp/kiosk.apk`, then restore the brightness (was 140, mode 1) and threads (3). Done this way on 2026-11-05 with no dropouts.
- **Installing the 148 MB kiosk over laptop USB power at full load fails.** The frame browns out while unpacking the app, then fails to reconnect over and over (`device not accepting address … error -71`). Android installs all-or-nothing, so the old version stays. Use a powered USB hub, or ADB over the guest WiFi.
- **Don't switch WiFi off with `svc wifi disable`.** It's saved, so the frame stays offline after a reboot, and the kiosk has no way to turn it back on (swiping down doesn't open quick settings). Fix over USB: `settings put global wifi_on 1; svc wifi enable`, run after `sys.boot_completed=1`, because ADB comes up before Android's services.
- **A 401 on `identity.4.azurestaticapps.net/.auth/login/done`** means a sign-in was finished minutes after it started and its one-time code had expired. Start again from the planner (restart the kiosk). After choosing "Stay signed in", a fresh sign-in goes through Microsoft silently in about 7 s.
- After a sign-in or "Stay signed in", wait a minute or two before cutting the power. Gecko saves cookies in the background, and a power cut right after sign-in lost them once.
- **Run it from the wall adapter once it's online.** On laptop USB power (default USB current through the C-to-A adapter) it lost power and cold-rebooted as soon as WiFi was on and Microsoft sign-in was rendering. ADB over USB is fine for offline maintenance.
- **The clock resets to 1970 on every power-up** (no clock battery) and is only correct once network time arrives. Gecko caps cookie lifetimes at 400 days from *its* "now", so anything signed in before the clock synced got a 1971 expiry and was thrown away the moment the real date arrived. That's why every restart needed the full Microsoft sign-in with a second factor. The kiosk now shows "Waiting for the time…" and doesn't start Gecko until the clock is at least the build date (`ClockGate.java`, regression-tested). Verified 2026-11-05: power cut, then it waited for the time and went straight to `/display` with no sign-in.
- **Sign-in history (superseded 2026-11-06 by the display key):** the Microsoft-account approach needed the SWA session (8 h) plus Microsoft's "stay signed in" cookies, which the frame's Gecko kept only for the session. A power-off longer than 8 h meant a full sign-in with a second factor, and repeated sign-ins got the new account locked by Microsoft. That's why FamilyPlanner now pairs displays with a key (S34, D9).
- **Lint runs on every build** (`lintDebug`, `abortOnError`). The automatic sign-in first used `URLDecoder.decode(String, Charset)`, which needs Android 13, and it crashed on the frame. Unit tests on the laptop's Java can't catch that, but lint's NewApi check does. Build: `./gradlew lintDebug testDebugUnitTest assembleDebug`.
- **Pairing the frame:** on FamilyPlanner's Members page, under **Displays**, **Add a display** and copy the link (shown once). With the frame on USB: `adb shell am start -n com.homedash.kiosk/.KioskActivity -d '<link>'`, then `adb logcat -c`, because Gecko's own debug log echoes the URL. The page swaps the key for an HttpOnly cookie and opens `/display`. Revoke on the Members page if the frame is lost or replaced.
- **Dimming** (`DimPolicy.java`): no motion sensor, so activity means a touch or the room lights coming on. It dims to 5% after 5 idle minutes, or at once when the room goes dark (below 5 lux, light again above 15 lux). Calibrated 2026-11-05: main light on about 60 lux, low light about 10 lux (stays dim, which is wanted), lights off 2–4 lux. The sensor also picks up some of the screen's own glow, so readings drop a little when it dims. A touch on a dimmed screen only wakes it.
- If the display key is missing or revoked, `/display` says "This display isn't paired" (no sign-in page, no redirect loop).
- To join WiFi: `adb shell am start -a android.settings.WIFI_SETTINGS`, choose the network on the frame, then `adb shell input keyevent KEYCODE_HOME`. The password is typed on the frame and never passes through the laptop.
- The bootloader partition's FAT header claims more sectors than the 32 MB partition has (`fsck.fat` reports "Failed to read sector 262143"). This is a factory quirk, already present in the original.

### 3. Put it on the ISP guest WiFi (do not skip)

- Router: **the ISP's WiFi hub**. Locked-down ISP firmware: no VLANs, firewall rules or DNS filtering. It does have guest WiFi.
- The guest network keeps the frame away from the laptop and home devices. It cannot filter the frame's internet traffic.
- A clean OS, the read-only `display` login and guest WiFi together leave low remaining risk.

#### Done 2026-11-05

- Guest SSID `Guest-WiFi`, subnet `192.168.5.0/24` (home is `192.168.1.0/24`). The guest network also gets its own public IPv6 /64, separate from home.
- Isolation tested from the frame and the laptop, with controls:

| Test | Result |
|---|---|
| Frame → laptop, IPv4 (ping and HTTP to a temporary listener) | Blocked |
| Frame → home router `192.168.1.1` | Blocked |
| Frame → laptop, IPv6 ping | Blocked (IPv6 ping from the frame to the internet works, so the test is valid) |
| Laptop → frame, IPv4 and IPv6 ping | Blocked |
| Frame → planner (control) | Reachable |

- The frame's `nc` can't test an actual IPv6 connection, so IPv6 isolation rests on ICMP. The router clearly doesn't forward guest-to-home traffic.
- After joining, the clock synced by NTP and nothing listened on or connected to 7806 or 8086.
- Not done: turning off Android's captive-portal check (`settings put global captive_portal_detection_enabled 0`), which contacts Google on every connect.

### 4. Optional: GL.iNet travel router for verification and an enforced allowlist

Only needed for proof of what the frame contacts, or for blocking at the network level.

```
Internet ── ISP WiFi hub     ── (home WiFi: laptop, phones)
                  │ LAN port
                  └── GL.iNet router ── "IoT" WiFi ── photo frame
```

- Hardware: **GL.iNet Opal (GL-SFT1200), about £35**, or **Beryl AX (GL-MT3000), about £80**. Both run OpenWrt with AdGuard Home built in.
- Connect an Ethernet cable from an ISP hub LAN port to the GL.iNet WAN port. Nothing changes on the ISP hub.
- Configuration:
  1. **Block forwarding to the home subnet** (e.g. 192.168.1.0/24). Without this rule, the travel router's NAT lets the frame reach home devices.
  2. **Force DNS** through AdGuard Home (redirect port 53) and block port 853 (DNS-over-TLS).
  3. **Allowlist** only: the planner domain (`*.azurestaticapps.net` host or custom domain), Microsoft login domains (`login.live.com`, `login.microsoftonline.com` and their CDN domains), and NTP. Block everything else, by DNS and by a firewall set that dnsmasq fills from the allowed domain names (Azure IPs change, so a fixed IP list won't work).
  4. **Client isolation** on the IoT WiFi.
- The laptop can't reach the frame directly through the NAT. Maintain it over USB ADB.
- More smart-home devices can join the same IoT WiFi later.

For reference, the full VLAN design this approximates:

| From → To | Rule |
|---|---|
| IoT → Home | Block |
| Home → IoT | Allow (management) |
| IoT → router | DHCP and DNS only, no admin page |
| IoT → Internet | Allowlist only |
| IoT ↔ IoT | Block (client isolation) |

## FamilyPlanner integration

The dashboard is the FamilyPlanner site on Azure Static Web Apps, behind a Microsoft sign-in plus an active member record. A normal `member` can read and change everything, including chat (Anthropic spend, reads data about the children) and the members list, so the frame uses its own read-only `display` role instead.

- **Display key (FamilyPlanner S34, D9, 2026-11-06):** the frame is paired with its own 256-bit key, held in an HttpOnly `__Host-DisplayKey` cookie, renewed daily, and revoked on the Members page. It reads only `/api/display/*`, the wall page's data, as the read-only `display` role. No Microsoft account, nothing that expires or asks for a second factor. It replaces the earlier Microsoft account `frame-account@example.com`, which Microsoft locked after repeated sign-ins from the frame. Remove that member once the key works.
- **`display` role (done, FamilyPlanner `cc5411a`):** the API refuses it on every route except eight GETs the wall page needs (calendar, tasks, meal plan, chores, weather, settings, people, me). It has no writes, chat, shopping lists, recipes, export or members page. Only GET routes can be opened to it, and a new route stays closed to it by default. Regression tests are in `api/test/display.test.ts`. It doesn't appear in people pickers and can't be given a chore.
- **`/display` page (done):** clock, weather, today (events, meals, tasks due), the next six days, and this week's chores. It has no links or controls, refreshes every minute (calendar cache 5 min) and reloads nightly to pick up deploys. The display account is sent there from any address, so the kiosk's `START_URL` of `/` works as it is.
- **Answered:** an SWA sign-in lasts 8 hours, which is why the frame moved to a display key.
- Alternative considered and not chosen: a home-server proxy holding an integration token. It needs a data-reading token, which FamilyPlanner S31 currently forbids, and is harder to revoke than a single member.
- The display key is a narrower version of that idea: a token that reads only the wall page's data, held by the frame itself, revocable per display.

## Status

- [x] Frame purchased and arrived
- [x] ISP guest WiFi (`Guest-WiFi`) enabled, isolation tested from the frame and laptop
- [x] Step 1: inspected offline over ADB (see "Confirmed on our unit")
- [x] Step 1: full firmware backup, verified (stored outside Dropbox and git; see the backup notes below)
- [x] Step 2: kiosk app, vendor apps disabled or removed, `su` fixed, time zone, splash replaced
- [x] Signed in on the frame as the display account, dashboard showing (2026-11-05, on wall power)
- [x] Kiosk update installed: dimming (calibrated) and automatic sign-in after restarts
- [x] Sign-in survives a power cut (clock gate), verified 2026-11-05
- [x] ~~Microsoft "stay signed in" cookies not saved~~: replaced by the display key
- [x] Paired with a FamilyPlanner display key (S34) on 2026-11-06. Cookie `__Host-DisplayKey` expires 2027-12-11 and renews daily. Power cut verified: waited for the time, then straight to the dashboard with no sign-in
- [ ] Remove the `frame-account@example.com` member from FamilyPlanner (and the locked Microsoft account if you like)
- [ ] Display Microsoft account (`frame-account@example.com`) secured and invited with the **Display** role
- [x] `display` role and `/display` page in FamilyPlanner (`cc5411a`)
- [x] Step 3: on ISP guest WiFi, nothing listening apart from ADB on localhost
- [ ] Step 4 (optional): GL.iNet router, allowlist, traffic check
