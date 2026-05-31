package dev.ambitionsoftware.tymeboxed.ui.navigation

/**
 * All Navigation Compose routes in one place. Single-activity app — the
 * [TymeBoxedNavHost] swaps between these composables inside `MainActivity`.
 */
object Routes {
    /** Onboarding; [initialStep] 0 = welcome, 1 = login (e.g. after delete account). */
    const val INTRO = "intro/{initialStep}"
    fun intro(initialStep: Int = 0) = "intro/$initialStep"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val IN_APP_BLOCKING = "in_app_blocking"
    const val PERMISSIONS = "permissions"

    /** Foqos-style 9-step profile creation wizard. */
    const val PROFILE_ONBOARD = "profile_onboard/{profileId}"
    fun profileOnboard(profileId: String = "new") = "profile_onboard/$profileId"

    const val PROFILE_ONBOARD_SELECT_APPS = "profile_onboard/{profileId}/select_apps"
    fun profileOnboardSelectApps(profileId: String = "new") = "profile_onboard/$profileId/select_apps"

    const val PROFILE_ONBOARD_SELECT_DOMAINS = "profile_onboard/{profileId}/select_domains"
    fun profileOnboardSelectDomains(profileId: String = "new") = "profile_onboard/$profileId/select_domains"

    const val PROFILE_ONBOARD_SCHEDULE = "profile_onboard/{profileId}/schedule"
    fun profileOnboardSchedule(profileId: String = "new") = "profile_onboard/$profileId/schedule"

    /** Profile create / edit. */
    const val PROFILE_EDIT = "profile_edit/{profileId}"
    fun profileEdit(profileId: String = "new") = "profile_edit/$profileId"

    /** Full-screen app multiselect; shares [ProfileEditViewModel] with [PROFILE_EDIT]. */
    const val PROFILE_EDIT_SELECT_APPS = "profile_edit/{profileId}/select_apps"
    fun profileEditSelectApps(profileId: String) = "profile_edit/$profileId/select_apps"

    /** Full-screen domain list + add; shares [ProfileEditViewModel] with [PROFILE_EDIT]. */
    const val PROFILE_EDIT_SELECT_DOMAINS = "profile_edit/{profileId}/select_domains"
    fun profileEditSelectDomains(profileId: String) = "profile_edit/$profileId/select_domains"

    /** Weekly schedule editor; shares [ProfileEditViewModel] with [PROFILE_EDIT]. */
    const val PROFILE_EDIT_SCHEDULE = "profile_edit/{profileId}/schedule"
    fun profileEditSchedule(profileId: String) = "profile_edit/$profileId/schedule"
}
