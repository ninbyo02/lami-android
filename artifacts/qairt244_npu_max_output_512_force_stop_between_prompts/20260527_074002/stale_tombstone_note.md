# stale tombstone note

This runner does not use stale tombstones as crash evidence. Fresh crash status is taken from the hidden receiver result, native diagnostics, and current logcat tail for each approved prompt run.

- fresh_crash must remain false for adoption.
- timeout must remain false for adoption.
- no retry or fallback run is performed by this script.
