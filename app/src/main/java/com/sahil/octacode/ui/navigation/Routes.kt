package com.sahil.octacode.ui.navigation

// M0: single source of truth for destinations.
// M3b adds mission launch + parameterized mission detail.
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CHAT = "chat"
    const val PROJECTS = "projects"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
    const val RUNTIME = "runtime"

    const val MISSION_LAUNCH = "mission/launch"
    const val MISSION_DETAIL_PATTERN = "mission/{missionId}"
    const val ARG_MISSION_ID = "missionId"

    fun missionDetail(missionId: String): String = "mission/$missionId"

    fun isMissionRoute(route: String?): Boolean =
        route != null && route.startsWith("mission/")
}
