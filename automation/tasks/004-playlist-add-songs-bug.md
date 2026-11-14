---
id: 004-playlist-add-songs-bug
status: in_progress
iteration: 0
iteration_cap: unlimited   # objective: all added songs persist, or they don't
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Creating a new playlist and adding many songs to it must keep all of them —
today it reportedly keeps only one (or very few) out of many.

## Acceptance criteria (first round is VERIFY-ONLY, not a blind fix)

- A checkpoint commit on this branch's base already contains an uncommitted
  "playlist-create failure" fix from a prior session, per project memory —
  it was never compiled or tested. First iteration: build the signed APK,
  install on the real device, create a new playlist, add 10+ songs at once,
  confirm all songs persist after leaving and re-entering the playlist
  screen (kills the "only DB write races/last-write-wins" class of bug).
- If it's already fixed: mark DONE with the on-device evidence (screenshot
  of the full song list) and stop — no code changes needed.
- If still broken: only then dig further (check `CreatePlaylistDialog.kt`,
  `PlaylistSongMap` insert path, any batching/transaction issue) and fix
  root cause, re-verify the same way.

## Context

Repro as described by the user: create playlist → add many songs → most
disappear, one or a few remain. Related files:
`app/src/main/kotlin/com/ozyern/exhale/ui/component/CreatePlaylistDialog.kt`,
`PlaylistSongMap` insert logic in `MusicDatabase`/`DatabaseDao`.
