# Releases

## Branches

- `develop` - integration branch. Every feature/fix/docs PR squash-merges here.
  Each push cuts a **beta prerelease** (`vX.Y.Z-beta.N`).
- `master` - stable. Only `develop` (a promotion) or `hotfix/*` may target it.
  Each push cuts a **stable release** (`vX.Y.Z`). The promotion PR is a true
  merge, never a squash - see the `close-milestone` skill.

## Versioning and changelog

[Release Please](https://github.com/googleapis/release-please) (`simple` release
type) derives the next version from Conventional Commit titles and owns two
files:

- `version.txt` - read by `build.clj` to name the jar
- `CHANGELOG.md`

Never hand-edit either; `repo-hygiene.yml` fails a PR that does. Squash-merging
matters because a merge commit replays every commit into the changelog twice.

Configs: `release-please-config.json` / `.release-please-manifest.json` (stable)
and the `-beta` pair (prerelease).

## What a release builds

`release.yml`, on a created release, on Windows, Linux and macOS:

1. `bb uber` -> `target/veil-<version>.jar` (AOT-compiled `veil.main`, Quil
   natives included)
2. `jpackage` -> `.exe` / `.deb` / `.pkg`, uploaded to the GitHub Release. The
   input folder holds the jar and a copy of `mods/`; jpackage is passed
   `-Dveil.mods.dir=$APPDIR/mods`, because an installed game's working
   directory isn't its install folder (`docs/mod-format.md`).

The workflow needs a `RELEASE_PLEASE_TOKEN` secret (fine-grained PAT, Contents +
Pull requests read/write on this repo) - `release.yml` explains why the default
token isn't enough.
