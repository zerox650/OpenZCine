package com.opencapture.openzcine.gyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import com.opencapture.opencine.nikon.CameraSession
import com.opencapture.opencine.nikon.CameraSessionEvent
import com.opencapture.opencine.nikon.CameraSessionState
import com.opencapture.opencine.nikon.LiveCameraLevel
import com.opencapture.opencine.nikon.SwiftCoreCameraSession
import com.opencapture.opencine.nikon.SwiftCoreLiveFrameSource
import com.opencapture.opencine.nikon.metadataOnly
import com.opencapture.openzcine.R
import com.opencapture.openzcine.bridge.SwiftCore
import com.opencapture.monitorui.MonitorTiltVector
import com.opencapture.monitorui.MonitorDeviceTiltMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal data class GyroCaptureState(
    val enabled: Boolean = false,
    val available: Boolean = false,
    val status: String = "Off",
    /**
     * The wire kind behind [status]: the status pill and the settings row switch on it, so the
     * displayed [status] text stays free to localize.
     */
    val statusKind: GyroStatusKind = GyroStatusKind.OFF,
    val logging: Boolean = false,
    val failed: Boolean = false,
    val logs: List<GyroLogEntry> = emptyList(),
    val storageWarning: String? = null,
    val mountPreset: String = "unconfigured",
    val mountOptions: List<GyroMountOption> = emptyList(),
    val mountCatalog: Map<String, GyroMountOption> = emptyMap(),
    val mountCalibration: String? = null,
    val calibration: GyroCalibrationState = GyroCalibrationState(),
)

/** The kind behind [GyroCaptureState.status]; never shown raw, only switched on. */
internal enum class GyroStatusKind {
    OFF, UNAVAILABLE, WAITING_FOR_CAMERA, STARTING, READY, LOGGING, SAVING, SAMPLE_GAPS,
}

private val gyroCaptureDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Gyro logs movie takes only, so the body must positively report the video selector. An unread
 * or unreported selector waits like photo mode instead of arming (ZT-06); each property update
 * re-evaluates it. iOS twin: `GyroCaptureCoordinator.selectorArms`.
 */
internal fun gyroSelectorArms(captureSelector: String?): Boolean =
    captureSelector.equals("video", ignoreCase = true)

/** Keeps replacement recovery behind prior activity writes without sharing cancellation. */
internal fun gyroCaptureScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + gyroCaptureDispatcher)

