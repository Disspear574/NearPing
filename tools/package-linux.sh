#!/usr/bin/env bash
# Builds NearPing for Linux x64 on macOS: a portable archive and a .deb into releases/<label>/.
# jlink runs on the Mac with the Linux JDK modules, so the local JDK must be the exact same build.
#   tools/package-linux.sh test15
set -euo pipefail
cd "$(dirname "$0")/.."

label="${1:?give a build label, for example test15, or release}"
version="$(sed -n 's/.*packageVersion = "\(.*\)".*/\1/p' linux/build.gradle.kts)"
arch="x64"
jdk_version="21.0.12.1"
jdk_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
jdk_sha="ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
# jdeps misses jdk.crypto.ec (X25519 and Ed25519 are looked up by name); jdk.localedata gives Russian month and day names.
modules="java.base,java.desktop,java.instrument,java.prefs,jdk.unsupported,jdk.crypto.ec,jdk.localedata"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/nearping-build"
out="releases/$label"
if [ "$label" = release ]; then
  name="NearPing-$version-linux-x86_64"
  deb_version="$version"
else
  name="NearPing-$label-linux-x86_64"
  # The "~" sorts a test build below the release of the same version.
  deb_version="$version~$label"
fi
stage="$out/$name"

./gradlew :linux:portableLibs -PlinuxTarget="$arch" -q

host_jdk="${HOST_JDK:-$(/usr/libexec/java_home -v 21)}"
host_version="$("$host_jdk/bin/jlink" --version)"
if [ "$host_version" != "$jdk_version" ]; then
  echo "local JDK $jdk_version is required for jlink but found $host_version ($host_jdk); set HOST_JDK" >&2
  exit 1
fi

mkdir -p "$cache"
jdk_tar="$cache/$(basename "$jdk_url")"
if [ ! -f "$jdk_tar" ] || ! echo "$jdk_sha  $jdk_tar" | shasum -a 256 -c --quiet; then
  curl -fsSL --retry 3 -o "$jdk_tar" "$jdk_url"
  echo "$jdk_sha  $jdk_tar" | shasum -a 256 -c --quiet   # a tampered JDK never reaches the package
fi
linux_jdk="$cache/jdk-$jdk_version-linux-x64"
if [ ! -d "$linux_jdk/jmods" ]; then
  rm -rf "$linux_jdk"; mkdir -p "$linux_jdk"
  tar -xzf "$jdk_tar" -C "$linux_jdk" --strip-components=1
fi

rm -rf "$stage"; mkdir -p "$stage/lib"
cp linux/build/portable/lib/*.jar "$stage/lib/"
python3 tools/store-jars.py "$stage/lib"   # xz packs uncompressed jars twice as well
"$host_jdk/bin/jlink" --module-path "$linux_jdk/jmods" --add-modules "$modules" \
  --include-locales=en,ru --strip-debug --no-header-files --no-man-pages \
  --output "$stage/jre"

cat > "$stage/nearping" <<'LAUNCHER'
#!/bin/sh
dir="$(cd "$(dirname "$0")" && pwd)"
NEARPING_LAUNCHER="$dir/nearping" exec "$dir/jre/bin/java" -Xss2m -cp "$dir/lib/*" com.disspear574.nearping.linux.MainKt "$@"
LAUNCHER
chmod +x "$stage/nearping" "$stage/jre/bin/"*

cat > "$stage/README.txt" <<'TXT'
NearPing test build for Linux x86_64 (Ubuntu/Debian desktop).

Run:   ./nearping
Java is included; nothing to install.

For the panel icon and notifications:
  sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-ayatanaappindicator3-0.1 libnotify-bin
Without them the app runs as a window only: no panel icon, no system notifications.

Data: ~/.local/share/nearping (identity key, contacts, selected network).
TXT

deb="nearping_${deb_version}_amd64.deb"
python3 tools/build-deb.py "$stage" "$out/$deb" "$deb_version"

( cd "$out" && COPYFILE_DISABLE=1 tar -cJf "$name.tar.xz" "$name" && shasum -a 256 "$name.tar.xz" "$deb" > SHA256SUMS.txt )
rm -rf "$stage"
echo "done: $out/$name.tar.xz, $out/$deb"
