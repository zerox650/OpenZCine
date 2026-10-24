package com.opencapture.openzcine.gyro

import android.content.Context
import com.opencapture.openzcine.R
import com.opencapture.openzcine.lstr
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID

/** An app-private motion artifact; incomplete and active files cannot be shared. */
internal data class GyroLogEntry(val descriptor: JSONObject, val summary: JSONObject?, val file: File,
    val active: Boolean = false) {
    val id: String get() = descriptor.getString("id")
    val cameraLabel: String get() = descriptor.getString("cameraLabel")
    val mountPreset: String get() = descriptor.optString("mountPreset", "unconfigured")
    fun mountDetail(catalog: Map<String, GyroMountOption>, context: Context? = null): String =
        if (descriptor.optJSONObject("mountCalibration") != null) {
            context?.getString(R.string.gyro_mount_calibrated) ?: "Calibrated phone mount"
        } else {
            (catalog[mountPreset] ?: catalog["unconfigured"])?.detail
                ?: context?.getString(R.string.gyro_mount_unconfigured) ?: "Unconfigured · set alignment in Gyroflow"
        }
    val startDateMillis: Long get() = descriptor.getDouble("startDate").toLong()
    val canShare: Boolean get() = summary != null && !active
    val health: String get() = healthLabel(null)
    fun healthLabel(context: Context?): String = when {
        active -> context?.getString(R.string.gyro_health_recording) ?: "Recording / post-roll"
        summary == null -> context?.getString(R.string.gyro_health_incomplete) ?: "Incomplete file"
        summary.getString("termination") == "recovered" -> context?.getString(R.string.gyro_health_recovered) ?: "Recovered · partial"
        summary.getString("termination") != "normal" -> context?.getString(R.string.gyro_health_interrupted) ?: "Interrupted · partial"
        summary.getBoolean("isPartial") -> context?.getString(R.string.gyro_health_partial) ?: "Partial"
        else -> context?.getString(R.string.gyro_health_complete) ?: "Complete"
    }
}

