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
#include <cstring>
#include <cmath>
#include <deque>
#include <vector>
#include <pjlib-util/errno.h>
#include <pjsip/sip_errno.h>
#include <pjsua-lib/pjsua_internal.h>
#ifndef _WIN32
#include <ifaddrs.h>
#include <net/if.h>
#include <sys/socket.h>
#endif

namespace {
std::atomic_bool occupied{false};
std::atomic<uint64_t> incoming_serial{1};
const char* const HANDOFF_TYPE="application/vnd.yeyofone.handoff+json";
constexpr size_t HANDOFF_MAX=1024;
// SIP token characters (RFC 3261) that are safe inside a Replaces header value.
bool token_char(unsigned char c){return std::isalnum(c)||std::strchr("-.!%*_+`'~",c)!=nullptr;}
bool valid_token(const uint8_t* p,uint32_t len,uint32_t max,bool call_id){
    if(!p||len==0||len>max)return false;
    for(uint32_t i=0;i<len;++i){unsigned char c=p[i];if(c==0||!(token_char(c)||(call_id&&(c=='@'||c==':'||c=='/'))))return false;}
    return true;
}
std::string lower(std::string v){std::transform(v.begin(),v.end(),v.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});return v;}
// True for a BYE or CANCEL carrying "Reason: SIP;cause=200", which FreeSWITCH sends with
// "Call completed elsewhere" when another device takes over or answers the call.
bool completed_elsewhere(const std::string& message){
    if(message.rfind("BYE ",0)!=0&&message.rfind("CANCEL ",0)!=0)return false;
    const auto text=lower(message);
    for(size_t at=text.find("\nreason:");at!=std::string::npos;at=text.find("\nreason:",at+1)){
        const auto end=text.find('\n',at+1);
        const auto line=text.substr(at,end==std::string::npos?std::string::npos:end-at);
        if(line.find("sip")!=std::string::npos&&line.find("cause=200")!=std::string::npos)return true;
    }
    return false;
}
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
    std::string target_host,username,own_uri;
    int target_port=0,target_transport=0;
    std::deque<std::string> handoff;
    const uint64_t token;
    pj::TransportId transport = PJSUA_INVALID_ID;
    NativeAccount(uint64_t t,EngineEndpoint* e,YvHandle* h) : endpoint(e), owner(h), token(t) { latest.token = t; }
    ~NativeAccount() override { shutdown(); }
    void onRegStarted(pj::OnRegStartedParam& event) noexcept override {
        try { std::lock_guard<std::mutex> lock(mutex); ++latest.sequence; latest.phase=1; latest.renew=event.renew; latest.status=0; latest.sip_code=0; latest.expires=0; latest.failure_kind=0; } catch (...) {}
    }
    void onRegState(pj::OnRegStateParam& event) noexcept override;
    void onIncomingCall(pj::OnIncomingCallParam& event) noexcept override;
    // Keeps handoff messages from this account's own address; everything else is ignored.
    void onInstantMessage(pj::OnInstantMessageParam& event) noexcept override {
        try {
            if(lower(event.contentType)!=HANDOFF_TYPE||event.msgBody.empty()||event.msgBody.size()>HANDOFF_MAX)return;
            const auto from=lower(event.fromUri);
            const auto at=from.find("sip:"+lower(username)+"@");
            if(at==std::string::npos)return;
            const auto host_start=at+4+username.size()+1;
            const auto host=from.substr(host_start,from.find_first_of(">;:",host_start)-host_start);
            if(host!=lower(target_host))return;
            std::lock_guard<std::mutex> lock(mutex);
            if(handoff.size()>=16)handoff.pop_front();
            handoff.push_back(event.msgBody);
        } catch (...) {}
    }
    bool next_handoff(std::string& out){std::lock_guard<std::mutex> lock(mutex);if(handoff.empty())return false;out=std::move(handoff.front());handoff.pop_front();return true;}
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
    float receive_gain=1.0f;
    pj::ToneGenerator ringtone;
    bool ringtone_active=false;
    pj::ToneGenerator ringback;
    bool ringback_created=false,ringback_active=false;
    // A recording is one or more WAV tracks: the mixed call (both sides), plus "you" (microphone
    // only) and "them" (the call's incoming audio only) so transcripts can tell the speakers apart.
    struct RecordingTrack {
        std::unique_ptr<pj::AudioMediaRecorder> recorder;
        std::filesystem::path final_path, temp_path;
        bool wants_stream=false, wants_capture=false;
        bool stream_connected=false, capture_connected=false;
    };
    std::vector<RecordingTrack> tracks;
    int recording_media_index=-1;
    void unlink_track_streams() noexcept {
        if(recording_media_index<0)return;
        for(auto& t:tracks)if(t.stream_connected){try{getAudioMedia(recording_media_index).stopTransmit(*t.recorder);}catch(...){}t.stream_connected=false;}
    }
    void unlink_track_captures() noexcept {
        for(auto& t:tracks)if(t.capture_connected){try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*t.recorder);}catch(...){}t.capture_connected=false;}
    }
    void link_tracks(pj::AudioMedia& stream, int index, bool send) {
        if(recording_media_index!=index){unlink_track_streams();recording_media_index=index;}
        auto& capture=endpoint.audDevManager().getCaptureDevMedia();
        for(auto& t:tracks){
            if(t.wants_stream&&!t.stream_connected){stream.startTransmit(*t.recorder);t.stream_connected=true;}
            if(t.wants_capture&&send&&!t.capture_connected){capture.startTransmit(*t.recorder);t.capture_connected=true;}
            if(t.capture_connected&&!send){capture.stopTransmit(*t.recorder);t.capture_connected=false;}
        }
    }
    int media_index=-1;
    // Merged calls: each call's audio is sent to the other, and both stay
    // connected to the local devices, so the conference bridge mixes all three.
    NativeCall* peer=nullptr;
    void link_peer() noexcept {
        if(!peer||controls.held||peer->controls.held||media_index<0||peer->media_index<0)return;
        // startTransmit on an existing connection is a no-op, so this is safe on every route().
        try{auto mine=getAudioMedia(media_index);auto theirs=peer->getAudioMedia(peer->media_index);mine.startTransmit(theirs);theirs.startTransmit(mine);}catch(...){}
    }
    void stop_peer_audio() noexcept {
        if(!peer||media_index<0||peer->media_index<0)return;
        try{auto mine=getAudioMedia(media_index);auto theirs=peer->getAudioMedia(peer->media_index);mine.stopTransmit(theirs);theirs.stopTransmit(mine);}catch(...){}
    }
    void unlink_peer() noexcept {
        if(!peer)return;
        stop_peer_audio();
        peer->peer=nullptr;peer=nullptr;
    }
