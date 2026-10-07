package com.yeyofone.core.model

/** An in-app contact: a name and the one destination (SIP URI or number) dialed for it. */
data class Contact(
    val id: ContactId,
    val displayName: String,
    val number: String,
    val favorite: Boolean = false,
)
