# Logcat Slice Policy

Each boundary writes a log marker using tag `QAIRT244_PROCESS_BOUNDARY`, then
stores a bounded `logcat -d -t 1200` slice.

The filtered marker file keeps lines matching:

- `ActivityTaskManager`
- `ActivityManager`
- `am_proc_died`
- `ProcessRecord`
- `Killing`
- `LowMemoryKiller`
- `lowmemorykiller`
- `lmkd`
- `FATAL EXCEPTION`
- `ANR`
- `tombstone`
- `SIGSEGV`
- `SIGABRT`
- package name
- `QAIRT244_PROCESS_BOUNDARY`

The next approved runtime pass should use these slices to identify whether
prompt-boundary process loss is runner-induced, lifecycle-induced, OS kill,
native abort, or still unexplained.