public:
    const uint64_t account_token;
    bool hold_pending=false, previous_hold=false;
    std::chrono::steady_clock::time_point transfer_started;
    YvCallControls controls{};
    bool cancelled=false;
    bool is_incoming=false;
    std::string dialog_call_id,dialog_local_tag,dialog_remote_tag,dialog_remote_user;
    bool ended_elsewhere=false;
    NativeCall(NativeAccount& account,uint64_t token,pj::Endpoint& ep,bool use_audio,int call_id=PJSUA_INVALID_ID,bool incoming=false,float gain=1.0f):pj::Call(account,call_id),endpoint(ep),audio(use_audio),receive_gain(gain),account_token(account.token),is_incoming(incoming){latest.token=token;latest.account_token=account.token;latest.incoming=incoming?1:0;if(incoming){latest.state=PJSIP_INV_STATE_INCOMING;capture_caller();}}
    ~NativeCall() override { unlink_peer(); stop_recording(); stop_ringtone(); stop_ringback(); }
    void capture_caller() noexcept {try{auto info=getInfo();auto* pool=pjsua_pool_create("caller-check",512,512);if(!pool)return;struct Guard{pj_pool_t*p;~Guard(){pj_pool_release(p);}}guard{pool};std::string uri=info.remoteUri;auto* parsed=pjsip_parse_uri(pool,uri.data(),uri.size(),0);if(!parsed||(!PJSIP_URI_SCHEME_IS_SIP(parsed)&&!PJSIP_URI_SCHEME_IS_SIPS(parsed)))return;auto* sip=static_cast<pjsip_sip_uri*>(pjsip_uri_get_uri(parsed));if(sip->user.slen<1||sip->user.slen>64)return;for(pj_ssize_t i=0;i<sip->user.slen;++i){unsigned char c=static_cast<unsigned char>(sip->user.ptr[i]);if(!(std::isalnum(c)||c=='+'||c=='*'||c=='#'||c=='-'||c=='_'||c=='.'))return;}std::lock_guard<std::mutex> lock(mutex);latest.caller_len=static_cast<int32_t>(sip->user.slen);std::copy(sip->user.ptr,sip->user.ptr+sip->user.slen,latest.caller);}catch(...){} }
    // Copies the dialog identifiers while PJSUA still has the dialog; they outlive the call.
    void capture_dialog() noexcept {
        try{
            const int id=getId();
            if(id<0||id>=static_cast<int>(PJSUA_MAX_CALLS))return;
            auto* inv=pjsua_var.calls[id].inv;
            if(!inv||!inv->dlg||inv->dlg->remote.info->tag.slen==0)return;
            auto* dlg=inv->dlg;
            dialog_call_id.assign(dlg->call_id->id.ptr,dlg->call_id->id.slen);
            dialog_local_tag.assign(dlg->local.info->tag.ptr,dlg->local.info->tag.slen);
            dialog_remote_tag.assign(dlg->remote.info->tag.ptr,dlg->remote.info->tag.slen);
            auto* uri=pjsip_uri_get_uri(dlg->remote.info->uri);
            if(uri&&(PJSIP_URI_SCHEME_IS_SIP(uri)||PJSIP_URI_SCHEME_IS_SIPS(uri))){
                auto* sip=static_cast<pjsip_sip_uri*>(uri);
                std::string user(sip->user.ptr,sip->user.slen);
                if(user.size()<=64&&std::all_of(user.begin(),user.end(),[](unsigned char c){return std::isalnum(c)||c=='+'||c=='*'||c=='#'||c=='-'||c=='_'||c=='.';}))dialog_remote_user=user;
            }
        }catch(...){}
    }
    void capture_elsewhere(const pj::SipEvent& event) noexcept {
        try{if(event.type==PJSIP_EVENT_TSX_STATE&&event.body.tsxState.type==PJSIP_EVENT_RX_MSG&&completed_elsewhere(event.body.tsxState.src.rdata.wholeMsg))ended_elsewhere=true;}catch(...){}
    }
    YvCallDialog dialog() noexcept {
        YvCallDialog out{};
        out.ended_elsewhere=ended_elsewhere?1:0;
        auto copy=[](const std::string& v,uint8_t* dst,uint32_t& len,size_t cap){len=static_cast<uint32_t>(std::min(v.size(),cap-1));std::copy(v.begin(),v.begin()+len,dst);};
        copy(dialog_call_id,out.call_id,out.call_id_len,sizeof(out.call_id));
        copy(dialog_local_tag,out.local_tag,out.local_tag_len,sizeof(out.local_tag));
        copy(dialog_remote_tag,out.remote_tag,out.remote_tag_len,sizeof(out.remote_tag));
        copy(dialog_remote_user,out.remote_user,out.remote_user_len,sizeof(out.remote_user));
        return out;
    }
    void update() noexcept {
        try {
            auto info=getInfo();
            if(info.state==PJSIP_INV_STATE_CONFIRMED)capture_dialog();
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
            if(info.state==PJSIP_INV_STATE_DISCONNECTED){unlink_peer();stop_recording();return;}
            if(controls.held){
                stop_peer_audio();
                if(media_index>=0){try{auto stream=getAudioMedia(media_index);auto& d=endpoint.audDevManager();if(capture_connected)d.getCaptureDevMedia().stopTransmit(stream);if(playback_connected)stream.stopTransmit(d.getPlaybackDevMedia());}catch(...){}}
                capture_connected=false;playback_connected=false;
                unlink_track_streams();unlink_track_captures();
                std::lock_guard<std::mutex> lock(mutex);latest.audio_active=0;latest.media_active=0;return;
            }
            for(auto& item:info.media) {
                if(item.type!=PJMEDIA_TYPE_AUDIO || item.status!=PJSUA_CALL_MEDIA_ACTIVE)continue;
                if(media_index!=static_cast<int>(item.index)){capture_connected=false;playback_connected=false;media_index=item.index;}
                bool muted;{std::lock_guard<std::mutex> lock(mutex);muted=latest.muted!=0;}
                if(audio){
                    auto stream=getAudioMedia(item.index);
                    stream.adjustRxLevel(receive_gain);
                    auto& devices=endpoint.audDevManager();
                    if(!playback_connected){stream.startTransmit(devices.getPlaybackDevMedia());playback_connected=true;}
                    const bool send=info.state==PJSIP_INV_STATE_CONFIRMED && !muted && !cancelled;
                    if(send&&!capture_connected){devices.getCaptureDevMedia().startTransmit(stream);capture_connected=true;}
                    if(!send&&capture_connected){devices.getCaptureDevMedia().stopTransmit(stream);capture_connected=false;}
                    if(!tracks.empty())link_tracks(stream,static_cast<int>(item.index),send);
                }
                if(audio)link_peer();
                std::lock_guard<std::mutex> lock(mutex);++latest.sequence;latest.media_active=1;latest.audio_active=audio?1:0;latest.recording=tracks.empty()?0:1;return;
            }
            unlink_track_captures();
            std::lock_guard<std::mutex> lock(mutex);latest.audio_active=0;latest.media_active=0;latest.recording=tracks.empty()?0:1;
        } catch(const pj::Error& e){std::lock_guard<std::mutex> lock(mutex);latest.audio_error=e.status;latest.audio_active=0;}
          catch(...){std::lock_guard<std::mutex> lock(mutex);latest.audio_error=-1;latest.audio_active=0;}
    }
    void hold(bool value){
        if(peer||getInfo().state!=PJSIP_INV_STATE_CONFIRMED||controls.transfer_pending||hold_pending)throw pj::Error(PJ_EINVALIDOP,"hold","","",0);
        if(controls.held==value)return;
        pj::CallOpParam p(true);p.opt.audioCount=1;p.opt.videoCount=0;p.opt.textCount=0;
        previous_hold=controls.held;hold_pending=true;
        try{if(value)setHold(p);else{p.opt.flag=PJSUA_CALL_UNHOLD;reinvite(p);}}catch(...){hold_pending=false;throw;}
        controls.held=value;route();
    }
    void onCallTransferStatus(pj::OnCallTransferStatusParam& p) noexcept override {controls.transfer_code=p.statusCode;if(p.finalNotify||(p.statusCode>=200&&p.statusCode<300)){controls.transfer_pending=0;p.cont=false;}}
    void capture_error(const pj::SipEvent& event) noexcept {try{if(event.type==PJSIP_EVENT_TSX_STATE && event.body.tsxState.type==PJSIP_EVENT_TRANSPORT_ERROR){std::lock_guard<std::mutex> lock(mutex);latest.native_code=event.body.tsxState.src.status;latest.failure_kind=yv_failure_kind(latest.native_code,0);}}catch(...){} }
    void onCallTsxState(pj::OnCallTsxStateParam& event) noexcept override {
        capture_error(event.e);capture_elsewhere(event.e);
        if(event.e.type!=PJSIP_EVENT_TSX_STATE)return;
        auto& tsx=event.e.body.tsxState.tsx;
        if(hold_pending&&tsx.role==PJSIP_ROLE_UAC&&tsx.method=="INVITE"&&tsx.statusCode>=200){
            hold_pending=false;
            if(tsx.statusCode>=300){controls.held=previous_hold;route();}
        }
    }
    void onCallMediaTransportState(pj::OnCallMediaTransportStateParam& event) noexcept override {if(event.status){try{std::lock_guard<std::mutex> lock(mutex);latest.audio_error=event.status;latest.audio_active=0;}catch(...){}}}
    void onCallState(pj::OnCallStateParam& event) noexcept override {capture_error(event.e);capture_elsewhere(event.e);update();route();}
    void onCallMediaState(pj::OnCallMediaStateParam&) noexcept override {update();route();if(peer)peer->route();}
    // Takes any held call off hold, then links the two calls' audio both ways.
    void merge_with(NativeCall& other){
        auto ready=[](NativeCall& c){return c.getInfo().state==PJSIP_INV_STATE_CONFIRMED&&!c.controls.transfer_pending&&!c.hold_pending&&!c.cancelled;};
        if(&other==this||peer||other.peer||!ready(*this)||!ready(other))throw pj::Error(PJ_EINVALIDOP,"merge","","",0);
        if(controls.held)hold(false);
        if(other.controls.held)other.hold(false);
        peer=&other;other.peer=this;
        route();other.route();
    }
    void start_ringtone() noexcept {if(!is_incoming||ringtone_active)return;try{auto& devices=endpoint.audDevManager();devices.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);ringtone.createToneGenerator(16000,1);pj::ToneDesc tone;tone.freq1=440;tone.freq2=480;tone.on_msec=1000;tone.off_msec=3000;tone.volume=9000;pj::ToneDescVector tones;tones.push_back(tone);ringtone.play(tones,true);ringtone.startTransmit(devices.getPlaybackDevMedia());ringtone_active=true;}catch(...){try{ringtone.stop();}catch(...){}}}
    // UK ringback cadence (400+450 Hz: 0.4 s on, 0.2 s off, 0.4 s on, 2 s off).
    void start_ringback() noexcept {if(is_incoming||!audio||ringback_active)return;try{auto& devices=endpoint.audDevManager();if(!ringback_created){ringback.createToneGenerator(16000,1);ringback_created=true;}pj::ToneDesc first;first.freq1=400;first.freq2=450;first.on_msec=400;first.off_msec=200;first.volume=0;pj::ToneDesc second=first;second.off_msec=2000;pj::ToneDescVector tones;tones.push_back(first);tones.push_back(second);ringback.play(tones,true);ringback.startTransmit(devices.getPlaybackDevMedia());ringback_active=true;}catch(...){try{ringback.stop();}catch(...){}}}
    void stop_ringback() noexcept {if(!ringback_active)return;try{ringback.stopTransmit(endpoint.audDevManager().getPlaybackDevMedia());}catch(...){}try{ringback.stop();}catch(...){}ringback_active=false;}
    void stop_ringtone() noexcept {if(!ringtone_active)return;try{ringtone.stopTransmit(endpoint.audDevManager().getPlaybackDevMedia());}catch(...){}try{ringtone.stop();}catch(...){}ringtone_active=false;}
    void send_ringing(){if(!is_incoming)return;pj::CallOpParam p;p.statusCode=PJSIP_SC_RINGING;pj::Call::answer(p);}
    void answer(){auto state=getInfo().state;if(!is_incoming||(state!=PJSIP_INV_STATE_INCOMING&&state!=PJSIP_INV_STATE_EARLY))throw pj::Error(PJ_EINVALIDOP,"answer","","",0);stop_ringtone();audio=true;pj::CallOpParam p(true);p.statusCode=PJSIP_SC_OK;p.opt.audioCount=1;p.opt.videoCount=0;p.opt.textCount=0;auto& d=endpoint.audDevManager();d.setCaptureDev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV);d.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);try{pj::Call::answer(p);}catch(...){d.setNullDev();throw;}}
    void reject(){if(!is_incoming)return;auto state=getInfo().state;if(state!=PJSIP_INV_STATE_INCOMING&&state!=PJSIP_INV_STATE_EARLY)return;stop_ringtone();pj::CallOpParam p;p.statusCode=PJSIP_SC_DECLINE;pj::Call::hangup(p);cancelled=true;}
    void start_recording(const std::string& final_path) {
        auto info=getInfo();
        if(info.state!=PJSIP_INV_STATE_CONFIRMED||!audio||!tracks.empty()||controls.held)throw pj::Error(PJ_EINVALIDOP,"recording","","",0);
        int audio_index=-1;
        for(const auto& item:info.media)if(item.type==PJMEDIA_TYPE_AUDIO&&item.status==PJSUA_CALL_MEDIA_ACTIVE){audio_index=static_cast<int>(item.index);break;}
        if(audio_index<0)throw pj::Error(PJ_EINVALIDOP,"recording","","",0);
        const std::filesystem::path main_path(final_path);
        // <id>.wav (both sides), <id>.you.wav (microphone) and <id>.them.wav (the other party).
        const auto sibling=[&](const char* suffix){return main_path.parent_path()/(main_path.stem().string()+suffix);};
        std::vector<RecordingTrack> candidates(3);
        candidates[0].final_path=main_path;candidates[0].wants_stream=true;candidates[0].wants_capture=true;
        candidates[1].final_path=sibling(".you.wav");candidates[1].wants_capture=true;
        candidates[2].final_path=sibling(".them.wav");candidates[2].wants_stream=true;
        for(auto& t:candidates){
            t.temp_path=t.final_path.parent_path()/(t.final_path.stem().string()+".partial.wav");
            if(std::filesystem::exists(t.final_path)||std::filesystem::exists(t.temp_path))throw pj::Error(PJ_EEXISTS,"recording","","",0);
        }
        const auto discard=[&]{
            for(auto& t:candidates){
                if(t.recorder){
                    if(t.capture_connected)try{endpoint.audDevManager().getCaptureDevMedia().stopTransmit(*t.recorder);}catch(...){}
                    if(t.stream_connected)try{getAudioMedia(audio_index).stopTransmit(*t.recorder);}catch(...){}
                    t.recorder.reset();
                }
                std::error_code ec;std::filesystem::remove(t.temp_path,ec);
            }
        };
        try{
            auto stream=getAudioMedia(audio_index);
            bool muted;{std::lock_guard<std::mutex> lock(mutex);muted=latest.muted!=0;}
            for(auto& t:candidates){
                t.recorder=std::make_unique<pj::AudioMediaRecorder>();
                t.recorder->createRecorder(t.temp_path.string());
                if(t.wants_stream){stream.startTransmit(*t.recorder);t.stream_connected=true;}
                if(t.wants_capture&&!muted){endpoint.audDevManager().getCaptureDevMedia().startTransmit(*t.recorder);t.capture_connected=true;}
            }
        }catch(...){discard();throw;}
        tracks=std::move(candidates);recording_media_index=audio_index;
        std::lock_guard<std::mutex> lock(mutex);latest.recording=1;
    }
    int stop_recording() noexcept {
        if(tracks.empty())return 0;
        unlink_track_streams();unlink_track_captures();
        int result=0;
        for(size_t i=0;i<tracks.size();++i){
            tracks[i].recorder.reset();
            std::error_code ec;std::filesystem::rename(tracks[i].temp_path,tracks[i].final_path,ec);
            // Only the main (mixed) recording decides success; the speaker tracks are best effort.
            if(ec&&i==0)result=-1;
            if(ec&&i>0){std::error_code ignored;std::filesystem::remove(tracks[i].temp_path,ignored);}
        }
        tracks.clear();recording_media_index=-1;
        {std::lock_guard<std::mutex> lock(mutex);latest.recording=0;}
        return result;
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
    float receive_gain = 1.0f;
    std::map<uint64_t,std::unique_ptr<NativeCall>> calls;
    std::map<uint64_t,std::unique_ptr<NativeAccount>> accounts;
};
namespace {
void NativeAccount::onIncomingCall(pj::OnIncomingCallParam& event) noexcept {
 try {
  if(!owner||!owner->running||owner->calls.size()>=16||std::any_of(owner->calls.begin(),owner->calls.end(),[](const auto& item){return item.second->isActive();})){pjsua_call_answer(event.callId,PJSIP_SC_BUSY_HERE,nullptr,nullptr);return;}
  const uint64_t serial=incoming_serial.fetch_add(1);if(serial==0||serial>0x7fffffffffffffffULL){pjsua_call_answer(event.callId,PJSIP_SC_SERVICE_UNAVAILABLE,nullptr,nullptr);return;}
  const uint64_t token=0x8000000000000000ULL|serial;
  auto call=std::make_unique<NativeCall>(*this,token,*endpoint,false,event.callId,true,owner->receive_gain);
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
    if (!h->running || !c || !c->token || !c->username || !c->authentication_username || !c->host || !c->password || c->username_len==0 || c->username_len>128 || c->authentication_username_len==0 || c->authentication_username_len>128 || c->host_len==0 || c->host_len>253 || c->password_len==0 || c->password_len>4096 || c->port==0 || c->port>65535 || c->transport<0 || c->transport>2) return -6;
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
        config.sipConfig.authCreds.emplace_back("digest","*",std::string(reinterpret_cast<const char*>(c->authentication_username),c->authentication_username_len),0,std::string(reinterpret_cast<const char*>(c->password),c->password_len));
        auto account=std::make_unique<NativeAccount>(c->token,h->endpoint.get(),h);account->transport=transport;
        account->target_host=std::string(reinterpret_cast<const char*>(c->host),c->host_len);account->target_port=c->port;account->target_transport=type;
        account->username=std::string(reinterpret_cast<const char*>(c->username),c->username_len);account->own_uri=config.idUri;
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

namespace {
int32_t start_call(YvHandle* h,uint64_t account,uint64_t token,const uint8_t* uri,uint32_t len,int32_t audio,const std::string& replaces) noexcept {
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
        if(audio){try{auto& devices=h->endpoint->audDevManager();devices.setCaptureDev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV);devices.setPlaybackDev(PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);}catch(...){h->endpoint->audDevManager().setNullDev();return -11;}}
        auto call=std::make_unique<NativeCall>(acc,token,*h->endpoint,audio!=0,PJSUA_INVALID_ID,false,h->receive_gain);
        auto* pointer=call.get();h->calls.emplace(token,std::move(call));
        try{pj::CallOpParam options(true);options.opt.audioCount=1;options.opt.videoCount=0;options.opt.textCount=0;
            if(!replaces.empty()){pj::SipHeader header;header.hName="Replaces";header.hValue=replaces;options.txOption.headers.push_back(header);}
            pointer->makeCall(destination,options);pointer->update();}
        catch(...){h->calls.erase(token);if(audio&&h->calls.empty())h->endpoint->audDevManager().setNullDev();throw;}
        return 0;
    }catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}
}
extern "C" int32_t yv_call_start(YvHandle* h,uint64_t account,uint64_t token,const uint8_t* uri,uint32_t len,int32_t audio) noexcept {
    if(auto code=check(h))return code;
    return start_call(h,account,token,uri,len,audio,std::string());
}
extern "C" int32_t yv_call_start_replacing(YvHandle* h,uint64_t account,uint64_t token,const uint8_t* uri,uint32_t len,
                                           const uint8_t* call_id,uint32_t call_id_len,const uint8_t* to_tag,uint32_t to_tag_len,
                                           const uint8_t* from_tag,uint32_t from_tag_len) noexcept {
    if(auto code=check(h))return code;
    if(!valid_token(call_id,call_id_len,128,true)||!valid_token(to_tag,to_tag_len,64,false)||!valid_token(from_tag,from_tag_len,64,false))return -6;
    try{
        const std::string replaces=std::string(reinterpret_cast<const char*>(call_id),call_id_len)+
            ";to-tag="+std::string(reinterpret_cast<const char*>(to_tag),to_tag_len)+
            ";from-tag="+std::string(reinterpret_cast<const char*>(from_tag),from_tag_len);
        return start_call(h,account,token,uri,len,1,replaces);
    }catch(...){return -1;}
}
extern "C" int32_t yv_call_dialog(YvHandle* h,uint64_t token,YvCallDialog* out) noexcept {
    if(auto code=check(h))return code;if(!out)return -3;
    auto found=h->calls.find(token);if(found==h->calls.end())return -6;
    try{found->second->capture_dialog();*out=found->second->dialog();return 0;}catch(...){return -1;}
}
extern "C" int32_t yv_handoff_send(YvHandle* h,uint64_t account,const uint8_t* body,uint32_t len) noexcept {
    if(auto code=check(h))return code;
    if(!h->running||!body||len==0||len>HANDOFF_MAX)return -6;
    for(uint32_t i=0;i<len;++i)if(body[i]==0)return -6;
    auto found=h->accounts.find(account);if(found==h->accounts.end())return -6;
    try{
        std::string to=found->second->own_uri,text(reinterpret_cast<const char*>(body),len);
        pj_str_t to_str=pj_str(&to[0]),mime=pj_str(const_cast<char*>(HANDOFF_TYPE)),content=pj_str(&text[0]);
        return pjsua_im_send(found->second->getId(),&to_str,&mime,&content,nullptr,nullptr);
    }catch(const pj::Error& e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_handoff_next(YvHandle* h,YvHandoffMessage* out) noexcept {
    if(auto code=check(h))return code;if(!out)return -3;
    try{
        std::string body;
        for(auto& item:h->accounts){
            if(!item.second->next_handoff(body))continue;
            *out=YvHandoffMessage{};out->account_token=item.first;
            out->len=static_cast<uint32_t>(std::min(body.size(),sizeof(out->body)));
            std::copy(body.begin(),body.begin()+out->len,out->body);
            return 1;
        }
        return 0;
    }catch(...){return -1;}
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
extern "C" int32_t yv_call_merge(YvHandle* h,uint64_t first,uint64_t second) noexcept {
 if(auto code=check(h))return code;if(first==second)return -6;
 auto a=h->calls.find(first),b=h->calls.find(second);if(a==h->calls.end()||b==h->calls.end())return -6;
 try{a->second->merge_with(*b->second);return 0;}catch(const pj::Error&e){return e.status;}catch(...){return -1;}
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
// Releasing the sound device uses the null device, not "no device": PJSIP 2.14+ queues conference
// bridge operations (such as removing a stopped recorder) and runs them on the bridge's clock. With
// no device there is no clock, so the last call's recording would stay open and its WAV header
// unwritten until the next call. The null device keeps the clock without opening any hardware.
extern "C" int32_t yv_call_release(YvHandle* h,uint64_t token) noexcept {
    if(auto code=check(h))return code;
    auto found=h->calls.find(token);if(found==h->calls.end())return 0;
    try{h->calls.erase(found);if(h->calls.empty()&&h->endpoint)h->endpoint->audDevManager().setNullDev();return 0;}catch(const pj::Error& e){return e.status;}catch(...){return -1;}
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
  if(!open){d.setNullDev();return 0;}
  // Only calls that carry audio need the device; with none, leave it closed.
  if(std::none_of(h->calls.begin(),h->calls.end(),[](const auto& x){return x.second->isActive();}))return 0;
  pjsua_set_no_snd_dev();
  pj_status_t status=pjsua_set_snd_dev(PJMEDIA_AUD_DEFAULT_CAPTURE_DEV,PJMEDIA_AUD_DEFAULT_PLAYBACK_DEV);
  return status;
 }catch(const pj::Error&e){return e.status;}catch(...){return -1;}
}
extern "C" int32_t yv_set_call_receive_gain(YvHandle* h,float gain) noexcept {
 if(auto code=check(h))return code;if(!std::isfinite(gain)||gain<0.5f||gain>1.5f)return -6;
 h->receive_gain=gain;
 for(auto& item:h->calls){
  try{auto info=item.second->getInfo();for(const auto& media:info.media)if(media.type==PJMEDIA_TYPE_AUDIO&&media.status==PJSUA_CALL_MEDIA_ACTIVE)item.second->getAudioMedia(media.index).adjustRxLevel(gain);}
  catch(...){}
 }
 return 0;
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
