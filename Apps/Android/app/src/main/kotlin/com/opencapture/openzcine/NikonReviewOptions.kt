package com.opencapture.openzcine

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.opencapture.monitorui.settings.MonitorSettingsChoices
import com.opencapture.monitorui.settings.MonitorSettingsInlineRow
import com.opencapture.monitorui.settings.MonitorSettingsSwitchInlineRow
import com.opencapture.opencine.appkit.MonitorReviewDuration
import com.opencapture.openzcine.R

/** Nikon supplies review preferences to the shared inspector's typed extension slot. */
@Composable
internal fun NikonReviewOptions(assists: NikonAssists) {
    val options = assists.reviewOptions
    MonitorSettingsInlineRow(stringResource(R.string.settings_review_duration), showTopDivider = false, stacked = true,
        help = stringResource(R.string.settings_review_duration_help)) {
        MonitorSettingsChoices(MonitorReviewDuration.entries, options.duration, accentSelection = true,
            title = MonitorReviewDuration::label) {
            assists.updateReviewOptions(options.copy(duration = it))
        }
    }
    MonitorSettingsSwitchInlineRow(stringResource(R.string.settings_review_focus_point), options.showsFocusPoint,
        help = stringResource(R.string.settings_review_focus_point_help)) {
        assists.updateReviewOptions(options.copy(showsFocusPoint = !options.showsFocusPoint))
    }
    MonitorSettingsSwitchInlineRow(stringResource(R.string.settings_review_capture_info), options.showsCaptureInfo,
        help = stringResource(R.string.settings_review_capture_info_help)) {
        assists.updateReviewOptions(options.copy(showsCaptureInfo = !options.showsCaptureInfo))
    }
}
