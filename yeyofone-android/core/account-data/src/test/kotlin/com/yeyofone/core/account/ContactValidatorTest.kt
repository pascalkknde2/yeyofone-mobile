package com.yeyofone.core.account

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ContactValidatorTest {
    @Test
    fun `valid draft is accepted`() {
        ContactValidator.validate(ContactDraft(displayName = "Sarah Ndion", number = "1005"))
    }

    @Test
    fun `blank display name is rejected`() {
        val failure = assertFailsWith<ContactValidationException.InvalidField> {
            ContactValidator.validate(ContactDraft(displayName = " ", number = "1005"))
        }
        kotlin.test.assertEquals("displayName", failure.field)
    }

    @Test
    fun `blank number is rejected`() {
        val failure = assertFailsWith<ContactValidationException.InvalidField> {
            ContactValidator.validate(ContactDraft(displayName = "Sarah Ndion", number = ""))
        }
        kotlin.test.assertEquals("number", failure.field)
    }

    @Test
    fun `oversized display name is rejected`() {
        assertFailsWith<ContactValidationException.InvalidField> {
            ContactValidator.validate(ContactDraft(displayName = "a".repeat(81), number = "1005"))
        }
    }

    @Test
    fun `sip uri destinations are accepted as a number`() {
        ContactValidator.validate(ContactDraft(displayName = "Bob", number = "sip:bob@example.com"))
    }
}
