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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomMacroClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("custommacro");

    public static final int BTN_X    = 5;
    public static final int BTN_Y    = 5;
    public static final int BTN_SIZE = 18;

    private static final Identifier ICON = Identifier.of("custommacro", "textures/gui/icon.png");

    // Track held keys untuk deteksi tekan baru (bukan hold)
    private final Set<Integer> heldKeys = new HashSet<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[CustomMacro] Initializing...");
        MacroConfig.load();

        // ── 1. Tick: fire macros dengan modifier support ───────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) return;
            if (client.player == null) return;
            if (client.getWindow() == null) return;

            long win = client.getWindow().getHandle();
            boolean shiftDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT)  == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean ctrlDown  = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean altDown   = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT)  == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

            List<MacroEntry> macros = MacroConfig.getMacros();
            for (MacroEntry macro : macros) {
                int key = macro.getKeyCode();
                if (key == GLFW.GLFW_KEY_UNKNOWN) continue;

                boolean mainPressed = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
                boolean modMatch    = (macro.isModShift() == shiftDown)
                                  && (macro.isModCtrl()  == ctrlDown)
                                  && (macro.isModAlt()   == altDown);

                // Buat combo ID unik agar Shift+A dan A beda state
                int comboId = key ^ (macro.isModShift() ? 0x10000 : 0)
                                  ^ (macro.isModCtrl()  ? 0x20000 : 0)
                                  ^ (macro.isModAlt()   ? 0x40000 : 0);

                if (mainPressed && modMatch && !heldKeys.contains(comboId)) {
                    heldKeys.add(comboId);
                    executeMacro(client, macro);
                } else if (!mainPressed || !modMatch) {
                    heldKeys.remove(comboId);
                }
            }
        });

        // ── 2. HUD overlay button ─────────────────────────────────────────────
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) return;
            renderOverlayButton(drawContext);
        });

        // ── 3. Pause menu: inject tombol ─────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                ButtonWidget macroBtn = ButtonWidget.builder(
                        Text.empty(),
                        btn -> client.setScreen(new MacroManagerScreen(screen))
                ).dimensions(BTN_X, BTN_Y, BTN_SIZE, BTN_SIZE)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                         Text.translatable("custommacro.tooltip")))
                 .build();
                Screens.getButtons(screen).add(macroBtn);
            }
        });

        // ── 4. Render icon di pause menu ─────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                ScreenEvents.afterRender(screen).register((scr, drawContext, mouseX, mouseY, delta) -> {
                    drawContext.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            ICON,
                            BTN_X + 1, BTN_Y + 1, 0, 0,
                            BTN_SIZE - 2, BTN_SIZE - 2,
                            BTN_SIZE - 2, BTN_SIZE - 2
                    );
                });
            }
        });

        LOGGER.info("[CustomMacro] Ready! Macros: {}", MacroConfig.getMacros().size());
    }

    private void executeMacro(MinecraftClient client, MacroEntry macro) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        if (macro.isSwapAction()) {
            executeSwap(client, macro.getAction().trim());
            return;
        }

        String action = macro.getAction().trim();
        if (action.isEmpty()) return;
        LOGGER.info("[CustomMacro] Firing '{}': {}", macro.getName(), action);
        if (action.startsWith("/")) {
            client.player.networkHandler.sendChatCommand(action.substring(1));
        } else {
            client.player.networkHandler.sendChatMessage(action);
        }
    }

    /**
     * Swap item di inventory dengan item yang sedang ada di slot target.
     *
     * Format action: "slotName|translationKeyA|translationKeyB"
     * Contoh: "chest|item.minecraft.elytra|item.minecraft.diamond_chestplate"
     *
     * Logika toggle:
     * - Kalau slot target berisi item A → pasang item B ke sana
     * - Kalau slot target berisi item B atau kosong → pasang item A ke sana
     */
    private void executeSwap(MinecraftClient client, String swapTarget) {
        if (client.player == null || client.interactionManager == null) return;

        // Format: "slotName|itemKeyA|itemKeyB"
        String[] parts = swapTarget.split("\\|", 3);
        if (parts.length != 3) {
            LOGGER.warn("[CustomMacro] Format swap salah (butuh 3 bagian): {}", swapTarget);
            return;
        }

        String slotName = parts[0].trim().toLowerCase();
        String itemKeyA = parts[1].trim(); // translation key penuh
        String itemKeyB = parts[2].trim();

        if (itemKeyA.isEmpty() || itemKeyB.isEmpty()) {
            LOGGER.warn("[CustomMacro] Item key kosong: '{}' / '{}'", itemKeyA, itemKeyB);
            return;
        }

        int targetSlot = resolveSlot(slotName);
        if (targetSlot < 0) {
            LOGGER.warn("[CustomMacro] Slot tidak dikenal: {}", slotName);
            return;
        }

        PlayerInventory inv = client.player.getInventory();

        // Cek item yang SEKARANG ada di slot target (exact match)
        ItemStack currentInSlot = inv.getStack(targetSlot);
        String currentKey = currentInSlot.isEmpty() ? "" : currentInSlot.getItem().getTranslationKey();

        // Toggle: slot berisi A → cari B, lainnya → cari A
        String wantKey = currentKey.equals(itemKeyA) ? itemKeyB : itemKeyA;

        // Cari wantKey di seluruh inventory (kecuali targetSlot)
        int foundSlot = -1;
        for (int i = 0; i < inv.size(); i++) {
            if (i == targetSlot) continue;
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getItem().getTranslationKey().equals(wantKey)) {
                foundSlot = i;
                break;
            }
        }

        if (foundSlot == -1) {
            LOGGER.info("[CustomMacro] Item '{}' tidak ada di inventory", wantKey);
            return;
        }

        LOGGER.info("[CustomMacro] Swap: inv[{}]('{}') <-> inv[{}]('{}')",
                foundSlot, wantKey, targetSlot, currentKey.isEmpty() ? "kosong" : currentKey);
        doInventorySwap(client, foundSlot, targetSlot);
    }

    private int resolveSlot(String slotName) {
        return switch (slotName) {
            case "head", "helmet"      -> 39;
            case "chest", "chestplate" -> 38;
            case "legs", "leggings"    -> 37;
            case "feet", "boots"       -> 36;
            case "offhand"             -> 40;
            default -> {
                if (slotName.startsWith("hotbar")) {
                    try { yield Integer.parseInt(slotName.substring(6)); }
                    catch (NumberFormatException e) { yield -1; }
                }
                yield -1;
            }
        };
    }

    /**
     * Lakukan swap dua slot di PlayerInventory secara langsung tanpa membuka screen.
     * Caranya: salin item sementara, set ulang kedua slot.
     */
    private void doInventorySwap(MinecraftClient client, int slotA, int slotB) {
        if (client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        ItemStack itemA = inv.getStack(slotA).copy();
        ItemStack itemB = inv.getStack(slotB).copy();
        inv.setStack(slotA, itemB);
        inv.setStack(slotB, itemA);
        // Sync ke server dengan mengirim perubahan
        // Fabric tidak expose direct packet, tapi MinecraftClient.player.networkHandler memiliki sendCommand
        // Cara paling reliable: trigger auto-sync MC dengan menyentuh selectedSlot
        client.player.getInventory().markDirty();
    }

    private void renderOverlayButton(DrawContext ctx) {
        ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE, 0xFF8B8B8B);
        ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_SIZE, BTN_Y + 1, 0xFFFFFFFF);
        ctx.fill(BTN_X, BTN_Y, BTN_X + 1, BTN_Y + BTN_SIZE, 0xFFFFFFFF);
        ctx.fill(BTN_X, BTN_Y + BTN_SIZE - 1, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE, 0xFF373737);
        ctx.fill(BTN_X + BTN_SIZE - 1, BTN_Y, BTN_X + BTN_SIZE, BTN_Y + BTN_SIZE, 0xFF373737);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON,
                BTN_X + 1, BTN_Y + 1, 0, 0,
                BTN_SIZE - 2, BTN_SIZE - 2,
                BTN_SIZE - 2, BTN_SIZE - 2);
    }

    public static boolean handleOverlayClick(MinecraftClient client, double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= BTN_X && mouseX <= BTN_X + BTN_SIZE
                        && mouseY >= BTN_Y && mouseY <= BTN_Y + BTN_SIZE) {
            client.execute(() -> client.setScreen(new MacroManagerScreen(null)));
            return true;
        }
        return false;
    }
}
