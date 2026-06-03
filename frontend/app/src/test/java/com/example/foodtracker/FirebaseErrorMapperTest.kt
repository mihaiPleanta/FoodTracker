package com.example.foodtracker

import com.example.foodtracker.util.mapFirebaseError
import org.junit.Assert.assertEquals
import org.junit.Test

// mapFirebaseError returnează un @StringRes Int (localizat în UI), nu textul în clar,
// deci aserțiile compară id-ul de resursă rezolvat din mesajul excepției.
class FirebaseErrorMapperTest {

    @Test
    fun `maps email already in use`() {
        val e = Exception("The email address is already in use by another account.")
        assertEquals(R.string.error_email_in_use, mapFirebaseError(e))
    }

    @Test
    fun `maps invalid login credentials`() {
        val e = Exception("INVALID_LOGIN_CREDENTIALS")
        assertEquals(R.string.error_bad_credentials, mapFirebaseError(e))
    }

    @Test
    fun `maps wrong password`() {
        val e = Exception("The password is invalid or the user does not have a password.")
        assertEquals(R.string.error_bad_credentials, mapFirebaseError(e))
    }

    @Test
    fun `maps weak password`() {
        val e = Exception("The given password is invalid. [ Password should be at least 6 characters ]")
        assertEquals(R.string.error_weak_password, mapFirebaseError(e))
    }

    @Test
    fun `maps network error`() {
        val e = Exception("A network error (such as timeout, interrupted connection or unreachable host) has occurred.")
        assertEquals(R.string.error_no_internet, mapFirebaseError(e))
    }

    @Test
    fun `maps unknown error to generic message`() {
        val e = Exception("Something completely unexpected")
        assertEquals(R.string.error_generic, mapFirebaseError(e))
    }
}
