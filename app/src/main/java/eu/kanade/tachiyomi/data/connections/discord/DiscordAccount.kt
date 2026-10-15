package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.Serializable

@Serializable
data class DiscordAccount(
    val id: String,
    val username: String,
    val name: String? = null,
    val avatarUrl: String?,
    val token: String,
    val isActive: Boolean = false,
)
