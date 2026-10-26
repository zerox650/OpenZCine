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
    R.string.wear_record_reject_monitor to "Open the live monitor on the phone.",
    R.string.wear_record_reject_camera to "Connect a camera on the phone.",
    R.string.wear_record_reject_pending to "A recording command is already pending.",
    R.string.wear_record_reject_confirmation to "Finish the pending record confirmation on the phone.",
    R.string.wear_record_reject_control to "Wait for the active camera control.",
    R.string.gallery_saved_one to "Saved %1\$d clip to Gallery",
    R.string.gallery_saved_other to "Saved %1\$d clips to Gallery",
    R.string.gallery_saved_none to "No videos were saved to Gallery",
    R.string.gallery_failed_save_one to "%1\$d clip couldn't be saved",
    R.string.gallery_failed_save_other to "%1\$d clips couldn't be saved",
    R.string.gallery_failed_more_one to "%1\$d more clip failed",
    R.string.gallery_failed_more_other to "%1\$d more clips failed",
    R.string.gallery_skipped_nonvideo_one to "%1\$d non-video item skipped",
    R.string.gallery_skipped_nonvideo_other to "%1\$d non-video items skipped",
    R.string.gallery_incomplete_one to "%1\$d incomplete item wasn't ready",
    R.string.gallery_incomplete_other to "%1\$d incomplete items weren't ready",
    R.string.gallery_prepare_failed_one to "%1\$d item couldn't be prepared",
    R.string.gallery_prepare_failed_other to "%1\$d items couldn't be prepared",
    R.string.gallery_export_removed_one to "%1\$d temporary export wasn't removed",
    R.string.gallery_export_removed_other to "%1\$d temporary exports weren't removed",
    R.string.gallery_fail_cleanup to
        "Android couldn't clean up an incomplete Gallery item for %1\$s. Restart the app before retrying.",
    R.string.gallery_fail_permission to
        "Gallery access was denied for %1\$s. Check Android storage access and try again.",
    R.string.gallery_fail_source to
        "%1\$s is no longer a complete readable video. Cache it again and retry.",
    R.string.gallery_fail_storage to
        "Android couldn't create a Gallery item for %1\$s. Check available device storage.",
    R.string.gallery_fail_write to
        "Android couldn't finish writing %1\$s to Gallery. Check available device storage.",
    R.string.gallery_fail_publish to
        "Android couldn't publish %1\$s to Gallery. The incomplete item was removed.",
    R.string.gallery_fail_generic to "Gallery delivery failed for %1\$s.",
    R.string.gallery_message_separator to "; ",
    R.string.gallery_message_period to ".",
    R.string.media_playback_notice_caching to "Still caching from the camera. Share will finish the copy first.",
    R.string.media_playback_notice_buffering to "Buffering camera proxy. Delivery unlocks after the private cache completes.",
    R.string.media_playback_failed_transfer to
        "Camera media transfer failed. The partial cache remains private and cannot be shared.",
    R.string.media_playback_failed_codec to
        "This camera proxy uses a codec this Android device cannot decode. It remains private and can be shared after its cache completes.",
    R.string.media_playback_failed_generic to
        "Couldn't play this camera proxy. It remains private and is shareable only after a complete cache artifact is verified.",
    R.string.media_playback_open_failed to "Could not open that clip.",
    R.string.media_browse_querying_card to "Querying card storage\u2026",
    R.string.media_browse_listing_camera to "Listing clips on camera\u2026",
    R.string.media_browse_checking_cache to "Checking persisted complete cache entries\u2026",
    R.string.media_browse_opening_local to "Opening local library\u2026",
    R.string.z_legal_privacy_body to """
        OpenZCine talks to your Nikon Z camera over Wi-Fi or USB-C. The app has no account. Automatic error reports are optional and off by default.

        OpenCapture is the data controller. Privacy or deletion requests: support@opencapture.org.

        %1${'$'}s

        Reporting a problem yourself
        Report a Problem sends your description and any optional reply email to OpenCapture through Sentry when you tap Send report. Technical details are included only if you choose them and can be reviewed first. You can choose up to three photos or screenshots and review or remove them before sending. Selected images are resized and re-encoded without original filenames or location metadata. Only share images you have permission to send; no images are attached automatically. This one-off submission does not enable automatic reports. Unsent reports stay privately on this phone, expire after 7 days (removed when the app next runs), wait until you leave camera Wi-Fi, and can be removed before delivery. Do not include passwords or other sensitive information in your message. You can decline the first-launch prompt and enable automatic reports later in System.

        What stays on this phone
        • Saved cameras, pairing profiles and the reconnect identity. Camera Wi-Fi passwords are sealed with an Android Keystore key on this phone only.
        • Operator preferences such as Keep Screen Awake and the selected look.
        • Imported .cube files you choose to open and RED looks you download.
        • Saved feed incident and session reports, until you delete them in System.

        What never leaves the phone
        • Live view, camera settings and pairing traffic stay on the local link. We do not operate a cloud.

        Third parties
        • Google may process permission prompts and OS diagnostics under its own policies. Play betas may send crash reports to the developer under Google terms.
        • Frame.io is optional. If this build is configured and you sign in, clips you pick upload from this phone to Adobe. The token stays in on-device storage.
        • RED looks are optional. If you choose Download from RED, the app shows RED's IPP2 output presets page inside the app and downloads the file you pick from RED's servers. That visit and download are governed by RED's own privacy policy.
        • Saving a clip or using the share sheet is something you start. Android or the app you pick then handles that file.
        • The source is at github.com/erik-sutton95/OpenZCine.

        Android may ask for location so the app can read the camera Wi-Fi name. OpenZCine does not use that permission for maps, ads, or a location history.

        The full privacy policy is https://openzcine.app/privacy/
        """.trimIndent(),
    R.string.z_legal_terms_body to """
        OpenZCine is free software under the Apache License 2.0. You may use, modify, and share it under that license. The Google Play build is also subject to Google Play's Terms of Service.

        This is an unofficial camera monitor and remote control. It is not affiliated with, endorsed by, or supported by Nikon. "Nikon" and "Nikon Z" are trademarks of Nikon Corporation, used only to identify the cameras the app can talk to. RED, Adobe and Frame.io belong to their owners and are named only to identify optional features.

        OpenZCine remotely controls physical camera equipment. Wireless links drop, settings writes can be rejected, and the display can lag the camera's real state. Always verify the recording state, exposure and media on the camera itself. Use on productions is at your own risk.

        The software is provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See LICENSE in the repository.

        By using the app you agree to follow the Code of Conduct when participating in the project.

        The full terms are https://openzcine.app/terms/
        """.trimIndent(),
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
    R.string.action_reconnect to "Reconnect",
    R.string.camera_label_mode to "MODE",
    R.string.camera_label_focus to "FOCUS",
    R.string.camera_label_iso to "ISO",
    R.string.camera_label_shutter to "SHUTTER",
    R.string.camera_label_iris to "IRIS",
    R.string.camera_label_drive to "DRIVE",
    R.string.camera_label_wb to "WB",
    R.string.camera_label_meter to "METER",
    R.string.camera_label_profile to "PROFILE",
    R.string.camera_subtitle_focus to "AF mode · area · subject",
    // The capture strip's metering and drive values, verbatim as the camera reports them.
    R.string.camera_meter_matrix to "Matrix",
    R.string.camera_meter_center to "Center",
    R.string.camera_meter_spot to "Spot",
    R.string.camera_meter_highlight to "Highlight",
    R.string.camera_drive_single to "Single",
    R.string.camera_drive_continuous_low to "CL",
    R.string.camera_drive_continuous_high to "CH",
    R.string.camera_drive_continuous_high_plus to "CH+",
    R.string.camera_drive_self_timer to "Timer",
    // The white balance panel's own copy, and the words the drums draw for the labels the body
    // writes. A drum still writes the body's own label back, so these are display words only.
    R.string.camera_title_wb to "WHITE BALANCE",
    R.string.camera_title_wb_subtitle to "Kelvin · auto · tint",
    R.string.camera_mode_kelvin to "Kelvin",
    R.string.camera_mode_preset to "Preset",
    R.string.camera_mode_tint to "Tint",
    R.string.camera_value_auto to "Auto",
    R.string.camera_value_off to "Off",
    R.string.camera_value_on to "On",
    R.string.camera_value_seconds to "%1\$s s",
    R.string.camera_value_focus_pinpoint to "Pinpoint",
    R.string.camera_value_focus_single to "Single",
    R.string.camera_value_focus_dyn_s to "Dyn-S",
    R.string.camera_value_focus_dyn_m to "Dyn-M",
    R.string.camera_value_focus_dyn_l to "Dyn-L",
    R.string.camera_value_focus_wide_s to "Wide-S",
    R.string.camera_value_focus_wide_l to "Wide-L",
    R.string.camera_value_focus_3d to "3D tracking",
    R.string.camera_value_focus_auto_area to "Auto",
    R.string.camera_value_subject_people to "People",
    R.string.camera_value_subject_animal to "Animal",
    R.string.camera_value_subject_vehicle to "Vehicle",
    R.string.camera_value_subject_bird to "Bird",
    R.string.camera_value_subject_airplane to "Airplane",
    R.string.camera_value_wb_natural_auto to "Natural auto",
    R.string.camera_value_wb_sunny to "Sunny",
    R.string.camera_value_wb_cloudy to "Cloudy",
    R.string.camera_value_wb_shade to "Shade",
    R.string.camera_value_wb_incandescent to "Incandescent",
    R.string.camera_value_wb_fluorescent to "Fluorescent",
    R.string.camera_value_wb_flash to "Flash",
    R.string.camera_value_wb_preset to "Preset",
    R.string.camera_value_pc_standard to "Standard",
    R.string.camera_value_pc_neutral to "Neutral",
    R.string.camera_value_pc_vivid to "Vivid",
    R.string.camera_value_pc_monochrome to "Monochrome",
    R.string.camera_value_pc_portrait to "Portrait",
    R.string.camera_value_pc_landscape to "Landscape",
    R.string.camera_value_pc_flat to "Flat",
    R.string.camera_value_pc_flat_mono to "Flat Mono",
    R.string.camera_value_pc_deep_tone_mono to "Deep Tone Mono",
    R.string.camera_value_pc_rich_tone_portrait to "Rich Tone Portrait",
    R.string.camera_value_pc_dream to "Dream",
    R.string.camera_value_pc_morning to "Morning",
    R.string.camera_value_pc_pop to "Pop",
    R.string.camera_value_pc_sunday to "Sunday",
    R.string.camera_value_pc_somber to "Somber",
    R.string.camera_value_pc_drama to "Drama",
    R.string.camera_value_pc_silence to "Silence",
    R.string.camera_value_pc_bleach to "Bleach",
    R.string.camera_value_pc_melancholic to "Melancholic",
    R.string.camera_value_pc_pure to "Pure",
    R.string.camera_value_pc_denim to "Denim",
    R.string.camera_value_pc_toy to "Toy",
    R.string.camera_value_pc_sepia to "Sepia",
    R.string.camera_value_pc_blue to "Blue",
    R.string.camera_value_pc_red to "Red",
    R.string.camera_value_pc_pink to "Pink",
    R.string.camera_value_pc_charcoal to "Charcoal",
    R.string.camera_value_pc_graphite to "Graphite",
    R.string.camera_value_pc_binary to "Binary",
    R.string.camera_value_pc_carbon to "Carbon",
    R.string.camera_value_pc_custom to "Custom %1\$d",
    R.string.camera_value_pc_cloud to "Cloud %1\$d",
    R.string.camera_value_size_large to "Size L",
    R.string.camera_value_size_medium to "Size M",
    R.string.camera_value_size_small to "Size S",
    R.string.record_action_start to "Start recording",
    R.string.record_action_stop to "Stop recording",
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
    R.string.media_camera_size_unknown to "Camera did not provide the %1\$s size.",
    R.string.media_transfer_cache_retired to "Delivery cache source retired.",
    R.string.media_cache_write_failed to "Media cache write failed.",
    R.string.still_preview_loading_progressive to "Loading full %1\$s preview…",
    R.string.still_preview_downloading_complete to "Downloading %1\$s image…",
    R.string.still_preview_downloading_file to "Downloading %1\$s file…",
    R.string.still_preview_decoder_unavailable_file to "%1\$s files are not decoded for a full preview on Android.",
    R.string.still_preview_decoder_unavailable_image to "This %1\$s image could not be decoded for a full preview on this Android device.",
    R.string.still_preview_thumbnail_shown to "Showing the camera thumbnail.",
    R.string.still_preview_no_thumbnail to "The camera did not provide a thumbnail.",
    R.string.still_preview_transfer_failed to "Camera image transfer failed.",
    R.string.still_preview_transfer_cancelled to "Camera image transfer was cancelled.",
    R.string.gyro_share_not_finalized to "This gyro log is not finalized.",
    R.string.gyro_share_directory_missing to "Missing share directory.",
    R.string.gyro_share_publish_failed to "Could not publish the gyro share copy.",
    R.string.gyro_share_storage_failed to "Could not create gyro share storage.",
    R.string.media_date_window_today to "TODAY",
    R.string.media_date_window_7_days to "7 DAYS",
    R.string.media_date_window_30_days to "30 DAYS",
    R.string.frameio_export_unsupported_bake to "%1\$s isn\'t a video proxy that can receive a baked LUT.",
    R.string.frameio_export_lut_unavailable to "The selected LUT is no longer approved for export. Re-select or re-import it and try again.",
    R.string.frameio_export_incomplete to "The LUT-baked export did not produce a complete video.",
    R.string.frameio_export_invalid_mov to "The LUT-baked MOV export is not a complete regular file.",
    R.string.frameio_export_incomplete_header to "The LUT-baked MOV export has an incomplete media header.",
    R.string.frameio_export_unsupported_header to "The LUT-baked MOV export has an unsupported media header.",
    R.string.frameio_export_codec_failed to "OpenZCine couldn\'t bake the selected LUT for this clip. The source may use a codec or HDR mode this device cannot export safely.",
    R.string.frameio_export_codec_stalled to "OpenZCine stopped the LUT export because this device\'s media encoder stopped making progress.",
    R.string.frameio_export_cleanup_incomplete to "OpenZCine couldn\'t remove the incomplete Frame.io export.",
    R.string.frameio_export_cleanup_temporary to "OpenZCine couldn\'t remove the temporary Frame.io export.",
    R.string.gyro_storage_create_failed to "Could not create gyro storage.",
    R.string.gyro_log_not_open to "Gyro log is not open.",
    R.string.gyro_descriptor_invalid to "Invalid gyro descriptor.",
    R.string.gyro_filename_invalid to "Invalid gyro filename.",
    R.string.gyro_delete_active to "A recording gyro log cannot be deleted.",
    R.string.gyro_delete_failed to "Could not delete the gyro log.",
    R.string.gyro_recovery_partial to "Some interrupted gyro logs could not be recovered. Saved logs remain available.",
    R.string.gyro_recovery_invalid_header to "The partial gyro log has an invalid header.",
    R.string.gyro_recovery_failed to "The partial gyro log could not be recovered.",
    R.string.gyro_finish_failed to "Could not finish the gyro log.",
    R.string.gyro_metadata_save_failed to "Could not save gyro metadata.",
    R.string.gyro_gravity_start_failed to "Could not start phone gravity.",
    R.string.gyro_sensor_start_failed to "Could not start the gyroscope.",
    R.string.gyro_core_rejected to "Gyro core did not accept the operation.",
    R.string.recording_error_not_connected to "Connect to a camera before changing recording.",
    R.string.recording_error_core_unavailable to "The shared camera core is unavailable in this app build.",
    R.string.recording_error_media_busy to "Close camera media before changing recording.",
    R.string.recording_error_command_rejected to "The camera rejected the recording command.",
    R.string.recording_error_unsupported to "This camera connection cannot control recording.",
    R.string.recording_error_transport_failed to "The camera connection failed while changing recording.",
    R.string.control_error_not_connected to "Connect to a camera before changing a camera control.",
    R.string.control_error_core_unavailable to "The shared camera core is unavailable in this app build.",
    R.string.control_error_media_busy to "Close camera media before changing a camera control.",
    R.string.control_error_unsupported to "This camera control or value is not supported.",
    R.string.control_error_command_rejected to "The camera rejected the control change.",
    R.string.control_error_readback_mismatch to "The camera did not keep that control value. Check its recording mode and try again.",
    R.string.control_error_transport_failed to "The camera connection failed while changing a control.",
    R.string.focus_error_not_connected to "Connect to a camera before changing the focus point.",
    R.string.focus_error_core_unavailable to "The shared camera core is unavailable in this app build.",
    R.string.focus_error_media_busy to "Close camera media before changing the focus point.",
    R.string.focus_error_unavailable to "Camera focus metadata is not available yet.",
    R.string.focus_error_command_rejected to "The camera rejected the focus-point change.",
    R.string.focus_error_transport_failed to "The camera connection failed while changing the focus point.",
    R.string.picker_mode_title to "MODE",
    R.string.picker_exposure_program to "Exposure program",
    R.string.picker_mode_label to "Mode",
    R.string.picker_u_mode to "U Mode",
    R.string.picker_iso_title to "ISO",
    R.string.picker_iso_auto to "Auto",
    R.string.picker_iso_manual to "Manual",
    R.string.picker_drive_title to "DRIVE",
    R.string.picker_release_mode to "Release mode",
    R.string.picker_drive_label to "Drive",
    R.string.picker_builtin_timer to "Built-in Timer",
    R.string.picker_app_timer to "App-timer",
    R.string.picker_focus_title to "FOCUS",
    R.string.picker_af_mode to "AF mode",
    R.string.picker_area_label to "Area",
    R.string.picker_subject_label to "Subject",
    R.string.picker_meter_title to "METER",
    R.string.picker_metering_pattern to "Metering pattern",
    R.string.picker_profile_title to "PROFILE",
    R.string.picker_picture_control to "Picture control",
    R.string.picker_size_title to "SIZE",
    R.string.picker_area_size to "Area · size",
    R.string.picker_size_label to "Size",
    R.string.picker_quality_title to "QUALITY",
    R.string.picker_raw_jpeg_heif to "RAW · JPEG/HEIF",
    R.string.picker_optimal_quality to "★ Optimal quality",
    R.string.picker_sensitivity to "Sensitivity",
    R.string.picker_speed to "Speed",
    R.string.picker_aperture to "Aperture",
    R.string.instant_playback to "Instant playback",
    R.string.help_controls_keep_awake to
        "Keeps the screen awake while this app is active. Turn off to use the device's normal auto-lock setting. The system may still dim the screen when the device is hot.",
    R.string.help_system_automatic_reports to
        "Optional: send crash, hang, live-feed reports and session health counts to OpenCapture through Sentry. Off by default. Turn off anytime without losing app features. Uploads wait until you leave camera Wi-Fi. Automatic reports exclude all images. No footage or GPS location. Sentry receives the connection IP. See Reporting Privacy below.",
    R.string.legal_reliability_reports_privacy to
        "Optional reliability reports\nWith your consent, configured builds send crash, hang and feed diagnostics, app/OS/device versions and session health counts through Sentry, with a random install identifier that is created when you opt in and deleted when you opt out. Automatic reports exclude all images. No footage, screenshots, raw camera packets or GPS location are included. Sentry receives the connection IP. Event storage uses Germany. Some provider processing may occur internationally.\nTurn reporting off in Operator Setup > System at any time without losing app features. This clears pending SDK uploads and deletes the install identifier, but does not delete reports already received. Contact us privately to request deletion. The website policy explains retention, transfers and your rights.",
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
