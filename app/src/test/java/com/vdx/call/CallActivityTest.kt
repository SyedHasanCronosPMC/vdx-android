package com.vdx.call

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.vdx.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CallActivityTest {
    @Test fun cleanInstallTypedFlowNeedsNoAccessibilityMicContactsOrKeyAndDialsOnlyAfterConfirm() {
        val controller = Robolectric.buildActivity(CallActivity::class.java).setup()
        val activity = controller.get()
        val shadow = shadowOf(activity)
        assertNull(shadow.nextStartedActivity)
        activity.findViewById<EditText>(R.id.call_input).setText("2025550123")
        activity.findViewById<Button>(R.id.call_find).performClick()
        assertNull(shadow.nextStartedActivity)
        assertTrue(activity.findViewById<TextView>(R.id.call_status).text.contains("2025550123"))
        activity.findViewById<Button>(R.id.call_confirm).performClick()
        val dial = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel:2025550123", dial.dataString)
        activity.findViewById<Button>(R.id.call_confirm).performClick()
        assertNull(shadow.nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test fun editingCancellingAndBackgroundingInvalidateConfirmation() {
        val controller = Robolectric.buildActivity(CallActivity::class.java).setup()
        val activity = controller.get()
        val input = activity.findViewById<EditText>(R.id.call_input)
        val find = activity.findViewById<Button>(R.id.call_find)
        val confirm = activity.findViewById<Button>(R.id.call_confirm)
        input.setText("2025550123"); find.performClick()
        input.setText("2025550199"); confirm.performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        find.performClick(); activity.findViewById<Button>(R.id.call_cancel).performClick(); confirm.performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        find.performClick(); controller.pause().stop(); confirm.performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.destroy()
    }

    @Test fun externalIntentExtrasCannotInjectCallsOrStartRecording() {
        val intent = Intent().putExtra("transcript", "call 911").putExtra("listen", true)
        val controller = Robolectric.buildActivity(CallActivity::class.java, intent).setup()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertEquals(controller.get().getString(R.string.call_ready), controller.get().findViewById<TextView>(R.id.call_status).text)
        controller.pause().stop().destroy()
    }
}
