# Git: branches, commits, pull requests, versions

**Stack:** any
**Do not apply:** the old VPN projects (V2Ray, Lokky and everything in their git, BHub, Ograna, RealProject and the rest of that family) keep their own rules; a project that has not yet adopted this version keeps its current `docs/GIT.md` until the owner agrees to switch
**Check:** the project's `commit-msg` and `pre-commit` git hooks, `publish-check.py`, and the Claude Code hook `check-conventions-on-commit.sh`; slice and branch rules rely on review
**Origin:** BeautyCosmo, 2026-09-21 — 25 commits and 82 dirty files piled up for eight days on an unpushed branch. Rewritten 2026-09-24 for English Conventional Commits and the issue → branch → pull request flow.

This is the **shared source** for all projects. A project gets a copy as `docs/GIT.md`, because
a path into the author's disk is no use in documentation read by others. Shared rules change
**here** and are then copied. Project specifics (repository, labels, scopes, which devices are
checked) live in the project's copy and are not lifted back here.

---

## Branches

- `main` is the truth. History is linear: rebase or fast-forward, no merge commits.
- One branch per theme, from `main`: `features/<N>-<slug>`, or `fix/<N>-<slug>` for a bug,
  where `<N>` is the number of its main issue.
- `backup/<branch>-<date>[-stepN]` before any history rewrite, at every step.
- `vX.Y.Z` is a release tag on `main`; it starts the release job in CI.

**One level of nesting.** Git cannot hold a branch `features` and a branch `features/x` at
the same time (one is a file in `.git/refs/heads`, the other a folder), and deep names make
the branch list unreadable.

**A branch's age measures its health.** A branch older than a week or thicker than about 15
commits has become a second `main`: nobody reads its pull request, and conflicts cost more than
the isolation gave. Close it and open the next one.

---

## When to commit

The boundary of a commit is set by **the state of the working tree**, not by whether the task
is finished. Commit when any of these is true:

1. **A slice is built.** The code compiles, and one sentence answers "what works now". This is
   the main signal; the others catch a missed one.
2. **The next edit touches the same files.** After it two pieces of work mix in one file and
   can no longer be separated. The hardest signal, because it cannot be undone.
3. **The layer or topic changes.** Domain done, moving to UI; one feature done, moving to another.
4. **Something risky is next:** a refactor across many files, a dependency upgrade, a history
   rewrite, an experiment. A commit is a rollback point, not a report.
5. **The session ends.** An uncommitted tree survives neither a mistake nor a neighbouring session.
6. **The size passes readable:** about 20 files or 500 lines on one topic. Beyond that a diff is
   scrolled, not read.

**Nothing to commit** when the request was to run something, take a screenshot, answer a
question or read code, and no code changed.

**Who presses the button.** The person decides to commit. The agent's duty is to **say** that
a boundary is reached, in one line with a ready header and a file count: "Slice ready: tariffs
come from the store. `feat(tariff): load tariffs from the store`, 9 files. Commit?"

---

## Card, commit and branch are different things

| | Holds | Created by |
| --- | --- | --- |
| Task card | **intent**: what was asked and why | a request that leads to an edit |
| Commit | **slice**: what started working | a boundary from the list above |
| Branch | **topic**: related work merged as a whole | an issue |

One card may produce three commits; one commit may close two cards.

**A card is created when an edit appears, not when a chat opens.** A session asked to run a
build on a device, answer a question or read code adds its result to the "Check" section of
the card for the work it checked. Otherwise the board fills with cards that hold no code.

---

## Commit message

