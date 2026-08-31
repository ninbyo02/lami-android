package io.github.ninbyo02.lami.npu

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class NpuKotlinConversationDurabilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val turns = intent.getIntExtra(
            NpuKotlinConversationDurabilityReceiver.EXTRA_TURNS,
            NpuKotlinConversationDurabilityReceiver.DEFAULT_TURNS,
        )
        sendBroadcast(
            Intent(NpuKotlinConversationDurabilityReceiver.ACTION)
                .setPackage(packageName)
                .putExtra(NpuKotlinConversationDurabilityReceiver.EXTRA_TURNS, turns),
        )
        finish()
    }
}
