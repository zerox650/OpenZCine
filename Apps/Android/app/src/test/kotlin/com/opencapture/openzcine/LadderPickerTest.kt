package com.opencapture.openzcine

import com.opencapture.opencine.core.SettingKey
import com.opencapture.opencine.nikon.CameraControl
import com.opencapture.opencine.nikon.CodecBitDepthOption
import com.opencapture.opencine.nikon.IsoPickerPolicy
import com.opencapture.opencine.nikon.NikonCapturePopupKind
import com.opencapture.opencine.nikon.NikonCapturePopupState
import com.opencapture.opencine.nikon.NikonLadderControl
import com.opencapture.opencine.nikon.NikonRecordingFormatPresentation
import com.opencapture.opencine.nikon.StillPickerLadders
import com.opencapture.opencine.vocabulary.CaptureLock
import com.opencapture.opencine.vocabulary.LadderWrite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Z pickers handed to the shared Nikon capture popup, and its writes routed back. */
class LadderPickerTest {
    @Test
    fun dualBaseIsoWritesTheBaseThroughItsOwnRequest() {
        val picker = picker(
            MonitorPickerKind.ISO,
            mode("Low Base", CameraControl.ISO, "800", listOf("800", "1000"), detail = "800 / 4000",
                activate = request(CameraControl.BASE_ISO, "Low", listOf("Low", "High"))),
            mode("High Base", CameraControl.ISO, "3200", listOf("3200", "6400"),
                activate = request(CameraControl.BASE_ISO, "High", listOf("Low", "High"))),
            popup = facts(NikonCapturePopupKind.ISO).copy(codec = "R3D NE", dualBaseIso = true, circuitIndex = 1),
        )
        val sheet = videoLadderPresentation(picker)
        assertEquals(listOf("Low Base", "High Base"), sheet.tabs.map { it.label })
        assertEquals("800 / 4000", sheet.tabs[0].detail)
        assertEquals(1, sheet.initialTab)
        assertEquals(IsoPickerPolicy.markedValues("R3D NE", 0), sheet.tabs[0].ladder.marked)
        assertEquals(LadderWrite<NikonLadderControl>(NikonLadderControl.BaseIso, "Low"), sheet.tabs[0].activate)
        assertTrue(sheet.tabs[0].applyOnActivate)
        assertNull(sheet.lock)
        val (request, value) = assertNotNull(requestForLadderWrite(picker, sheet.tabs[1].activate!!))
        assertEquals(CameraControl.BASE_ISO to "High", request.control to value)
    }

    @Test
    fun autoIsoLocksEachDrumAndWritesTheBodysSpelling() {
        val auto = request(CameraControl.ISO_AUTO, "ON", listOf("ON", "OFF"))
        val picker = picker(
            MonitorPickerKind.ISO,
            mode("Auto On", CameraControl.ISO, "800", listOf("800"), activate = auto),
            mode("Auto Off", CameraControl.ISO, "800", listOf("800"), activate = auto.copy(currentValue = "OFF")),
            popup = facts(NikonCapturePopupKind.ISO).copy(codec = "H.265", autoIso = true, isoCameraOwned = true),
        )
        val sheet = videoLadderPresentation(picker)
        assertNull(sheet.lock)
        assertTrue(sheet.tabs.all { it.ladder.lock == CaptureLock.AUTO })
        assertTrue(sheet.lockBanner.orEmpty().startsWith("Auto ISO is on"))
        assertFalse(sheet.tabs[0].applyOnActivate)
        val (request, value) = assertNotNull(requestForLadderWrite(picker, sheet.tabs[0].activate!!))
        assertEquals(CameraControl.ISO_AUTO to "ON", request.control to value)
    }