[Conventional Commits](https://www.conventionalcommits.org), in English:

```
type(scope): imperative description

Body wrapped at 72 characters: what changed and why.
```

- `type`: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.
- `scope`: a feature, flow, domain or library in kebab-case; omitted for a change across the
  project. Each project lists its scopes in its copy.
- The header is **at most 72 characters**, lowercase after the colon, no trailing period, and
  completes the sentence "If applied, this commit will …". Measure in characters (`python3 len()`).
- A blank line after the header. `feat`, `fix`, `perf` and `refactor` need a body: what changed
  and why, naming the mechanism (types, endpoints, files worth finding).
- No trailers: no `Co-Authored-By`, `Signed-off-by`, `Generated with` or links to tools.
  A commit has one author, a person.
- The issue number belongs to the branch and the pull request, not the commit.
- Forbidden as a header: "improvements", "fixes", "misc", "wip". Such history tells neither
  when something appeared nor what to revert.

```
feat(admission): record the four admission steps on mocks

AdmissionRoot holds a ChildStack of steps; each step reports
StepCompleted(mediaRef) up. Video goes through AppVideoRecorder with
the 30 s limit from domain/media, and KnowledgeTestStore scores the
test locally without the server.
```

---

## Issues

Work starts from an issue created with a form (`.github/ISSUE_TEMPLATE`); blank issues are off.

| Label | Meaning | Who creates it |
| --- | --- | --- |
| `epic` | A goal made of several stories, with a clear point where it is done | the owner; an agent drafts it in chat |
| `story` | Something checked by hand on a device | the owner; an agent drafts it in chat |
| `task` | Technical work inside a story or an epic | anyone, an agent included |
| `bug` | Behaves differently from what its story promised | whoever found it |

Tasks and bugs are sub-issues of their story or epic. A project board tracks status:
Backlog → TODO → In progress → Testing → Done.

---

## Pull request and merge

**A pull request covers a theme, not a single issue.** Small issues of one theme (a screen
polish, a round of UI fixes, a tooling change) share one branch and one pull request with a
`Closes #N` line for each. Issues stay one per task, so the board still shows what was done.
Separate pull requests are for risky work (data, sync, migrations) and for a large feature,
which are read and reverted on their own. Owner (2026-09-24): "too many PRs, one per task?
maybe combine some".

1. Branch from `main` for the theme; its issues move to In progress.
2. Commits by slices. Intermediate commits are fine inside the branch.
3. **Review before pushing:** the full check run plus reading the whole `main..HEAD` diff.
   Findings are fixed in the same branch, not in a separate "review fixes" commit.
4. Squash intermediate commits into slices locally if the branch collected "fix import",
   "revert that".
5. Push, open a pull request with the template filled in. Title in the commit header form.
   A task is closed by `Closes #N`; a story is never closed by a pull request: `Part of #N`.
6. Read the diff in the web interface: it shows what the editor hides.
7. **Merge is done by a person**, with Rebase and merge (or fast-forward), after green CI.
   An agent opens the pull request, reports CI and stops.

Repository settings: rebase or fast-forward merge only, **squash off**, branch deleted after
merge, checks required.

**Why squash is off.** When the build number comes from the commit count
(`git rev-list --count`), squash lowers it, and the next build gets a number below the last one;
Play and TestFlight reject it. Squash locally before pushing, so the count only grows.

---

## Pull request template

In the repository as `.github/pull_request_template.md`:

```markdown
## Summary
Closes #

## Changes
- 

## How to test
1. 

## Screenshots
| Before | After |
|---|---|
|  |  |

## Data
- [ ] Database schema or migration
- [ ] Backend format
- [ ] UI strings (every language)
- [ ] Permissions, entitlements, manifest
- [ ] New dependencies

## Risk
Low

## Checklist
- [ ] Tested on a device or simulator
- [ ] Tests added or updated and passing
- [ ] No secrets, debug output or commented-out code
- [ ] Documentation updated
```

**Risk** is Low for a small fix, Medium for a new feature, High for data, sync or migrations,
with one line on what could break. A migration is tested by upgrading from the **previous**
version: a clean install reproduces no migration bug, which is why users find them first.

---

## Device checks

A pull request that needs a check by hand gets the `device-check` label. After merge CI builds
the app and sends it with the "How to test" section to the owner, and the issue moves to Testing
instead of Done. When the last task of a story is merged, the story is checked on real devices
against every item of its "Done when" list and closed by the owner. A failure becomes a `bug`
linked to the story; a closed story is not reopened.

---

## Version and release

- `MAJOR.MINOR.PATCH` by hand, only for a release. There is no separate "bump version" commit;
  the bump is part of the release commit.
- The build number is not committed and comes from **one** counter per project. The commit
  count is practical: monotonic, equal on every machine, reproducible for a revision. It needs a
  full clone; on a shallow one it is the clone depth.
- Release: tag `vX.Y.Z` on `main`; the tag starts the release job.
- After a history rewrite the build number base is set so the next number is above the last
  **published** one, not the last built one.

---

## Parallel sessions in one tree

- A branch is per topic, not per session.
- Stage by listing paths (`git add -- <paths>`), never `git add -A`: otherwise a neighbour's
  unfinished edit rides along.
- Before pushing, compare task cards with the real diff (`/preflight`).
- Two or three sessions per tree at most; beyond that use `git worktree`.

**Git is the truth, a card is the intent.** Readiness is judged by the diff.

---

## Two kinds of hooks

| | Git hook | Claude Code hook |
| --- | --- | --- |
| Where | `core.hooksPath` in the repository | `~/.claude/hooks/` + `settings.json` |
| When | on `git commit`, whoever runs it | when the agent calls a tool |
| Sees | the index | the command or the written file |
| Misses | everything the agent does before a commit | commits from an IDE or a terminal |
| Blocks with | a non-zero exit | exit 2 and a message on stderr |

**Both are needed.** The git hook is the only thing that catches a commit made from an IDE; the
Claude Code hook catches a violation right after a file is written, in every project at once.

One body for the rules: the check lives in the project (`scripts/check-conventions.sh`), and
both hooks are thin wrappers around it. Git hooks live in the repository (`scripts/git-hooks/`)
and are enabled with `git config core.hooksPath scripts/git-hooks`, because `.git/hooks` is not
cloned.

`--no-verify` turns off git hooks but not the agent's hook, which asks for the reason to be
said to the owner. Force pushes, deleting remote branches and pushing to `main` directly are
left to a person (`~/.claude/hooks/git-push-guard.sh`).

The commit check looks at **the index**, not the whole tree: blocking on a neighbour's
unfinished file is the first step to `--no-verify` by habit.

---

## Not committed

- Keys and signing: keystores, certificates, profiles, `config/*.local.*`, `local.properties`.
- Platform config files with project ids: `google-services.json`, `GoogleService-Info.plist`.
- Build output and reports.
- Agent working files: `.claude/`, `CLAUDE.md`, `CLAUDE.local.md`. Project rules for people and
  agents live in `docs/`.
- IDE files, except a shared linter configuration when the project needs one.

Machine paths, tokens, device ids, personal emails and AI attribution are caught by
`publish-check.py` ([process/nothing-private-in-git.md](process/nothing-private-in-git.md)).
Documentation in `docs/` is committed in full and holds nothing that cannot be shown to an
outsider.
