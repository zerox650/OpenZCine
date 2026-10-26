package com.opencapture.openzcine

import android.content.Context
import com.opencapture.opencine.cameracontrols.CaptureFormatChoice
import com.opencapture.opencine.cameracontrols.CaptureFormatGroup
import com.opencapture.opencine.nikon.CameraControl
import com.opencapture.opencine.nikon.NikonCapturePopupKind
import com.opencapture.opencine.nikon.NikonCapturePopupState
import com.opencapture.opencine.nikon.NikonControlSetting
import com.opencapture.opencine.nikon.NikonLadderControl
import com.opencapture.opencine.nikon.NikonLadderMode
import com.opencapture.opencine.nikon.NikonRecordingFormatPresentation
import com.opencapture.opencine.nikon.WhiteBalanceTint
import com.opencapture.opencine.nikon.bitDepth
import com.opencapture.opencine.vocabulary.LadderSheetPresentation
import com.opencapture.opencine.vocabulary.LadderTab
import com.opencapture.opencine.vocabulary.LadderWrite

// The Z pickers as the shared Nikon capture popup (OpenCine NikonCapturePopupState): this file
// only hands the session's values to the shared rules and routes the sheet's writes back.

/**
 * Pickers that draw on the shared LadderSheet. QUALITY stays on its own panel: the two wheels
 * compose one compression label, and the NEF chips write a different control.
 */
internal val ladderKinds: Set<MonitorPickerKind> = setOf(
    MonitorPickerKind.ISO,
    MonitorPickerKind.SHUTTER,
    MonitorPickerKind.IRIS,
    MonitorPickerKind.FOCUS,
    MonitorPickerKind.WHITE_BALANCE,
    MonitorPickerKind.RESOLUTION,
    MonitorPickerKind.CODEC,
    MonitorPickerKind.MODE,
    MonitorPickerKind.DRIVE,
    MonitorPickerKind.METER,
    MonitorPickerKind.PROFILE,
    MonitorPickerKind.SIZE,
)

/** The photo timer shot row rides on the drive tabs that ask for it. */
internal fun ladderShowsTimerShots(
    picker: MonitorPickerPresentation,
    tab: LadderTab<NikonLadderControl>,
): Boolean = (tab.key as? NikonLadderControl.Value)?.let { picker.modes.getOrNull(it.index)?.showsTimerShots } == true

/**
 * The shared popup state for [picker]. Builders that know the body's circuits (ISO, shutter,
 * white balance) attach their facts as [MonitorPickerPresentation.popup]; every other picker is
 * one setting, or independent settings when no tab writes on activation.
 */
internal fun nikonCapturePopup(picker: MonitorPickerPresentation, context: Context? = null): NikonCapturePopupState {
    val first = picker.modes.firstOrNull()?.request
    val facts = picker.popup ?: NikonCapturePopupState(
        kind = when {
            picker.kind == MonitorPickerKind.IRIS -> NikonCapturePopupKind.APERTURE
            picker.kind == MonitorPickerKind.CODEC -> NikonCapturePopupKind.CODEC
            picker.kind == MonitorPickerKind.WHITE_BALANCE -> NikonCapturePopupKind.WHITE_BALANCE
            picker.kind == MonitorPickerKind.DRIVE -> NikonCapturePopupKind.DRIVE
            picker.kind == MonitorPickerKind.FOCUS || picker.modes.none { it.applyValueOnActivate } ->
                NikonCapturePopupKind.INDEPENDENT_TABS
            else -> NikonCapturePopupKind.SETTINGS
        },
        title = picker.title,
        subtitle = picker.subtitle,
        modes = emptyList(),
        seededTab = picker.initialModeIndex,
        codecDepths = first?.bitDepths.orEmpty(),
        activeCodecDepth = first?.bitDepths?.firstOrNull { it.label == first.bitDepthSelection }?.bitDepth,
        tintCells = picker.modes.firstOrNull { it.request.control == CameraControl.WHITE_BALANCE_TINT }
            ?.let { WhiteBalanceTint.cells(it.request.currentValue.ifBlank { "Neutral" }) }
            ?: (0 to 0),
        tintAvailable = true,
        context = context,
    )
    return facts.copy(
        title = picker.title,
        subtitle = picker.subtitle,
        modes = picker.modes.mapIndexed { index, mode ->
            NikonLadderMode(
                index = index,
                title = mode.label,
                detail = mode.detail,
                options = mode.request.options,
                base = mode.markedValues.singleOrNull().orEmpty(),
                current = mode.request.currentValue,
                disabled = mode.disabled,
            )
        },
        typed = facts.typed ?: NikonControlSetting.entries.firstOrNull { it.control == first?.control },
    )
}