    @Test
    fun shutterLockFreezesTheSheetAndATabWritesOnlyTheCircuit() {
        val picker = picker(
            MonitorPickerKind.SHUTTER,
            mode("Angle", CameraControl.SHUTTER, "180", listOf("90", "180"),
                activate = request(CameraControl.SHUTTER_MODE, "Angle", listOf("Angle", "Speed"))),
            mode("Speed", CameraControl.SHUTTER, "1/50", listOf("1/50", "1/100"),
                activate = request(CameraControl.SHUTTER_MODE, "Speed", listOf("Angle", "Speed"))),
            popup = facts(NikonCapturePopupKind.SHUTTER).copy(shutterLockedOnCamera = true),
        )
        val sheet = videoLadderPresentation(picker)
        assertEquals(CaptureLock.CAMERA_CONTROL, sheet.lock)
        assertTrue(sheet.lock!!.freezesSheet)
        assertFalse(sheet.tabs[0].applyOnActivate)
        // Pocket's order: Speed, Angle.
        assertEquals(listOf("Speed", "Angle"), sheet.tabs.map { it.label })
        val (request, value) = assertNotNull(requestForLadderWrite(picker, sheet.tabs[0].activate!!))
        assertEquals(CameraControl.SHUTTER_MODE to "Speed", request.control to value)
    }

    @Test
    fun photoModeDisablesTheUserBankTabAndDriveTimersKeepTheShotRow() {
        val modeSheet = videoLadderPresentation(
            picker(
                MonitorPickerKind.MODE,
                mode("Mode", CameraControl.EXPOSURE_MODE, "P", listOf("Auto", "P", "U1"), applyOnActivate = false),
                mode(
                    "U Mode", CameraControl.STILL_USER_MODE_PROGRAM, "P", listOf("P", "S", "A", "M"),
                    applyOnActivate = false, disabled = true,
                ),
            ),
        )
        assertFalse(modeSheet.tabs[0].disabled)
        assertTrue(modeSheet.tabs[1].disabled)
        assertFalse(modeSheet.tabs[1].applyOnActivate)
        assertEquals(NikonLadderControl.Value(1), modeSheet.tabs[1].key)
        assertNull(modeSheet.tabs[0].setting)

        val drivePicker = picker(
            MonitorPickerKind.DRIVE,
            mode(
                "Drive", CameraControl.STILL_DRIVE, "Single", listOf("Single", "Continuous L"),
                applyOnActivate = false, disabled = true,
            ),
            mode(
                "Built-in Timer", CameraControl.STILL_DRIVE, "On", listOf("Off", "On"),
                applyOnActivate = false, showsTimerShots = true,
            ),
            mode(
                "App-timer", CameraControl.STILL_DRIVE, "Off", StillPickerLadders.APP_TIMER_OPTIONS,
                applyOnActivate = false, showsTimerShots = true,
            ),
        )
        val drive = videoLadderPresentation(drivePicker)
        assertTrue(drive.tabs[0].disabled)
        assertFalse(ladderShowsTimerShots(drivePicker, drive.tabs[0]))
        assertTrue(ladderShowsTimerShots(drivePicker, drive.tabs[1]))
        assertTrue(ladderShowsTimerShots(drivePicker, drive.tabs[2]))
        val (write, _) = assertNotNull(requestForLadderWrite(drivePicker, LadderWrite(drive.tabs[2].key, "5s")))
        assertTrue("5s" in write.options)
        assertFalse("Single" in write.options)

        val meter = videoLadderPresentation(
            picker(
                MonitorPickerKind.METER,
                mode("METER", CameraControl.STILL_METER, "Matrix", listOf("Matrix", "Spot")),
            ),
        )
        assertEquals(listOf("Matrix", "Spot"), meter.tabs.single().ladder.values)
        assertNull(meter.tabs.single().setting)
    }