/** App-owned sensor/file adapter; all take boundaries and health rules are Swift-owned. */
internal class GyroCaptureCoordinator(private val context: Context, private val native: GyroNative = SwiftGyroNative,
    private val calibrationNative: GyroCalibrationNative = SwiftGyroCalibrationNative) {
    private val scope = gyroCaptureScope()
    private val preferences = context.getSharedPreferences("gyro-logging", Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val naturalPortrait = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)?.mode?.let { it.physicalWidth < it.physicalHeight } == true
    private val store by lazy { GyroLogStore(File(context.filesDir, "gyro-logs"), native) }
    private val mutableState = MutableStateFlow(GyroCaptureState(enabled = preferences.getBoolean("enabled", false)))
    val state: StateFlow<GyroCaptureState> = mutableState
    private var session: CameraSession? = null
    private var foreground = false
    private var owner: String? = null
    private var sensorThread: HandlerThread? = null
    private var tick: Job? = null
    private var observations: Job? = null
    private var events: Job? = null
    private var connection: Job? = null
    private var cameraMode: Job? = null
    private val pending = ArrayDeque<AndroidGyroSample>()
    private val pendingObservations = ArrayDeque<RecordingObservation>()
    private var overflowed = false
    private var cameraLabel = "Camera"
    private var lastReported: Boolean? = null
    private var stalled = false
    private var sensorGeneration = 0L
    private var listener: SensorEventListener? = null
    private var bindingGeneration = 0L
    private var armedTimestamp: Double? = null
    @Volatile private var selectedMount = GyroMountSelection()
    private val calibration = GyroCalibrationSession(calibrationNative)
    private var calibrationGeneration = 0L
    private var calibrationFrames: Job? = null
    private var calibrationThread: HandlerThread? = null
    private var calibrationListener: SensorEventListener? = null
    private var calibrationCamera: Pair<LiveCameraLevel, Double>? = null
    private var calibrationSourceAvailable = true

    init {
        scope.launch {
            val available = sensor != null && SwiftCore.isAvailable
            mutableState.value = mutableState.value.copy(available = available,
                status = context.getString(if (available) R.string.gyro_status_off else R.string.gyro_status_unavailable),
                statusKind = if (available) GyroStatusKind.OFF else GyroStatusKind.UNAVAILABLE)
            if (SwiftCore.isAvailable) {
                runCatching {
                    val catalog = decodeGyroMountPresets(native.mountPresets(true))
                    val options = if (naturalPortrait) catalog else decodeGyroMountPresets(native.mountPresets(false))
                    catalog to options
                }.onSuccess { (catalog, options) ->
                    val saved = preferences.getString("mountPreset", "unconfigured")
                    val savedCalibration = preferences.getString("mountCalibration", null)
                        ?.takeIf(calibrationNative::validate)
                    selectedMount = GyroMountSelection(options.firstOrNull { it.id == saved }?.id ?: "unconfigured",
                        savedCalibration.orEmpty())
                    mutableState.value = mutableState.value.copy(mountOptions = options,
                        mountCatalog = catalog.associateBy { it.id },
                        mountPreset = selectedMount.preset, mountCalibration = savedCalibration)
                }.onFailure {
                    mutableState.value = mutableState.value.copy(available = false)
                    fail(it.message ?: context.getString(R.string.gyro_error_mount_presets))
                }
                runCatching { store.recover() }.onFailure {
                    mutableState.value = mutableState.value.copy(
                        storageWarning = it.message ?: context.getString(R.string.gyro_error_recovery))
                }
                runCatching { refreshLogs() }.onFailure {
                    fail(it.message ?: context.getString(R.string.gyro_error_logs_read))
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        scope.launch {
            mutableState.value = mutableState.value.copy(enabled = enabled, failed = false)
            safely { reconcile(if (enabled) "interrupted" else "disabled") }
        }
    }

    fun setMountPreset(id: String) {
        require(mutableState.value.mountOptions.any { it.id == id }) { "Unknown gyro mount preset." }
        preferences.edit().putString("mountPreset", id).remove("mountCalibration").apply()
        selectedMount = GyroMountSelection(id)
        scope.launch {
            cancelCalibration()
            mutableState.value = mutableState.value.copy(mountPreset = id, mountCalibration = null)
        }
    }

    fun resetCalibration() = setMountPreset("unconfigured")

    fun setCalibrationSourceAvailable(available: Boolean) { scope.launch {
        if (calibrationSourceAvailable != available) cancelCalibration()
        calibrationSourceAvailable = available
    } }

    fun setForeground(active: Boolean) { scope.launch {
        foreground = active
        if (!active) cancelCalibration()
        safely { reconcile("backgrounded") }
    } }

    fun bind(next: CameraSession?) { scope.launch {
        if (session === next) return@launch
        cancelCalibration()
        safely { stop("disconnected") }
        observations?.cancel(); events?.cancel(); connection?.cancel(); cameraMode?.cancel()
        session = next
        lastReported = null
        val binding = synchronized(pending) { pendingObservations.clear(); ++bindingGeneration }
        if (next == null) { safely { reconcile("disconnected") }; return@launch }
        connection = scope.launch { next.state.collect { value ->
            if (session !== next) return@collect
            cameraLabel = (value as? CameraSessionState.Connected)?.identity?.name ?: "Camera"
            if (value !is CameraSessionState.Connected) cancelCalibration()
            safely { reconcile("disconnected") }
        } }
        cameraMode = scope.launch { next.cameraProperties.collect {
            if (calibration.state.active && !calibrationEligible()) cancelCalibration()
            safely { reconcile("interrupted") }
        } }
        // These collectors never do file IO. Sensor readings and timestamped REC observations
        // enter the same bounded queue before the serial writer can block on storage.
        observations = scope.launch(Dispatchers.Default) { next.reportedRecording.collect { recording ->
            val mount = selectedMount
            enqueue(RecordingObservation(recording, now(), System.currentTimeMillis() / 1_000.0,
                mountPreset = mount.preset, calibrationJSON = mount.calibrationJSON), binding)
            if (recording != false) scope.launch { if (binding == bindingGeneration) cancelCalibration() }
        } }
        events = scope.launch(Dispatchers.Default) { next.events.collect { event ->
            if (event is CameraSessionEvent.RecordingInterrupted) {
                val mount = selectedMount
                enqueue(RecordingObservation(false, now(), System.currentTimeMillis() / 1_000.0, true,
                    mount.preset, mount.calibrationJSON), binding)
            }
        } }
        safely { reconcile("disconnected") }
    } }

    fun delete(entry: GyroLogEntry) { scope.launch {
        runCatching { store.delete(entry); refreshLogs() }
            .onFailure { fail(it.message ?: context.getString(R.string.gyro_error_log_delete)) }
    } }

    fun close() { scope.launch {
        cancelCalibration()
        try { safely { stop("backgrounded"); store.close() } } finally { scope.cancel() }
    } }

    fun beginCalibration() { scope.launch {
        cancelCalibration()
        if (!calibrationEligible()) {
            mutableState.value = mutableState.value.copy(calibration = GyroCalibrationState(
                error = context.getString(R.string.gyro_error_calibration_live)))
            return@launch
        }
        val gravity = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val tiltSensor = gravity ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (tiltSensor == null) {
            mutableState.value = mutableState.value.copy(calibration = GyroCalibrationState(
                error = context.getString(R.string.gyro_error_gravity_unavailable)))
            return@launch
        }
        try {
            calibration.start()
            val generation = ++calibrationGeneration
            val source = (session as SwiftCoreCameraSession).liveFrames as SwiftCoreLiveFrameSource
            calibrationFrames = scope.launch {
                try {
                    source.currentStreamFrames.metadataOnly().collect { frame ->
                        if (generation != calibrationGeneration) return@collect
                        val level = frame.level?.takeIf { !it.isDebugFixture && it.hasPitch }
                        calibrationCamera = level?.let { it to cameraCalibrationTimestamp(frame.timestampNanos,
                            SystemClock.elapsedRealtimeNanos(), System.nanoTime()) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { failCalibration(error) }
            }
            val thread = HandlerThread("OpenZCineCalibration").also { it.start() }
            calibrationThread = thread
            val listener = calibrationSensorListener(generation, gravity == null)
            calibrationListener = listener
            check(sensorManager?.registerListener(listener, tiltSensor, 50_000, Handler(thread.looper)) == true)
                { "Could not start phone gravity." }
            publishCalibration()
        } catch (error: Exception) { failCalibration(error) }
    } }

    fun captureCalibration() { scope.launch {
        if (!calibrationEligible()) { cancelCalibration(); return@launch }
        try { calibration.capture(now()); publishCalibration() }
        catch (error: Exception) { failCalibration(error) }
    } }

    fun stopCalibration() { scope.launch { cancelCalibration() } }

    private fun calibrationEligible(): Boolean = foreground && calibrationSourceAvailable &&
        session is SwiftCoreCameraSession && session?.state?.value is CameraSessionState.Connected &&
        session?.reportedRecording?.value == false &&
        gyroSelectorArms(session?.cameraProperties?.value?.captureSelector)

    private fun calibrationSensorListener(generation: Long, accelerometer: Boolean): SensorEventListener =
        object : SensorEventListener {
            private var filtered: MonitorTiltVector? = null
            private val deliveryPending = AtomicBoolean()
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                // Android reports support acceleration (+Z face up); the core accepts physical down.
                val down = if (accelerometer) MonitorDeviceTiltMath.lowPass(filtered,
                    -event.values[0], -event.values[1], -event.values[2])?.also { filtered = it } ?: return
                    else MonitorTiltVector(-event.values[0], -event.values[1], -event.values[2])
                if (!down.x.isFinite() || !down.y.isFinite() || !down.z.isFinite()) return
                val timestamp = event.timestamp / 1_000_000_000.0
                // File IO shares the serial scope. Never accumulate sensor jobs behind a slow write.
                if (!deliveryPending.compareAndSet(false, true)) return
                scope.launch {
                    try {
                        if (generation != calibrationGeneration || !calibration.state.active) return@launch
                        val camera = calibrationCamera ?: return@launch
                        val observation = JSONObject().put("phoneDown", JSONObject()
                            .put("x", down.x.toDouble()).put("y", down.y.toDouble()).put("z", down.z.toDouble()))
                            .put("phoneTimestamp", timestamp).put("rollDegrees", camera.first.rollDegrees)
                            .put("pitchDegrees", camera.first.pitchDegrees).put("cameraTimestamp", camera.second)
                        calibration.add(observation.toString()); publishCalibration()
                    }
                    catch (error: Exception) { failCalibration(error) }
                    finally { deliveryPending.set(false) }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

    private fun publishCalibration() {
        calibration.state.calibration?.let { value ->
            preferences.edit().putString("mountCalibration", value).apply()
            selectedMount = selectedMount.copy(calibrationJSON = value)
            mutableState.value = mutableState.value.copy(mountCalibration = value)
        }
        mutableState.value = mutableState.value.copy(calibration = calibration.state)
        if (!calibration.state.active) stopCalibrationResources()
    }

    private fun stopCalibrationResources() {
        ++calibrationGeneration
        calibrationFrames?.cancel(); calibrationFrames = null
        calibrationListener?.let { sensorManager?.unregisterListener(it) }; calibrationListener = null
        calibrationThread?.quitSafely(); calibrationThread = null
        calibrationCamera = null
    }

    private fun cancelCalibration() {
        stopCalibrationResources()
        calibration.cancel()
        mutableState.value = mutableState.value.copy(calibration = calibration.state)
    }

    private fun failCalibration(error: Exception) {
        stopCalibrationResources()
        calibration.cancel(error.message ?: context.getString(R.string.gyro_error_calibration))
        mutableState.value = mutableState.value.copy(calibration = calibration.state)
    }

    private fun reconcile(reason: String) {
        val eligible = mutableState.value.enabled && foreground &&
            session?.state?.value is CameraSessionState.Connected &&
            gyroSelectorArms(session?.cameraProperties?.value?.captureSelector)
        if (!eligible) {
            stop(reason)
            val kind = when {
                !mutableState.value.available -> GyroStatusKind.UNAVAILABLE
                !mutableState.value.enabled -> GyroStatusKind.OFF
                else -> GyroStatusKind.WAITING_FOR_CAMERA
            }
            mutableState.value = mutableState.value.copy(
                status = context.getString(
                    when (kind) {
                        GyroStatusKind.UNAVAILABLE -> R.string.gyro_status_unavailable
                        GyroStatusKind.OFF -> R.string.gyro_status_off
                        else -> R.string.gyro_status_waiting
                    },
                ),
                statusKind = kind,
            )
            return
        }
        if (owner != null || mutableState.value.failed) return
        if (sensor == null || !SwiftCore.isAvailable || mutableState.value.mountOptions.isEmpty()) {
            fail(context.getString(R.string.gyro_status_unavailable)); return
        }
        try {
            owner = native.create()
            stalled = false
            val generation = synchronized(pending) {
                armedTimestamp = now()
                pending.clear(); pendingObservations.clear(); overflowed = false; ++sensorGeneration
            }
            val activeListener = sensorListener(generation)
            listener = activeListener
            val thread = HandlerThread("OpenZCineGyro").also { it.start() }
            sensorThread = thread
            if (sensorManager?.registerListener(activeListener, sensor, 10_000, Handler(thread.looper)) != true) {
                throw IllegalStateException("Could not start the gyroscope.")
            }
            mutableState.value = mutableState.value.copy(
                status = context.getString(R.string.gyro_status_starting),
                statusKind = GyroStatusKind.STARTING, logging = false,
            )
            apply(native.poll(owner ?: return, now()))
            session?.reportedRecording?.value?.let { observe(it) }
            tick = scope.launch { while (owner != null) { delay(100); drainAndPoll() } }
        } catch (error: Exception) { failCapture(error) }
    }

    private fun observe(recording: Boolean) {
        val active = owner ?: return
        val mount = selectedMount
        try { lastReported = recording; apply(native.observe(active, recording, now(),
            System.currentTimeMillis() / 1_000.0, cameraLabel, mount.preset, mount.calibrationJSON)) }
        catch (error: Exception) { failCapture(error) }
    }

    private fun enqueue(observation: RecordingObservation, binding: Long) {
        synchronized(pending) {
            if (binding != bindingGeneration) return
            val armed = armedTimestamp ?: return
            if (observation.timestamp < armed) return
            if (pendingObservations.size >= 64) { overflowed = true; return }
            pendingObservations.addLast(observation)
        }
    }

    private fun sensorListener(generation: Long): SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            synchronized(pending) {
                if (generation != sensorGeneration) return
                if (pending.size >= 512) { overflowed = true; return }
                pending.addLast(AndroidGyroSample(event.timestamp / 1_000_000_000.0,
                    event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble()))
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun drainAndPoll() {
        if (owner == null) return
        try {
            val overflow = synchronized(pending) { overflowed }
            if (overflow) {
                stop("bufferOverflow")
                fail(context.getString(R.string.gyro_error_overflow))
                return
            }
            drain(); owner?.let { apply(native.poll(it, now())) }; store.flush(SystemClock.elapsedRealtime())
            if (stalled) {
                stop("sensorStalled")
                fail(context.getString(R.string.gyro_error_stalled))
            }
        } catch (error: Exception) { failCapture(error) }
    }

    private fun drain() {
        if (owner == null) return
        val (samples, observations) = synchronized(pending) {
            (pending.toList() to pendingObservations.toList()).also {
                pending.clear(); pendingObservations.clear()
            }
        }
        replayGyroIngress(samples, observations, ::ingest) { observation ->
            val active = owner ?: return@replayGyroIngress
            if (observation.interrupted || observation.recording == null && lastReported != null) {
                apply(native.finish(active, "interrupted"))
                native.close(active)
                owner = native.create()
                apply(native.poll(owner ?: return@replayGyroIngress, observation.timestamp))
            }
            lastReported = observation.recording
            observation.recording?.let { recording ->
                apply(native.observe(owner ?: return@replayGyroIngress, recording, observation.timestamp,
                    observation.date, cameraLabel, observation.mountPreset, observation.calibrationJSON))
            }
        }
    }

    private fun ingest(samples: List<AndroidGyroSample>) {
        if (samples.isNotEmpty()) owner?.let { apply(native.ingest(it, encodeGyroBatch(samples))) }
    }

    private fun apply(payload: String?) {
        val reply = JSONObject(payload ?: throw IllegalStateException("Gyro core did not accept the operation."))
        store.apply(reply)
        val updates = reply.getJSONArray("updates")
        var changed = false
        for (index in 0 until updates.length()) {
            val update = updates.getJSONObject(index)
            val kind = update.getString("kind")
            if (kind == "opened" || kind == "finished") changed = true
        }
        val logging = reply.getBoolean("active")
        val warning = reply.getBoolean("gapWarning")
        if (reply.optString("termination") == "sensorStalled") stalled = true
        val statusKind = when {
            warning -> GyroStatusKind.SAMPLE_GAPS
            logging -> if (lastReported == false) GyroStatusKind.SAVING else GyroStatusKind.LOGGING
            reply.getBoolean("hasSamples") -> GyroStatusKind.READY
            else -> GyroStatusKind.STARTING
        }
        mutableState.value = mutableState.value.copy(logging = logging,
            status = context.getString(
                when (statusKind) {
                    GyroStatusKind.SAMPLE_GAPS -> R.string.gyro_status_gaps
                    GyroStatusKind.SAVING -> R.string.gyro_status_saving
                    GyroStatusKind.LOGGING -> R.string.gyro_status_logging
                    GyroStatusKind.READY -> R.string.gyro_status_ready
                    else -> R.string.gyro_status_starting
                },
            ),
            statusKind = statusKind,
            failed = warning)
        if (changed) refreshLogs()
    }

    private fun stopSensors() {
        tick?.cancel(); tick = null
        synchronized(pending) { armedTimestamp = null; ++sensorGeneration }
        listener?.let { sensorManager?.unregisterListener(it) }; listener = null
        sensorThread?.quitSafely(); sensorThread = null
    }

    private fun stop(reason: String) {
        stopSensors()
        if (owner == null) return
        try {
            val overflow = synchronized(pending) { overflowed }
            if (!overflow) drain()
            owner?.let { apply(native.finish(it, if (overflow) "bufferOverflow" else reason)) }
        }
        finally {
            owner?.let(native::close); owner = null; store.close()
            mutableState.value = mutableState.value.copy(logging = false)
        }
    }

    private fun failCapture(error: Exception) {
        stopSensors()
        owner?.let(native::close); owner = null
        // The core may have accepted more rows than the failed write persisted. Recover the
        // valid file prefix instead of publishing the core's in-memory completion counters.
        runCatching { store.abort() }.onFailure {
            mutableState.value = mutableState.value.copy(
                storageWarning = it.message ?: context.getString(R.string.gyro_error_recovery))
        }
        runCatching { refreshLogs() }
        fail(error.message ?: context.getString(R.string.gyro_error_capture))
    }

    private fun fail(message: String) { mutableState.value = mutableState.value.copy(status = message, failed = true, logging = false) }
    private fun safely(operation: () -> Unit) { try { operation() } catch (error: Exception) { failCapture(error) } }
    private fun refreshLogs() { mutableState.value = mutableState.value.copy(logs = store.entries()) }
    private fun now(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0
}
