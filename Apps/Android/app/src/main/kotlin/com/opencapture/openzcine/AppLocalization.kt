package com.opencapture.openzcine

import android.content.Context
import com.opencapture.openzcine.multicam.MulticamCopy
import java.util.Locale

/**
 * The app module's own English copy, keyed by resource id. It backs [lstr] off-device (JVM unit
 * tests, background workers built without a [Context]) so those code paths keep their exact
 * English behaviour and their assertions; on device the string resources answer instead.
 *
 * Every entry must stay byte-identical to the literal it replaces (curly apostrophes, the
 * U+2011 in Wi-Fi, and so on) — the tests assert on these.
 */
internal val APP_ENGLISH_STRINGS: Map<Int, String> = mapOf(
    R.string.media_browse_incomplete to "Couldn't finish listing camera media.",
    R.string.media_browse_stopped_after to "Listing stopped after %1\$d %2\$s. Retry to load the complete library.",
    R.string.media_count_noun_one to "item",
    R.string.media_count_noun_other to "items",
    R.string.media_share_unknown_date to "unknown date",
    R.string.media_share_unknown_size to "unknown size",
    R.string.media_share_more_selected to "+%1\$d more selected %2\$s",
    R.string.media_delete_photo_title to "Delete this photo from the camera card?",
    R.string.media_delete_clip_named to "Delete %1\$s from the camera card?",
    R.string.media_delete_items_count to "Delete %1\$d items from the camera card?",
    R.string.media_delete_pair_note to " Both the RAW and JPEG files of a pair are removed.",
    R.string.media_delete_master_note to " The camera master file is removed with its proxy.",
    R.string.media_delete_backup_note to " The backup copies on the other card are removed too.",
    R.string.media_transfer_not_connected to "Not connected to a camera.",
    R.string.media_cached_open_failed to "Cached %1\$s could not be opened.",
    R.string.media_cached_unavailable to "Cached %1\$s is no longer available.",
    R.string.media_camera_core_missing to "Camera core is not bundled in this build.",
    R.string.media_camera_open_failed to "Camera %1\$s could not be opened.",
    R.string.relay_join_refused to "The broadcast refused this device.",
    R.string.relay_join_disconnected to "The broadcast disconnected.",
    R.string.camera_color_title to "Color",
    R.string.camera_shutter_lock to "Shutter Lock",
    R.string.still_release_refused to "The camera refused the still release.",
    R.string.still_release_failed to "The camera did not accept the still release.",
    R.string.rating_refusal_no_response to "Rating not saved — the camera didn't respond.",
    R.string.rating_refusal_access_denied to "Rating not saved — camera refused (Access Denied %1\$s). It may not accept ratings in this mode.",
    R.string.rating_refusal_coded to "Rating not saved — camera refused (%1\$s).",
    R.string.multicam_session_full to MulticamCopy.SESSION_FULL,
    R.string.multicam_already_member to MulticamCopy.ALREADY_MEMBER,
    R.string.multicam_camera_wifi to MulticamCopy.CAMERA_WIFI,
    R.string.multicam_not_reachable to MulticamCopy.NOT_REACHABLE,
    R.string.multicam_empty_picker to MulticamCopy.EMPTY_PICKER,
    R.string.multicam_couldnt_connect to MulticamCopy.COULDNT_CONNECT,
    R.string.multicam_lost_connection to MulticamCopy.LOST_CONNECTION,
    R.string.multicam_not_applied to "%1\$s not applied",
    R.string.multicam_movie_mode_only to "Movie mode only.",
    R.string.multicam_support_unknown to "Multiview support is not known for this setup.",
    R.string.multicam_tile_connected to "Connected",
    R.string.multicam_tile_connecting to "Connecting…",
    R.string.multicam_tile_remove to "Remove",
    R.string.multicam_tile_open_live_view to "Open Live View",
    R.string.multicam_tile_remove_camera to "Remove camera",
    R.string.multicam_settings_title to "Camera",
    R.string.multicam_settings_close to "Close camera settings",
    R.string.multicam_settings_picture to "%1\$s picture",
    R.string.multicam_tab_camera to "Camera %1\$s",
    R.string.multicam_lut_title to "LUT",
    R.string.multicam_lut_close to "Close LUT",
    R.string.multicam_lut_on to "On",
    R.string.multicam_lut_off to "Off",
    R.string.multicam_lut_selected to "Selected",
    R.string.multicam_wb_auto to "Auto",
)

/**
 * Resolves one of this module's strings: [context] localizes (the device's locale), its absence
 * keeps the bundled English copy. The optional Context twin of `stringResource`.
 */
internal fun lstr(context: Context?, resId: Int, vararg args: Any?): String {
    if (context != null) return context.getString(resId, *args)
    val template = APP_ENGLISH_STRINGS[resId] ?: return ""
    return if (args.isEmpty()) template else String.format(Locale.US, template, *args)
}
