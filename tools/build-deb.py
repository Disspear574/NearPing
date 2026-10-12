#!/usr/bin/env python3
"""Builds the NearPing .deb from a portable stage directory, without dpkg-deb or Linux.

    tools/build-deb.py <stage> <out.deb> <version>

Layout: /opt/nearping (JRE, jars, launcher), /usr/bin/nearping, the .desktop entry and the icon.
Every parent directory is declared in data.tar before its files, or dpkg fails to create them.
Everything is root:root with normalised modes; xz compresses Java much better than gzip.
"""
import hashlib
import io
import os
import sys
import tarfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "linux/src/main/resources/nearping.png"
MAIN_CLASS = "com.disspear574.nearping.linux.MainKt"

DESKTOP = f"""[Desktop Entry]
Type=Application
Name=NearPing
Comment=Call a colleague who is wearing headphones
Comment[ru]=Позвать коллегу в наушниках
Exec=nearping
Icon=nearping
Terminal=false
Categories=Utility;Network;
StartupWMClass={MAIN_CLASS.replace('.', '-')}
"""

BIN = """#!/bin/sh
exec /opt/nearping/nearping "$@"
"""

# Libraries the AWT/Skia window needs; libasound2 is renamed to t64 in Ubuntu 24.04.
DEPENDS = "libc6, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1, libasound2t64 | libasound2"
RECOMMENDS = "python3-gi, gir1.2-gtk-3.0, gir1.2-ayatanaappindicator3-0.1, libnotify-bin"


def normalized(info: tarfile.TarInfo) -> tarfile.TarInfo:
    info.uid = info.gid = 0
    info.uname = info.gname = "root"
    if info.isdir() or (info.isfile() and info.mode & 0o111):
        info.mode = 0o755
    elif info.isfile():
        info.mode = 0o644
    return info


class DataTar:
    def __init__(self) -> None:
        self.buffer = io.BytesIO()
        self.tar = tarfile.open(fileobj=self.buffer, mode="w:xz", format=tarfile.GNU_FORMAT, preset=9)
        self.dirs: set[str] = set()
        self.md5: list[str] = []
        self.size = 0
        self.mtime = int(time.time())

    def directory(self, path: str) -> None:
        parts = path.strip("/").split("/")
        for i in range(1, len(parts) + 1):
            name = "./" + "/".join(parts[:i])
            if name in self.dirs:
                continue
            info = tarfile.TarInfo(name)
            info.type = tarfile.DIRTYPE
            info.mtime = self.mtime
            self.tar.addfile(normalized(info))
            self.dirs.add(name)

    def data(self, path: str, content: bytes, executable: bool = False) -> None:
        self.directory(os.path.dirname(path))
        info = tarfile.TarInfo("." + path)
        info.size = len(content)
        info.mode = 0o755 if executable else 0o644
        info.mtime = self.mtime
        self.tar.addfile(normalized(info), io.BytesIO(content))
        self.md5.append(f"{hashlib.md5(content).hexdigest()}  {path.lstrip('/')}")
        self.size += len(content)

    def tree(self, source: Path, target: str) -> None:
        self.directory(target)
        for dirpath, dirnames, filenames in os.walk(source):
            dirnames.sort()
            rel = Path(dirpath).relative_to(source)
            base = target if str(rel) == "." else f"{target}/{rel.as_posix()}"
            self.directory(base)
            for name in sorted(filenames):
                file = Path(dirpath) / name
                if file.is_symlink():
                    info = tarfile.TarInfo(f".{base}/{name}")
                    info.type = tarfile.SYMTYPE
                    info.linkname = os.readlink(file)
                    info.mtime = self.mtime
                    self.tar.addfile(normalized(info))
                else:
                    self.data(f"{base}/{name}", file.read_bytes(), os.access(file, os.X_OK))
            for name in dirnames:
                if (Path(dirpath) / name).is_symlink():
                    raise SystemExit(f"directory symlink not supported: {dirpath}/{name}")

    def close(self) -> bytes:
        self.tar.close()
        return self.buffer.getvalue()


def control_tar(control: str, md5sums: str) -> bytes:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:xz", format=tarfile.GNU_FORMAT) as tar:
        root = tarfile.TarInfo(".")
        root.type = tarfile.DIRTYPE
        tar.addfile(normalized(root))
        for name, text in (("control", control), ("md5sums", md5sums)):
            body = text.encode()
            info = tarfile.TarInfo(f"./{name}")
            info.size = len(body)
            tar.addfile(normalized(info), io.BytesIO(body))
    return buffer.getvalue()


def ar(members: list[tuple[str, bytes]]) -> bytes:
    out = bytearray(b"!<arch>\n")
    now = int(time.time())
    for name, body in members:
        header = f"{name:<16}{now:<12}{0:<6}{0:<6}{'100644':<8}{len(body):<10}`\n"
        assert len(header) == 60
        out += header.encode() + body
        if len(body) % 2:
            out += b"\n"
    return bytes(out)


def main() -> None:
    stage, out, version = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    if not (stage / "nearping").is_file() or not (stage / "jre/bin/java").is_file():
        raise SystemExit(f"{stage}: launcher or JRE missing")

    data = DataTar()
    data.data("/opt/nearping/nearping", (stage / "nearping").read_bytes(), executable=True)
    data.tree(stage / "lib", "/opt/nearping/lib")
    data.tree(stage / "jre", "/opt/nearping/jre")
    data.data("/usr/bin/nearping", BIN.encode(), executable=True)
    data.data("/usr/share/applications/nearping.desktop", DESKTOP.encode())
    data.data("/usr/share/icons/hicolor/256x256/apps/nearping.png", ICON.read_bytes())
    data_bytes = data.close()

    control = (
        "Package: nearping\n"
        f"Version: {version}\n"
        "Architecture: amd64\n"
        "Maintainer: disspear574 <77851401+Disspear574@users.noreply.github.com>\n"
        f"Installed-Size: {data.size // 1024 + 1}\n"
        f"Depends: {DEPENDS}\n"
        f"Recommends: {RECOMMENDS}\n"
        "Section: net\n"
        "Priority: optional\n"
        "Description: Call a colleague who is wearing headphones\n"
        " NearPing sends a short ping over the local network to a trusted\n"
        " colleague. An urgent ping plays a loud sound and a banner on top.\n"
    )
    out.write_bytes(ar([
        ("debian-binary", b"2.0\n"),
        ("control.tar.xz", control_tar(control, "\n".join(data.md5) + "\n")),
        ("data.tar.xz", data_bytes),
    ]))
    print(f"done: {out}")


if __name__ == "__main__":
    main()
