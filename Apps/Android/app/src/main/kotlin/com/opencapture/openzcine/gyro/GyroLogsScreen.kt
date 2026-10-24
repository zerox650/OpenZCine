package com.opencapture.openzcine.gyro

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.opencapture.openzcine.R
import com.opencapture.monitorui.MonitorPalette
import com.opencapture.monitorui.MonitorConfirmPopup
import com.opencapture.monitorui.settings.MonitorSettingsActionPill
import com.opencapture.monitorui.settings.MonitorSettingsRowCard
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Compact capture health, opening the same logs list as Operator Setup. */
@Composable
internal fun GyroStatusPill(state: GyroCaptureState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = when {
        state.statusKind == GyroStatusKind.SAMPLE_GAPS -> stringResource(R.string.gyro_pill_gap)
        state.failed -> stringResource(R.string.gyro_pill_error)
        state.statusKind == GyroStatusKind.SAVING -> stringResource(R.string.gyro_pill_saving)
        state.logging -> stringResource(R.string.gyro_pill_rec)
        state.statusKind == GyroStatusKind.READY -> stringResource(R.string.gyro_pill_armed)
        state.statusKind == GyroStatusKind.STARTING -> stringResource(R.string.gyro_pill_starting)
        else -> stringResource(R.string.gyro_pill_offline)
    }
    Text(label, modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
        RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp)
        .testTag("gyro-status"), color = if (state.failed) MonitorPalette.accent else MonitorPalette.text,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
}

/** App-owned motion logs, including incomplete files retained after failed recovery. */
@Composable
internal fun GyroLogsScreen(state: GyroCaptureState, onShare: (GyroLogEntry) -> Unit,
    onDelete: (GyroLogEntry) -> Unit, onClose: () -> Unit) {
    var deleting by remember { mutableStateOf<GyroLogEntry?>(null) }
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(MonitorPalette.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
        .testTag("gyro-logs"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.gyro_logs_title), color = MonitorPalette.text)
            MonitorSettingsActionPill(stringResource(R.string.action_close), onClick = onClose)
        }
        Text(stringResource(R.string.gyro_logs_instruction),
            color = MonitorPalette.muted)
        if (state.logging || state.failed) Text(state.status,
            color = if (state.failed) MonitorPalette.accent else MonitorPalette.text)
        state.storageWarning?.let { Text(it, color = MonitorPalette.accent) }
        if (state.logs.isEmpty()) Text(stringResource(R.string.gyro_logs_empty),
            color = MonitorPalette.muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(state.logs, key = { it.id }) { entry ->
                MonitorSettingsRowCard {
                    Text(entry.cameraLabel, color = MonitorPalette.text)
                    Text(DateFormat.getDateTimeInstance().format(Date(entry.startDateMillis)), color = MonitorPalette.muted)
                    Text(stringResource(R.string.gyro_logs_mount, entry.mountDetail(state.mountCatalog, context)), color = MonitorPalette.muted)
                    entry.summary?.let { summary ->
                        Text(stringResource(R.string.gyro_logs_summary, summary.getDouble("duration"),
                            summary.getDouble("measuredRate"), entry.healthLabel(context)), color = MonitorPalette.text)
                        Text(stringResource(R.string.gyro_logs_gap, summary.getDouble("largestGap") * 1_000,
                            summary.getDouble("approximateStartOffset")), color = MonitorPalette.muted)
                    } ?: Text(entry.healthLabel(context), color = MonitorPalette.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonitorSettingsActionPill(stringResource(R.string.gyro_logs_share), modifier = Modifier.testTag("gyro-share-${entry.id}"),
                            enabled = entry.canShare, onClick = { onShare(entry) })
                        MonitorSettingsActionPill(stringResource(R.string.gyro_logs_delete), modifier = Modifier.testTag("gyro-delete-${entry.id}"),
                            enabled = !entry.active, onClick = { deleting = entry })
                    }
                }
            }
        }
    }
    deleting?.let { entry ->
        Dialog(onDismissRequest = { deleting = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.testTag("gyro-delete-confirmation")) {
                MonitorConfirmPopup(stringResource(R.string.gyro_logs_delete_title),
                    stringResource(R.string.gyro_logs_delete_confirm), onDismiss = { deleting = null },
                    onConfirm = { deleting = null; onDelete(entry) })
            }
        }
    }
}

/** Stages off Main under the activity's scope; chooser and failure callbacks run on Main. */
internal fun shareGyroLog(context: Context, entry: GyroLogEntry, scope: CoroutineScope,
    onFailure: (Exception) -> Unit): Job = shareGyroLog(scope, context.cacheDir, entry,
    onReady = { staged -> presentGyroLog(context, staged) }, onFailure = onFailure, context = context)

private fun presentGyroLog(context: Context, staged: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.media-share", staged)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, staged.name)
        clipData = ClipData.newRawUri(context.getString(R.string.gyro_logs_share_clip), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.gyro_logs_share_chooser)))
}