internal fun videoLadderPresentation(picker: MonitorPickerPresentation, context: Context? = null): LadderSheetPresentation<NikonLadderControl> {
    val sheet = nikonCapturePopup(picker, context).presentation
    return sheet.copy(
        // The shared rules reorder white balance into display order, so a tab is found by the mode
        // index its key carries, never by its position on the bar.
        tabs = sheet.tabs.map { tab ->
            // Only the labels this drum actually offers carry a word, so a tab never inherits
            // another drum's vocabulary (the white balance Kelvin drum spells no preset).
            tab.copy(valueLabels = ladderValueLabels(picker.kind, tab.ladder.values, context)
                .filterKeys { it in tab.ladder.values })
        },
        tabLabels = ladderTabLabels(picker, context),
    )
}

/**
 * The tab titles an operator reads, keyed by the tab's own label: the white balance bar spells its
 * three drums in the operator's language while every rule keeps reading Kelvin / Preset / Tint.
 */
private fun ladderTabLabels(picker: MonitorPickerPresentation, context: Context?): Map<String, String> {
    if (picker.kind != MonitorPickerKind.WHITE_BALANCE) return emptyMap()
    return mapOf(
        "Mode" to lstr(context, R.string.camera_mode_preset),
        "Kelvin" to lstr(context, R.string.camera_mode_kelvin),
        "Tint" to lstr(context, R.string.camera_mode_tint),
    ).filterValues { it.isNotEmpty() }
}

/**
 * The words one drum draws for the labels the body writes, keyed by the body's own label. The drum
 * reports the body's label back, so a translated drum still writes exactly what the camera
 * advertised — this is a display map, never a value map.
 *
 * The numbers are the body's own readouts and stay as the camera writes them (ISO, shutter,
 * aperture, Kelvin, codec, sensor area), as do the abbreviations an operator reads off the camera's
 * own screen (P/S/A/M, AF-S, CL/CH, C15). Which tab is which is read off the drum's own options,
 * never off a translated title: the Built-in Timer drum is the release picker's Off/On pair, and
 * the App-timer drum is the countdown ladder.
 */
