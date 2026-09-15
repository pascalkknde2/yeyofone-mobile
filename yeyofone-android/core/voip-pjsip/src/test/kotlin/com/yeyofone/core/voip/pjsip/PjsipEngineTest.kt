package com.yeyofone.core.voip.pjsip

import com.yeyofone.core.voip.EngineState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.pjsip.pjsua2.pjsip_transport_type_e

@OptIn(ExperimentalCoroutinesApi::class)
class PjsipEngineTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `transport mapping uses generated PJSIP constants`() {
        assertEquals(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, SipTransport.UDP.toPjsipTransportType())
        assertEquals(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, SipTransport.TCP.toPjsipTransportType())
        assertEquals(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, SipTransport.TLS.toPjsipTransportType())
    }

    @Test
    fun `start serializes the endpoint lifecycle in order`() = runTest(dispatcher) {
        val backend = RecordingBackend()
        val engine = PjsipEngine(backend, PjsipEngineConfiguration(), dispatcher)

        engine.start()

        assertEquals(listOf("create", "initialize", "transports:UDP", "start"), backend.calls)
        assertEquals(EngineState.Running, engine.state.value)
    }

    @Test
    fun `repeated start is idempotent`() = runTest(dispatcher) {
        val backend = RecordingBackend()
        val engine = PjsipEngine(backend, PjsipEngineConfiguration(), dispatcher)

        engine.start()
        engine.start()

        assertEquals(1, backend.calls.count { it == "start" })
    }

    @Test
    fun `initialization failure attempts cleanup and becomes failed`() = runTest(dispatcher) {
        val backend = RecordingBackend(failAt = "initialize")
        val engine = PjsipEngine(backend, PjsipEngineConfiguration(), dispatcher)

        engine.start()

        assertIs<EngineState.Failed>(engine.state.value)
        assertEquals("destroy", backend.calls.last())
    }

    @Test
    fun `stop destroys once and reaches stopped`() = runTest(dispatcher) {
        val backend = RecordingBackend()
        val engine = PjsipEngine(backend, PjsipEngineConfiguration(), dispatcher)

        engine.start()
        engine.stop()
        engine.stop()

        assertEquals(1, backend.calls.count { it == "destroy" })
        assertEquals(EngineState.Stopped, engine.state.value)
    }

    private class RecordingBackend(private val failAt: String? = null) : EndpointBackend {
        val calls = mutableListOf<String>()
        override fun create() = record("create")
        override fun initialize(configuration: PjsipEngineConfiguration) = record("initialize")
        override fun createTransports(transports: Set<SipTransport>) =
            record("transports:${transports.joinToString { it.name }}")
        override fun start() = record("start")
        override fun destroy() = record("destroy")

        private fun record(call: String) {
            calls += call
            if (call == failAt) error("failure at $call")
        }
    }
}
