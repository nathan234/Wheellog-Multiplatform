package com.cooper.wheellog.compose.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.data.TripDataDbEntry
import com.cooper.wheellog.core.utils.ByteUtils
import com.cooper.wheellog.core.utils.DisplayUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RidesScreen(viewModel: WheelViewModel) {
    val context = LocalContext.current
    var trips by remember { mutableStateOf<List<TripDataDbEntry>>(emptyList()) }
    val useMph = viewModel.appConfig.useMph

    LaunchedEffect(Unit) {
        trips = viewModel.loadTrips()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        Text(
            "Rides",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (trips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No Rides Recorded",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect to your wheel and start logging",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(trips, key = { it.id }) { trip ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                trips = trips.filter { it.id != trip.id }
                                viewModel.deleteTrip(trip, context)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                    MaterialTheme.colorScheme.errorContainer
                                else Color.Transparent,
                                label = "swipe-bg"
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        RideRow(
                            trip = trip,
                            useMph = useMph,
                            onShare = {
                                val ridesDir = File(context.getExternalFilesDir(null), "rides")
                                val csvFile = File(ridesDir, trip.fileName)
                                if (csvFile.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        csvFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ride"))
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun RideRow(
    trip: TripDataDbEntry,
    useMph: Boolean,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title: friendly date
            Text(
                text = formatFriendlyDate(trip.start.toLong() * 1000),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            // Line 1: Duration + Distance
            val durationStr = DisplayUtils.formatDurationShort(trip.duration)
            val distStr = DisplayUtils.formatDistance(trip.distance / 1000.0, useMph)
            Text(
                text = "$durationStr  |  $distStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Line 2: Max speed + Avg speed
            val maxSpeedStr = DisplayUtils.formatSpeed(trip.maxSpeed.toDouble(), useMph)
            val avgSpeedStr = DisplayUtils.formatSpeed(trip.avgSpeed.toDouble(), useMph)
            Text(
                text = "$maxSpeedStr max  |  $avgSpeedStr avg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Line 3: Power + Energy (if data exists)
            if (trip.maxPower > 0 || trip.consumptionByKm > 0) {
                val parts = mutableListOf<String>()
                if (trip.maxPower > 0) {
                    parts.add("${trip.maxPower.toInt()} W max")
                }
                if (trip.consumptionByKm > 0) {
                    val unit = if (useMph) "Wh/mi" else "Wh/km"
                    val value = if (useMph) trip.consumptionByKm / ByteUtils.KM_TO_MILES_MULTIPLIER else trip.consumptionByKm.toDouble()
                    parts.add("%.1f %s".format(value, unit))
                }
                Text(
                    text = parts.joinToString("  |  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


private fun formatFriendlyDate(epochMillis: Long): String {
    if (epochMillis <= 0) return "Unknown date"

    val date = Date(epochMillis)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(date)

    return when {
        isSameDay(now, then) -> "Today, $timeStr"
        isYesterday(now, then) -> "Yesterday, $timeStr"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> {
            val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            "${dayFormat.format(date)}, $timeStr"
        }
        else -> {
            val dayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            "${dayFormat.format(date)}, $timeStr"
        }
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(now: Calendar, then: Calendar): Boolean {
    val yesterday = now.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(yesterday, then)
}
