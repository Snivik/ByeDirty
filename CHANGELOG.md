# Changelog

ByeDirty is in alpha. Every push to `main` that adds a new section here becomes a GitHub release:
CI builds a signed APK and publishes the top section as its release notes (see README → Releases).
Fix releases bump the patch (0.1.0 → 0.1.1); significant new features bump the minor (→ 0.2.0).
Newest first; written for people, not commits.

## 0.1.0 — 2026-10-14

**First release**
- Reads Hello Klean smart shower heads (model HKSSH001) over NFC. Tap Sync, or just hold your phone to the
  handle while the app is open. With the app closed, tapping the handle opens it.
- **Filter life:** percentage and litres left come straight from the shower head, so they match what the head
  itself tracks. After a new filter, hold the reset button on the head; ByeDirty picks it up on the next sync.
- **Days left and replacement date:** worked out from your average litres per shower and how many showers
  your household takes a week (set on the Filter tab). After a few days of syncing it switches to your real
  usage.
- **Reminder:** the bell sends a notification a few days before the filter is due.
- **Shower insights for the last 7 or 30 showers:** average shower length with its hot/cold split, time with
  hot water, time with cold water and your cold streak. They're calculated the same way as the official iOS
  app, so the numbers agree.
- **History:** totals and fun facts, a bar chart of your last 30 showers (tap one for details), a histogram of
  shower lengths and the water-pressure split. ByeDirty keeps every shower it has seen, beyond the 256 the head
  remembers.
- Read-only: ByeDirty never writes to the shower head, and nothing leaves your phone.
