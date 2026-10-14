#!/usr/bin/env bash
# Builds a signed release APK locally, the same way CI does, taking the keystore password from
# 1Password (see Tools/signing.env) instead of a file on disk.
#
#   Tools/release-local.sh            -> app/build/outputs/apk/release/app-release.apk
#
# Needs the 1Password CLI (`op`) with app integration on, a "byedirty-release-key" item holding the
# keystore password in vault $BYEDIRTY_OP_VAULT (default "Personal"), and the keystore at
# ~/.android/byedirty/release.jks (or BYEDIRTY_KEYSTORE pointing elsewhere).
#
#   BYEDIRTY_OP_VAULT=Work Tools/release-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."

export BYEDIRTY_KEYSTORE="${BYEDIRTY_KEYSTORE:-$HOME/.android/byedirty/release.jks}"
export BYEDIRTY_OP_VAULT="${BYEDIRTY_OP_VAULT:-Personal}"
[ -f "$BYEDIRTY_KEYSTORE" ] || { echo "No keystore at $BYEDIRTY_KEYSTORE" >&2; exit 1; }
v="$(Tools/changelog.sh version)"

# No configuration cache and no daemon, so the password never lands in .gradle/ or lingers in memory.
op run --env-file=Tools/signing.env -- \
  ./gradlew --no-daemon --no-configuration-cache :app:assembleRelease \
    -PversionName="$v" -PversionCode="$(git rev-list --count HEAD)"
