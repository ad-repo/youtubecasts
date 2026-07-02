package com.deniscerri.ytdl.ui.more

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R

@Composable
fun MoreScreen(
    showTerminal: Boolean,
    showDownloads: Boolean,
    showDownloadQueue: Boolean,
    tintDynamicAppIcon: Boolean,
    terminateEnabled: Boolean,
    onTerminalClick: () -> Unit,
    onLogsClick: () -> Unit,
    onCommandTemplatesClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onDownloadQueueClick: () -> Unit,
    onCookiesClick: () -> Unit,
    onBackClick: () -> Unit,
    onObserveSourcesClick: () -> Unit,
    onChannelsClick: () -> Unit,
    onTerminateClick: () -> Unit,
    onTerminateLongClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            MoreHeader(
                tintDynamicAppIcon = tintDynamicAppIcon,
                onBackClick = onBackClick
            )

            SectionLabel(text = stringResource(R.string.downloads))

            MoreMenuItem(
                text = stringResource(R.string.logs),
                icon = R.drawable.ic_baseline_file_open_24,
                onClick = onLogsClick
            )

            MoreMenuItem(
                text = stringResource(R.string.command_templates),
                icon = R.drawable.ic_baseline_keyboard_arrow_right_24,
                onClick = onCommandTemplatesClick
            )

            if (showDownloads) {
                MoreMenuItem(
                    text = stringResource(R.string.downloads),
                    icon = R.drawable.ic_downloads,
                    usePrimaryIcon = true,
                    onClick = onDownloadsClick
                )
            }

            if (showDownloadQueue) {
                MoreMenuItem(
                    text = stringResource(R.string.download_queue),
                    icon = R.drawable.baseline_downloading_24,
                    usePrimaryIcon = true,
                    onClick = onDownloadQueueClick
                )
            }

            SectionDivider()

            SectionLabel(text = stringResource(R.string.settings))

            if (showTerminal) {
                MoreMenuItem(
                    text = stringResource(R.string.terminal),
                    icon = R.drawable.ic_terminal,
                    onClick = onTerminalClick
                )
            }

            MoreMenuItem(
                text = stringResource(R.string.cookies),
                icon = R.drawable.baseline_cookie_24,
                onClick = onCookiesClick
            )

            MoreMenuItem(
                text = stringResource(R.string.observe_sources),
                icon = R.drawable.baseline_calendar_month_24,
                onClick = onObserveSourcesClick
            )

            MoreMenuItem(
                text = stringResource(R.string.channels),
                icon = R.drawable.baseline_live_tv_24,
                onClick = onChannelsClick
            )

            MoreMenuItem(
                text = stringResource(R.string.settings),
                icon = R.drawable.ic_settings,
                usePrimaryIcon = true,
                onClick = onSettingsClick
            )

            SectionDivider()

            MoreMenuItem(
                text = stringResource(R.string.kill_app),
                icon = R.drawable.ic_power,
                enabled = terminateEnabled,
                onClick = onTerminateClick,
                onLongClick = onTerminateLongClick
            )
        }
    }
}

@Composable
private fun MoreHeader(tintDynamicAppIcon: Boolean, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = null,
                colorFilter = if (tintDynamicAppIcon && Build.VERSION.SDK_INT >= 32) {
                    ColorFilter.tint(MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 6.dp
        )
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 72.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun LeadingIcon(icon: Int, enabled: Boolean, usePrimaryIcon: Boolean) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        usePrimaryIcon -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        usePrimaryIcon -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreMenuItem(
    text: String,
    icon: Int,
    enabled: Boolean = true,
    usePrimaryIcon: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    ListItem(
        headlineContent = {
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            LeadingIcon(
                icon = icon,
                enabled = enabled,
                usePrimaryIcon = usePrimaryIcon
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.72f else 0.38f),
                modifier = Modifier.size(24.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
