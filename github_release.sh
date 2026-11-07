#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/app"

VERSION="${1:-}"
APK="${2:-}"

if [[ -z "$VERSION" || -z "$APK" ]]; then
  echo "Usage: ./github_release.sh V47 /sdcard/Download/SmartFreeze_V47.apk"
  exit 1
fi

if [[ ! -s "$APK" ]]; then
  echo "APK not found: $APK"
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI missing. Install it using: pkg install gh"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub login required. Run: gh auth login"
  exit 1
fi

if [[ "$(git config user.email || true)" == "YOUR_GITHUB_EMAIL" ]]; then
  echo "Set your real Git email first:"
  echo 'git config user.email "YOUR_GITHUB_EMAIL_ADDRESS"'
  exit 1
fi

echo "Verifying APK..."
if command -v apksigner >/dev/null 2>&1; then
  apksigner verify --verbose "$APK"
else
  echo "apksigner not found; APK signature verification skipped."
  exit 1
fi

TITLE="Smart Freeze $VERSION"
NOTES="RELEASE_NOTES_TEMPLATE.md"
TEMP_NOTES="$HOME/smartfreeze_release_notes.md"

sed "s/VVERSION/$VERSION/g" "$NOTES" > "$TEMP_NOTES"

echo
echo "Release notes file: $TEMP_NOTES"
echo "Edit it before publishing if needed."
read -r -p "Type PUBLISH to commit, push source and publish this release: " CONFIRM
[[ "$CONFIRM" == "PUBLISH" ]] || { echo "Cancelled. Nothing uploaded."; exit 0; }

git add -A
git commit -m "Smart Freeze $VERSION" || true
git push -u origin main

gh release create "$VERSION" "$APK" \
  --repo roz66r/SmartFreeze \
  --title "$TITLE" \
  --notes-file "$TEMP_NOTES"

echo "Published: https://github.com/roz66r/SmartFreeze/releases/tag/$VERSION"
