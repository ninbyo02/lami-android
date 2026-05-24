# Tombstone Freshness Classification

- classification: `stale-tombstone-ignored`
- diagnostic chat run id: `diag-chat-1779499713497-a1f0822e-d06b-48b0-b067-3bbe089eb43b`
- result status: `success`
- selected tombstone path: `/data/tombstones/tombstone_22`
- signal line: ``signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------``
- tombstone/dropbox contains current run id: `false`
- current run marker present in app files: `true`
- process alive after UI smoke: `true`
- process pid: `18923`

The collector selected an older tombstone/dropbox body that does not contain the current Diagnostic Chat run id. Because the UI smoke result is success and current-run markers are present in app-private files, this tombstone is ignored for the run outcome.