private fun ladderValueLabels(
    kind: MonitorPickerKind,
    options: List<String>,
    context: Context?,
): Map<String, String> {
    // A word the offline table does not carry keeps the body's own label rather than blanking it.
    fun word(res: Int, vararg args: Any?): String? = lstr(context, res, *args).takeIf { it.isNotEmpty() }
    fun labels(vararg pairs: Pair<String, Int>): Map<String, String> =
        pairs.mapNotNull { (label, res) -> word(res)?.let { label to it } }.toMap()
    fun releaseLabels(): Map<String, String> = labels(
        "Single" to R.string.camera_drive_single,
        "Continuous L" to R.string.camera_drive_continuous_low,
        "CL" to R.string.camera_drive_continuous_low,
        "Continuous H" to R.string.camera_drive_continuous_high,
        "CH" to R.string.camera_drive_continuous_high,
        "Continuous H+" to R.string.camera_drive_continuous_high_plus,
        "CH+" to R.string.camera_drive_continuous_high_plus,
        "Timer" to R.string.camera_drive_self_timer,
        "Self-timer" to R.string.camera_drive_self_timer,
    )
    return when (kind) {
        MonitorPickerKind.DRIVE -> when {
            // The Built-in Timer drum: the body's engage/restore pair.
            options == listOf("Off", "On") -> labels(
                "Off" to R.string.camera_value_off,
                "On" to R.string.camera_value_on,
            )
            // The App-timer drum: the in-app countdown ladder.
            options.isNotEmpty() && options.all { it == "Off" || it.removeSuffix("s").toIntOrNull() != null } ->
                options.mapNotNull { label ->
                    val seconds = label.removeSuffix("s").toIntOrNull()
                    when {
                        seconds != null -> word(R.string.camera_value_seconds, seconds.toString())?.let { label to it }
                        label == "Off" -> word(R.string.camera_value_off)?.let { label to it }
                        else -> null
                    }
                }.toMap()
            else -> releaseLabels()
        }
        MonitorPickerKind.METER -> labels(
            "Matrix" to R.string.camera_meter_matrix,
            "Center" to R.string.camera_meter_center,
            "Spot" to R.string.camera_meter_spot,
            "Highlight" to R.string.camera_meter_highlight,
        )
        MonitorPickerKind.FOCUS -> when {
            // AF mode: AF-S / AF-C / AF-A / MF, the body's own abbreviations.
            options.any { it == "AF-S" || it == "AF-C" || it == "AF-A" || it == "MF" } -> emptyMap()
            // AF area: "Auto" here is the auto-area position, not subject detection.
            options.any { it in FOCUS_AREA_MARKERS } -> labels(
                "Pinpoint" to R.string.camera_value_focus_pinpoint,
                "Single" to R.string.camera_value_focus_single,
                "Dyn-S" to R.string.camera_value_focus_dyn_s,
                "Dyn-M" to R.string.camera_value_focus_dyn_m,
                "Dyn-L" to R.string.camera_value_focus_dyn_l,
                "Wide-S" to R.string.camera_value_focus_wide_s,
                "Wide-L" to R.string.camera_value_focus_wide_l,
                "3D tracking" to R.string.camera_value_focus_3d,
                "Auto" to R.string.camera_value_focus_auto_area,
            )
            // Subject detection: "Auto" here is automatic subject selection.
            options.any { it in FOCUS_SUBJECT_MARKERS } -> labels(
                "Off" to R.string.camera_value_off,
                "Auto" to R.string.camera_value_auto,
                "People" to R.string.camera_value_subject_people,
                "Animal" to R.string.camera_value_subject_animal,
                "Vehicle" to R.string.camera_value_subject_vehicle,
                "Bird" to R.string.camera_value_subject_bird,
                "Airplane" to R.string.camera_value_subject_airplane,
            )
            else -> emptyMap()
        }
        MonitorPickerKind.MODE -> labels("Auto" to R.string.camera_value_auto)
        MonitorPickerKind.PROFILE -> buildMap {
            putAll(labels(
                "Auto" to R.string.camera_value_auto,
                "Standard" to R.string.camera_value_pc_standard,
                "Neutral" to R.string.camera_value_pc_neutral,
                "Vivid" to R.string.camera_value_pc_vivid,
                "Monochrome" to R.string.camera_value_pc_monochrome,
                "Portrait" to R.string.camera_value_pc_portrait,
                "Landscape" to R.string.camera_value_pc_landscape,
                "Flat" to R.string.camera_value_pc_flat,
                "Flat Mono" to R.string.camera_value_pc_flat_mono,
                "Deep Tone Mono" to R.string.camera_value_pc_deep_tone_mono,
                "Rich Tone Portrait" to R.string.camera_value_pc_rich_tone_portrait,
                "Dream" to R.string.camera_value_pc_dream,
                "Morning" to R.string.camera_value_pc_morning,
                "Pop" to R.string.camera_value_pc_pop,
                "Sunday" to R.string.camera_value_pc_sunday,
                "Somber" to R.string.camera_value_pc_somber,
                "Drama" to R.string.camera_value_pc_drama,
                "Silence" to R.string.camera_value_pc_silence,
                "Bleach" to R.string.camera_value_pc_bleach,
                "Melancholic" to R.string.camera_value_pc_melancholic,
                "Pure" to R.string.camera_value_pc_pure,
                "Denim" to R.string.camera_value_pc_denim,
                "Toy" to R.string.camera_value_pc_toy,
                "Sepia" to R.string.camera_value_pc_sepia,
                "Blue" to R.string.camera_value_pc_blue,
                "Red" to R.string.camera_value_pc_red,
                "Pink" to R.string.camera_value_pc_pink,
                "Charcoal" to R.string.camera_value_pc_charcoal,
                "Graphite" to R.string.camera_value_pc_graphite,
                "Binary" to R.string.camera_value_pc_binary,
                "Carbon" to R.string.camera_value_pc_carbon,
            ))
            // The numbered slots follow the same rule with the body's own number.
            options.forEach { label ->
                val slot = label.substringAfterLast(' ').toIntOrNull()
                val res = when (label.substringBeforeLast(' ')) {
                    "Custom" -> R.string.camera_value_pc_custom
                    "Cloud" -> R.string.camera_value_pc_cloud
                    else -> null
                }
                if (slot != null && res != null) word(res, slot)?.let { put(label, it) }
            }
        }
        MonitorPickerKind.SIZE -> labels(
            "Size L" to R.string.camera_value_size_large,
            "Size M" to R.string.camera_value_size_medium,
            "Size S" to R.string.camera_value_size_small,
        )
        MonitorPickerKind.WHITE_BALANCE -> labels(
            "Auto" to R.string.camera_value_auto,
            "Natural auto" to R.string.camera_value_wb_natural_auto,
            "Sunny" to R.string.camera_value_wb_sunny,
            "Cloudy" to R.string.camera_value_wb_cloudy,
            "Shade" to R.string.camera_value_wb_shade,
            "Incandescent" to R.string.camera_value_wb_incandescent,
            "Fluorescent" to R.string.camera_value_wb_fluorescent,
            "Flash" to R.string.camera_value_wb_flash,
            "Preset" to R.string.camera_value_wb_preset,
        )
        else -> emptyMap()
    }
}

