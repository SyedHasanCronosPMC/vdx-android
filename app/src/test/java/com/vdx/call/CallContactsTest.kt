package com.vdx.call

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
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
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class CallContactsTest {
    private val provider = ContactsProvider()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() { ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider) }

    @Test fun multipleContactsAndNumbersRemainChoicesWhileDuplicateRowsCollapse() {
        provider.rows = listOf(arrayOf("1", "Mom", "2025550123"), arrayOf("1", "Mom", "202-555-0123"),
            arrayOf("1", "Mom", "2025550199"), arrayOf("2", "Mom", "2025550111"))
        val found = CallContacts(context).find("Mom")
        assertEquals(3, found.size)
        assertEquals(1, provider.selections.size) // exact results: no fuzzy query
    }
    @Test fun wildcardCharactersAreEscapedAndCursorIsClosed() {
        CallContacts(context).find("A_%")
        assertEquals("%A\\_\\%%", provider.arguments.last())
        assertTrue(provider.cursors.all { it.isClosed })
    }
    @Test fun invalidNumbersNeverBecomeDialTargets() {
        provider.rows = listOf(arrayOf("1", "Mom", "*21*123#"))
        assertTrue(CallContacts(context).find("Mom").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun overlargeResultsFailClosedInsteadOfTruncatingToFirstPerson() {
        provider.rows = (1..101).map { arrayOf("$it", "Mom", "2025550123") }
        CallContacts(context).find("Mom")
    }

    class ContactsProvider : ContentProvider() {
        var rows = emptyList<Array<String>>()
        val selections = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        val cursors = mutableListOf<Cursor>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            selections += selection.orEmpty()
            arguments += selectionArgs?.first().orEmpty()
            return MatrixCursor(arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER)).apply {
                rows.forEach { addRow(it) }; cursors += this
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
