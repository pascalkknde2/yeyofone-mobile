package com.yeyofone.core.model

@JvmInline
value class SipAccountId(val value: String) {
    init { require(value.isNotBlank()) { "Account ID must not be blank" } }
}

@JvmInline
value class CallId(val value: String) {
    init { require(value.isNotBlank()) { "Call ID must not be blank" } }
}
