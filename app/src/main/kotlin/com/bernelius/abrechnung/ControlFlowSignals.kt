package com.bernelius.abrechnung

/**
 * Base class for intentional non-local control flow signals.
 *
 * This abstraction exists to distinguish between:
 * - Expected control flow changes (e.g., user presses Ctrl+C to exit a scene)
 * - Actual errors/exceptions that indicate something went wrong
 *
 * Using exceptions for control flow is generally discouraged, but in terminal UI applications
 * where input handling happens deep in the call stack and needs to unwind multiple layers
 * (e.g., from raw input reading back to the main menu), exceptions provide a clean mechanism
 * for non-local returns without threading signal parameters through every function signature.
 *
 * The [fillInStackTrace] override is intentional - these signals are expected program paths,
 * not errors, so generating a full stack trace would be wasteful.
 *
 * Signals:
 * - [ProgramExitSignal]: Terminates the entire application
 * - [ExitSignal]: Exits the current scene/navigation level (e.g., Ctrl+C pressed)
 */
abstract class ControlFlowSignal : Exception() {
    override fun fillInStackTrace(): Throwable = this
}

/** Signals that the application should terminate cleanly. */
class ProgramExitSignal : ControlFlowSignal()

/** Signals that the current scene or input operation should be exited (e.g., user pressed Ctrl+C). */
class ExitSignal : ControlFlowSignal()
