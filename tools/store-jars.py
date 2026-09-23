#!/usr/bin/env python3
"""Перепаковывает jar-файлы без сжатия (ZIP_STORED), на месте.

    tools/store-jars.py <каталог с jar>

Зачем: пакет целиком жмётся xz, а уже сжатые zip он почти не берёт — 40 МБ jar
превращались в 36 МБ. Несжатые jar xz ужимает до 18 МБ, потому что видит
одинаковые куски классов во всех файлах сразу. Цена — место на диске после
установки: jar становятся ~99 МБ вместо ~40.

Содержимое записей не меняется, поэтому подписи jar (META-INF/*.SF) остаются верными.
"""
import sys
import zipfile
from pathlib import Path

for jar in sorted(Path(sys.argv[1]).glob("*.jar")):
    stored = jar.with_suffix(".stored")
    with zipfile.ZipFile(jar) as source, zipfile.ZipFile(stored, "w", zipfile.ZIP_STORED) as target:
        for info in source.infolist():
            copy = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            copy.compress_type = zipfile.ZIP_STORED
            copy.external_attr = info.external_attr
            copy.comment = info.comment
            target.writestr(copy, source.read(info))
    stored.replace(jar)
