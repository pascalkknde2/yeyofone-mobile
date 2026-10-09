#include <pjsua2.hpp>
#include <pj/config.h>
#include <openssl/crypto.h>
#include <iostream>
#include <string>
int main() {
    try {
        if (std::string(OpenSSL_version(OPENSSL_VERSION)).find("OpenSSL 3.5.9") != 0) return 3;
        pj::EpConfig config;
        if (config.uaConfig.maxCalls == 0 || std::string(pj_get_version()) != "2.17") return 1;
        std::cout << "PJSUA2 " << pj_get_version() << " linked successfully; no engine started\n";
        return 0;
    } catch (...) { return 2; }
}
