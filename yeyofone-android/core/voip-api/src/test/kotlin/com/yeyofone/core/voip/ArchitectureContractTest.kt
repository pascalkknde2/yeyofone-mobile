package com.yeyofone.core.voip

import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureContractTest {
    @Test
    fun `voip boundary exposes interfaces only`() {
        val contracts = listOf(
            SipEngine::class,
            SipAccountManager::class,
            RegistrationManager::class,
            CallManager::class,
            MediaManager::class,
            AudioRouteManager::class,
            SipDiagnostics::class,
        )
        assertTrue(contracts.all { it.java.isInterface })
    }
}
