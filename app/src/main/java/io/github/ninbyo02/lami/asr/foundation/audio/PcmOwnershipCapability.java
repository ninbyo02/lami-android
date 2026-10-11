package io.github.ninbyo02.lami.asr.foundation.audio;

/** Package-private JVM capability used only by PCM ownership-transfer code in this package. */
final class PcmOwnershipCapability {
    private static final Object TOKEN = new Object();

    static Object token() {
        return TOKEN;
    }

    static boolean isValid(Object candidate) {
        return candidate == TOKEN;
    }

    private PcmOwnershipCapability() {
        throw new AssertionError("no instances");
    }
}
