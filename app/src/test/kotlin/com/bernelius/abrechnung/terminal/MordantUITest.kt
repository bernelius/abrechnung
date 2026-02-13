package com.bernelius.abrechnung.terminal

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class MordantUITest {
    private val mockWriter = MockWriter()

    @Test
    fun `withLoading shows loading message`() = runBlocking {
        var blockExecuted = false

        mockWriter.withLoading(
            message = "Test message",
            block = {
                blockExecuted = true
                "result"
            }
        )

        assertEquals(true, blockExecuted)
    }

    @Test
    fun `withLoading returns block result`() = runBlocking {
        val result = mockWriter.withLoading(
            message = "Loading...",
            block = { "expected result" }
        )

        assertEquals("expected result", result)
    }

    @Test
    fun `withLoading handles exception`() = runBlocking {
        try {
            mockWriter.withLoading(
                message = "Loading...",
                block = { throw RuntimeException("Test exception") }
            )
        } catch (e: RuntimeException) {
            assertEquals("Test exception", e.message)
        }
    }

    @Test
    fun `withLoading default message is used`() = runBlocking {
        var blockExecuted = false

        mockWriter.withLoading(
            block = {
                blockExecuted = true
                42
            }
        )

        assertEquals(true, blockExecuted)
    }
}