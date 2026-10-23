package com.vdx.sonic.executor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionExecutorConfirmationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val service = object : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
    private val target = TargetRef(text = "Delete")

    @Test
    fun missingConfirmationDeniesDestructiveClick() = runBlocking {
        val executor = ActionExecutor(context, service)
        try {
            assertEquals(ActionResult.Cancelled, executor.execute(ResolvedAction.Click(target, destructive = true)))
        } finally {
            executor.destroy()
        }
    }

    @Test
    fun explicitDenialCancelsDestructiveClick() = runBlocking {
        val executor = ActionExecutor(context, service)
        var prompts = 0
        try {
            val result = executor.execute(ResolvedAction.Click(target, destructive = true)) {
                prompts++
                false
            }
            assertEquals(1, prompts)
            assertEquals(ActionResult.Cancelled, result)
        } finally {
            executor.destroy()
        }
    }

    @Test
    fun nonDestructiveClickDoesNotRequestConfirmation() = runBlocking {
        val executor = ActionExecutor(context, service)
        var prompts = 0
        try {
            val result = executor.execute(ResolvedAction.Click(target, destructive = false)) {
                prompts++
                false
            }
            assertEquals(0, prompts)
            assertTrue(result is ActionResult.Failed)
        } finally {
            executor.destroy()
        }
    }

    @Test
    fun explicitApprovalStillRequiresAnAvailableTarget() = runBlocking {
        val executor = ActionExecutor(context, service)
        var prompts = 0
        try {
            val result = executor.execute(ResolvedAction.Click(target, destructive = true)) {
                prompts++
                true
            }
            assertEquals(1, prompts)
            assertTrue(result is ActionResult.Failed)
        } finally {
            executor.destroy()
        }
    }
}
