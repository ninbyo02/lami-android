# Snapshot Schema

Each snapshot is stored under:

`process_boundary/prompt_<index>_<slug>/<boundary>/`

Files:

- `host_timestamp.txt`
- `device_timestamp.txt`
- `pidof.txt`
- `ps_all.txt`
- `ps_package.txt`
- `dumpsys_activity_processes.txt`
- `dumpsys_activity_processes_package_context.txt`
- `dumpsys_activity_top.txt`
- `dumpsys_window_visible_apps.txt`
- `logcat_slice.txt`
- `logcat_process_markers.txt`
- `summary.txt`

`summary.txt` contains:

- `prompt_index`
- `slug`
- `boundary`
- `package`
- `pidof`
- `classification`
- `can_dispatch`
- `process_disappeared_suspect`
- `reuse_allowed`
- `hidden_per_run_isolated_required`
