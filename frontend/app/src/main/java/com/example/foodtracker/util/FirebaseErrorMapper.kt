package com.example.foodtracker.util

import androidx.annotation.StringRes
import com.example.foodtracker.R

@StringRes
fun mapFirebaseError(exception: Exception): Int {
    val msg = exception.message ?: ""
    return when {
        msg.contains("already in use")            -> R.string.error_email_in_use
        msg.contains("INVALID_LOGIN_CREDENTIALS") ||
        msg.contains("password is invalid") ||
        msg.contains("no user record")            -> R.string.error_bad_credentials
        msg.contains("badly formatted")           -> R.string.error_invalid_email
        msg.contains("at least 6") ||
        msg.contains("WEAK_PASSWORD")             -> R.string.error_weak_password
        msg.contains("network error") ||
        msg.contains("unreachable host")          -> R.string.error_no_internet
        else                                      -> R.string.error_generic
    }
}
