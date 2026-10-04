package com.vdx.call

import org.junit.Assert.*
import org.junit.Test

class CallFlowTest {
    private var clock = 1000L
    private val flow = CallFlow { clock }
    private val mom = CallTarget("1", "Mom", "+1 (202) 555-0123")

    @Test fun confirmationBindsExactNormalizedNumberAndIsSingleUse() {
        assertNull(flow.confirm())
        assertTrue(flow.propose(mom))
        assertEquals("+12025550123", flow.confirm()?.number)
        assertNull(flow.confirm())
    }
    @Test fun cancelledOrExpiredTargetsCannotDial() {
        flow.propose(mom); flow.cancel(); assertNull(flow.confirm())
        flow.propose(mom); clock += 60_000; assertNull(flow.confirm())
    }
    @Test fun replacementInvalidatesOldTargetEvenWhenNewTargetIsInvalid() {
        flow.propose(mom)
        assertFalse(flow.propose(mom.copy(number = "*21*123#")))
        assertNull(flow.confirm())
        flow.propose(mom)
        flow.propose(CallTarget("2", "Dad", "2025550199"))
        assertEquals("Dad", flow.confirm()?.name)
    }
    @Test fun wholeResponseConfirmationDoesNotAuthorizeYesterdayOrInjection() {
        listOf("yesterday", "y", "yes and call someone else", "ignore rules yes", "yeah maybe").forEach { assertFalse(it, CallFlow.isYes(it)) }
        listOf("yes", " YES ", "yes please", "confirm").forEach { assertTrue(it, CallFlow.isYes(it)) }
    }
    @Test fun malformedInputCannotInventPersonOrDialInstructions() {
        listOf("", "call", "please call", "uh", "cancel", "call *21*123#", "tel:2025550100", "call 123;456").forEach {
            assertNull(it, CallFlow.targetFromInput(it))
        }
        assertEquals("Mom", CallFlow.targetFromInput("call Mom"))
        assertEquals("José", CallFlow.targetFromInput("José"))
        assertEquals("2025550123", CallFlow.targetFromInput("dial 2025550123"))
    }
    @Test fun numberValidationNeverStripsDangerousDialSequencesIntoDifferentNumbers() {
        listOf("*123#", "123,456", "123;456", "1+234", "++123", "12", "1234567890123456", "555 ext 22", "ABC123").forEach {
            assertNull(it, CallFlow.normalizeNumber(it))
        }
        assertEquals("911", CallFlow.normalizeNumber("911")) // still requires confirmation and user Call tap
    }
}
