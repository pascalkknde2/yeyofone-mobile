#include "bridge.h"
#include <pjsua2.hpp>
#include <pj/log.h>
#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <map>
#include <cerrno>
#include <tuple>
#include <algorithm>
#include <filesystem>
#include <cctype>
#include <pjlib-util/errno.h>
#include <pjsip/sip_errno.h>
#ifndef _WIN32
#include <ifaddrs.h>
#include <net/if.h>
#include <sys/socket.h>
#endif

namespace {
std::atomic_bool occupied{false};
std::atomic<uint64_t> incoming_serial{1};
class EngineEndpoint;
class NativeCall;
}
struct YvHandle;
namespace {
class EngineEndpoint;
class NativeAccount final : public pj::Account {
    std::mutex mutex;
    YvRegistration latest{};
public:
    EngineEndpoint* endpoint;
    YvHandle* owner;
    std::string target_host;
    int target_port=0,target_transport=0;
    const uint64_t token;
    pj::TransportId transport = PJSUA_INVALID_ID;
    NativeAccount(uint64_t t,EngineEndpoint* e,YvHandle* h) : endpoint(e), owner(h), token(t) { latest.token = t; }
    ~NativeAccount() override { shutdown(); }
    void onRegStarted(pj::OnRegStartedParam& event) noexcept override {
        try { std::lock_guard<std::mutex> lock(mutex); ++latest.sequence; latest.phase=1; latest.renew=event.renew; latest.status=0; latest.sip_code=0; latest.expires=0; latest.failure_kind=0; } catch (...) {}
    }
    void onRegState(pj::OnRegStateParam& event) noexcept override;
    void onIncomingCall(pj::OnIncomingCallParam& event) noexcept override;
    YvRegistration snapshot() { std::lock_guard<std::mutex> lock(mutex); return latest; }
};
class EngineEndpoint final : public pj::Endpoint {
    std::mutex mutex;
    std::array<YvEvent, 16> events{};
    using FailureKey=std::tuple<std::string,int,int>;
    std::map<FailureKey,int> failure_codes;
    size_t head = 0, count = 0;
    uint64_t dropped = 0;
public:
    static FailureKey key(std::string host,int port,int transport) {std::transform(host.begin(),host.end(),host.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});return {host,port,transport};}
    int last_error(const std::string& host,int port,int transport) {std::lock_guard<std::mutex> lock(mutex);auto found=failure_codes.find(key(host,port,transport));return found==failure_codes.end() ? 0 : found->second;}
    void clear_error(const std::string& host,int port,int transport) {std::lock_guard<std::mutex> lock(mutex);failure_codes.erase(key(host,port,transport));}
    void onTransportState(const pj::OnTransportStateParam& event) noexcept override {
        try {
            std::lock_guard<std::mutex> lock(mutex);
            if (event.hnd) {
                // PJSUA2 supplies a live transport for the callback duration. Only numeric error/port data is retained.
                const auto* transport=static_cast<const pjsip_transport*>(event.hnd);
                // Never dereference transport->factory: PJSIP documents that it may already be shutdown.
                const auto& name=transport->remote_name;
                if (name.host.ptr && name.host.slen>0 && name.host.slen<=253 && name.port>0 && (event.lastError!=0 || event.state==PJSIP_TP_STATE_CONNECTED)) {
                    const auto k=key(std::string(name.host.ptr,name.host.slen),name.port,transport->key.type & ~PJSIP_TRANSPORT_IPV6);
                    if (failure_codes.size()>=64 && !failure_codes.count(k)) failure_codes.erase(failure_codes.begin());
                    failure_codes[k]=event.lastError;
                }
            }
            if (count == events.size()) { ++dropped; return; }
            events[(head + count) % events.size()] = {static_cast<int32_t>(event.state), event.lastError, 0};
            ++count;
        } catch (...) { /* Nothing unwinds into PJSIP callback frames. */ }
    }
    bool next(YvEvent& output) {
        std::lock_guard<std::mutex> lock(mutex);
        if (count == 0) return false;
        output = events[head]; output.dropped = dropped;
        head = (head + 1) % events.size(); --count;
        return true;
    }
};
void NativeAccount::onRegState(pj::OnRegStateParam& event) noexcept {
    try {
        int status=event.status;
        // Verified in PJSIP 2.17 sip_transaction.c: local DNS errors become a synthetic 502.
        if (status==0 && !event.rdata.pjRxData && event.code==PJSIP_SC_BAD_GATEWAY) status=PJ_ERESOLVE;
        // Local transport/TLS failures become synthetic 503. Keep the actual callback error when available.
        if (status==0 && !event.rdata.pjRxData && event.code==PJSIP_SC_SERVICE_UNAVAILABLE) {
            status=endpoint->last_error(target_host,target_port,target_transport);
            if (status==0) status=PJSIP_ETPNOTAVAIL;
        }
        std::lock_guard<std::mutex> lock(mutex);++latest.sequence;latest.phase=2;latest.status=status;latest.sip_code=event.code;latest.expires=event.expiration;latest.failure_kind=yv_failure_kind(status,event.code);
    } catch (...) {}
}

