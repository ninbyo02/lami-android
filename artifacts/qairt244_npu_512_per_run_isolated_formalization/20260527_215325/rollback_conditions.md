# Rollback conditions

512 must remain rollback-only for:

- sequential execution used as a 512 baseline
- Activity-restart-only execution used as a 512 baseline
- timeout
- missing cleanup or `Engine.close`
- missing QNN/HTP/FastRPC evidence
- retained process after 10 seconds
- memory high retained
- broken code indentation
- incomplete or unclosed code fence
- fresh crash
- fallback
- selectedPath=NPU persistence
- assistant message-list insertion
- DB ingress
- TTS ingress
- Markdown renderer ingress
- streaming ingress
- any request above `max_output_tokens=512`
