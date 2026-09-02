package com.puretv.twitch.core.update

/**
 * The decision both Android updaters make BEFORE touching the network or the
 * system installer, lifted out of them so it can be tested off-device.
 *
 * The ordering is the whole point. Consent is settled before anything is
 * downloaded and before any [android.content.pm.PackageInstaller] session
 * exists, so the consent detour, which sends the viewer into system Settings
 * and can take this process down on the way, happens while the app is holding
 * nothing that a kill could corrupt or leak. Committing a session first and
 * discovering the consent gap afterwards is what put a half-finished update and
 * an orphaned session on the other side of that kill.
 *
 * Everything here is a plain value. The platform calls that produce those
 * values (`canRequestPackageInstalls`, `longVersionCode`) stay in the app
 * modules, which is why this file has no Android imports and app-tv, which has
 * no test source set of its own, still gets covered.
 */
enum class UpdateGate {
    /** Nothing newer is published, or a manifest points backwards. */
    ALREADY_CURRENT,

    /**
     * A newer build exists, but the OS will refuse to install it until the
     * viewer grants "install unknown apps". Ask first, download nothing.
     */
    NEEDS_INSTALL_CONSENT,

    /** Newer build, consent in hand: safe to download and commit. */
    READY_TO_INSTALL,
}

/**
 * [candidateVersionCode] is the manifest's; [installedVersionCode] is what is
 * running. The downgrade check comes first deliberately: an edited or rolled
 * back manifest must be refused whether or not consent was ever granted, so
 * that a viewer who is already current is never sent into Settings for an
 * install that would then be rejected as a downgrade.
 */
fun updateGate(
    candidateVersionCode: Long,
    installedVersionCode: Long,
    hasInstallConsent: Boolean,
): UpdateGate = when {
    candidateVersionCode <= installedVersionCode -> UpdateGate.ALREADY_CURRENT
    !hasInstallConsent -> UpdateGate.NEEDS_INSTALL_CONSENT
    else -> UpdateGate.READY_TO_INSTALL
}
