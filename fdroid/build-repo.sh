#!/usr/bin/env bash
# Builds Exhale's F-Droid repository from the APKs on the GitHub releases page.
#
#   fdroid/build-repo.sh <out-dir>        -> <out-dir>/repo
#
# Needs fdroidserver and gh on the PATH, plus:
#   GH_TOKEN                any token that can read the releases
#   FDROID_KEYSTORE_BASE64  base64 of the index signing keystore (PKCS12)
#   FDROID_KEYSTORE_PASS    its password
#   FDROID_KEEP             how many releases to carry (default 3)
set -euo pipefail

OUT=$(realpath -m "$1")
KEEP=${FDROID_KEEP:-3}
REPO=${GITHUB_REPOSITORY:-ozyern/exhale}
APP_ID=com.ozyern.exhale

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(dirname "$HERE")
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

cp "$HERE/config.yml" "$WORK/"
cp -r "$HERE/metadata" "$WORK/"
mkdir -p "$WORK/metadata/$APP_ID" "$WORK/repo/icons"
cp -r "$ROOT/fastlane/metadata/android/." "$WORK/metadata/$APP_ID/"
cp "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$WORK/repo/icons/icon.png"

echo "$FDROID_KEYSTORE_BASE64" | base64 -d > "$WORK/keystore.p12"
chmod 600 "$WORK/config.yml" "$WORK/keystore.p12"

# Newest releases first. A release without an APK (one that failed half-way) is
# skipped rather than failing the whole site deploy.
gh release list --repo "$REPO" --exclude-drafts --exclude-pre-releases \
    --limit "$KEEP" --json tagName -q '.[].tagName' |
while read -r tag; do
  gh release download "$tag" --repo "$REPO" --pattern 'Exhale-*.apk' --dir "$WORK/repo" ||
    echo "::warning::$tag has no APK, skipped"
done

(cd "$WORK" && fdroid update --pretty --use-date-from-apk)

mkdir -p "$OUT"
rm -rf "$OUT/repo"
cp -r "$WORK/repo" "$OUT/repo"

FINGERPRINT=$(cd "$WORK" && keytool -list -v -keystore keystore.p12 -alias exhale-fdroid \
    -storepass:env FDROID_KEYSTORE_PASS | grep -oP 'SHA256:\s*\K[0-9A-F:]+' | tr -d ':')
echo "Add-repo link: https://exhale.ozyern.me/fdroid/repo?fingerprint=$FINGERPRINT"
