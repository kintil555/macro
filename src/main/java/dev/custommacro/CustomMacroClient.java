package dev.custommacro;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import dev.custommacro.gui.MacroManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main client-side entry point for CustomMacro.
 *
 * Responsibilities:
 *  1. Load config on startup.
 *  2. Register tick listener to detect macro key presses and execute actions.
 *  3. Inject a redstone-icon button into the Pause Menu (top-left corner).
 *  4. Render a small clickable overlay button in-game (top-left, 5,5).
 */
public class CustomMacroClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("custommacro");

    // Button geometry (top-left corner)
    private static final int BTN_X = 5;
    private static final int BTN_Y = 5;
    private static final int BTN_SIZE = 18;

    // Track which keys are currently held to fire once-per-press
    private final Set<Integer> heldKeys = new HashSet<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[CustomMacro] Initializing CustomMacro mod...");

        // Load macros from disk
        MacroConfig.load();

        // ── 1. Tick: fire macros on key press ────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) return; // only fire in-game, not in any GUI

            List<MacroEntry> macros = MacroConfig.getMacros();
            for (MacroEntry macro : macros) {
                int key = macro.getKeyCode();
                if (key == GLFW.GLFW_KEY_UNKNOWN) continue;

                boolean pressed = GLFW.glfwGetKey(
                        client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;

                if (pressed && !heldKeys.contains(key)) {
                    heldKeys.add(key);
                    executeMacro(client, macro);
                } else if (!pressed) {
                    heldKeys.remove(key);
                }
            }
        });

        // ── 2. HUD overlay button (in-game top-left) ─────────────────────────
        HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) return; // don't draw over screens

            renderOverlayButton(drawContext, client);
        });

        // ── 3. Pause Menu button injection ───────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                // Add a small 18x18 redstone button in the top-left of the pause menu
                ButtonWidget macroBtn = ButtonWidget.builder(
                        Text.literal("◉"), // Unicode redstone-like symbol
                        btn -> client.setScreen(new MacroManagerScreen(screen))
                ).dimensions(BTN_X, BTN_Y, BTN_SIZE, BTN_SIZE)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                         Text.translatable("custommacro.tooltip")))
                 .build();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(macroBtn);
            }
        });

        LOGGER.info("[CustomMacro] Ready! Macros loaded: {}", MacroConfig.getMacros().size());
    }

    // ── Execute a macro action ────────────────────────────────────────────────
    private void executeMacro(MinecraftClient client, MacroEntry macro) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        String action = macro.getAction().trim();
        if (action.isEmpty()) return;

        LOGGER.info("[CustomMacro] Firing macro '{}': {}", macro.getName(), action);

        if (action.startsWith("/")) {
            // Send as command (strip leading slash)
            client.player.networkHandler.sendCommand(action.substring(1));
        } else {
            // Send as chat message
            client.player.networkHandler.sendChatMessage(action);
        }
    }

    // ── Render clickable button in top-left of HUD ────────────────────────────
    private void renderOverlayButton(DrawContext ctx, MinecraftClient client) {
        // Button background (dark red, semi-transparent)
        ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE, 0xBB8B0000);
        // Border
        ctx.drawBorder(BTN_X, BTN_Y, BTN_SIZE, BTN_SIZE, 0xFFCC0000);
        // Redstone icon text centered
        ctx.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal("◉"),
                BTN_X + BTN_SIZE / 2,
                BTN_Y + BTN_SIZE / 2 - 4,
                0xFF4444
        );
    }

    /**
     * Called by the mouse click handler (via mixin or event) when the overlay
     * button is clicked while in-game.
     */
    public static boolean handleOverlayClick(MinecraftClient client, double mouseX, double mouseY, int button) {
        if (button == 0 // left click
                && mouseX >= BTN_X && mouseX <= BTN_X + BTN_SIZE
                && mouseY >= BTN_Y && mouseY <= BTN_Y + BTN_SIZE) {
            client.setScreen(new MacroManagerScreen(null));
            return true;
        }
        return false;
    }
}
