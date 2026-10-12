#!/usr/bin/env python3
"""Comment rules for Kotlin and Swift sources. Prints one line per violation, exits 1 if any."""
import os
import re
import sys

TYPE_KEYWORDS = r"(class|interface|object|struct|protocol|enum|actor)\s"
MEMBER_KEYWORDS = r"(fun|func|init|val|var|let|case|subscript|typealias|extension)\b"
DIRECTIVE = re.compile(r"^//\s*(swift-tools-version|MARK:|swiftlint:|region\b|endregion\b|noinspection|ktlint|@formatter)")
PROCESS_TRACE = re.compile(r"\b20\d\d-\d\d-\d\d\b|\.claude/|CLAUDE\.md|\bISSUES\b|\bDECISIONS\b|\b[Tt]he (user|owner)\b")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")
MODIFIERS = re.compile(
    r"^(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:public|private|internal|protected|open|final|abstract|sealed|data|inner|value"
    r"|annotation|enum|expect|actual|indirect|nonisolated|fileprivate|package|static|override|lateinit|const"
    r"|mutating|async|suspend|inline|operator|infix|tailrec|external|weak|lazy|required|convenience|dynamic)\s+)*"
)


def spans(src, swift):
    """(start, end) of every comment, skipping string literals and interpolation."""
    out, i, n = [], 0, len(src)
    stack = [["code", None, None, 0]]
    while i < n:
        top, c = stack[-1], src[i]
        if top[0] == "code":
            if src.startswith("//", i):
                j = src.find("\n", i)
                j = n if j < 0 else j
                out.append((i, j))
                i = j
            elif src.startswith("/*", i):
                d, k = 1, i + 2
                while k < n and d:
                    if src.startswith("/*", k):
                        d, k = d + 1, k + 2
                    elif src.startswith("*/", k):
                        d, k = d - 1, k + 2
                    else:
                        k += 1
                out.append((i, k))
                i = k
            elif swift and c == "#":
                j = i
                while j < n and src[j] == "#":
                    j += 1
                if j < n and src[j] == '"':
                    triple = src.startswith('"""', j)
                    stack.append(["str", '"""' if triple else '"', j - i])
                    i = j + (3 if triple else 1)
                else:
                    i = j
            elif c == '"':
                triple = src.startswith('"""', i)
                stack.append(["str", '"""' if triple else '"', 0])
                i += 3 if triple else 1
            elif not swift and c == "'":
                j = i + 1 + (2 if src.startswith("\\", i + 1) else 0)
                k = src.find("'", j)
                i = n if k < 0 else k + 1
            elif c == "`":
                k = src.find("`", i + 1)
                i = n if k < 0 else k + 1
            else:
                if top[2] is not None:
                    if c == top[1]:
                        top[3] += 1
                    elif c == top[2]:
                        if top[3] == 0:
                            stack.pop()
                        else:
                            top[3] -= 1
                i += 1
            continue
        _, delim, hashes = top
        if swift:
            esc = "\\" + "#" * hashes
            if src.startswith(esc + "(", i):
                stack.append(["code", "(", ")", 0])
                i += len(esc) + 1
            elif src.startswith(esc, i):
                i += len(esc) + 1
            elif src.startswith(delim + "#" * hashes, i):
                stack.pop()
                i += len(delim) + hashes
            else:
                i += 1
        elif delim == '"' and c == "\\":
            i += 2
        elif src.startswith("${", i):
            stack.append(["code", "{", "}", 0])
            i += 2
        elif delim == '"""' and src.startswith('"""', i):
            while i < n and src[i] == '"':
                i += 1
            stack.pop()
        elif delim == '"' and c == '"':
            stack.pop()
            i += 1
        else:
            i += 1
    return out


def check(path):
    src = open(path, encoding="utf-8").read()
    problems = []
    found = spans(src, path.endswith(".swift"))
    for (s0, e0), (s1, _) in zip(found, found[1:]):
        gap = src[e0:s1]
        if gap.strip() == "" and gap.count("\n") == 1 and not DIRECTIVE.match(src[s1:s1 + 40]):
            problems.append((src.count("\n", 0, s1) + 1, "multi-line comment; one line says the purpose or the reason"))
    for s, e in found:
        text = src[s:e]
        line = src.count("\n", 0, s) + 1
        if DIRECTIVE.match(text):
            continue
        nxt = MODIFIERS.sub("", re.sub(r"\s+", " ", src[e:e + 300]).strip())
        doc = text.startswith(("/**", "///"))
        if PROCESS_TRACE.search(text):
            problems.append((line, "work history in a comment (date, .claude, CLAUDE.md, ISSUES, the user)"))
        if CYRILLIC.search(text):
            problems.append((line, "comment not in English"))
        if "\n" in text.strip():
            problems.append((line, "multi-line comment; one line says the purpose or the reason"))
        if re.search(r"@(param|return|throws|property)\b|- (Parameter|Returns|Throws)", text):
            problems.append((line, "parameter documentation; this is an app, not a library"))
        if doc and re.match(MEMBER_KEYWORDS, nxt) and not nxt.startswith("fun interface"):
            problems.append((line, "doc comment on a member; only types get one"))
    return problems


DENSITY_LIMIT = 5.0


def density(paths):
    """Share of comment lines in all given sources, in percent."""
    code = comments = 0
    for path in paths:
        src = open(path, encoding="utf-8").read()
        code += src.count("\n") + 1
        comments += sum(src.count("\n", s, e) + 1 for s, e in spans(src, path.endswith(".swift")))
    return 100.0 * comments / max(code, 1)


def main():
    # A file deleted in the working tree still arrives from a diff; there is nothing left to check in it.
    paths = [path for path in sys.argv[1:] if path == "--density" or os.path.exists(path)]
    if paths[:1] == ["--density"]:
        share = density(paths[1:])
        print(f"comments: {share:.1f}% of code lines (target about 1.7%, limit {DENSITY_LIMIT:.0f}%)")
        sys.exit(1 if share > DENSITY_LIMIT else 0)
    bad = 0
    for path in paths:
        for line, why in check(path):
            print(f"{path}:{line}: {why}")
            bad += 1
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
