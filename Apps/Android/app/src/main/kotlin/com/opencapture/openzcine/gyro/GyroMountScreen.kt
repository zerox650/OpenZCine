package com.opencapture.openzcine.gyro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencapture.monitorui.MonitorPalette
import com.opencapture.monitorui.MonitorTypography
import com.opencapture.monitorui.monitorScrollFade
import com.opencapture.monitorui.settings.MonitorSettingsActionPill
import com.opencapture.monitorui.settings.MonitorSettingsRowCard
import com.opencapture.openzcine.R

/** Saves the phone's physical mount for future takes; screen rotation has no effect. */
@Composable
internal fun GyroMountScreen(state: GyroCaptureState, onSelect: (String) -> Unit, onClose: () -> Unit,
    onCalibrate: (() -> Unit)? = null, onCapture: (() -> Unit)? = null,
    onCancelCalibration: (() -> Unit)? = null, onResetCalibration: (() -> Unit)? = null) {
    BackHandler(onBack = onClose)
    val scroll = rememberLazyListState()
    Column(Modifier.fillMaxSize().background(MonitorPalette.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).testTag("gyro-mount"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gyro_mount_title), style = MonitorTypography.text(17f, FontWeight.SemiBold), color = MonitorPalette.text)
            MonitorSettingsActionPill(stringResource(R.string.action_close), onClick = onClose)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().monitorScrollFade(scroll), state = scroll,
            contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.calibration.active) {
                item {
                    MonitorSettingsRowCard {
                        Text(stringResource(R.string.gyro_mount_step, state.calibration.poseCount + 1),
                            style = MonitorTypography.text(13f, FontWeight.SemiBold), color = MonitorPalette.text)
                        Text(stringResource(when (state.calibration.poseCount) {
                            0 -> R.string.gyro_mount_pose_level
                            1 -> R.string.gyro_mount_pose_tilt
                            else -> R.string.gyro_mount_pose_point
                        }), style = MonitorTypography.text(12f), color = MonitorPalette.muted)
                        state.calibration.error?.let { Text(it, style = MonitorTypography.text(12f), color = MonitorPalette.accent) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            onCapture?.let { MonitorSettingsActionPill(
                                stringResource(if (state.calibration.poseCount == 2) R.string.gyro_mount_verify else R.string.gyro_mount_capture), onClick = it) }
                            onCalibrate?.let { MonitorSettingsActionPill(stringResource(R.string.gyro_mount_restart), onClick = it) }
                            onCancelCalibration?.let { MonitorSettingsActionPill(stringResource(R.string.action_cancel), onClick = it) }
                        }
                    }
                }
            } else {
                if (onCalibrate != null) item {
                    MonitorSettingsRowCard {
                        Text(stringResource(if (state.mountCalibration != null) R.string.gyro_mount_calibrated else R.string.gyro_mount_auto),
                            style = MonitorTypography.text(13f, FontWeight.SemiBold), color = MonitorPalette.text)
                        Text(stringResource(R.string.gyro_mount_auto_help),
                            style = MonitorTypography.text(12f), color = MonitorPalette.muted)
                        state.calibration.error?.let { Text(it, style = MonitorTypography.text(12f), color = MonitorPalette.accent) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonitorSettingsActionPill(stringResource(R.string.gyro_mount_calibrate), onClick = onCalibrate)
                            if (state.mountCalibration != null) onResetCalibration?.let {
                                MonitorSettingsActionPill(stringResource(R.string.action_reset), onClick = it)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(if (state.mountOptions.size == 1) R.string.gyro_mount_single_hint else R.string.gyro_mount_manual_hint),
                        style = MonitorTypography.text(12f), color = MonitorPalette.muted)
                }
                for ((group, options) in state.mountOptions.groupBy { it.group }) {
                    if (group != null) item { Text(group, style = MonitorTypography.text(13f, FontWeight.SemiBold), color = MonitorPalette.text) }
                    items(options, key = { it.id }) { option ->
                        MonitorSettingsRowCard {
                            val selected = state.mountCalibration == null && option.id == state.mountPreset
                            Row(Modifier.fillMaxWidth().selectable(selected = selected,
                                role = Role.RadioButton, onClick = { onSelect(option.id) })
                                .testTag("gyro-mount-${option.id}"), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = selected, onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MonitorPalette.accent,
                                        unselectedColor = MonitorPalette.muted))
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(option.title, style = MonitorTypography.text(13f, FontWeight.SemiBold), color = MonitorPalette.text)
                                    Text(option.detail, style = MonitorTypography.text(12f), color = MonitorPalette.muted)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.gyro_mount_changes_note),
                    style = MonitorTypography.text(12f), color = MonitorPalette.muted)
            }
        }
    }
}