// Only the serialized owner creates/destroys calls. Callbacks copy scalars;
// terminal objects survive until Rust has consumed their snapshot.
class NativeCall final : public pj::Call {
    std::mutex mutex;
    YvCall latest{};
    pj::Endpoint& endpoint;
    bool audio, capture_connected=false, playback_connected=false;
    pj::ToneGenerator ringtone;
    bool ringtone_active=false;
    pj::ToneGenerator ringback;
    bool ringback_created=false,ringback_active=false;
    std::unique_ptr<pj::AudioMediaRecorder> recorder;
    std::filesystem::path recording_final_path, recording_temp_path;
    int recording_media_index=-1;
    bool recording_audio_connected=false, recording_capture_connected=false;
    int media_index=-1;
public:
    const uint64_t account_token;
    bool hold_pending=false, previous_hold=false;
    std::chrono::steady_clock::time_point transfer_started;
    YvCallControls controls{};
    bool cancelled=false;
    bool is_incoming=false;
    NativeCall(NativeAccount& account,uint64_t token,pj::Endpoint& ep,bool use_audio,int call_id=PJSUA_INVALID_ID,bool incoming=false):pj::Call(account,call_id),endpoint(ep),audio(use_audio),account_token(account.token),is_incoming(incoming){latest.token=token;latest.account_token=account.token;latest.incoming=incoming?1:0;if(incoming){latest.state=PJSIP_INV_STATE_INCOMING;capture_caller();}}
    ~NativeCall() override { stop_recording(); stop_ringtone(); stop_ringback(); }
    void capture_caller() noexcept {try{auto info=getInfo();auto* pool=pjsua_pool_create("caller-check",512,512);if(!pool)return;struct Guard{pj_pool_t*p;~Guard(){pj_pool_release(p);}}guard{pool};std::string uri=info.remoteUri;auto* parsed=pjsip_parse_uri(pool,uri.data(),uri.size(),0);if(!parsed||(!PJSIP_URI_SCHEME_IS_SIP(parsed)&&!PJSIP_URI_SCHEME_IS_SIPS(parsed)))return;auto* sip=static_cast<pjsip_sip_uri*>(pjsip_uri_get_uri(parsed));if(sip->user.slen<1||sip->user.slen>64)return;for(pj_ssize_t i=0;i<sip->user.slen;++i){unsigned char c=static_cast<unsigned char>(sip->user.ptr[i]);if(!(std::isalnum(c)||c=='+'||c=='*'||c=='#'||c=='-'||c=='_'||c=='.'))return;}std::lock_guard<std::mutex> lock(mutex);latest.caller_len=static_cast<int32_t>(sip->user.slen);std::copy(sip->user.ptr,sip->user.ptr+sip->user.slen,latest.caller);}catch(...){} }
    void update() noexcept {
        try {
            auto info=getInfo();
            std::lock_guard<std::mutex> lock(mutex);
            ++latest.sequence;latest.state=info.state;latest.sip_code=info.lastStatusCode;
            latest.connected_ms=static_cast<uint64_t>(info.connectDuration.sec)*1000+info.connectDuration.msec;
            if(info.state==PJSIP_INV_STATE_DISCONNECTED){latest.audio_active=0;latest.media_active=0;}
        } catch (...) {}
    }
    void route() noexcept {
        try {
            auto info=getInfo();
            if(ringtone_active&&info.state!=PJSIP_INV_STATE_INCOMING&&info.state!=PJSIP_INV_STATE_EARLY)stop_ringtone();
            if(!is_incoming){
                // Local ringback while the far end rings, unless it sends its own early media (183 with SDP).
                const bool early_media=std::any_of(info.media.begin(),info.media.end(),[](const pj::CallMediaInfo& m){return m.type==PJMEDIA_TYPE_AUDIO&&m.status==PJSUA_CALL_MEDIA_ACTIVE;});
                if(info.state==PJSIP_INV_STATE_EARLY&&!early_media&&!cancelled)start_ringback();else stop_ringback();
            }
            if(info.state==PJSIP_INV_STATE_DISCONNECTED){stop_recording();return;}
            if(controls.held){
                if(media_index>=0){try{auto stream=getAudioMedia(media_index);auto& d=endpoint.audDevManager();if(capture_connected)d.getCaptureDevMedia().stopTransmit(stream);if(playback_connected)stream.stopTransmit(d.getPlaybackDevMedia());if(recorder&&recording_audio_connected)stream.stopTransmit(*recorder);}catch(...){}}
                capture_connected=false;playback_connected=false;recording_audio_connected=false;
                if(recorder&&recording_capture_connected){try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*recorder);}catch(...){}recording_capture_connected=false;}
                std::lock_guard<std::mutex> lock(mutex);latest.audio_active=0;latest.media_active=0;return;
            }
            for(auto& item:info.media) {
                if(item.type!=PJMEDIA_TYPE_AUDIO || item.status!=PJSUA_CALL_MEDIA_ACTIVE)continue;
                if(media_index!=static_cast<int>(item.index)){capture_connected=false;playback_connected=false;media_index=item.index;}
                bool muted;{std::lock_guard<std::mutex> lock(mutex);muted=latest.muted!=0;}
                if(audio){
                    auto stream=getAudioMedia(item.index);
                    auto& devices=endpoint.audDevManager();
                    if(!playback_connected){stream.startTransmit(devices.getPlaybackDevMedia());playback_connected=true;}
                    const bool send=info.state==PJSIP_INV_STATE_CONFIRMED && !muted && !cancelled;
                    if(send&&!capture_connected){devices.getCaptureDevMedia().startTransmit(stream);capture_connected=true;}
                    if(!send&&capture_connected){devices.getCaptureDevMedia().stopTransmit(stream);capture_connected=false;}
                    if(recorder){
                        if(recording_media_index!=static_cast<int>(item.index)){
                            if(recording_audio_connected&&recording_media_index>=0){try{auto old_stream=getAudioMedia(recording_media_index);old_stream.stopTransmit(*recorder);}catch(...){}}
                            recording_audio_connected=false;
                            recording_media_index=static_cast<int>(item.index);
                        }
                        if(!recording_audio_connected){stream.startTransmit(*recorder);recording_audio_connected=true;}
                        if(send&&!recording_capture_connected){devices.getCaptureDevMedia().startTransmit(*recorder);recording_capture_connected=true;}
                        if(!send&&recording_capture_connected){devices.getCaptureDevMedia().stopTransmit(*recorder);recording_capture_connected=false;}
                    }
                }
                std::lock_guard<std::mutex> lock(mutex);++latest.sequence;latest.media_active=1;latest.audio_active=audio?1:0;latest.recording=recorder?1:0;return;
            }
            if(recorder&&recording_capture_connected){try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*recorder);}catch(...){}recording_capture_connected=false;}
            std::lock_guard<std::mutex> lock(mutex);latest.audio_active=0;latest.media_active=0;latest.recording=recorder?1:0;
        } catch(const pj::Error& e){std::lock_guard<std::mutex> lock(mutex);latest.audio_error=e.status;latest.audio_active=0;}
          catch(...){std::lock_guard<std::mutex> lock(mutex);latest.audio_error=-1;latest.audio_active=0;}
    }
    void hold(bool value){
        if(getInfo().state!=PJSIP_INV_STATE_CONFIRMED||controls.transfer_pending||hold_pending)throw pj::Error(PJ_EINVALIDOP,"hold","","",0);
        if(controls.held==value)return;
        pj::CallOpParam p(true);p.opt.audioCount=1;p.opt.videoCount=0;p.opt.textCount=0;
        previous_hold=controls.held;hold_pending=true;
        try{if(value)setHold(p);else{p.opt.flag=PJSUA_CALL_UNHOLD;reinvite(p);}}catch(...){hold_pending=false;throw;}
        controls.held=value;route();
    }
    void onCallTransferStatus(pj::OnCallTransferStatusParam& p) noexcept override {controls.transfer_code=p.statusCode;if(p.finalNotify||(p.statusCode>=200&&p.statusCode<300)){controls.transfer_pending=0;p.cont=false;}}
    void capture_error(const pj::SipEvent& event) noexcept {try{if(event.type==PJSIP_EVENT_TSX_STATE && event.body.tsxState.type==PJSIP_EVENT_TRANSPORT_ERROR){std::lock_guard<std::mutex> lock(mutex);latest.native_code=event.body.tsxState.src.status;latest.failure_kind=yv_failure_kind(latest.native_code,0);}}catch(...){} }
    void onCallTsxState(pj::OnCallTsxStateParam& event) noexcept override {
        capture_error(event.e);
        if(event.e.type!=PJSIP_EVENT_TSX_STATE)return;
        auto& tsx=event.e.body.tsxState.tsx;
        if(hold_pending&&tsx.role==PJSIP_ROLE_UAC&&tsx.method=="INVITE"&&tsx.statusCode>=200){
            hold_pending=false;
            if(tsx.statusCode>=300){controls.held=previous_hold;route();}
        }
    }
    void onCallMediaTransportState(pj::OnCallMediaTransportStateParam& event) noexcept override {if(event.status){try{std::lock_guard<std::mutex> lock(mutex);latest.audio_error=event.status;latest.audio_active=0;}catch(...){}}}
    void onCallState(pj::OnCallStateParam& event) noexcept override {capture_error(event.e);update();route();}
    void onCallMediaState(pj::OnCallMediaStateParam&) noexcept override {update();route();}
    void start_ringtone() noexcept {if(!is_incoming||ringtone_active)return;try{auto& devices=endpoint.audDevManager();devices.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);ringtone.createToneGenerator(16000,1);pj::ToneDesc tone;tone.freq1=440;tone.freq2=480;tone.on_msec=1000;tone.off_msec=3000;tone.volume=9000;pj::ToneDescVector tones;tones.push_back(tone);ringtone.play(tones,true);ringtone.startTransmit(devices.getPlaybackDevMedia());ringtone_active=true;}catch(...){try{ringtone.stop();}catch(...){}}}
    // UK ringback cadence (400+450 Hz: 0.4 s on, 0.2 s off, 0.4 s on, 2 s off).
    void start_ringback() noexcept {if(is_incoming||!audio||ringback_active)return;try{auto& devices=endpoint.audDevManager();if(!ringback_created){ringback.createToneGenerator(16000,1);ringback_created=true;}pj::ToneDesc first;first.freq1=400;first.freq2=450;first.on_msec=400;first.off_msec=200;first.volume=0;pj::ToneDesc second=first;second.off_msec=2000;pj::ToneDescVector tones;tones.push_back(first);tones.push_back(second);ringback.play(tones,true);ringback.startTransmit(devices.getPlaybackDevMedia());ringback_active=true;}catch(...){try{ringback.stop();}catch(...){}}}
    void stop_ringback() noexcept {if(!ringback_active)return;try{ringback.stopTransmit(endpoint.audDevManager().getPlaybackDevMedia());}catch(...){}try{ringback.stop();}catch(...){}ringback_active=false;}
    void stop_ringtone() noexcept {if(!ringtone_active)return;try{ringtone.stopTransmit(endpoint.audDevManager().getPlaybackDevMedia());}catch(...){}try{ringtone.stop();}catch(...){}ringtone_active=false;}
    void send_ringing(){if(!is_incoming)return;pj::CallOpParam p;p.statusCode=PJSIP_SC_RINGING;pj::Call::answer(p);}
    void answer(){auto state=getInfo().state;if(!is_incoming||(state!=PJSIP_INV_STATE_INCOMING&&state!=PJSIP_INV_STATE_EARLY))throw pj::Error(PJ_EINVALIDOP,"answer","","",0);stop_ringtone();audio=true;pj::CallOpParam p(true);p.statusCode=PJSIP_SC_OK;p.opt.audioCount=1;p.opt.videoCount=0;p.opt.textCount=0;auto& d=endpoint.audDevManager();d.setCaptureDev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV);d.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);try{pj::Call::answer(p);}catch(...){d.setNoDev();throw;}}
    void reject(){if(!is_incoming)return;auto state=getInfo().state;if(state!=PJSIP_INV_STATE_INCOMING&&state!=PJSIP_INV_STATE_EARLY)return;stop_ringtone();pj::CallOpParam p;p.statusCode=PJSIP_SC_DECLINE;pj::Call::hangup(p);cancelled=true;}
    void start_recording(const std::string& final_path) {
        auto info=getInfo();
        if(info.state!=PJSIP_INV_STATE_CONFIRMED||!audio||recorder||controls.held)throw pj::Error(PJ_EINVALIDOP,"recording","","",0);
        int audio_index=-1;
        for(const auto& item:info.media)if(item.type==PJMEDIA_TYPE_AUDIO&&item.status==PJSUA_CALL_MEDIA_ACTIVE){audio_index=static_cast<int>(item.index);break;}
        if(audio_index<0)throw pj::Error(PJ_EINVALIDOP,"recording","","",0);
        recording_final_path=std::filesystem::path(final_path);
        recording_temp_path=recording_final_path.parent_path()/(recording_final_path.stem().string()+".partial.wav");
        if(std::filesystem::exists(recording_final_path)||std::filesystem::exists(recording_temp_path))throw pj::Error(PJ_EEXISTS,"recording","","",0);
        auto candidate=std::make_unique<pj::AudioMediaRecorder>();
        candidate->createRecorder(recording_temp_path.string());
        auto stream=getAudioMedia(audio_index);
        bool stream_linked=false,capture_linked=false;
        try{
            stream.startTransmit(*candidate);stream_linked=true;
            bool muted;{std::lock_guard<std::mutex> lock(mutex);muted=latest.muted!=0;}
            if(!muted){endpoint.audDevManager().getCaptureDevMedia().startTransmit(*candidate);capture_linked=true;}
        }catch(...){
            if(capture_linked)try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*candidate);}catch(...){}
            if(stream_linked)try{stream.stopTransmit(*candidate);}catch(...){}
            candidate.reset();std::error_code ec;std::filesystem::remove(recording_temp_path,ec);throw;
        }
        recorder=std::move(candidate);recording_media_index=audio_index;recording_audio_connected=true;recording_capture_connected=capture_linked;
        std::lock_guard<std::mutex> lock(mutex);latest.recording=1;
    }
    int stop_recording() noexcept {
        if(!recorder)return 0;
        if(recording_audio_connected&&recording_media_index>=0){try{auto stream=getAudioMedia(recording_media_index);stream.stopTransmit(*recorder);}catch(...){}}
        if(recording_capture_connected){try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*recorder);}catch(...){}}
        recording_audio_connected=false;recording_capture_connected=false;recording_media_index=-1;
        recorder.reset();
        std::error_code ec;std::filesystem::rename(recording_temp_path,recording_final_path,ec);
        recording_temp_path.clear();recording_final_path.clear();
        {std::lock_guard<std::mutex> lock(mutex);latest.recording=0;}
        return ec? -1 : 0;
    }
    void mute(bool value) { {std::lock_guard<std::mutex> lock(mutex);latest.muted=value?1:0;}route();auto snap=snapshot();if(snap.audio_error)throw pj::Error(snap.audio_error,"audio","","",0); }
    YvCall snapshot(){if(controls.transfer_pending&&std::chrono::steady_clock::now()-transfer_started>std::chrono::seconds(120)){controls.transfer_pending=0;controls.transfer_code=408;}update();std::lock_guard<std::mutex> lock(mutex);return latest;}
};

}
struct YvHandle {
    const std::thread::id owner = std::this_thread::get_id();
    std::unique_ptr<EngineEndpoint> endpoint;
    bool running = false;
    bool ringtone = true;
    std::map<uint64_t,std::unique_ptr<NativeCall>> calls;
    std::map<uint64_t,std::unique_ptr<NativeAccount>> accounts;
};
namespace {
void NativeAccount::onIncomingCall(pj::OnIncomingCallParam& event) noexcept {
 try {
  if(!owner||!owner->running||owner->calls.size()>=16||std::any_of(owner->calls.begin(),owner->calls.end(),[](const auto& item){return item.second->isActive();})){pjsua_call_answer(event.callId,PJSIP_SC_BUSY_HERE,nullptr,nullptr);return;}
  const uint64_t serial=incoming_serial.fetch_add(1);if(serial==0||serial>0x7fffffffffffffffULL){pjsua_call_answer(event.callId,PJSIP_SC_SERVICE_UNAVAILABLE,nullptr,nullptr);return;}
  const uint64_t token=0x8000000000000000ULL|serial;
  auto call=std::make_unique<NativeCall>(*this,token,*endpoint,false,event.callId,true);
  auto* incoming=call.get();owner->calls.emplace(token,std::move(call));if(owner->ringtone)incoming->start_ringtone();incoming->send_ringing();
 } catch (...) {pjsua_call_answer(event.callId,PJSIP_SC_TEMPORARILY_UNAVAILABLE,nullptr,nullptr);}
}
int32_t check(YvHandle* h) {
    if (!h) return -3;
    return h->owner == std::this_thread::get_id() ? 0 : -4;
}
int32_t dispose(YvHandle* h) noexcept {
    try {
        if (h->endpoint) {
            h->calls.clear();
            for (auto& item : h->accounts) { pj::AccountShutdownParam options; options.force=true; item.second->shutdown2(options); }
            h->accounts.clear();
            h->endpoint->libDestroy(PJSUA_DESTROY_NO_NETWORK);
            h->endpoint.reset();
        }
        h->running = false;
        return 0;
    } catch (const pj::Error& e) { return e.status; }
      catch (...) { return -1; }
}
}
extern "C" int32_t yv_create(YvHandle** output) noexcept {
    if (!output) return -3;
    *output = nullptr;
    bool expected = false;
    if (!occupied.compare_exchange_strong(expected, true)) return -5;
    try { *output = new YvHandle; return 0; }
    catch (...) { occupied.store(false); return -2; }
}
extern "C" int32_t yv_start(YvHandle* h, uint32_t udp_port) noexcept {
    if (auto code = check(h)) return code;
    if (udp_port > 65535) return -6;
    if (h->running) return 0;
    // Dispose partially initialized state before any restart.
    if (auto code = dispose(h)) return code;
    try {
        pj_log_set_level(0);
        h->endpoint = std::make_unique<EngineEndpoint>();
        h->endpoint->libCreate(); // Registers this owner thread as PJSIP's main thread.
        pj::EpConfig config;
        config.uaConfig.threadCnt = 0;
        config.uaConfig.mainThreadOnly = true;
        config.medConfig.threadCnt = 0;
        config.medConfig.hasIoqueue = false;
        config.logConfig.level = 0;
        config.logConfig.consoleLevel = 0;
        config.logConfig.msgLogging = 0;
        h->endpoint->libInit(config);
        pj::TransportConfig transport;
        transport.port = udp_port;
        transport.boundAddress = "127.0.0.1";
        transport.publicAddress = "127.0.0.1";
        h->endpoint->transportCreate(PJSIP_TRANSPORT_UDP, transport);
        h->endpoint->audDevManager().setNoDev();
        h->endpoint->libStart();
        h->running = true;
        return 0;
    } catch (const pj::Error& e) { const int32_t status = e.status; dispose(h); return status; }
      catch (...) { dispose(h); return -1; }
}
extern "C" int32_t yv_stop(YvHandle* h) noexcept {
    if (auto code = check(h)) return code;
    return dispose(h);
}
extern "C" int32_t yv_pump(YvHandle* h) noexcept {
    if (auto code = check(h)) return code;
    if (!h->running) return 0;
    try { return h->endpoint->libHandleEvents(0) < 0 ? -7 : 0; }
    catch (const pj::Error& e) { return e.status; }
    catch (...) { return -1; }
}
extern "C" int32_t yv_next_event(YvHandle* h, YvEvent* output) noexcept {
    if (auto code = check(h)) return code;
    if (!output) return -3;
    try { return h->endpoint && h->endpoint->next(*output) ? 1 : 0; }
    catch (...) { return -1; }
}
extern "C" int32_t yv_destroy(YvHandle* h) noexcept {
    if (auto code = check(h)) return code;
    const int32_t status = dispose(h);
    if (status != 0) return status; // Never delete a potentially live endpoint after failed destruction.
    delete h;
    occupied.store(false);
    return 0;
}

