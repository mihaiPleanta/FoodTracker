package com.example.foodtracker.util

fun mapFirebaseError(exception: Exception): String {
    val msg = exception.message ?: ""
    return when {
        msg.contains("already in use")            -> "Acest email este deja folosit"
        msg.contains("INVALID_LOGIN_CREDENTIALS") ||
        msg.contains("password is invalid") ||
        msg.contains("no user record")            -> "Email sau parolă incorectă"
        msg.contains("badly formatted")           -> "Email invalid"
        msg.contains("at least 6") ||
        msg.contains("WEAK_PASSWORD")             -> "Parola trebuie să aibă minim 6 caractere"
        msg.contains("network error") ||
        msg.contains("unreachable host")          -> "Verifică conexiunea la internet"
        else                                      -> "A apărut o eroare. Încearcă din nou."
    }
}
