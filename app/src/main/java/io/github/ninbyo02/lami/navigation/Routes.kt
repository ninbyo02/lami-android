package io.github.ninbyo02.lami.navigation

object Routes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val CHAT = "chat"
    const val CHAT_ROOT = CHAT
    const val CHAT_ID_ARG = "chatID"
    const val CHAT_WITH_ID = "$CHAT/{$CHAT_ID_ARG}"
    const val CHAT_ID_ARG_ROUTE = "chatId"
    const val CHAT_WITH_ID_ROUTE = "$CHAT/{$CHAT_ID_ARG_ROUTE}"
    const val SETTINGS = "setting"
    const val ABOUT = "about"
    const val NOTICE = "notice"
    const val SPRITE_SETTINGS = "settings/sprite_settings"
    const val SPRITE_EDITOR = "settings/sprite_editor"
    const val LOCAL_BASE_MODEL = "settings/local_base_model"
    const val LOCAL_GENERIC_FALLBACK_MODEL = "settings/local_generic_fallback_model"

    fun chat(chatId: Int): String = "$CHAT/$chatId"

    fun chatNew(): String = CHAT_ROOT

    fun chatRoutePattern(): String = CHAT_WITH_ID_ROUTE
}

sealed interface SettingsRoute {
    val route: String

    data object SpriteSettings : SettingsRoute {
        override val route: String = Routes.SPRITE_SETTINGS
    }

    data object SpriteEditor : SettingsRoute {
        override val route: String = Routes.SPRITE_EDITOR
    }

    data object LocalBaseModel : SettingsRoute {
        override val route: String = Routes.LOCAL_BASE_MODEL
    }

    data object LocalGenericFallbackModel : SettingsRoute {
        override val route: String = Routes.LOCAL_GENERIC_FALLBACK_MODEL
    }
}
