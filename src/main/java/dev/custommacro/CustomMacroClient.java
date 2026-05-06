package dev.custommacro;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import dev.custommacro.gui.MacroManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
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
 * Main client-side entry point for CustomMacro mod (Minecraft 1.21.11).
 *
 * Responsibilities:
 *  1. Load config on startup.
 *  2. Tick listener: detect macro key presses, fire action.
 *  3. Inject ◉ button into the Pause Menu (top-left).
 *  4. Render small ◉ overlay button in-game (top-left HUD).
 */
public class CustomMacroClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("custommacro");

    // Overlay button geometry
    private static final int BTN_X    = 5;
    private static final int BTN_Y    = 5;
    private static final int BTN_SIZE = 18;

    // Track held keys to fire once-per-press
    private final Set<Integer> heldKeys = new HashSet<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[CustomMacro] Initializing...");
        MacroConfig.load();

        // ── 1. Tick: fire macros ──────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) return; // only when no GUI open
            if (client.player == null) return;

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

        // ── 2. HUD overlay button ─────────────────────────────────────────────
        HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) return;
            renderOverlayButton(drawContext, client);
        });

        // ── 3. Pause menu button injection ────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                ButtonWidget macroBtn = ButtonWidget.builder(
                        Text.literal("◉"),
                        btn -> client.setScreen(new MacroManagerScreen(screen))
                ).dimensions(BTN_X, BTN_Y, BTN_SIZE, BTN_SIZE)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                         Text.translatable("custommacro.tooltip")))
                 .build();
                Screens.getButtons(screen).add(macroBtn);
            }
        });

        LOGGER.info("[CustomMacro] Ready! Macros: {}", MacroConfig.getMacros().size());
    }

    // ── Execute macro action ──────────────────────────────────────────────────
    private void executeMacro(MinecraftClient client, MacroEntry macro) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        String action = macro.getAction().trim();
        if (action.isEmpty()) return;

        LOGGER.info("[CustomMacro] Firing '{}': {}", macro.getName(), action);

        if (action.startsWith("/")) {
            // Strip leading slash and send as command via player
            // sendChatMessage handles both chat and commands (with /)
            client.player.sendChatMessage(action);
        } else {
            client.player.sendChatMessage(action);
        }
    }

    // ── Render small overlay button in HUD (top-left) ────────────────────────
    private void renderOverlayButton(DrawContext ctx, MinecraftClient client) {
        // Background
        ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE, 0xBB8B0000);
        // Border (manual 4 edges)
        ctx.fill(BTN_X,               BTN_Y,               BTN_X + BTN_SIZE, BTN_Y + 1,           0xFFCC0000);
        ctx.fill(BTN_X,               BTN_Y + BTN_SIZE - 1, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE,    0xFFCC0000);
        ctx.fill(BTN_X,               BTN_Y,               BTN_X + 1,        BTN_Y + BTN_SIZE,    0xFFCC0000);
        ctx.fill(BTN_X + BTN_SIZE - 1, BTN_Y,              BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE,    0xFFCC0000);
        // Icon
        ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("◉"),
                BTN_X + BTN_SIZE / 2,
                BTN_Y + BTN_SIZE / 2 - 4,
                0xFF4444);
    }

    /**
     * Called by MouseHandlerMixin when user clicks in-game (no screen open).
     */
    public static boolean handleOverlayClick(MinecraftClient client, double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= BTN_X && mouseX <= BTN_X + BTN_SIZE
                && mouseY >= BTN_Y && mouseY <= BTN_Y + BTN_SIZE) {
            client.setScreen(new MacroManagerScreen(null));
            return true;
        }
        return false;
    }
}
