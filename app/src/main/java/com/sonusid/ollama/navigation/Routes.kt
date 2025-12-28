package com.sonusid.ollama.navigation

object Routes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val CHAT = "chat"
    const val CHAT_ID_ARG = "chatID"
    const val CHAT_WITH_ID = "$CHAT/{$CHAT_ID_ARG}"
    const val SETTINGS = "setting"
    const val ABOUT = "about"
    const val SPRITE_DEBUG = "sprite_debug"
    const val SPRITE_DEBUG_SETTINGS = "sprite_debug_settings"
    const val SPRITE_DEBUG_TOOLS = "sprite_debug_tools"

    fun chat(chatId: Int): String = "$CHAT/$chatId"
}
