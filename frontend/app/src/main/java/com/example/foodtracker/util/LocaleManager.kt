package com.example.foodtracker.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.foodtracker.model.AppLanguage
import java.util.Locale

/**
 * Overrides the Compose locale so stringResource() resolves to the in-app
 * language choice without recreating the Activity. Base resources are Romanian;
 * English lives in values-en/.
 */
@Composable
fun AppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val tag = when (language) {
        AppLanguage.ROMANIAN -> "ro"
        AppLanguage.ENGLISH -> "en"
    }
    val baseContext = LocalContext.current
    val localizedConfig = remember(tag, baseContext) {
        Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale(tag))
        }
    }
    val localizedContext = remember(tag, baseContext) {
        baseContext.createConfigurationContext(localizedConfig)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
        content = content,
    )
}
