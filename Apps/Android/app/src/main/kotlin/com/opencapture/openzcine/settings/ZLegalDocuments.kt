package com.opencapture.openzcine.settings

import android.content.Context
import com.opencapture.opencine.cameracontrols.MonitorLegalDocument
import com.opencapture.opencine.cameracontrols.MonitorLegalDocuments
import com.opencapture.opencine.cameracontrols.MonitorPlatformLegalNotices
import com.opencapture.opencine.cameracontrols.MonitorSettingsCopy
import com.opencapture.openzcine.R
import com.opencapture.openzcine.lstr

/**
 * Z's legal set (A2-27): privacy and terms are this app's own text, readable on the camera's
 * network and ending with the full web page's address (iOS twin: `MonitorLegalDocuments.z`).
 * The two bodies live in resources so the device locale picks the language; the reliability
 * paragraph stays the shared core's copy until the core is localized. Licenses and NOTICE are
 * the repository's LICENSE, THIRD-PARTY-NOTICES.md and NOTICE, staged as resources at build
 * time, with the platform notices; a build missing one shows the unavailable page.
 */
internal fun zLegalDocuments(context: Context? = null): MonitorLegalDocuments = MonitorLegalDocuments(
    wordmark = "OPENZCINE",
    privacy = MonitorLegalDocument.Text(
        lstr(context, R.string.z_legal_privacy_body, lstr(context, R.string.legal_reliability_reports_privacy)),
    ),
    terms = MonitorLegalDocument.Text(lstr(context, R.string.z_legal_terms_body)),
    licenses = MonitorLegalDocument.bundledText(listOf(
        MonitorLegalDocument.resource("openzcine/legal/LICENSE"),
        MonitorLegalDocument.resource("openzcine/legal/THIRD-PARTY-NOTICES.md"),
        MonitorPlatformLegalNotices.text,
    )),
    notice = MonitorLegalDocument.bundledText(listOf(MonitorLegalDocument.resource("openzcine/legal/NOTICE"))),
)
