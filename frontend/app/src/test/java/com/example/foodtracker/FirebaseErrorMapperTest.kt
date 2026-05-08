package com.example.foodtracker

import com.example.foodtracker.util.mapFirebaseError
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseErrorMapperTest {

    @Test
    fun `maps email already in use`() {
        val e = Exception("The email address is already in use by another account.")
        assertEquals("Acest email este deja folosit", mapFirebaseError(e))
    }

    @Test
    fun `maps invalid login credentials`() {
        val e = Exception("INVALID_LOGIN_CREDENTIALS")
        assertEquals("Email sau parolă incorectă", mapFirebaseError(e))
    }

    @Test
    fun `maps wrong password`() {
        val e = Exception("The password is invalid or the user does not have a password.")
        assertEquals("Email sau parolă incorectă", mapFirebaseError(e))
    }

    @Test
    fun `maps weak password`() {
        val e = Exception("The given password is invalid. [ Password should be at least 6 characters ]")
        assertEquals("Parola trebuie să aibă minim 6 caractere", mapFirebaseError(e))
    }

    @Test
    fun `maps network error`() {
        val e = Exception("A network error (such as timeout, interrupted connection or unreachable host) has occurred.")
        assertEquals("Verifică conexiunea la internet", mapFirebaseError(e))
    }

    @Test
    fun `maps unknown error to generic message`() {
        val e = Exception("Something completely unexpected")
        assertEquals("A apărut o eroare. Încearcă din nou.", mapFirebaseError(e))
    }
}
