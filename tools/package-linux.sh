#!/usr/bin/env bash
# Собирает NearPing для Linux x64 — переносимый архив и .deb: jar-файлы с linux-нативами,
# урезанная под приложение Java (jlink из официального JDK Temurin с проверкой SHA-256)
# и запускающий скрипт.
#
# jlink запускается на маке, но берёт модули Linux-JDK: так можно, если версии
# совпадают до последней цифры. Поэтому нужен локальный JDK той же сборки.
# Собирается на macOS: jpackage тут не нужен. Результат — releases/<label>/.
#
#   tools/package-linux.sh test15
set -euo pipefail
cd "$(dirname "$0")/.."

label="${1:?укажи метку сборки, например test15}"
arch="x64"
jdk_version="21.0.12.1"
jdk_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
# Сверено 2026-09-23 по двум источникам: .sha256.txt релиза и api.adoptium.net.
jdk_sha="ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
# jdeps по всем jar даёт java.base, java.desktop, java.instrument, java.prefs, jdk.unsupported.
# jdk.crypto.ec в Java 21 держит X25519 и Ed25519 — jdeps его не видит, потому что
# алгоритм ищется по имени. jdk.localedata — русские названия месяцев и дней.
modules="java.base,java.desktop,java.instrument,java.prefs,jdk.unsupported,jdk.crypto.ec,jdk.localedata"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/nearping-build"
out="releases/$label"
name="NearPing-$label-linux-x86_64"
stage="$out/$name"

./gradlew :linux:portableLibs -PlinuxTarget="$arch" -q

host_jdk="${HOST_JDK:-$(/usr/libexec/java_home -v 21)}"
host_version="$("$host_jdk/bin/jlink" --version)"
if [ "$host_version" != "$jdk_version" ]; then
  echo "нужен локальный JDK $jdk_version для jlink, а найден $host_version ($host_jdk); укажи HOST_JDK" >&2
  exit 1
fi

mkdir -p "$cache"
jdk_tar="$cache/$(basename "$jdk_url")"
if [ ! -f "$jdk_tar" ] || ! echo "$jdk_sha  $jdk_tar" | shasum -a 256 -c --quiet; then
  curl -fsSL --retry 3 -o "$jdk_tar" "$jdk_url"
  echo "$jdk_sha  $jdk_tar" | shasum -a 256 -c --quiet   # чужой JDK в пакет не попадёт
fi
linux_jdk="$cache/jdk-$jdk_version-linux-x64"
if [ ! -d "$linux_jdk/jmods" ]; then
  rm -rf "$linux_jdk"; mkdir -p "$linux_jdk"
  tar -xzf "$jdk_tar" -C "$linux_jdk" --strip-components=1
fi

rm -rf "$stage"; mkdir -p "$stage/lib"
cp linux/build/portable/lib/*.jar "$stage/lib/"
python3 tools/store-jars.py "$stage/lib"   # xz жмёт несжатые jar вдвое лучше
# Без сжатия внутри: пакет целиком жмётся xz, и он справляется лучше.
"$host_jdk/bin/jlink" --module-path "$linux_jdk/jmods" --add-modules "$modules" \
  --include-locales=en,ru --strip-debug --no-header-files --no-man-pages \
  --output "$stage/jre"

cat > "$stage/nearping" <<'LAUNCHER'
#!/bin/sh
# Запуск NearPing из любого каталога: Java лежит рядом, системная не нужна.
dir="$(cd "$(dirname "$0")" && pwd)"
NEARPING_LAUNCHER="$dir/nearping" exec "$dir/jre/bin/java" -Xss2m -cp "$dir/lib/*" com.disspear574.nearping.linux.MainKt "$@"
LAUNCHER
chmod +x "$stage/nearping" "$stage/jre/bin/"*

cat > "$stage/README.txt" <<'TXT'
NearPing — тестовая сборка для Linux x86_64 (Ubuntu/Debian с рабочим столом).

Запуск:   ./nearping
Java включена, ставить ничего не нужно.

Для значка в панели и уведомлений желательно:
  sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-ayatanaappindicator3-0.1 libnotify-bin
Без них приложение работает окном; значка в панели и системных уведомлений не будет.

Данные: ~/.local/share/nearping (ключ личности, контакты, выбранная сеть).
TXT

# Версия пакета: 0.1.0~test16 — «~» ставит тестовую сборку ниже будущей 0.1.0.
deb="nearping_0.1.0~${label}_amd64.deb"
python3 tools/build-deb.py "$stage" "$out/$deb" "0.1.0~$label"

( cd "$out" && COPYFILE_DISABLE=1 tar -cJf "$name.tar.xz" "$name" && shasum -a 256 "$name.tar.xz" "$deb" > SHA256SUMS.txt )
rm -rf "$stage"
echo "готово: $out/$name.tar.xz, $out/$deb"