/** AF-area labels, so an area drum is told apart from the subject-detection drum beside it. */
private val FOCUS_AREA_MARKERS: Set<String> =
    setOf("Pinpoint", "Single", "Dyn-S", "Dyn-M", "Dyn-L", "Wide-S", "Wide-L", "3D tracking")

/** Subject-detection labels, which spell their own automatic position "Auto". */
private val FOCUS_SUBJECT_MARKERS: Set<String> =
    setOf("People", "Animal", "Vehicle", "Bird", "Airplane")

/** The control a circuit write drives, so it travels on the tab's own activation request. */
private fun circuitControls(key: NikonLadderControl): Set<CameraControl> = when (key) {
    NikonLadderControl.BaseIso -> setOf(CameraControl.BASE_ISO)
    NikonLadderControl.AutoIso -> setOf(CameraControl.ISO_AUTO)
    NikonLadderControl.StillAutoIso -> setOf(CameraControl.STILL_ISO_AUTO)
    NikonLadderControl.ShutterMode -> setOf(CameraControl.SHUTTER_MODE)
    NikonLadderControl.Tint -> setOf(CameraControl.WHITE_BALANCE_TINT)
    is NikonLadderControl.Value -> emptySet()
}

/**
 * The request and camera label a ladder write travels on: a value write uses its own tab's
 * request (tabs can share a control, as the three DRIVE tabs do), a circuit write the matching
 * activation request, spelled the way that request offers it ("On" is the body's "ON").
 */
internal fun requestForLadderWrite(
    picker: MonitorPickerPresentation,
    write: LadderWrite<NikonLadderControl>,
): Pair<CommandControlRequest, String>? {
    val key = write.key
    if (key is NikonLadderControl.Value) {
        return picker.modes.getOrNull(key.index)?.request?.let { it to write.value }
    }
    val controls = circuitControls(key)
    val request = picker.modes.flatMap { listOfNotNull(it.activateRequest, it.request) }
        .firstOrNull { it.control in controls } ?: return null
    return request to (request.options.firstOrNull { it.equals(write.value, ignoreCase = true) } ?: write.value)
}

/**
 * A recording-format picker's choices for the shared format picker, grouped by the camera's
 * reported typed formats (an unreported token stays one ungrouped choice); null for every other
 * picker. Tokens are never parsed.
 */
internal fun ladderFormats(
    picker: MonitorPickerPresentation,
    recordingFormats: List<NikonRecordingFormatPresentation>,
): List<CaptureFormatChoice<String>>? {
    if (picker.kind != MonitorPickerKind.RESOLUTION) return null
    return picker.modes.firstOrNull()?.request?.options?.map { token ->
        val format = recordingFormats.firstOrNull { it.token == token }
        CaptureFormatChoice(value = token, title = token,
            imageArea = format?.imageArea?.let { CaptureFormatGroup(it, it) },
            resolution = format?.resolution?.let { CaptureFormatGroup(it, it) },
            frameRate = format?.frameRate?.let { CaptureFormatGroup(it, it) })
    }
}
