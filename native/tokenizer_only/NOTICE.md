# Tokenizer-only diagnostic dependency

This optional library statically incorporates SentencePiece v0.2.1,
commit 31646a467d2051eb904e0b45de3a73e91fe1c1e3, Google Inc., Apache-2.0.
Source: https://github.com/google/sentencepiece/tree/31646a467d2051eb904e0b45de3a73e91fe1c1e3
The build copies upstream LICENSE and NOTICE files alongside its manifest.
It uses SentencePiece's bundled protobuf/Abseil dependencies and notices.
Only diagnostic builds opt into this artifact. No inference runtime is linked.
