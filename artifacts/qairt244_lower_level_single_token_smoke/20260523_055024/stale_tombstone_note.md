# Tombstone Freshness Classification

- classification: `stale-tombstone-ignored`
- smoke run id: `1779483024756`
- result status: `success`
- selected tombstone path: `/data/tombstones/tombstone_22`
- signal line: `signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------`
- tombstone contains smoke run id: `false`
- current run marker present in app files: `true`
- process alive after smoke: `true`
- process pid: `22071`

The collector selected an older tombstone that does not contain the current
smoke run id. Because the smoke result is success and the current run marker is
present in `qairt244_single_token_smoke_result.txt`, this tombstone is ignored
for the smoke outcome.
