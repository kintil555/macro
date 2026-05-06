package dev.custommacro.gui;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Main GUI screen for the CustomMacro mod.
 * Opened from the pause menu button (top-left corner, redstone icon).
 */
public class MacroManagerScreen extends Screen {

    // ── Layout constants ────────────────────────────────────────────────────
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 260;
    private static final int ROW_H   = 22;
    private static final int SCROLL_AREA_H = 120;

    // ── State ────────────────────────────────────────────────────────────────
    private final Screen parent;
    private List<MacroEntry> entries;

    // Editing state
    private boolean editing = false;
    private int editIndex = -1;       // -1 = new

    // Form widgets
    private TextFieldWidget nameField;
    private TextFieldWidget actionField;
    private ButtonWidget   keyBindButton;
    private int            pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean        awaitingKey    = false;

    // Scroll
    private int scrollOffset = 0;

    // Panel origin (centered)
    private int px, py;

    public MacroManagerScreen(Screen parent) {
        super(Text.translatable("custommacro.title"));
        this.parent = parent;
        this.entries = new ArrayList<>(MacroConfig.getMacros());
    }

    // ── init ─────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        if (!editing) {
            initListView();
        } else {
            initEditView();
        }
    }

    private void initListView() {
        clearChildren();

        // "Add Macro" button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.add"),
                btn -> openEdit(-1)
        ).dimensions(px + PANEL_W - 130, py + PANEL_H - 30, 120, 20).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.close"),
                btn -> close()
        ).dimensions(px + 10, py + PANEL_H - 30, 70, 20).build());

        buildRowButtons();
    }

    private void buildRowButtons() {
        int visibleRows = SCROLL_AREA_H / ROW_H;
        for (int i = 0; i < visibleRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            MacroEntry e = entries.get(idx);
            int rowY = py + 46 + i * ROW_H;

            final int finalIdx = idx;
            // Edit button per row
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("custommacro.button.edit"),
                    btn -> openEdit(finalIdx)
            ).dimensions(px + PANEL_W - 110, rowY, 45, 18).build());

            // Delete button per row
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("custommacro.button.delete"),
                    btn -> deleteEntry(finalIdx)
            ).dimensions(px + PANEL_W - 60, rowY, 50, 18).build());
        }

        // Scroll buttons
        addDrawableChild(ButtonWidget.builder(
                Text.literal("▲"),
                btn -> { scrollOffset = Math.max(0, scrollOffset - 1); rebuildList(); }
        ).dimensions(px + PANEL_W - 20, py + 46, 18, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("▼"),
                btn -> { scrollOffset = Math.min(Math.max(0, entries.size() - (SCROLL_AREA_H / ROW_H)), scrollOffset + 1); rebuildList(); }
        ).dimensions(px + PANEL_W - 20, py + 46 + SCROLL_AREA_H - 20, 18, 18).build());
    }

    private void rebuildList() {
        clearChildren();
        buildRowButtons();
        // Re-add static buttons
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.add"),
                btn -> openEdit(-1)
        ).dimensions(px + PANEL_W - 130, py + PANEL_H - 30, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.close"),
                btn -> close()
        ).dimensions(px + 10, py + PANEL_H - 30, 70, 20).build());
    }

    private void initEditView() {
        clearChildren();

        // Name field
        nameField = new TextFieldWidget(textRenderer,
                px + 90, py + 60, 220, 18,
                Text.translatable("custommacro.label.name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("e.g. Go Home"));
        if (editIndex >= 0) nameField.setText(entries.get(editIndex).getName());
        addDrawableChild(nameField);

        // Action field
        actionField = new TextFieldWidget(textRenderer,
                px + 90, py + 90, 220, 18,
                Text.translatable("custommacro.label.action"));
        actionField.setMaxLength(256);
        actionField.setPlaceholder(Text.literal("e.g. /home"));
        if (editIndex >= 0) actionField.setText(entries.get(editIndex).getAction());
        addDrawableChild(actionField);

        // Key bind button
        String keyLabel = editIndex >= 0
                ? keyName(entries.get(editIndex).getKeyCode())
                : "[ Click to bind key ]";
        pendingKeyCode = editIndex >= 0 ? entries.get(editIndex).getKeyCode() : GLFW.GLFW_KEY_UNKNOWN;

        keyBindButton = ButtonWidget.builder(
                Text.literal(keyLabel),
                btn -> { awaitingKey = true; keyBindButton.setMessage(Text.literal("[ Press a key... ]")); }
        ).dimensions(px + 90, py + 120, 220, 20).build();
        addDrawableChild(keyBindButton);

        // Save
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.save"),
                btn -> saveEdit()
        ).dimensions(px + PANEL_W - 130, py + PANEL_H - 30, 60, 20).build());

        // Cancel
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommacro.button.cancel"),
                btn -> { editing = false; init(); }
        ).dimensions(px + PANEL_W - 65, py + PANEL_H - 30, 55, 20).build());
    }

    // ── Edit logic ───────────────────────────────────────────────────────────
    private void openEdit(int index) {
        editing = true;
        editIndex = index;
        init();
    }

    private void saveEdit() {
        String name   = nameField.getText().trim();
        String action = actionField.getText().trim();
        if (name.isEmpty() || action.isEmpty() || pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) return;

        MacroEntry entry = new MacroEntry(name, pendingKeyCode, action);
        if (editIndex < 0 || editIndex >= entries.size()) {
            entries.add(entry);
        } else {
            entries.set(editIndex, entry);
        }
        MacroConfig.setMacros(entries);
        MacroConfig.save();
        editing = false;
        init();
    }

    private void deleteEntry(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            scrollOffset = Math.max(0, scrollOffset - 1);
            MacroConfig.setMacros(entries);
            MacroConfig.save();
            rebuildList();
        }
    }

    // ── Key input ────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                awaitingKey = false;
                keyBindButton.setMessage(Text.literal(keyName(pendingKeyCode)));
                return true;
            }
            pendingKeyCode = keyCode;
            awaitingKey    = false;
            keyBindButton.setMessage(Text.literal(keyName(keyCode)));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Render ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dim background
        renderBackground(context, mouseX, mouseY, delta);

        // Panel background
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0xDD1A1A2E);
        // Panel border
        context.drawBorder(px, py, PANEL_W, PANEL_H, 0xFFCC0000);

        // Title bar
        context.fill(px, py, px + PANEL_W, py + 22, 0xFF8B0000);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("custommacro.title"), px + PANEL_W / 2, py + 7, 0xFFFFFF);

        if (!editing) {
            renderListView(context);
        } else {
            renderEditView(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderListView(DrawContext context) {
        // Column headers
        context.drawTextWithShadow(textRenderer, Text.literal("Name"),      px + 12,  py + 28, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Key"),       px + 150, py + 28, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Action"),    px + 200, py + 28, 0xAAAAAA);
        // Separator line
        context.fill(px + 8, py + 38, px + PANEL_W - 8, py + 39, 0x88CC0000);

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("custommacro.label.empty"),
                    px + PANEL_W / 2, py + 90, 0x888888);
        } else {
            int visibleRows = SCROLL_AREA_H / ROW_H;
            for (int i = 0; i < visibleRows; i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MacroEntry e = entries.get(idx);
                int rowY = py + 46 + i * ROW_H;

                // Alternating row bg
                int bg = (i % 2 == 0) ? 0x22FFFFFF : 0x11FFFFFF;
                context.fill(px + 8, rowY, px + PANEL_W - 170, rowY + ROW_H - 2, bg);

                String nameStr   = truncate(e.getName(),    16);
                String keyStr    = keyName(e.getKeyCode());
                String actionStr = truncate(e.getAction(),  18);

                context.drawTextWithShadow(textRenderer, Text.literal(nameStr),   px + 12,  rowY + 5, 0xFFFFFF);
                context.drawTextWithShadow(textRenderer, Text.literal("[" + keyStr + "]"), px + 120, rowY + 5, 0xFFDD44);
                context.drawTextWithShadow(textRenderer, Text.literal(actionStr), px + 175, rowY + 5, 0xAAFFAA);
            }
        }

        // Scroll info
        if (entries.size() > SCROLL_AREA_H / ROW_H) {
            String scrollInfo = (scrollOffset + 1) + "-" +
                    Math.min(entries.size(), scrollOffset + SCROLL_AREA_H / ROW_H) +
                    " / " + entries.size();
            context.drawTextWithShadow(textRenderer, Text.literal(scrollInfo), px + 12, py + PANEL_H - 25, 0x888888);
        }
    }

    private void renderEditView(DrawContext context) {
        String titleStr = editIndex < 0 ? "Add New Macro" : "Edit Macro";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleStr),
                px + PANEL_W / 2, py + 28, 0xFFDD88);

        context.drawTextWithShadow(textRenderer, Text.translatable("custommacro.label.name"),
                px + 12, py + 63, 0xCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.translatable("custommacro.label.action"),
                px + 12, py + 93, 0xCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.translatable("custommacro.label.key"),
                px + 12, py + 123, 0xCCCCCC);

        if (awaitingKey) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Press any key (ESC to cancel)"),
                    px + PANEL_W / 2, py + 155, 0xFFAA00);
        }
        if (nameField != null && nameField.getText().isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("← required"), px + 315, py + 63, 0xFF4444);
        }
        if (actionField != null && actionField.getText().isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("← required"), px + 315, py + 93, 0xFF4444);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private static String keyName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "?";
        String raw = GLFW.glfwGetKeyName(keyCode, 0);
        if (raw != null && !raw.isEmpty()) return raw.toUpperCase();
        // Fallback for special keys
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE        -> "SPACE";
            case GLFW.GLFW_KEY_BACKSPACE    -> "BACKSPACE";
            case GLFW.GLFW_KEY_ENTER        -> "ENTER";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_UP           -> "UP";
            case GLFW.GLFW_KEY_DOWN         -> "DOWN";
            case GLFW.GLFW_KEY_LEFT         -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT        -> "RIGHT";
            case GLFW.GLFW_KEY_F1           -> "F1";
            case GLFW.GLFW_KEY_F2           -> "F2";
            case GLFW.GLFW_KEY_F3           -> "F3";
            case GLFW.GLFW_KEY_F4           -> "F4";
            case GLFW.GLFW_KEY_F5           -> "F5";
            case GLFW.GLFW_KEY_F6           -> "F6";
            case GLFW.GLFW_KEY_F7           -> "F7";
            case GLFW.GLFW_KEY_F8           -> "F8";
            case GLFW.GLFW_KEY_F9           -> "F9";
            case GLFW.GLFW_KEY_F10          -> "F10";
            case GLFW.GLFW_KEY_F11          -> "F11";
            case GLFW.GLFW_KEY_F12          -> "F12";
            case GLFW.GLFW_KEY_INSERT       -> "INSERT";
            case GLFW.GLFW_KEY_DELETE       -> "DELETE";
            case GLFW.GLFW_KEY_HOME         -> "HOME";
            case GLFW.GLFW_KEY_END          -> "END";
            case GLFW.GLFW_KEY_PAGE_UP      -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN    -> "PGDN";
            case GLFW.GLFW_KEY_LEFT_SHIFT   -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT  -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL-> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT     -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT    -> "RALT";
            default -> "KEY_" + keyCode;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
