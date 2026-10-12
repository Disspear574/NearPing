#!/usr/bin/env python3
"""Repacks jars uncompressed (ZIP_STORED) in place so the final xz sees duplicate class bytes across files.

    tools/store-jars.py <jar directory>

Entry contents are unchanged, so jar signatures (META-INF/*.SF) stay valid.
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
