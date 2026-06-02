package com.example.foodtracker.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
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
 *
 * The overridden LocalContext is a [ContextWrapper] around the ORIGINAL activity
 * context, only swapping getResources() for the localized resources. Keeping the
 * activity in the baseContext chain is essential: owners resolved by walking
 * LocalContext (e.g. ActivityResultRegistryOwner used by
 * rememberLauncherForActivityResult) would otherwise fail with
 * "No ActivityResultRegistryOwner was provided".
 */
@Composable
fun AppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val tag = when (language) {
        AppLanguage.ROMANIAN -> "ro"
        AppLanguage.ENGLISH -> "en"
    }
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localizedConfig = remember(tag, baseConfig) {
        Configuration(baseConfig).apply { setLocale(Locale(tag)) }
    }
    val localizedContext = remember(tag, baseContext, localizedConfig) {
        val localizedResources =
            baseContext.createConfigurationContext(localizedConfig).resources
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localizedResources
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
        content = content,
    )
}

/** Unwraps [ContextWrapper] layers (such as the AppLocale wrapper) to find the host Activity. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
