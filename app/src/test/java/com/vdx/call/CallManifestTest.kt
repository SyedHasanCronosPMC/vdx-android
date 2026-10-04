package com.vdx.call

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallManifestTest {
    @Test fun mergedShippingBoundaryHasNoLegacyAutomationOrTelemetryEntryPoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS)
        val forbidden = setOf("CALL_PHONE", "SEND_SMS", "READ_SMS", "WRITE_CONTACTS", "READ_CALL_LOG", "QUERY_ALL_PACKAGES", "RECEIVE_BOOT_COMPLETED")
        assertTrue(info.requestedPermissions.orEmpty().none { it.substringAfterLast('.') in forbidden })
        assertTrue(info.services.orEmpty().none { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" || it.name.startsWith("androidx.work") })
        assertTrue(info.providers.orEmpty().none { it.name.startsWith("io.sentry") || it.name.startsWith("androidx.startup") })
        assertTrue(info.receivers.orEmpty().none { it.name.contains("ExternalAutomation") || it.name.startsWith("androidx.work") })
        assertEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertNotEquals("com.vdx.VdxApplication", info.applicationInfo!!.className)
    }
}
