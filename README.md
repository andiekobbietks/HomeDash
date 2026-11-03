# HomeDash

A cheap Android photo frame turned into a wall display for the family dashboard ([FamilyPlanner](../FamilyPlanner)), set up so it can't harm the home network.

## The device

- **Humblestead 10.1" Digital Picture Frame** ([Amazon](https://www.amazon.co.uk/dp/B0GGB7LWX8)), 1280×800 IPS touchscreen, 32GB, WiFi, ships with the "AiMOR" photo-sharing app.
- The listing gives no chipset or Android version. It is probably a Rockchip or Allwinner board on old Android with no security updates.
- **Treat it as untrusted for its whole life**, even after cleaning.

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
- **Back up the stock firmware before changing anything** (Rockchip: `rkdeveloptool`; Allwinner: PhoenixSuit or `dd` with root). Keep backups out of git (see `.gitignore`).

### 2. Clean it as far as the device allows

- **Preferred: Generic System Image (GSI).** Needs Treble support and an unlockable bootloader. It replaces the system partition with a standard Android build. The vendor partition (drivers, firmware) stays the manufacturer's code, so the result is "much cleaner", not "verified clean".
- **Fallback: debloat over ADB.** Without rooting, remove the vendor apps with `adb shell pm uninstall --user 0 <package>`.
- No LineageOS build is expected to exist for this board.
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

- [x] Frame purchased
- [ ] Step 1: inspect offline (`getprop`), back up firmware
- [ ] Step 2: GSI or debloat, browser, kiosk app
- [ ] Display Microsoft account created and invited
- [ ] `display` role and `/display` page in FamilyPlanner
- [ ] Step 3: on ISP guest WiFi, port scan clean
- [ ] Step 4 (optional): GL.iNet router, allowlist, traffic check
