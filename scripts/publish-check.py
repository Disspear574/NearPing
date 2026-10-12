#!/usr/bin/env python3
"""Keeps secrets, machine details and build output out of git.

--staged           check what is staged for the next commit (added lines and added files)
--tree             check every tracked file, before a repository is made public
--range REV...     check every commit `git rev-list REV...` gives: added lines and files,
                   the message, and the author and committer emails (pre-push and CI)
Exit 1 on any finding, 2 on a usage or git error. A line ending in `publish-check: allow` is
skipped.
"""
import os
import re
import subprocess
import sys

FORBIDDEN_FILES = re.compile(
    r"(\.(jks|keystore|p12|p8|pem|cer|mobileprovision|provisionprofile|apk|aab|ipa|xcarchive)$"
    r"|(^|/)(GoogleService-Info\.plist|google-services\.json|local\.properties|secrets\.properties)$"
    r"|(^|/)(?![^/]*\.example$)[^/]*\.local\.[^/]+$"
    r"|(^|/)(build|DerivedData|\.gradle|\.kotlin)/"
    r"|(^|/)(reports|results)/.*\.(html|txt|xml)$)"
)
PERSONAL_EMAIL = re.compile(r"\b[\w.+-]+@(gmail|yandex|ya|mail|icloud|me|outlook|hotmail)\.[a-z]{2,}\b", re.I)
FORBIDDEN_TEXT = [
    ("machine path", re.compile(r"/Users/[A-Za-z0-9._-]+/|/home/[A-Za-z0-9._-]+/|~/develop\b|C:\\\\Users\\\\")),
    ("private key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("Google API key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("GitHub token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b")),
    ("API secret key", re.compile(r"\bsk-(ant-|proj-)?[A-Za-z0-9_-]{20,}\b")),
    ("Telegram bot token", re.compile(r"\b\d{8,10}:[A-Za-z0-9_-]{30,50}\b")),
    ("AWS access key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("signing team", re.compile(r"DEVELOPMENT_?TEAM[\"'=:\s]+[A-Z0-9]{10}\b", re.I)),
    ("device identifier", re.compile(r"\b0000[0-9]{4}-[0-9A-F]{16}\b")),
    ("personal email", PERSONAL_EMAIL),
    ("AI attribution", re.compile(r"Co-Authored-By:.*(Claude|anthropic)|Generated with \[?Claude|claude\.ai/code", re.I)),
]
ALLOW = "publish-check: allow"
SELF = "scripts/publish-check.py"
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)")


class GitError(Exception):
    pass


def git(*args, check=False):
    result = subprocess.run(["git", *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        raise GitError(f"git {' '.join(args)}: {result.stderr.strip()}")
    return result.stdout


def deny_files():
    # A worktree has no copy of the ignored deny list; the main checkout keeps it.
    paths = ["config/publish-deny.local.txt", os.path.expanduser("~/.config/publish-check/deny.txt")]
    common = git("rev-parse", "--path-format=absolute", "--git-common-dir").strip()
    if common:
        paths.append(os.path.join(os.path.dirname(common), "config/publish-deny.local.txt"))
    seen, out = set(), []
    for path in paths:
        real = os.path.realpath(path)
        if real not in seen and os.path.isfile(real):
            seen.add(real)
            out.append(real)
    return out


def deny_words():
    words = []
    for path in deny_files():
        words += [w.strip() for w in open(path, encoding="utf-8") if w.strip() and not w.startswith("#")]
    return words


def scan_line(text, words):
    if text.rstrip().endswith(ALLOW):
        return []
    found = [name for name, pattern in FORBIDDEN_TEXT if pattern.search(text)]
    found += [f"denied word '{w}'" for w in words if w.lower() in text.lower()]
    return found


def forbidden_names(names, prefix=""):
    return [f"{prefix}{n}: file type that does not belong in git" for n in names.split("\n")
            if n and FORBIDDEN_FILES.search(n)]


def added_lines(diff, words, prefix="", quote=True):
    problems = []
    current, number = None, 0
    for line in diff.split("\n"):
        hunk = HUNK.match(line)
        if hunk:
            number = int(hunk.group(1))
        elif line.startswith("+++ "):
            current = line[6:] if line.startswith("+++ b/") else None
        elif line.startswith("+") and current and current != SELF:
            for what in scan_line(line[1:], words):
                shown = f": {line[1:].strip()[:80]}" if quote else ""
                problems.append(f"{prefix}{current}:{number}: {what}{shown}")
            number += 1
    return problems


def staged(words):
    names = git("diff", "--cached", "--name-only", "--diff-filter=AR")
    diff = git("diff", "--cached", "--unified=0", "--no-color", "--no-ext-diff", "--diff-filter=ACMR")
    return forbidden_names(names) + added_lines(diff, words)


def tree(words):
    problems = []
    for name in git("ls-files").split("\n"):
        if not name or name == SELF:
            continue
        if FORBIDDEN_FILES.search(name):
            problems.append(f"{name}: file type that does not belong in git")
            continue
        try:
            lines = open(name, encoding="utf-8").read().split("\n")
        except (UnicodeDecodeError, FileNotFoundError, IsADirectoryError):
            continue
        for n, text in enumerate(lines, 1):
            for what in scan_line(text, words):
                problems.append(f"{name}:{n}: {what}")
    return problems


def masked(email):
    user, _, domain = email.partition("@")
    return f"{user[:2]}***@{domain}"


def history(revs, words):
    problems = []
    show = ["show", "--diff-merges=first-parent", "--no-color", "--no-ext-diff"]
    for sha in git("rev-list", *revs, check=True).split():
        prefix = f"commit {sha[:10]}: "
        author, committer, message = git("show", "-s", "--format=%ae%x00%ce%x00%B", sha, check=True).split("\0", 2)
        for role, email in (("author", author), ("committer", committer)):
            if PERSONAL_EMAIL.search(email):
                problems.append(f"{prefix}{role} email is personal ({masked(email)}); use the noreply address")
        for n, text in enumerate(message.split("\n"), 1):
            problems += [f"{prefix}message line {n}: {what}" for what in scan_line(text, words)]
        problems += forbidden_names(git(*show, "--format=", "--name-only", "--diff-filter=AR", sha, check=True), prefix)
        diff = git(*show, "--format=", "--unified=0", "--diff-filter=ACMR", sha, check=True)
        problems += added_lines(diff, words, prefix, quote=False)
    return problems


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--staged"
    words = deny_words()
    try:
        if mode == "--staged":
            problems = staged(words)
        elif mode == "--tree":
            problems = tree(words)
        elif mode == "--range" and len(sys.argv) > 2:
            problems = history(sys.argv[2:], words)
        else:
            print(__doc__.strip(), file=sys.stderr)
            sys.exit(2)
    except GitError as e:
        print(f"✗ publish-check could not read the commits: {e}", file=sys.stderr)
        sys.exit(2)
    for p in problems:
        print(f"✗ {p}")
    if problems:
        print("\n  Secrets, machine paths, personal data and build output stay out of git.")
        print(f"  A deliberate exception ends the line with `{ALLOW}`.")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
