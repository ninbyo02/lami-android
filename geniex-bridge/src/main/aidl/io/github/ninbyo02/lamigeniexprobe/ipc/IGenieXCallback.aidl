package io.github.ninbyo02.lamigeniexprobe.ipc;

/** On-device streaming callback; does not expose model paths. */
oneway interface IGenieXCallback {
    void onToken(int requestId, String text);
    void onComplete(int requestId, String metricsJson);
    void onError(int requestId, String code, String message);
}
