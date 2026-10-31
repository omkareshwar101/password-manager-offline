package com.offlinevault.app.util

data class BuiltInIcon(
    val id: String,
    val label: String,
    val emoji: String,
    val color: Long,
)

/**
 * A local, dependency-free icon set. Emoji are rendered by the system font, so
 * the library ships 50+ recognizable choices without bundling or downloading
 * brand assets. Uploaded photos are always preferred when present.
 */
object BuiltInIcons {
    val icons: List<BuiltInIcon> = listOf(
        icon("gmail", "Gmail", "✉️", 0xFFEA4335),
        icon("google", "Google", "🔵", 0xFF4285F4),
        icon("instagram", "Instagram", "📸", 0xFFE4405F),
        icon("facebook", "Facebook", "📘", 0xFF1877F2),
        icon("x", "X / Twitter", "𝕏", 0xFF111111),
        icon("whatsapp", "WhatsApp", "💬", 0xFF25D366),
        icon("telegram", "Telegram", "✈️", 0xFF229ED9),
        icon("snapchat", "Snapchat", "👻", 0xFFFFFC00),
        icon("linkedin", "LinkedIn", "💼", 0xFF0A66C2),
        icon("youtube", "YouTube", "▶️", 0xFFFF0000),
        icon("netflix", "Netflix", "🎬", 0xFFE50914),
        icon("spotify", "Spotify", "🎵", 0xFF1DB954),
        icon("amazon", "Amazon", "📦", 0xFFFF9900),
        icon("flipkart", "Flipkart", "🛒", 0xFF2874F0),
        icon("bank", "Bank", "🏦", 0xFF00695C),
        icon("paypal", "PayPal", "💳", 0xFF003087),
        icon("card", "Card", "💳", 0xFF473198),
        icon("crypto", "Crypto", "₿", 0xFFF7931A),
        icon("github", "GitHub", "🐙", 0xFF24292F),
        icon("gitlab", "GitLab", "🦊", 0xFFFC6D26),
        icon("reddit", "Reddit", "👽", 0xFFFF4500),
        icon("discord", "Discord", "🎮", 0xFF5865F2),
        icon("steam", "Steam", "🎮", 0xFF171A21),
        icon("playstation", "PlayStation", "🕹️", 0xFF003791),
        icon("xbox", "Xbox", "🎮", 0xFF107C10),
        icon("apple", "Apple", "🍎", 0xFF333333),
        icon("microsoft", "Microsoft", "🪟", 0xFF00A4EF),
        icon("dropbox", "Dropbox", "📁", 0xFF0061FF),
        icon("drive", "Cloud Drive", "☁️", 0xFF0F9D58),
        icon("notion", "Notion", "📝", 0xFF333333),
        icon("slack", "Slack", "💬", 0xFF4A154B),
        icon("zoom", "Zoom", "🎥", 0xFF2D8CFF),
        icon("teams", "Teams", "👥", 0xFF6264A7),
        icon("jira", "Jira", "🧩", 0xFF0052CC),
        icon("figma", "Figma", "🎨", 0xFFF24E1E),
        icon("canva", "Canva", "🖌️", 0xFF00C4CC),
        icon("adobe", "Adobe", "🅰️", 0xFFFF0000),
        icon("school", "School", "🎓", 0xFF5E35B1),
        icon("work", "Work", "💼", 0xFF3949AB),
        icon("mail", "Email", "📧", 0xFF1E88E5),
        icon("shopping", "Shopping", "🛍️", 0xFFE91E63),
        icon("health", "Health", "⚕️", 0xFF00897B),
        icon("gaming", "Gaming", "🎮", 0xFF8E24AA),
        icon("travel", "Travel", "✈️", 0xFF039BE5),
        icon("music", "Music", "🎧", 0xFFD81B60),
        icon("streaming", "Streaming", "📺", 0xFFD32F2F),
        icon("cloud", "Cloud", "☁️", 0xFF546E7A),
        icon("server", "Server", "🖥️", 0xFF455A64),
        icon("wifi", "Wi-Fi", "📶", 0xFF1565C0),
        icon("key", "Key", "🔑", 0xFFF9A825),
        icon("lock", "Lock", "🔒", 0xFF37474F),
        icon("home", "Home", "🏠", 0xFF6D4C41),
        icon("car", "Car", "🚗", 0xFF3949AB),
        icon("insurance", "Insurance", "🛡️", 0xFF00838F),
        icon("tax", "Tax", "🧾", 0xFF6D4C41),
        icon("passport", "Passport", "🛂", 0xFF2E7D32),
        icon("identity", "Identity", "🪪", 0xFF283593),
        icon("leaf", "Personal", "🌿", 0xFF43A047),
        icon("star", "Important", "⭐", 0xFFF9A825),
        icon("folder", "Folder", "📁", 0xFFFB8C00),
        icon("pin", "PIN / Code", "📍", 0xFFD32F2F),
    )

    private fun icon(id: String, label: String, emoji: String, color: Long) =
        BuiltInIcon(id = id, label = label, emoji = emoji, color = color)

    fun find(ref: String?): BuiltInIcon? {
        if (ref == null || !ref.startsWith(Constants.BUILTIN_ICON_PREFIX)) return null
        val id = ref.removePrefix(Constants.BUILTIN_ICON_PREFIX)
        return icons.firstOrNull { it.id == id }
    }

    fun ref(id: String): String = Constants.BUILTIN_ICON_PREFIX + id
}
