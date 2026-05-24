# Tombstone Freshness Classification

- classification: `stale-tombstone-ignored`
- smoke run id: `1779495021422`
- result status: `success`
- selected tombstone path: `/data/tombstones/tombstone_22`
- signal line: ``signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------``
- tombstone contains smoke run id: `false`
- current run marker present in app files: `true`
- process alive after smoke: `true`
- process pid: `31094`

The collector selected an older tombstone that does not contain the current smoke run id. Because the smoke result is success and current-run markers are present in app-private files, this tombstone is ignored for the smoke outcome.