extern "C" int32_t yv_failure_kind(int32_t status, int32_t sip_code) noexcept {
    if (status == PJ_ERESOLVE || (status >= PJLIB_UTIL_EDNSQRYTOOSMALL && status <= PJLIB_UTIL_EDNS_NOTZONE)) return 2;
    if (status >= PJSIP_TLS_EUNKNOWN && status <= PJSIP_TLS_ECERTVERIF) return 3;
#ifndef _WIN32
    if (status == PJ_STATUS_FROM_OS(ENETDOWN) || status == PJ_STATUS_FROM_OS(ENETUNREACH) || status == PJ_STATUS_FROM_OS(EHOSTUNREACH)) return 4;
#endif
    if (status == PJSIP_EFAILEDCREDENTIAL || status == PJSIP_ENOCREDENTIAL) return 1;
    if (status != 0) return 5;
    return sip_code >= 300 ? 1 : 0;
}
extern "C" int32_t yv_network_online() noexcept {
#ifndef _WIN32
    ifaddrs* list=nullptr;
    if (getifaddrs(&list) != 0) return -1;
    bool up=false;
    for (auto* i=list;i;i=i->ifa_next) {
        if (i->ifa_addr && (i->ifa_flags & IFF_UP) && (i->ifa_flags & IFF_RUNNING) && !(i->ifa_flags & IFF_LOOPBACK) && (i->ifa_addr->sa_family==AF_INET || i->ifa_addr->sa_family==AF_INET6)) {up=true;break;}
    }
    freeifaddrs(list);return up ? 1 : 0;
#else
    return -1; // No unsupported-platform connectivity claim.
#endif
}
extern "C" int32_t yv_account_add(YvHandle* h, const YvAccountConfig* c) noexcept {
    if (auto code=check(h)) return code;
    if (!h->running || !c || !c->token || !c->username || !c->host || !c->password || c->username_len==0 || c->username_len>128 || c->host_len==0 || c->host_len>253 || c->password_len==0 || c->password_len>4096 || c->port==0 || c->port>65535 || c->transport<0 || c->transport>2) return -6;
    if (h->accounts.count(c->token)) return -6;
    // PJSUA 2.17 has eight transport slots; one is reserved for the engine.
    if (h->accounts.size()>=7) return -9;
    pj::TransportId transport=PJSUA_INVALID_ID;
    try {
        pj::TransportConfig tcp; tcp.port=0; tcp.boundAddress="0.0.0.0";
        if (c->transport==2) { tcp.tlsConfig.verifyServer=true; tcp.tlsConfig.CaListFile="/etc/ssl/cert.pem"; tcp.tlsConfig.proto=PJ_SSL_SOCK_PROTO_TLS1_2 | PJ_SSL_SOCK_PROTO_TLS1_3; }
        const auto type=c->transport==0 ? PJSIP_TRANSPORT_UDP : (c->transport==1 ? PJSIP_TRANSPORT_TCP : PJSIP_TRANSPORT_TLS);
        transport=h->endpoint->transportCreate(type,tcp);
        std::string host(reinterpret_cast<const char*>(c->host),c->host_len);
        if (host.find(':') != std::string::npos) host="["+host+"]";
        const std::string protocol=c->transport==2 ? "sips:" : "sip:";
        const std::string suffix=c->transport==0 ? ";transport=udp" : (c->transport==1 ? ";transport=tcp" : ";transport=tls");
        pj::AccountConfig config;
        config.idUri=protocol+std::string(reinterpret_cast<const char*>(c->username),c->username_len)+"@"+host;
        config.regConfig.registrarUri=protocol+host+":"+std::to_string(c->port)+suffix;
        config.regConfig.registerOnAdd=false;config.regConfig.disableRegOnModify=true;
        config.regConfig.retryIntervalSec=0;config.regConfig.firstRetryIntervalSec=0;config.regConfig.randomRetryIntervalSec=0;
        config.regConfig.timeoutSec=300;config.regConfig.delayBeforeRefreshSec=30;config.regConfig.unregWaitMsec=1000;
        config.sipConfig.transportId=transport;
        // Same media/NAT policy as yeyofone-android, live-verified against FreeSWITCH: without STUN/ICE,
        // advertise the address learned from REGISTER instead of a private device address; omit RFC 5626
        // ";ob" (FreeSWITCH then sends no RTP at all on inbound or bridged calls); offer plain RTP only,
        // since this bridge has no SRTP setting and SDES keys must not travel over UDP/TCP signaling.
        config.natConfig.sdpNatRewriteUse=1;config.natConfig.sipOutboundUse=0;
        config.mediaConfig.srtpUse=PJMEDIA_SRTP_DISABLED;
        config.sipConfig.authCreds.emplace_back("digest","*",std::string(reinterpret_cast<const char*>(c->username),c->username_len),0,std::string(reinterpret_cast<const char*>(c->password),c->password_len));
        auto account=std::make_unique<NativeAccount>(c->token,h->endpoint.get(),h);account->transport=transport;
        account->target_host=std::string(reinterpret_cast<const char*>(c->host),c->host_len);account->target_port=c->port;account->target_transport=type;
        account->create(config,false);
        // Erase this transient C++ config copy; PJSUA keeps its own credential pool.
        auto& secret=config.sipConfig.authCreds.front().data;
        for (volatile char* p=secret.empty() ? nullptr : &secret[0]; p && p<&secret[0]+secret.size(); ++p) *p=0;
        h->accounts.emplace(c->token,std::move(account));return 0;
    } catch (const pj::Error& e) {if (transport!=PJSUA_INVALID_ID) try {h->endpoint->transportClose(transport);} catch (...) {} return e.status;}
    catch (...) {if (transport!=PJSUA_INVALID_ID) try {h->endpoint->transportClose(transport);} catch (...) {} return -1;}
}
extern "C" int32_t yv_account_remove(YvHandle* h,uint64_t token) noexcept {
    if (auto code=check(h)) return code;
    for(auto& item:h->calls)if(item.second->account_token==token && item.second->isActive())return -9;
    auto found=h->accounts.find(token);if (found==h->accounts.end()) return 0;
    try {pj::AccountShutdownParam options;options.force=true;found->second->shutdown2(options);const auto transport=found->second->transport;h->accounts.erase(found);h->endpoint->transportClose(transport);return 0;}
    catch (const pj::Error& e) {return e.status;} catch (...) {return -1;}
}
extern "C" int32_t yv_account_register(YvHandle* h,uint64_t token,int32_t renew) noexcept {
    if (auto code=check(h)) return code;
    const auto found=h->accounts.find(token);if(found==h->accounts.end() || (renew!=0 && renew!=1)) return -6;
    try {if (renew) h->endpoint->clear_error(found->second->target_host,found->second->target_port,found->second->target_transport);found->second->setRegistration(renew!=0);return 0;}
    catch (const pj::Error& e) {return e.status;} catch (...) {return -1;}
}
extern "C" int32_t yv_registration(YvHandle* h,uint64_t token,YvRegistration* output) noexcept {
    if (auto code=check(h)) return code;
    if (!output) return -3;
    const auto found=h->accounts.find(token);if (found==h->accounts.end()) return -6;
    try {*output=found->second->snapshot();return 0;} catch (...) {return -1;}
}

