# update.sh local workflow

`update.sh update` is safe by default for local Codex CLI work. It updates only
when the worktree is clean. If local changes exist, it prints `git status -sb`,
shows a short diff stat when available, and stops without committing, stashing,
or resetting anything.

Use one of these explicit modes when you want the script to handle local changes:

```bash
./update.sh update --stash
./update.sh update --wip
./update.sh wip
./update.sh stash
```

- `update --stash` runs `git stash push --include-untracked -m "stash(auto): before update.sh"`,
  performs the fast-forward update, then runs `git stash pop`.
- `update --wip` is the legacy explicit mode. It creates a local commit with
  `wip(auto): before update.sh`, then updates.
- `wip` only creates that WIP commit when the worktree is dirty.
- `stash` only creates that stash when the worktree is dirty.

Other commands that previously used automatic WIP commits now require explicit
`--wip` before creating a WIP commit.

The stash mode includes untracked files so update does not overwrite new local
files. Ignored files are left in place and should be reviewed manually if they
matter for a local run.

The update path prefers the current branch upstream. If no upstream is set, it
falls back to the historical `origin/future` target when available. Updates use
fast-forward-only merging and stop on detached HEAD, dirty default state, update
failure, or stash pop conflicts.
