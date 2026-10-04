package com.vdx.call

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Exact matches first. The UI must choose when more than one number remains. */
class CallContacts(private val context: Context) {
    fun find(name: String): List<CallTarget> {
        val exact = query("${Phone.DISPLAY_NAME} = ? COLLATE NOCASE", name)
        if (exact.isNotEmpty()) return exact
        val escaped = name.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return query("${Phone.DISPLAY_NAME} LIKE ? ESCAPE '\\'", "%$escaped%")
    }

    private fun query(selection: String, argument: String): List<CallTarget> {
        val found = mutableListOf<CallTarget>()
        val cursor = context.contentResolver.query(Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER),
            selection, arrayOf(argument), "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC")
            ?: throw IllegalStateException("Contacts are temporarily unavailable")
        cursor.use {
            var rows = 0
            while (it.moveToNext()) {
                if (++rows > 100) throw IllegalArgumentException("Too many matches. Type a fuller name.")
                val number = CallFlow.normalizeNumber(it.getString(2).orEmpty()) ?: continue
                found += CallTarget(it.getString(0), it.getString(1).orEmpty(), number)
            }
        }
        return found.distinctBy { it.contactId to it.number }
    }
}