extern "C" int32_t yv_call_start(YvHandle* h,uint64_t account,uint64_t token,const uint8_t* uri,uint32_t len,int32_t audio) noexcept {
    if(auto code=check(h))return code;
    if(!h->running||!token||!uri||len==0||len>512||(audio!=0&&audio!=1)||h->calls.size()>=2||h->calls.count(token)||std::any_of(h->calls.begin(),h->calls.end(),[](const auto& x){return x.second->isActive()&&!x.second->controls.held;}))return -6;
    auto found=h->accounts.find(account);if(found==h->accounts.end())return -6;
    try {
        std::string destination(reinterpret_cast<const char*>(uri),len);
        // Defense in depth: parse using pinned PJSIP, reject headers/params and
        // credential-bearing destinations. Rust additionally validates grammar.
        auto* pool=pjsua_pool_create("dial-check",1024,1024);
        if(!pool)return -2;
        struct PoolGuard{pj_pool_t* pool;~PoolGuard(){pj_pool_release(pool);}} guard{pool};
        auto* parsed=pjsip_parse_uri(pool,destination.data(),destination.size(),0);
        if(!parsed || (!PJSIP_URI_SCHEME_IS_SIP(parsed)&&!PJSIP_URI_SCHEME_IS_SIPS(parsed)))return -6;
        auto* target=static_cast<pjsip_sip_uri*>(pjsip_uri_get_uri(parsed));
        std::string host(target->host.ptr,target->host.slen);
        const bool secure=PJSIP_URI_SCHEME_IS_SIPS(parsed);
        const int port=target->port?target->port:(secure?5061:5060);
        auto& acc=*found->second;
        if(EngineEndpoint::key(host,port,acc.target_transport)!=EngineEndpoint::key(acc.target_host,acc.target_port,acc.target_transport)||secure!=(acc.target_transport==PJSIP_TRANSPORT_TLS)||target->passwd.slen || !pj_list_empty(&target->header_param))return -6;
        if(audio){try{auto& devices=h->endpoint->audDevManager();devices.setCaptureDev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV);devices.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);}catch(...){h->endpoint->audDevManager().setNoDev();return -11;}}
        auto call=std::make_unique<NativeCall>(acc,token,*h->endpoint,audio!=0);
        auto* pointer=call.get();h->calls.emplace(token,std::move(call));
        try{pj::CallOpParam options(true);options.opt.audioCount=1;options.opt.videoCount=0;options.opt.textCount=0;pointer->makeCall(destination,options);pointer->update();}
        catch(...){h->calls.erase(token);if(audio&&h->calls.empty())h->endpoint->audDevManager().setNoDev();throw;}
        return 0;
    }catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_snapshot(YvHandle* h,uint64_t token,YvCall* out) noexcept {
    if(auto code=check(h))return code;if(!out)return -3;
    auto found=h->calls.find(token);if(found==h->calls.end())return -6;
    try{*out=found->second->snapshot();return 0;}catch(...){return -1;}
}
extern "C" int32_t yv_call_hangup(YvHandle* h,uint64_t token) noexcept {
    if(auto code=check(h))return code;
    auto found=h->calls.find(token);if(found==h->calls.end())return 0;
    try{auto& call=*found->second;if(!call.isActive()||call.cancelled)return 0;call.stop_recording();call.cancelled=true;call.route();pj::CallOpParam options;call.hangup(options);return 0;}
    catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_record_start(YvHandle* h,uint64_t token,const uint8_t* path,uint32_t len) noexcept {
 if(auto code=check(h))return code;if(!path||len<1||len>4096)return -6;
 for(uint32_t i=0;i<len;++i)if(path[i]==0)return -6;
 auto f=h->calls.find(token);if(f==h->calls.end())return -6;
 try{f->second->start_recording(std::string(reinterpret_cast<const char*>(path),len));return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_record_stop(YvHandle* h,uint64_t token) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(f==h->calls.end())return -6;return f->second->stop_recording();
}
extern "C" int32_t yv_call_dtmf(YvHandle* h,uint64_t token,const uint8_t* digits,uint32_t len) noexcept {
 if(auto code=check(h))return code;
 if(!digits||len!=1)return -6;
 const char digit=static_cast<char>(digits[0]);
 if(!((digit>='0'&&digit<='9')||digit=='*'||digit=='#'))return -6;
 auto f=h->calls.find(token);if(f==h->calls.end())return -6;
 try{if(f->second->getInfo().state!=PJSIP_INV_STATE_CONFIRMED||f->second->controls.held)return -6;f->second->dialDtmf(std::string(1,digit));return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_controls(YvHandle* h,uint64_t token,YvCallControls* out) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(!out||f==h->calls.end())return -6;*out=f->second->controls;return 0;
}
extern "C" int32_t yv_call_hold(YvHandle* h,uint64_t token,int32_t held) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(f==h->calls.end()||(held!=0&&held!=1))return -6;
 try{f->second->hold(held!=0);return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_transfer(YvHandle* h,uint64_t token,const uint8_t* uri,uint32_t len,uint64_t consultation) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(f==h->calls.end())return -6;
 try{auto& c=*f->second;if(c.getInfo().state!=PJSIP_INV_STATE_CONFIRMED||c.controls.transfer_pending)return -6;pj::CallOpParam p;
 c.controls.transfer_code=0;c.controls.transfer_pending=1;c.transfer_started=std::chrono::steady_clock::now();
 try{if(consultation){auto dest=h->calls.find(consultation);if(dest==h->calls.end()||dest->second->getInfo().state!=PJSIP_INV_STATE_CONFIRMED)throw pj::Error(PJ_EINVALIDOP,"transfer","","",0);c.xferReplaces(*dest->second,p);}else{if(!uri||len<1||len>512)throw pj::Error(PJ_EINVAL,"transfer","","",0);c.xfer(std::string(reinterpret_cast<const char*>(uri),len),p);}}catch(...){c.controls.transfer_pending=0;throw;}
 return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_mute(YvHandle* h,uint64_t token,int32_t muted) noexcept {
    if(auto code=check(h))return code;if(muted!=0&&muted!=1)return -6;
    auto found=h->calls.find(token);if(found==h->calls.end())return -6;
    try{found->second->mute(muted!=0);return 0;}catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_release(YvHandle* h,uint64_t token) noexcept {
    if(auto code=check(h))return code;
    auto found=h->calls.find(token);if(found==h->calls.end())return 0;
    try{h->calls.erase(found);if(h->calls.empty()&&h->endpoint)h->endpoint->audDevManager().setNoDev();return 0;}catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}

extern "C" int32_t yv_set_ringtone(YvHandle* h,int32_t enabled) noexcept {
 if(auto code=check(h))return code;if(enabled!=0&&enabled!=1)return -6;
 h->ringtone=enabled!=0;return 0;
}
extern "C" int32_t yv_audio_device(YvHandle* h,int32_t open) noexcept {
 if(auto code=check(h))return code;if(open!=0&&open!=1)return -6;
 if(!h->running)return 0;
 try{
  auto& d=h->endpoint->audDevManager();
  if(!open){d.setNoDev();return 0;}
  // Only calls that carry audio need the device; with none, leave it closed.
  if(std::none_of(h->calls.begin(),h->calls.end(),[](const auto& x){return x.second->isActive();}))return 0;
  pjsua_set_no_snd_dev();
  pj_status_t status=pjsua_set_snd_dev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV,PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);
  return status;
 }catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}

extern "C" int32_t yv_call_list(YvHandle* h,YvCall* output,uint32_t capacity,uint32_t* count) noexcept {
 if(auto code=check(h))return code;if(!count||capacity>16||(!output&&capacity))return -6;
 try{uint32_t n=0;for(auto& item:h->calls){if(n>=capacity)return -9;output[n++]=item.second->snapshot();}*count=n;return 0;}catch(...){return -1;}
}
extern "C" int32_t yv_call_answer(YvHandle* h,uint64_t token) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(f==h->calls.end()||!f->second->is_incoming)return -6;
 try{f->second->answer();return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_call_reject(YvHandle* h,uint64_t token,int32_t status) noexcept {
 if(auto code=check(h))return code;auto f=h->calls.find(token);if(f==h->calls.end()||!f->second->is_incoming)return -6;
 if(status!=480&&status!=603)return -6;
 try{if(status==603)f->second->reject();else{auto info=f->second->getInfo();if(info.state==PJSIP_INV_STATE_INCOMING){pj::CallOpParam p;p.statusCode=static_cast<pjsip_status_code>(status);f->second->cancelled=true;f->second->hangup(p);}}return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
