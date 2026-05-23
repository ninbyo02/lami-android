# Large Artifacts Are Local-Only

This cold-start force-stop profile references local QAIRT/LiteRT artifacts and
the nested smoke artifact. Rebuilt `.so` files, APKs, and extracted native
libraries remain local-only. Commit text summaries, meminfo, run metadata, and
diagnostics only.
