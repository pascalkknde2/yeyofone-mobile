// Compile the exact shim implementation into an isolated native test executable.
// No test-only command is added to the production ABI.
#include "bridge.cpp"
#include <stdexcept>
#include <iostream>
void require(bool value) { if (!value) throw std::runtime_error("Bridge assertion failed"); }
int main() {
    try {
        require(yv_create(nullptr) == -3);
        require(yv_start(nullptr, 0) == -3);
        YvHandle* handle = nullptr;
        require(yv_create(&handle) == 0);
        YvHandle* duplicate = nullptr;
        require(yv_create(&duplicate) == -5 && duplicate == nullptr);
        require(yv_start(handle, 65536) == -6);
        int result = 0;
        std::thread wrong_thread([&] { result = yv_start(handle, 0); });
        wrong_thread.join();
        require(result == -4);
        require(yv_start(handle, 0) == 0);
        YvCallControls controls{};
        require(yv_call_controls(handle,123,&controls)==-6);
        require(yv_call_hold(handle,123,1)==-6);
        require(yv_call_merge(handle,123,124)==-6);
        require(yv_call_merge(handle,123,123)==-6);
        require(yv_call_transfer(handle,123,nullptr,0,0)==-6);
        std::thread control_thread([&]{result=yv_call_hold(handle,123,1);});control_thread.join();require(result==-4);
        std::thread merge_thread([&]{result=yv_call_merge(handle,123,124);});merge_thread.join();require(result==-4);
        YvEvent output{};
        while (yv_next_event(handle, &output) == 1) {}
        // Exercise the real transport callback on another thread, copying only numeric fields.
        std::thread callback_thread([&] {
            pj::OnTransportStateParam event{};
            event.state = PJSIP_TP_STATE_DISCONNECTED;
            event.lastError = 70001;
            for (int i = 0; i < 20; ++i) handle->endpoint->onTransportState(event);
        });
        callback_thread.join();
        unsigned count = 0;
        while (yv_next_event(handle, &output) == 1) {
            require(output.state == PJSIP_TP_STATE_DISCONNECTED && output.error_code == 70001 && output.dropped == 4);
            ++count;
        }
        require(count == 16);
        require(yv_stop(handle) == 0 && yv_stop(handle) == 0);
        require(yv_destroy(handle) == 0);
        std::cout << "Native callback bounds, thread guards and disposal pass\n";
        return 0;
    } catch (...) { return 1; }
}