    @Test
    fun kelvinGrowsAndStepsLandWhilePresetIsTheTypedSetting() {
        val picker = picker(
            MonitorPickerKind.WHITE_BALANCE,
            mode("Kelvin", CameraControl.WHITE_BALANCE, "5560K", listOf("5560K", "5880K")),
            mode("Preset", CameraControl.WHITE_BALANCE, "Sunny", listOf("Sunny", "Cloudy")),
            mode("Tint", CameraControl.WHITE_BALANCE_TINT, "A1 \u00B7 M0.25", listOf("Neutral")),
        )
        val sheet = videoLadderPresentation(picker)
        // Pocket's order: Mode (the body's presets), Kelvin, Tint.
        assertEquals(listOf("Mode", "Kelvin", "Tint"), sheet.tabs.map { it.label })
        val kelvin = sheet.tabs[1]
        assertNull(kelvin.setting)
        assertEquals(listOf("5550K", "5560K"), kelvin.values?.invoke("5550K")?.filter { it == "5550K" || it == "5560K" })
        val steps = assertNotNull(kelvin.steps?.invoke("5560K"))
        assertEquals("\u221210", steps.first.label)
        assertEquals("5550K", steps.first.value)
        assertTrue(steps.first.lands)
        assertEquals("5570K", steps.second.value)
        assertEquals(SettingKey.WHITE_BALANCE, sheet.tabs[0].setting?.key)
        val tint = sheet.tabs[2]
        assertEquals(-1, tint.tint?.greenMagenta)
        assertEquals(2, tint.tint?.amberBlue)
        assertFalse(tint.applyOnActivate)
        assertNull(tint.setting)
    }

    @Test
    fun focusTabsDoNotWriteOnActivation() {
        val picker = picker(
            MonitorPickerKind.FOCUS,
            mode("AF Mode", CameraControl.FOCUS_MODE, "AF-C", listOf("AF-S", "AF-C", "MF")),
            mode("Area", CameraControl.FOCUS_AREA, "Wide", listOf("Wide", "Subject")),
            mode("Subject", CameraControl.FOCUS_SUBJECT, "Auto", listOf("Auto", "People")),
        )
        val sheet = videoLadderPresentation(picker)
        assertTrue(sheet.tabs.all { !it.applyOnActivate && it.activate == null && it.setting == null && it.steps == null })
        assertEquals(CameraControl.FOCUS_AREA, requestForLadderWrite(picker, LadderWrite(sheet.tabs[1].key, "Wide"))?.first?.control)
    }

    @Test
    fun codecBitDepthIsTwoStepsThatDoNotLand() {
        val depth = listOf(
            CodecBitDepthOption("H.265", "8-bit", "H.265 8-bit"),
            CodecBitDepthOption("H.265", "10-bit", "H.265 10-bit"),
        )
        val codec = mode("Codec", CameraControl.CODEC, "H.265", listOf("H.264", "H.265"))
            .copy(request = request(CameraControl.CODEC, "H.265", listOf("H.264", "H.265")).copy(
                bitDepths = depth,
                bitDepthSelection = "10-bit",
            ))
        val sheet = videoLadderPresentation(picker(MonitorPickerKind.CODEC, codec))
        val steps = assertNotNull(sheet.tabs.single().steps?.invoke("H.265"))
        assertEquals("8-bit" to "H.265 8-bit", steps.first.label to steps.first.value)
        assertEquals("10-bit" to "H.265 10-bit", steps.second.label to steps.second.value)
        assertEquals("Record H.265 at 10 bit", steps.second.description)
        assertFalse(steps.first.lands)
        assertFalse(steps.first.selected)
        assertTrue(steps.second.selected)
        assertNull(sheet.tabs.single().steps?.invoke("H.264"))
    }

    @Test
    fun resolutionIsTheSharedFormatPickerAndIrisIsTheTypedAperture() {
        val resolutionPicker = picker(
            MonitorPickerKind.RESOLUTION,
            mode("Resolution", CameraControl.RESOLUTION_FRAMERATE, "4K 24p", listOf("4K 24p", "4K 30p")),
        )
        val resolution = videoLadderPresentation(resolutionPicker)
        assertNull(resolution.tabs.single().steps)
        assertNull(resolution.tabs.single().setting)
        // The recording format is the shared format picker, one choice per camera token.
        // The camera's typed formats group the choices; an unreported token stays one ungrouped choice.
        val formats = assertNotNull(ladderFormats(resolutionPicker,
            listOf(NikonRecordingFormatPresentation("4K 24p", "FX", "4K", "24p"))))
        assertEquals(listOf("4K 24p", "4K 30p"), formats.map { it.value })
        assertEquals("24p", formats[0].frameRate?.id)
        assertNull(formats[1].frameRate)
        assertNull(ladderFormats(picker(MonitorPickerKind.ISO, mode("ISO", CameraControl.ISO, "800", listOf("800"))), emptyList()))
        val iris = videoLadderPresentation(picker(MonitorPickerKind.IRIS, irisMode()))
        assertEquals(SettingKey.APERTURE, iris.tabs.single().setting?.key)
        assertTrue(iris.tabs.single().setting?.offered.orEmpty().isNotEmpty())
    }