/** Native files only: take boundaries, GCSV encoding and recovery validation stay in Swift. */
internal class GyroLogStore(private val root: File, private val native: GyroNative,
    private val context: Context? = null,
    private val writeBytes: (RandomAccessFile, ByteArray) -> Unit = { file, bytes -> file.write(bytes) }) {
    private val writers = mutableMapOf<String, RandomAccessFile>()
    private var lastFlush = 0L

    init { if (!root.exists() && !root.mkdirs()) throw IOException(lstr(context, R.string.gyro_storage_create_failed)) }

    fun apply(reply: JSONObject) {
        val updates = reply.getJSONArray("updates")
        for (index in 0 until updates.length()) {
            val update = updates.getJSONObject(index)
            val id = UUID.fromString(update.getString("id")).toString().uppercase()
            when (update.getString("kind")) {
                "opened" -> {
                    val descriptor = update.getJSONObject("descriptor")
                    atomicWrite(File(root, "$id.json"), descriptor.toString())
                    writers[id] = RandomAccessFile(File(root, "$id.partial"), "rw")
                }
                "appended" -> {
                    val writer = writers[id] ?: throw IOException(lstr(context, R.string.gyro_log_not_open))
                    writeBytes(writer, Base64.getDecoder().decode(update.getString("data")))
                }
                "finished" -> finish(id, update.getJSONObject("summary"))
            }
        }
    }

    fun flush(nowMillis: Long) {
        if (nowMillis - lastFlush < 1_000) return
        writers.values.forEach { it.fd.sync() }
        lastFlush = nowMillis
    }

    fun close() {
        writers.values.forEach { writer -> runCatching { writer.fd.sync() }; runCatching { writer.close() } }
        writers.clear()
    }

    fun entries(): List<GyroLogEntry> = root.listFiles().orEmpty()
        .filter { it.name.endsWith(".json") && !it.name.endsWith(".summary.json") }
        .mapNotNull { file -> runCatching { readEntry(file) }.getOrNull() }
        .sortedByDescending { it.startDateMillis }

    private fun readEntry(file: File): GyroLogEntry? {
        val descriptor = JSONObject(file.readText())
        val id = UUID.fromString(descriptor.getString("id")).toString().uppercase()
        require(file.name == "$id.json") { lstr(context, R.string.gyro_descriptor_invalid) }
        descriptor.getString("cameraLabel")
        descriptor.getDouble("startDate")
        val filename = descriptor.getString("filename")
        require(File(filename).name == filename) { lstr(context, R.string.gyro_filename_invalid) }
        val partial = File(root, "$id.partial")
        val artifact = if (partial.isFile) partial else File(root, filename)
        if (!artifact.isFile) return null
        val summary = if (partial.isFile) null else runCatching {
            JSONObject(File(root, "$id.summary.json").readText()).also {
                require(it.getJSONObject("descriptor").getString("id") == id)
                it.getDouble("duration"); it.getDouble("measuredRate"); it.getDouble("largestGap")
                it.getBoolean("isPartial"); it.getString("termination")
                it.getDouble("approximateStartOffset")
            }
        }.getOrNull()
        return GyroLogEntry(descriptor, summary, artifact, active = writers.containsKey(id))
    }

    fun delete(entry: GyroLogEntry) {
        check(!writers.containsKey(entry.id)) { lstr(context, R.string.gyro_delete_active) }
        if (!entry.file.delete()) throw IOException(lstr(context, R.string.gyro_delete_failed))
        File(root, "${entry.id}.summary.json").delete()
        File(root, "${entry.id}.json").delete()
    }

    fun recover() {
        var failures = 0
        for (file in root.listFiles().orEmpty().filter { it.name.endsWith(".partial") }) {
            try { recoverFile(file) } catch (_: Exception) { failures += 1 }
        }
        if (failures > 0) throw IOException(lstr(context, R.string.gyro_recovery_partial))
    }

    /** Rebuilds completion metadata from bytes actually written after an append failure. */
    fun abort() { close(); recover() }

    private fun recoverFile(file: File) {
            val id = UUID.fromString(file.name.removeSuffix(".partial")).toString().uppercase()
            val descriptor = File(root, "$id.json").readText()
            val owner = native.recoveryCreate()
            var validLength = 0L
            var summary: String? = null
            try {
                file.inputStream().use { input ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        validLength = native.recoveryAccept(owner, buffer.copyOf(count))
                        if (validLength < 0) throw IOException(lstr(context, R.string.gyro_recovery_invalid_header))
                    }
                }
            } finally { summary = native.recoveryFinish(owner, descriptor) }
            val recovered = summary ?: throw IOException(lstr(context, R.string.gyro_recovery_failed))
            RandomAccessFile(file, "rw").use { it.setLength(validLength); it.fd.sync() }
            finish(id, JSONObject(recovered))
    }

    private fun finish(id: String, summary: JSONObject) {
        writers.remove(id)?.let { writer -> try { writer.fd.sync() } finally { writer.close() } }
        val filename = summary.getJSONObject("descriptor").getString("filename")
        require(File(filename).name == filename) { lstr(context, R.string.gyro_filename_invalid) }
        val partial = File(root, "$id.partial")
        val destination = File(root, filename)
        atomicWrite(File(root, "$id.summary.json"), summary.toString())
        if (!partial.renameTo(destination)) throw IOException(lstr(context, R.string.gyro_finish_failed))
    }

    private fun atomicWrite(destination: File, value: String) {
        val temporary = File(root, "${destination.name}.tmp")
        temporary.outputStream().use { output -> output.write(value.toByteArray()); output.fd.sync() }
        if (!temporary.renameTo(destination)) throw IOException(lstr(context, R.string.gyro_metadata_save_failed))
    }
}
