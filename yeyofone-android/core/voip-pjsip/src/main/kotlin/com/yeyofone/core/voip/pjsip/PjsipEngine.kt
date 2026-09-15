package com.yeyofone.core.voip.pjsip

import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.EngineState
import com.yeyofone.core.voip.SipEngine
import com.yeyofone.core.voip.SipRegistrationGateway
import com.yeyofone.core.voip.NativeRegistrationEvent
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors

class PjsipEngine internal constructor(
    private val backend: EndpointBackend,
    private val configuration: PjsipEngineConfiguration,
    private val dispatcher: CoroutineDispatcher,
    private val dispatcherOwner: Closeable? = null,
) : SipEngine, SipRegistrationGateway, Closeable {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<EngineState>(EngineState.Uninitialized)

    override val state: StateFlow<EngineState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<NativeRegistrationEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<NativeRegistrationEvent> = mutableEvents.asSharedFlow()

    override suspend fun createOrUpdate(account: SipAccount, password: CharArray) {
        check(state.value == EngineState.Running) { "PJSIP engine is not running" }
        withContext(dispatcher) {
            backend.createOrUpdateAccount(account, password) { mutableEvents.tryEmit(it) }
        }
    }

    override suspend fun setRegistration(accountId: SipAccountId, renew: Boolean) {
        withContext(dispatcher) { backend.setRegistration(accountId, renew) }
    }

    override suspend fun remove(accountId: SipAccountId) {
        withContext(dispatcher) { backend.removeAccount(accountId) }
    }

    override suspend fun start() = lifecycleMutex.withLock {
        if (state.value == EngineState.Running || state.value == EngineState.Initializing) return

        mutableState.value = EngineState.Initializing
        try {
            withContext(dispatcher) {
                backend.create()
                backend.initialize(configuration)
                backend.createTransports(configuration.transports)
                backend.start()
            }
            mutableState.value = EngineState.Running
        } catch (cancellation: CancellationException) {
            tryDestroy()
            throw cancellation
        } catch (_: Exception) {
            tryDestroy()
            mutableState.value = EngineState.Failed(
                VoipError.Native("PJSIP initialization failed"),
            )
        }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        when (state.value) {
            EngineState.Uninitialized, EngineState.Stopped -> return
            else -> Unit
        }

        mutableState.value = EngineState.Stopping
        try {
            withContext(dispatcher) { backend.destroy() }
            mutableState.value = EngineState.Stopped
        } catch (_: Exception) {
            mutableState.value = EngineState.Failed(
                VoipError.Native("PJSIP shutdown failed"),
            )
        }
    }

    private suspend fun tryDestroy() {
        try {
            withContext(dispatcher) { backend.destroy() }
        } catch (_: Exception) {
            // Preserve the original initialization failure; cleanup is best-effort here.
        }
    }

    override fun close() {
        dispatcherOwner?.close()
    }

    companion object {
        /** Creates the production engine backed by the bundled PJSUA2 native library. */
        fun create(
            configuration: PjsipEngineConfiguration = PjsipEngineConfiguration(),
        ): PjsipEngine = createForBackend(Pjsua2EndpointBackend(), configuration)

        internal fun createForBackend(
            backend: EndpointBackend,
            configuration: PjsipEngineConfiguration = PjsipEngineConfiguration(),
        ): PjsipEngine {
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "yeyofone-pjsip").apply { isDaemon = true }
            }
            val dispatcher = executor.asCoroutineDispatcher()
            return PjsipEngine(backend, configuration, dispatcher, dispatcher)
        }
    }
}
