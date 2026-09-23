#!/usr/bin/env bash
# Собирает NearPing для Linux x64 — переносимый архив и .deb: jar-файлы с linux-нативами,
# официальный JRE Temurin с проверкой SHA-256 и запускающий скрипт.
# Собирается на macOS: jpackage тут не нужен. Результат — releases/<label>/.
#
#   tools/package-linux.sh test15
set -euo pipefail
cd "$(dirname "$0")/.."

label="${1:?укажи метку сборки, например test15}"
arch="x64"
jre_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz"
jre_sha="2413149700df0f7d440500a84a8f764c535f21e5a5e87d38328b64eec2c5b500"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/nearping-build"
out="releases/$label"
name="NearPing-$label-linux-x86_64"
stage="$out/$name"

./gradlew :linux:portableLibs -PlinuxTarget="$arch" -q

mkdir -p "$cache"
jre_tar="$cache/$(basename "$jre_url")"
if [ ! -f "$jre_tar" ] || ! echo "$jre_sha  $jre_tar" | shasum -a 256 -c --quiet; then
  curl -fL --retry 3 -o "$jre_tar" "$jre_url"
  echo "$jre_sha  $jre_tar" | shasum -a 256 -c --quiet   # чужой JRE в архив не попадёт
fi

rm -rf "$stage"; mkdir -p "$stage/lib" "$stage/jre"
cp linux/build/portable/lib/*.jar "$stage/lib/"
tar -xzf "$jre_tar" -C "$stage/jre" --strip-components=1

cat > "$stage/nearping" <<'LAUNCHER'
#!/bin/sh
# Запуск NearPing из любого каталога: Java лежит рядом, системная не нужна.
dir="$(cd "$(dirname "$0")" && pwd)"
exec "$dir/jre/bin/java" -Xss2m -cp "$dir/lib/*" com.disspear574.nearping.linux.MainKt "$@"
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

( cd "$out" && tar -czf "$name.tar.gz" "$name" && shasum -a 256 "$name.tar.gz" "$deb" > SHA256SUMS.txt )
rm -rf "$stage"
echo "готово: $out/$name.tar.gz, $out/$deb"
