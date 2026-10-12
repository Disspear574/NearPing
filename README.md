# NearPing

Call a colleague who is wearing headphones. Pick a person in the menu bar (macOS) or the tray
(Linux): they hear a sound and see a banner. An urgent call plays a siren straight into the
current audio device and shows a window above everything, so Focus does not silence it.

Local network only: no server, no accounts. A device's identity is its public key; peers find
each other over Bonjour / mDNS and talk over an end-to-end encrypted TCP connection
([protocol](docs/protocol.md)).

## Layout

```
core/     Kotlin Multiplatform: protocol, handshake, pairing, pause, call limits, Engine
macos/    SwiftUI menu bar app (XcodeGen project in macos/project.yml), macOS 26+, Apple Silicon
linux/    Compose Desktop app (JVM 21), tray and screen-edge helpers in GTK
tools/    Linux packages (.deb and portable .tar.xz), built on a Mac
```

## Build

```
make test            # core tests
make run             # macOS app (needs macos/Signing.xcconfig, see the .example)
./gradlew :linux:run # Linux app on the current machine
tools/package-linux.sh
```

Rules for code and git: [docs/CONVENTIONS.md](docs/CONVENTIONS.md), [docs/GIT.md](docs/GIT.md).
