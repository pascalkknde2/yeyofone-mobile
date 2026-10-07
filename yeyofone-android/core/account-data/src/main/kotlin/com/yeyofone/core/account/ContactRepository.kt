package com.yeyofone.core.account

import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.ContactId
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeAll(): Flow<List<Contact>>
    suspend fun save(draft: ContactDraft): ContactId
    suspend fun setFavorite(id: ContactId, favorite: Boolean)
    suspend fun delete(id: ContactId)
}

/** [id] null creates a new contact; set, updates the existing one. */
class ContactDraft(
    val id: ContactId? = null,
    val displayName: String,
    val number: String,
    val favorite: Boolean = false,
)

sealed class ContactValidationException(message: String) : IllegalArgumentException(message) {
    class InvalidField(val field: String, reason: String) : ContactValidationException("$field: $reason")
}

object ContactValidator {
    fun validate(draft: ContactDraft) {
        requireText("displayName", draft.displayName, 1, 80)
        requireText("number", draft.number, 1, 128)
    }

    private fun requireText(field: String, value: String, min: Int, max: Int) {
        if (value.trim().length !in min..max) invalid(field, "must contain $min to $max characters")
    }

    private fun invalid(field: String, reason: String): Nothing =
        throw ContactValidationException.InvalidField(field, reason)
}
