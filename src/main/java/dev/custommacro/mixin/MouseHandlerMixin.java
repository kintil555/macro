package dev.custommacro.mixin;

/**
 * MouseHandlerMixin is intentionally left empty.
 * Mouse click detection for the overlay button is handled via
 * GLFW mouse button callback registered in CustomMacroClient,
 * avoiding mixin signature issues across MC versions.
 */
public class MouseHandlerMixin {
    // No mixin needed — see CustomMacroClient#registerMouseCallback()
}
