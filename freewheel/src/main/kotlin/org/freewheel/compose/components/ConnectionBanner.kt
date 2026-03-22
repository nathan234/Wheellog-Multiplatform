package org.freewheel.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.freewheel.core.service.ConnectionState

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val show = connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.DiscoveringServices ||
            connectionState is ConnectionState.ConnectionLost ||
            connectionState is ConnectionState.Failed

    AnimatedVisibility(
        visible = show,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        val backgroundColor = when (connectionState) {
            is ConnectionState.Connecting, is ConnectionState.DiscoveringServices -> MaterialTheme.colorScheme.primary
            is ConnectionState.ConnectionLost -> MaterialTheme.colorScheme.tertiary
            is ConnectionState.Failed -> MaterialTheme.colorScheme.error
            is ConnectionState.Disconnected, is ConnectionState.Connected, is ConnectionState.Scanning -> MaterialTheme.colorScheme.outline
        }
        val contentColor = when (connectionState) {
            is ConnectionState.Connecting, is ConnectionState.DiscoveringServices -> MaterialTheme.colorScheme.onPrimary
            is ConnectionState.ConnectionLost -> MaterialTheme.colorScheme.onTertiary
            is ConnectionState.Failed -> MaterialTheme.colorScheme.onError
            is ConnectionState.Disconnected, is ConnectionState.Connected, is ConnectionState.Scanning -> MaterialTheme.colorScheme.onSurface
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(MaterialTheme.shapes.small)
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connectionState is ConnectionState.Connecting || connectionState is ConnectionState.DiscoveringServices) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = connectionState.statusText,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
