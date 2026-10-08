#pragma once
#include <stdint.h>
#ifdef __cplusplus
#define YV_NOEXCEPT noexcept
extern "C" {
#else
#define YV_NOEXCEPT
#endif
struct YvHandle;
struct YvEvent { int32_t state; int32_t error_code; uint64_t dropped; };
struct YvRegistration {
    uint64_t token;
    uint64_t sequence;
    int32_t phase; /* 1 started, 2 final */
    int32_t renew;
    int32_t status;
    int32_t sip_code;
    uint32_t expires;
    int32_t failure_kind; /* 0 none, 1 SIP, 2 DNS, 3 TLS, 4 offline, 5 transport */
};
struct YvCall { uint64_t token; uint64_t account_token; uint64_t sequence; int32_t state; int32_t sip_code; int32_t audio_active; int32_t audio_error; int32_t muted; int32_t media_active; int32_t recording; int32_t native_code; int32_t failure_kind; int32_t incoming; int32_t caller_len; uint8_t caller[65]; uint64_t connected_ms; };
int32_t yv_call_list(struct YvHandle*, struct YvCall* output, uint32_t capacity, uint32_t* count) YV_NOEXCEPT;
int32_t yv_call_answer(struct YvHandle*, uint64_t token) YV_NOEXCEPT;
int32_t yv_call_reject(struct YvHandle*, uint64_t token, int32_t status) YV_NOEXCEPT;
int32_t yv_call_reject(struct YvHandle*, uint64_t token, int32_t status) YV_NOEXCEPT;
int32_t yv_call_start(struct YvHandle*, uint64_t account, uint64_t token, const uint8_t* uri, uint32_t len, int32_t audio) YV_NOEXCEPT;
int32_t yv_call_snapshot(struct YvHandle*, uint64_t token, struct YvCall*) YV_NOEXCEPT;
int32_t yv_call_hangup(struct YvHandle*, uint64_t token) YV_NOEXCEPT;
struct YvCallControls { int32_t held; int32_t transfer_pending; int32_t transfer_code; };
int32_t yv_call_controls(struct YvHandle*, uint64_t token, struct YvCallControls*) YV_NOEXCEPT;
int32_t yv_call_hold(struct YvHandle*, uint64_t token, int32_t held) YV_NOEXCEPT;
int32_t yv_call_transfer(struct YvHandle*, uint64_t token, const uint8_t* uri, uint32_t len, uint64_t consultation) YV_NOEXCEPT;
int32_t yv_call_mute(struct YvHandle*, uint64_t token, int32_t muted) YV_NOEXCEPT;
int32_t yv_call_record_start(struct YvHandle*, uint64_t token, const uint8_t* path, uint32_t len) YV_NOEXCEPT;
int32_t yv_call_record_stop(struct YvHandle*, uint64_t token) YV_NOEXCEPT;
int32_t yv_call_dtmf(struct YvHandle*, uint64_t token, const uint8_t* digits, uint32_t len) YV_NOEXCEPT;
int32_t yv_call_release(struct YvHandle*, uint64_t token) YV_NOEXCEPT;
struct YvAccountConfig {
    uint64_t token;
    const uint8_t* username; uint32_t username_len;
    const uint8_t* host; uint32_t host_len;
    const uint8_t* password; uint32_t password_len;
    uint32_t port; int32_t transport; /* 0 UDP, 1 TCP, 2 TLS */
};
int32_t yv_account_add(struct YvHandle*, const struct YvAccountConfig*) YV_NOEXCEPT;
int32_t yv_account_remove(struct YvHandle*, uint64_t token) YV_NOEXCEPT;
int32_t yv_account_register(struct YvHandle*, uint64_t token, int32_t renew) YV_NOEXCEPT;
int32_t yv_registration(struct YvHandle*, uint64_t token, struct YvRegistration*) YV_NOEXCEPT;
int32_t yv_failure_kind(int32_t status, int32_t sip_code) YV_NOEXCEPT;
int32_t yv_network_online(void) YV_NOEXCEPT;
int32_t yv_create(struct YvHandle** output) YV_NOEXCEPT;
int32_t yv_start(struct YvHandle* handle, uint32_t udp_port) YV_NOEXCEPT;
int32_t yv_stop(struct YvHandle* handle) YV_NOEXCEPT;
int32_t yv_pump(struct YvHandle* handle) YV_NOEXCEPT;
int32_t yv_next_event(struct YvHandle* handle, struct YvEvent* output) YV_NOEXCEPT;
int32_t yv_destroy(struct YvHandle* handle) YV_NOEXCEPT;
#ifdef __cplusplus
}
#endif