    @Test
    fun aFineKelvinWriteStillFindsTheWhiteBalanceRequest() {
        val picker = picker(
            MonitorPickerKind.WHITE_BALANCE,
            mode("Kelvin", CameraControl.WHITE_BALANCE, "5560K", listOf("5560K")),
        )
        val write = LadderWrite<NikonLadderControl>(NikonLadderControl.Value(0), "5550K")
        assertEquals(CameraControl.WHITE_BALANCE to "5550K", requestForLadderWrite(picker, write)?.let { it.first.control to it.second })
    }

    @Test
    fun drumsDrawTheOperatorsWordsAndWriteTheBodysOwnLabelsBack() {
        // The words an operator reads never reach the camera: the drum reports the body's label.
        val drive = videoLadderPresentation(picker(
            MonitorPickerKind.DRIVE,
            mode("Drive", CameraControl.STILL_DRIVE, "Single",
                listOf("Single", "Continuous L", "Continuous H+", "C15"), applyOnActivate = false),
            mode("Built-in Timer", CameraControl.STILL_DRIVE, "On", listOf("Off", "On"),
                applyOnActivate = false, showsTimerShots = true),
            mode("App-timer", CameraControl.STILL_DRIVE, "Off", StillPickerLadders.APP_TIMER_OPTIONS,
                applyOnActivate = false, showsTimerShots = true),
        ))
        // Release mode, the two timers, and the abbreviations a body spells its own way.
        assertEquals(mapOf("Single" to "Single", "Continuous L" to "CL", "Continuous H+" to "CH+"),
            drive.tabs[0].valueLabels.filterKeys { it in setOf("Single", "Continuous L", "Continuous H+") })
        assertEquals(mapOf("Off" to "Off", "On" to "On"), drive.tabs[1].valueLabels)
        assertEquals(mapOf("Off" to "Off", "1s" to "1 s", "10s" to "10 s"), drive.tabs[2].valueLabels
            .filterKeys { it in setOf("Off", "1s", "10s") })
        // The ladder keeps the body's own labels, so the write still round-trips.
        assertEquals(listOf("Single", "Continuous L", "Continuous H+", "C15"), drive.tabs[0].ladder.values)
        val (request, value) = assertNotNull(requestForLadderWrite(
            picker(MonitorPickerKind.DRIVE,
                mode("Drive", CameraControl.STILL_DRIVE, "Single",
                    listOf("Single", "Continuous L"), applyOnActivate = false)),
            LadderWrite(NikonLadderControl.Value(0), "Continuous L")))
        assertEquals(CameraControl.STILL_DRIVE to "Continuous L", request.control to value)

        val meter = videoLadderPresentation(picker(
            MonitorPickerKind.METER,
            mode("METER", CameraControl.STILL_METER, "Matrix", listOf("Matrix", "Center", "Spot"))))
        // The map carries the whole metering vocabulary; a drum only offers what its body does.
        assertEquals(mapOf("Matrix" to "Matrix", "Center" to "Center", "Spot" to "Spot"),
            meter.tabs.single().valueLabels.filterKeys { it in setOf("Matrix", "Center", "Spot") })
        // The body's abbreviations stay as the camera writes them.
        val focus = videoLadderPresentation(picker(
            MonitorPickerKind.FOCUS,
            mode("AF Mode", CameraControl.FOCUS_MODE, "AF-S", listOf("AF-S", "AF-C", "MF")),
            mode("Area", CameraControl.FOCUS_AREA, "Wide-L",
                listOf("Pinpoint", "Single", "Wide-L", "3D tracking", "Auto")),
            mode("Subject", CameraControl.FOCUS_SUBJECT, "Auto", listOf("Off", "Auto", "People"))))
        assertTrue(focus.tabs[0].valueLabels.isEmpty())
        assertEquals("Auto" to "Auto", focus.tabs[1].valueLabels.entries.single { it.key == "Auto" }.toPair())
        assertEquals("Auto" to "Auto", focus.tabs[2].valueLabels.entries.single { it.key == "Auto" }.toPair())
        // The area drum's "Single" is one AF point; the drive drum's is one frame.
        assertEquals("Single" to "Single", focus.tabs[1].valueLabels.entries.single { it.key == "Single" }.toPair())

        val profile = videoLadderPresentation(picker(
            MonitorPickerKind.PROFILE,
            mode("PROFILE", CameraControl.STILL_PICTURE_CONTROL, "Dream",
                listOf("Auto", "Dream", "Custom 3", "Cloud 2"))))
        // The whole picture-control vocabulary travels; a numbered slot keeps the body's number.
        assertEquals(mapOf("Auto" to "Auto", "Dream" to "Dream", "Custom 3" to "Custom 3", "Cloud 2" to "Cloud 2"),
            profile.tabs.single().valueLabels.filterKeys { it in setOf("Auto", "Dream", "Custom 3", "Cloud 2") })
    }

