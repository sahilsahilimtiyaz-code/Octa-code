package com.sahil.octacode.ui.navigation

// M0: single source of truth for destinations.
// M3b adds mission launch + parameterized mission detail.
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CHAT = "chat"
    const val PROJECTS = "projects"
    // The terminal can be opened already pointed at one userland. An agent row
    // knows which userland its binary needs, so it can say so by arriving
    // there rather than leaving the user to work out which chip to press.
    // The argument has a default, so the bare route still resolves.
    const val TERMINAL = "terminal?userland={userland}"
    const val ARG_TERMINAL_USERLAND = "userland"
    const val DEFAULT_TERMINAL_USERLAND = "termux"

    fun terminal(userland: String = DEFAULT_TERMINAL_USERLAND): String =
        "terminal?userland=$userland"

    const val AGENTS = "agents"
    const val SETTINGS = "settings"
    const val RUNTIME = "runtime"

    // Settings sub-pages. Each one exists because a hub row points at it —
    // a route is only added together with the screen it opens, so the hub
    // can never offer a destination that is not there yet.
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PROVIDERS = "settings/providers"
    const val SETTINGS_AUTONOMY = "settings/autonomy"
    const val SETTINGS_SESSIONS = "settings/sessions"
    const val SETTINGS_CHAT = "settings/chat"
    const val SETTINGS_SERVER = "settings/server"

    // A destination of its own rather than a settings sub-page: folders are
    // data the agent works on, not a preference about the app, and the chat
    // pill has to be able to lead straight here.
    const val WORKSPACES = "workspaces"

    const val MISSION_LAUNCH = "mission/launch"
    const val MISSION_DETAIL_PATTERN = "mission/{missionId}"
    const val ARG_MISSION_ID = "missionId"

    fun missionDetail(missionId: String): String = "mission/$missionId"

    fun isMissionRoute(route: String?): Boolean =
        route != null && route.startsWith("mission/")
}
