# Conventions

Rules for code in this repository. Each rule names the check that enforces it. Branches,
commits and pull requests are in [GIT.md](GIT.md); the wire format is in [protocol.md](protocol.md).

## Language

- Code, comments, logs, docs, commit messages and pull requests are in English.
- Russian lives only in localization (`macos/NearPing/ru.lproj/`, `linux/.../Strings.kt`) and in
  test data.

Check: `scripts/check-conventions.sh`.

## Comments

- A type gets one line saying what it is for: `/** App key-value storage. */`.
- Inside code, a comment is allowed only where the code would be misread without it:
  one line, saying why.
- Functions, properties and parameters are not documented. This is an app, not a library.
- No dates, bug stories or links to working notes. History goes into commits and pull requests.
- Target about 2 % of code lines; the check fails above 5 %.

Check: `scripts/comment-check.py`, called by `scripts/check-conventions.sh`.

## UI strings

- Every user-facing string exists in English and Russian: `Localizable.strings` on macOS,
  `Strings.kt` on Linux.
- A user-provided name in a row is limited to one or two lines and ellipsized.

Check: none; caught on review.

## Shared core

- Everything above the socket (protocol, trust, pairing, call logic) lives in `core/` and is
  shared by both apps. The UI is native on each platform: SwiftUI on macOS, Compose Desktop on
  Linux.
- A change to the wire format changes `docs/protocol.md` in the same pull request, and both
  platforms in the same release.

Check: `:core:jvmTest` in CI; the CryptoKit vectors test pins the byte format.

## Private data

Secrets, signing team ids, machine paths, personal emails and build output never reach git.
Signing lives in the ignored `macos/Signing.xcconfig`.

Check: `scripts/publish-check.py` (privacy workflow, tree and commits).
