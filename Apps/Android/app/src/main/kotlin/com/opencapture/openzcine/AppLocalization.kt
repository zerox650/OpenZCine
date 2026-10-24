package com.opencapture.openzcine

import android.content.Context
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
    R.string.rating_refusal_no_response to "Rating not saved — the camera didn't respond.",
    R.string.rating_refusal_access_denied to "Rating not saved — camera refused (Access Denied %1\$s). It may not accept ratings in this mode.",
    R.string.rating_refusal_coded to "Rating not saved — camera refused (%1\$s).",
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
