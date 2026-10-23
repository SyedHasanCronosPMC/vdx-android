package com.vdx.sonic.system

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemControllerDialTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val provider = PhoneProvider()
    private val controller = SystemController(application)

    @Before
    fun setup() {
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun blankInputDoesNotOpenDialer() {
        assertEquals("No number to dial", controller.execute("dial", mapOf("number" to " ")))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun permissionDenialCannotExtractDigitsFromTheContactName() {
        controller.execute("dial", mapOf("contact" to "Office 2025550199"))
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(0, provider.queries)
    }

    @Test
    fun missingContactDoesNotOpenEmptyDialer() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        controller.execute("dial", mapOf("contact" to "Missing Person"))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun ambiguousContactDoesNotPickFirstNumber() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        provider.rows = listOf(arrayOf("1", "Alex", "2025550123"), arrayOf("2", "Alex", "2025550199"))
        controller.execute("dial", mapOf("contact" to "Alex"))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun resolvedContactUsesNumberColumnNotDigitsInName() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        provider.rows = listOf(arrayOf("1", "Office 2025550199", "+1 (202) 555-0123"))
        controller.execute("dial", mapOf("contact" to "Office 2025550199"))
        val opened = shadowOf(application).nextStartedActivity
        assertNotNull(opened)
        assertEquals(Intent.ACTION_DIAL, opened.action)
        assertEquals("tel:+12025550123", opened.data.toString())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun explicitNumberOpensOnlyUserControlledDialer() {
        assertEquals("Opened dialer for +12025550123", controller.execute("dial", mapOf("number" to "+1 (202) 555-0123")))
        val opened = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, opened.action)
        assertEquals("tel:+12025550123", opened.data.toString())
        assertEquals(0, provider.queries)
    }

    @Test
    fun malformedNumericInputNeverOpensPhoneInterface() {
        for (input in listOf("---", "++1234567", "12", "*21*123#", "123;456")) {
            controller.execute("dial", mapOf("number" to input))
            assertNull(input, shadowOf(application).nextStartedActivity)
        }
    }

    @Test
    fun unavailableProviderDoesNotOpenPhoneInterface() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        provider.unavailable = true
        controller.execute("dial", mapOf("contact" to "Alex"))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    class PhoneProvider : ContentProvider() {
        var rows = emptyList<Array<String>>()
        var unavailable = false
        var queries = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries++
            if (unavailable) return null
            val columns = projection ?: arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER)
            return MatrixCursor(columns).apply {
                rows.forEach { row ->
                    val values = mapOf(Phone.CONTACT_ID to row[0], Phone.DISPLAY_NAME to row[1], Phone.NUMBER to row[2])
                    addRow(columns.map { values[it] }.toTypedArray())
                }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
