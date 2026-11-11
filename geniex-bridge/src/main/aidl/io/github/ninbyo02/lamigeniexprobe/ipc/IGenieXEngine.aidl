package io.github.ninbyo02.lamigeniexprobe.ipc;

import io.github.ninbyo02.lamigeniexprobe.ipc.IGenieXCallback;

/**
 * Versioned Binder boundary. Native GenieX/QNN libraries stay in the separate
 * process / APK; the LAMI client never loads them.
 */
interface IGenieXEngine {
    int getProtocolVersion();
    void startGeneration(int requestId, String compute, String turnsJson, int maxTokens, IGenieXCallback callback);
    void stop(int requestId);
    void releaseEngine();
    boolean isModelReady();
}
