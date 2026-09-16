package com.yeyofone.core.voip.pjsip

import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.EngineState
import com.yeyofone.core.voip.SipEngine
import com.yeyofone.core.voip.SipCallGateway
import com.yeyofone.core.voip.SipRegistrationGateway
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.NativeMediaEvent
import com.yeyofone.core.voip.NativeRegistrationEvent
import com.yeyofone.core.voip.NativeTransferEvent
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
) : SipEngine, SipRegistrationGateway, SipCallGateway, Closeable {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<EngineState>(EngineState.Uninitialized)

    override val state: StateFlow<EngineState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<NativeRegistrationEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<NativeRegistrationEvent> = mutableEvents.asSharedFlow()
    private val mutableCallEvents = MutableSharedFlow<NativeCallEvent>(extraBufferCapacity = 32)
    override val callEvents: SharedFlow<NativeCallEvent> = mutableCallEvents.asSharedFlow()
    private val mutableMediaEvents = MutableSharedFlow<NativeMediaEvent>(extraBufferCapacity = 32)
    override val mediaEvents: SharedFlow<NativeMediaEvent> = mutableMediaEvents.asSharedFlow()
    private val mutableTransferEvents = MutableSharedFlow<NativeTransferEvent>(extraBufferCapacity = 32)
    override val transferEvents: SharedFlow<NativeTransferEvent> = mutableTransferEvents.asSharedFlow()

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

    override suspend fun makeCall(accountId: SipAccountId, destination: String): String {
        check(state.value == EngineState.Running) { "PJSIP engine is not running" }
        return withContext(dispatcher) {
            backend.makeCall(accountId, destination) { mutableCallEvents.tryEmit(it) }
        }
    }

    override suspend fun answer(callId: String) {
        withContext(dispatcher) { backend.answerCall(callId) }
    }

    override suspend fun hangup(callId: String) {
        withContext(dispatcher) { backend.hangupCall(callId) }
    }

    override suspend fun sendDtmf(callId: String, digit: Char) {
        withContext(dispatcher) { backend.sendDtmf(callId, digit) }
    }

    override suspend fun transfer(callId: String, destination: String) {
        withContext(dispatcher) { backend.transferCall(callId, destination) }
    }

    override suspend fun attendedTransfer(callId: String, destinationCallId: String) {
        withContext(dispatcher) { backend.attendedTransferCall(callId, destinationCallId) }
    }

    override suspend fun setMuted(callId: String, muted: Boolean) {
        withContext(dispatcher) { backend.setMuted(callId, muted) }
    }

    override suspend fun setHeld(callId: String, held: Boolean) {
        withContext(dispatcher) { backend.setHeld(callId, held) }
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
                backend.setCallEventListener { mutableCallEvents.tryEmit(it) }
                backend.setMediaEventListener { mutableMediaEvents.tryEmit(it) }
                backend.setTransferEventListener { mutableTransferEvents.tryEmit(it) }
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
