package com.deniscerri.ytdl.ui.compose

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.deniscerri.ytdl.R

@Composable
fun YtdlnisComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) {
        context.materialColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun Context.materialColorScheme(): ColorScheme {
    val primary = colorAttribute(R.attr.colorPrimary)
    val surface = colorAttribute(R.attr.colorSurface)

    return lightColorScheme(
        primary = primary,
        onPrimary = colorAttribute(R.attr.colorOnPrimary),
        primaryContainer = colorAttribute(R.attr.colorPrimaryContainer),
        onPrimaryContainer = colorAttribute(R.attr.colorOnPrimaryContainer),
        inversePrimary = colorAttribute(R.attr.colorPrimaryInverse),
        secondary = colorAttribute(R.attr.colorSecondary),
        onSecondary = colorAttribute(R.attr.colorOnSecondary),
        secondaryContainer = colorAttribute(R.attr.colorSecondaryContainer),
        onSecondaryContainer = colorAttribute(R.attr.colorOnSecondaryContainer),
        tertiary = colorAttribute(R.attr.colorTertiary),
        onTertiary = colorAttribute(R.attr.colorOnTertiary),
        tertiaryContainer = colorAttribute(R.attr.colorTertiaryContainer),
        onTertiaryContainer = colorAttribute(R.attr.colorOnTertiaryContainer),
        background = colorAttribute(android.R.attr.colorBackground),
        onBackground = colorAttribute(R.attr.colorOnBackground),
        surface = surface,
        onSurface = colorAttribute(R.attr.colorOnSurface),
        surfaceVariant = colorAttribute(R.attr.colorSurfaceVariant),
        onSurfaceVariant = colorAttribute(R.attr.colorOnSurfaceVariant),
        surfaceTint = primary,
        inverseSurface = colorAttribute(R.attr.colorSurfaceInverse),
        inverseOnSurface = colorAttribute(R.attr.colorOnSurfaceInverse),
        error = colorAttribute(R.attr.colorError),
        onError = colorAttribute(R.attr.colorOnError),
        errorContainer = colorAttribute(R.attr.colorErrorContainer),
        onErrorContainer = colorAttribute(R.attr.colorOnErrorContainer),
        outline = colorAttribute(R.attr.colorOutline),
        outlineVariant = colorAttribute(R.attr.colorOutline, surface),
        scrim = Color.Black
    )
}

private fun Context.colorAttribute(@AttrRes attribute: Int, fallback: Color = Color.Unspecified): Color {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(attribute, typedValue, true)) {
        Color(typedValue.data)
    } else {
        fallback
    }
}
