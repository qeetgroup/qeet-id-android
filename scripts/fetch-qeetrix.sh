#!/usr/bin/env bash
# Fetches Qeetrix for Android at the release this app is pinned to, into .qeetrix/qeetrix-android, where the Gradle
# composite build (settings.gradle.kts) picks it up. The repository is public, so no token is needed; the clone is
# checked against the pinned commit, so a moved tag cannot change what the app is built with.
#
#   scripts/fetch-qeetrix.sh            # once after cloning, and again whenever the pin in gradle.properties changes
#
# To build against another checkout instead (working on the library beside the app), set qeetrix.dir in
# local.properties; this script is then not needed.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
prop() { sed -n "s/^$1=//p" "$root/gradle.properties"; }
tag="$(prop qeetrix.tag)"
commit="$(prop qeetrix.commit)"
repo="${QEETRIX_REPO:-https://github.com/qeetgroup/qeetrix-android.git}"
dest="$root/.qeetrix/qeetrix-android"

if [ -z "$tag" ] || [ -z "$commit" ]; then
    echo "gradle.properties must set qeetrix.tag and qeetrix.commit" >&2
    exit 1
fi

# The included build finds the Android SDK as this one does: ANDROID_HOME, or sdk.dir in its own local.properties.
share_sdk_dir() {
    if [ -f "$root/local.properties" ] && grep -q '^sdk.dir=' "$root/local.properties"; then
        grep '^sdk.dir=' "$root/local.properties" > "$dest/local.properties"
    fi
}

if [ -d "$dest/.git" ] && [ "$(git -C "$dest" rev-parse HEAD)" = "$commit" ]; then
    share_sdk_dir
    echo "Qeetrix for Android $tag is already at $dest"
    exit 0
fi

rm -rf "$dest"
mkdir -p "$(dirname "$dest")"
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$tag" "$repo" "$dest"

actual="$(git -C "$dest" rev-parse HEAD)"
if [ "$actual" != "$commit" ]; then
    echo "Qeetrix for Android $tag is $actual, not the pinned $commit. Refusing to build with it." >&2
    rm -rf "$dest"
    exit 1
fi
share_sdk_dir
echo "Qeetrix for Android $tag ($commit) is at $dest"
