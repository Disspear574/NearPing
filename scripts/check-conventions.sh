#!/usr/bin/env bash
# Project conventions: English outside localization and test data, comment rules.
set -euo pipefail
cd "$(dirname "$0")/.."

# Untracked files count too: new code is not in the index until `git add`.
files() { git ls-files --cached --others --exclude-standard -- "$@" | while read -r f; do [ -f "$f" ] && echo "$f"; done; }

status=0

# Desktop entries carry their Russian translation as `Key[ru]=`.
cyrillic=$(files '*.kt' '*.kts' '*.swift' '*.py' '*.sh' '*.yml' '*.md' '*.xcconfig*' 'Makefile' '.gitignore' \
  | grep -vE '(^|/)(ru\.lproj/|Strings\.kt$|commonTest/|jvmTest/)' \
  | python3 -c '
import re, sys
for path in sys.stdin.read().split():
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        if re.search("[\u0400-\u04FF]", line) and "[ru]=" not in line:
            print(f"{path}:{n}: {line.rstrip()}")
')
if [ -n "$cyrillic" ]; then
  echo "Russian outside localization and test data:"
  echo "$cyrillic"
  status=1
fi

sources=$(files '*.kt' '*.swift')
if ! python3 scripts/comment-check.py $sources; then status=1; fi
density=$(python3 scripts/comment-check.py --density $sources)
echo "$density"
share=$(echo "$density" | sed -E 's/^comments: ([0-9.]+)%.*/\1/')
if awk "BEGIN { exit !($share > 5) }"; then
  echo "Comment share above 5 %."
  status=1
fi

exit $status
