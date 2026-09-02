package com.puretv.twitch.core.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the ordering that keeps the consent detour off the dangerous path.
 *
 * A TV viewer reported the app refusing to start after tapping update: the
 * installer asked for "install unknown apps" only once a session had already
 * been committed, so granting it sent them through system Settings while a
 * download and an installer session were live. These assertions pin the
 * decision that has to happen before any of that exists.
 */
class UpdateGateTest {

    @Test
    fun consentIsDemandedBeforeAnythingIsDownloaded() {
        assertEquals(
            UpdateGate.NEEDS_INSTALL_CONSENT,
            updateGate(candidateVersionCode = 7, installedVersionCode = 6, hasInstallConsent = false),
            "a newer build without consent must stop at the gate, not at the installer",
        )
    }

    @Test
    fun consentInHandProceeds() {
        assertEquals(
            UpdateGate.READY_TO_INSTALL,
            updateGate(candidateVersionCode = 7, installedVersionCode = 6, hasInstallConsent = true),
        )
    }

    @Test
    fun sameVersionIsCurrentRatherThanAnInstall() {
        assertEquals(
            UpdateGate.ALREADY_CURRENT,
            updateGate(candidateVersionCode = 7, installedVersionCode = 7, hasInstallConsent = true),
        )
    }

    /**
     * The downgrade check outranks the consent check. Were the order reversed, a
     * viewer already on the newest build and lacking consent would be sent into
     * Settings to authorise an install the anti-downgrade rule then refuses,
     * which is a dead end that looks exactly like the bug this change fixes.
     */
    @Test
    fun aBackwardsManifestIsRefusedWithoutSendingAnyoneToSettings() {
        assertEquals(
            UpdateGate.ALREADY_CURRENT,
            updateGate(candidateVersionCode = 6, installedVersionCode = 7, hasInstallConsent = false),
        )
        assertEquals(
            UpdateGate.ALREADY_CURRENT,
            updateGate(candidateVersionCode = 6, installedVersionCode = 7, hasInstallConsent = true),
        )
    }

    /**
     * versionCode is a Long on purpose: the updater reads `longVersionCode`, and
     * a value past Int range must not wrap into a downgrade.
     */
    @Test
    fun largeVersionCodesDoNotWrap() {
        assertEquals(
            UpdateGate.READY_TO_INSTALL,
            updateGate(
                candidateVersionCode = Int.MAX_VALUE.toLong() + 1L,
                installedVersionCode = Int.MAX_VALUE.toLong(),
                hasInstallConsent = true,
            ),
        )
    }
}
