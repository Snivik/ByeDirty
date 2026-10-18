# ByeDirty: Google Play brief

Everything the Play Console asks for, in roughly the order it asks. Files are in this folder.

## App identity

| Field | Value |
|---|---|
| App name (max 30) | `ByeDirty: Shower Filter` |
| Package name | `dev.snivik.byedirty` (permanent once uploaded) |
| Default language | English (United Kingdom), `en-GB` |
| App or game | App |
| Free or paid | Free (can't be changed to paid later) |
| Category | House & Home |
| Tags (pick up to 5) | Home maintenance, Water, Utilities |
| Contact email | `main.snivik@gmail.com` (your public git email; change if you prefer) |
| Website | https://github.com/Snivik/ByeDirty |
| Privacy policy URL | https://github.com/Snivik/ByeDirty/blob/main/PRIVACY.md |

## Release

| Field | Value |
|---|---|
| App bundle | [`ByeDirty-0.1.0.aab`](ByeDirty-0.1.0.aab), also at `app/build/outputs/bundle/release/app-release.aab` |
| Version name / code | `0.1.0` / `3` (code = commit count; each upload must be higher) |
| Min / target SDK | 26 (Android 8.0) / 37 |
| Upload key | `~/.android/byedirty/release.jks`, alias `byedirty`, password in 1Password (`byedirty-release-key`) |
| Upload certificate SHA-256 | `B8:FC:19:15:19:DA:02:ED:12:CB:E9:DC:E0:2C:A0:C4:B1:E5:0A:54:58:6A:C3:A3:8B:F0:8D:B8:1D:AD:B1:E4` |
| Play App Signing | Accept "Let Google manage and protect your app signing key". Google signs what users install; our key is only the upload key, which Google can reset if it's lost. |
| Rebuild | `Tools/release-local.sh bundle` |

The sideloaded GitHub APKs are signed with our key, but Play installs are signed with Google's. So the two
can't update each other: someone switching to Play uninstalls the GitHub version first, and loses their history.

### Release notes (max 500)

```
First release.
• Filter life, litres left and a replacement date, read from your shower head over NFC
• Reminder before the filter is due
• Shower insights for your last 7 or 30 showers: length, hot and cold water, cold streak
• History charts and fun facts
• Read-only, no account, no internet
```

## Store listing

### Short description (max 80)

```
Filter life, reminders and shower insights for NFC smart shower heads.
```

### Full description (max 4000)

```
ByeDirty reads your smart shower head over NFC and tells you when to change the filter, plus how your showers are going.

HOW IT WORKS
Open ByeDirty, tap Sync and hold the back of your phone against the shower head's handle. A second later you have the latest from the shower head itself. With the app closed, just tap the handle to open it.

FILTER LIFE
• Percentage and litres left, exactly as the shower head counts them
• An estimated replacement date based on your average shower and how many showers your household takes a week
• A reminder a few days before the filter is due
• Fitted a new filter? Hold the reset button on the shower head as usual and ByeDirty picks it up on the next sync

SHOWER INSIGHTS
Switch between your last 7 and last 30 showers:
• Average shower length, and how much of it was hot or cold water
• Time spent with hot water (above 42 °C) and cold water (below 25 °C)
• Your cold-finish streak

HISTORY
• Total showers, water used and time spent showering
• A chart of your last 30 showers; tap one for its details
• How long your showers usually run, and your water-pressure split
• ByeDirty keeps every shower it has seen, beyond the last 256 the shower head remembers

PRIVATE BY DESIGN
No account, no ads, no analytics and no internet access. ByeDirty only reads the shower head and never writes to it. Everything stays on your phone.

COMPATIBILITY
Works with Hello Klean smart shower heads (model HKSSH001) that have the NFC tag in the handle. Needs a phone with NFC.

ByeDirty is an independent, open-source app (github.com/Snivik/ByeDirty). It isn't made, endorsed or supported by Hello Klean. "Hello Klean" is a trademark of its owner and is only used to say which shower heads ByeDirty works with.
```

### Graphics

| Asset | File | Spec |
|---|---|---|
| App icon | [`icon-512.png`](icon-512.png) | 512×512, 32-bit PNG. Square on purpose: Play rounds the corners itself. |
| Feature graphic | [`feature-graphic.png`](feature-graphic.png) | 1024×500, no transparency |
| Phone screenshots | [`screenshots/`](screenshots/) `01`–`06` | 1080×1920 (9:16), upload in this order |

Tablet screenshots aren't needed unless you want the "designed for tablets" badge. The screenshots use made-up
sample data (60-odd showers, a filter at 62%), not a real household's.

Keep "Hello Klean" out of the app name, short description and graphics. Naming another company's brand there
is what usually triggers Play's impersonation and trademark reviews. The compatibility line and disclaimer in the
full description are the safe place for it.

## App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No, the app doesn't contain ads |
| App access | All functionality is available without special access. Add a note: "No login. Reading data needs a compatible NFC shower head; without one the app shows an empty state explaining how to sync." |
| Content rating (IARC) | Category "All other app types". Answer No to every question → expect Everyone / PEGI 3 |
| Target audience | 18 and over (simplest: avoids the Families policy) |
| News app | No |
| Data safety | **No data collected, no data shared.** Data is processed only on the device and never leaves it, which Play doesn't count as collection. Encryption in transit: not applicable. Deletion: users clear app storage or uninstall. |
| Government app | No |
| Financial features | None |
| Health apps | None of the listed health features |
| Advertising ID | No (the app doesn't use it; no ad SDKs) |

### Permissions the app declares

| Permission | Why |
|---|---|
| `android.permission.NFC` | Read the shower head's tag |
| `android.permission.POST_NOTIFICATIONS` | Filter reminder (asked at runtime, only when the user turns on the bell) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Re-schedule the reminder after a restart |

None need a Play permissions declaration. The manifest marks NFC as required, so Play hides the app from
phones without it.

## Before production: closed testing

New personal developer accounts must run a **closed test with at least 12 testers opted in for 14 days in a
row** before Play lets you apply for production. Plan:

1. Create a Closed testing track, upload this AAB, add testers by email or a Google Group.
2. Share the opt-in link with Android owners of these shower heads.
3. After 14 days, apply for production access from the Dashboard.