    @Test
    fun theWhiteBalanceBarSpellsItsTabsAndItsPresetsInTheOperatorsLanguage() {
        val picker = picker(
            MonitorPickerKind.WHITE_BALANCE,
            mode("Kelvin", CameraControl.WHITE_BALANCE, "5560K", listOf("5560K")),
            mode("Preset", CameraControl.WHITE_BALANCE, "Sunny", listOf("Auto", "Sunny", "Cloudy")),
            mode("Tint", CameraControl.WHITE_BALANCE_TINT, "Neutral", listOf("Neutral")),
        )
        val sheet = videoLadderPresentation(picker)
        // The bar's titles, while every rule keeps reading Mode / Kelvin / Tint.
        assertEquals(mapOf("Mode" to "Preset", "Kelvin" to "Kelvin", "Tint" to "Tint"), sheet.tabLabels)
        assertEquals(listOf("Mode", "Kelvin", "Tint"), sheet.tabs.map { it.label })
        // Kelvin keeps the body's own readouts; the preset drum draws words.
        assertTrue(sheet.tabs[1].valueLabels.isEmpty())
        assertEquals(mapOf("Auto" to "Auto", "Sunny" to "Sunny", "Cloudy" to "Cloudy"), sheet.tabs[0].valueLabels)
        // A tab the host has no words for keeps its own label.
        val iso = videoLadderPresentation(picker(
            MonitorPickerKind.ISO,
            mode("ISO", CameraControl.ISO, "800", listOf("800"))))
        assertTrue(iso.tabLabels.isEmpty())
    }

    private fun irisMode() = mode("Iris", CameraControl.IRIS, "f/2.8", listOf("f/2.8"))

    private fun picker(
        kind: MonitorPickerKind,
        vararg modes: MonitorPickerModePresentation,
        popup: NikonCapturePopupState? = null,
    ) = MonitorPickerPresentation(
        kind = kind,
        title = kind.name,
        subtitle = "video",
        modes = modes.toList(),
        popup = popup,
    )

    private fun facts(kind: NikonCapturePopupKind) =
        NikonCapturePopupState(kind = kind, title = "T", subtitle = "S", modes = emptyList())

    private fun mode(
        label: String,
        control: CameraControl,
        current: String,
        options: List<String>,
        detail: String? = null,
        activate: CommandControlRequest? = null,
        marked: Set<String> = emptySet(),
        applyOnActivate: Boolean = true,
        disabled: Boolean = false,
        showsTimerShots: Boolean = false,
    ) = MonitorPickerModePresentation(
        label = label,
        request = request(control, current, options),
        detail = detail,
        activateRequest = activate,
        markedValues = marked,
        applyValueOnActivate = applyOnActivate,
        disabled = disabled,
        showsTimerShots = showsTimerShots,
    )

    private fun request(control: CameraControl, current: String, options: List<String>) = CommandControlRequest(
        title = control.name,
        control = control,
        currentValue = current,
        options = options,
    )
}
