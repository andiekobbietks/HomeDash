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

- Stored at `~/photoframe-backups/2026-11-04/` on the laptop, **deliberately outside this Dropbox folder and outside git**. The `private` partition probably holds the device credential. Copy the backup to an external drive. It has its own README with the partition layout.
- Contents: all named partitions plus both eMMC boot areas (each sha1-verified), and the full 15.76 GB eMMC as 59 verified 256 MiB chunks.
- **The frame's default `dd` (toolbox) wraps at 2 GiB because it uses 32-bit offsets.** Use `toybox dd` for anything past 2 GiB. The first full-disk attempt silently produced wrapped data because of this, which is why each chunk is now also size-checked.
- The USB link drops occasionally on long transfers. The chunked script retries automatically.

### 2. Clean it as far as the device allows

- **A GSI is ruled out** (Android 6, no Treble), and no LineageOS build exists for this board. So the route is **debloat over root ADB**.
- Order matters: install the browser and kiosk launcher → disable `com.efercro.os.aimor`, `com.yhk.qeota` and `com.DeviceTest` → deal with `su` → reboot → confirm nothing listens on 8086 and nothing connects on 7806.
- Install a current Chromium-based browser or WebView. The stock Android System WebView is likely years old, and the browser is the frame's main exposure.
- Install a kiosk app locked to the planner domain.
- Do not sign into a Google account or the AiMOR app on the frame. Upload no photos to vendor clouds.

### 3. Put it on the ISP guest WiFi (do not skip)

- Router: **the ISP's WiFi hub**. Locked-down ISP firmware: no VLANs, firewall rules or DNS filtering. It does have guest WiFi.
- The guest network keeps the frame away from the laptop and home devices. It cannot filter the frame's internet traffic.
- A clean OS, the read-only `display` login and guest WiFi together leave low remaining risk.

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

The dashboard is the FamilyPlanner site on Azure Static Web Apps, behind a Microsoft sign-in plus an active member record. Its roles are `admin` and `member`, and a member can read and change everything, including chat (Anthropic spend, reads data about the children) and the members list. A frame signed in as a normal member holds full family access.

- **Dedicated identity:** a Microsoft account used only by the display (e.g. `kitchen-display@example.com`), with a unique password and MFA codes on a phone. Invite it as a member, never an admin. Removing that member revokes the frame's access on its next request (FamilyPlanner S17) without affecting anyone else.
- **Planned `display` role:** GET-only access to dashboard data, with no writes, chat, export or members page. `req.md` §4 plans for adding a role in one place (`getCaller`). Add it with regression tests showing a display caller can't write, chat, export or list members.
- **Planned `/display` page:** a wall-friendly layout that refreshes itself.
- **Open question:** how long an SWA sign-in session lasts before it bounces to the Microsoft login. Test early. It decides whether the setup is livable.
- Alternative considered and not chosen: a home-server proxy holding an integration token. It needs a data-reading token, which FamilyPlanner S31 currently forbids, and is harder to revoke than a single member.

## Status

- [x] Frame purchased and arrived
- [ ] ISP guest WiFi enabled and isolation tested with a phone
- [x] Step 1: inspected offline over ADB (see "Confirmed on our unit")
- [x] Step 1: full firmware backup, verified (stored outside Dropbox and git; see the backup notes below)
- [ ] Step 2: debloat, browser, kiosk app, `su` fixed
- [ ] Display Microsoft account created and invited
- [ ] `display` role and `/display` page in FamilyPlanner
- [ ] Step 3: on ISP guest WiFi, port scan clean
- [ ] Step 4 (optional): GL.iNet router, allowlist, traffic check
