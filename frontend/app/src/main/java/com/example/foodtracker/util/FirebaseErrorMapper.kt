package com.example.foodtracker.util

import androidx.annotation.StringRes
import com.example.foodtracker.R

@StringRes
fun mapFirebaseError(exception: Exception): Int {
    val msg = exception.message ?: ""
    return when {
        // Google Sign-In pe un email care are deja cont email/parolă (Firebase
        // "one account per email" → colision pe credențiale).
        msg.contains("different sign-in credentials") ||
        msg.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") ->
            R.string.error_account_exists_other_method
        msg.contains("already in use")            -> R.string.error_email_in_use
        // Verificat înaintea "password is invalid" (bad credentials): mesajul de
        // parolă slabă conține tot "password is invalid", dar "at least 6" e specific.
        msg.contains("at least 6") ||
        msg.contains("WEAK_PASSWORD")             -> R.string.error_weak_password
        msg.contains("INVALID_LOGIN_CREDENTIALS") ||
        msg.contains("password is invalid") ||
        msg.contains("no user record")            -> R.string.error_bad_credentials
        msg.contains("badly formatted")           -> R.string.error_invalid_email
        msg.contains("network error") ||
        msg.contains("unreachable host")          -> R.string.error_no_internet
        else                                      -> R.string.error_generic
    }
}
