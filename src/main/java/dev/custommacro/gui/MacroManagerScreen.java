package dev.custommacro.gui;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Macro Manager GUI — Minecraft style.
 * Layout:
 *  LIST VIEW : scrollable table + [+ Add] [Close]
 *  EDIT VIEW : form fields + [Save] [Cancel]
 */
public class MacroManagerScreen extends Screen {

    // ── Ukuran panel ──────────────────────────────────────────────────────────
    private static final int PANEL_W   = 320;
    private static final int PANEL_H   = 220;
    private static final int TITLE_H   = 24;
    private static final int ROW_H     = 24;
    private static final int MAX_ROWS  = 5;   // baris visible
    private static final int BTN_H     = 20;

    // Warna Minecraft style
    private static final int C_BG         = 0xC0000000; // overlay gelap
    private static final int C_PANEL      = 0xFF8B8B8B; // stone grey
    private static final int C_PANEL_DARK = 0xFF373737; // shadow
    private static final int C_PANEL_LITE = 0xFFFFFFFF; // highlight
    private static final int C_TITLE_BG   = 0xFF6A6A6A; // header
    private static final int C_ROW_A      = 0x44000000; // row ganjil
    private static final int C_ROW_B      = 0x22000000; // row genap
    private static final int C_INSET      = 0xFF595959; // inset field bg
    private static final int C_INSET_DARK = 0xFF000000;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private List<MacroEntry> entries;
    private boolean editing   = false;
    private int     editIndex = -1;

    // Form widgets
    private TextFieldWidget nameField;
    private TextFieldWidget actionField;
    private ButtonWidget    keyBindButton;
    private int     pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean awaitingKey    = false;

    // Scroll
    private int scrollOffset = 0;

    // Panel origin
    private int px, py;

    public MacroManagerScreen(Screen parent) {
        super(Text.translatable("custommacro.title"));
        this.parent  = parent;
        this.entries = new ArrayList<>(MacroConfig.getMacros());
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;
        clearChildren();
        if (!editing) initListView();
        else          initEditView();
    }

    // ─── LIST VIEW ────────────────────────────────────────────────────────────
    private void initListView() {
        buildRowButtons();

        // [+ Add]  di kanan bawah
        addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add Macro"),
                btn -> openEdit(-1)
        ).dimensions(px + PANEL_W - 100, py + PANEL_H - 28, 92, BTN_H).build());

        // [Close] di kiri bawah
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                btn -> close()
        ).dimensions(px + 8, py + PANEL_H - 28, 60, BTN_H).build());
    }

    private void buildRowButtons() {
        int listTop = py + TITLE_H + 24; // di bawah header kolom
        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            int rowY  = listTop + i * ROW_H;
            int btnY  = rowY + 3;
            final int fi = idx;

            // [Edit]
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Edit"),
                    btn -> openEdit(fi)
            ).dimensions(px + PANEL_W - 100, btnY, 42, 18).build());

            // [Del]
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Del"),
                    btn -> deleteEntry(fi)
            ).dimensions(px + PANEL_W - 55, btnY, 36, 18).build());
        }

        // Tombol scroll (kanan)
        int scrollX = px + PANEL_W - 16;
        int scrollTop = listTop;
        int scrollBot = listTop + MAX_ROWS * ROW_H - 16;

        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> {
            scrollOffset = Math.max(0, scrollOffset - 1);
            rebuildAll();
        }).dimensions(scrollX, scrollTop, 14, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
            int max = Math.max(0, entries.size() - MAX_ROWS);
            scrollOffset = Math.min(max, scrollOffset + 1);
            rebuildAll();
        }).dimensions(scrollX, scrollBot, 14, 14).build());
    }

    private void rebuildAll() {
        clearChildren();
        buildRowButtons();
        addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add Macro"), btn -> openEdit(-1)
        ).dimensions(px + PANEL_W - 100, py + PANEL_H - 28, 92, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"), btn -> close()
        ).dimensions(px + 8, py + PANEL_H - 28, 60, BTN_H).build());
    }

    // ─── EDIT VIEW ────────────────────────────────────────────────────────────
    private void initEditView() {
        int fieldX = px + 90;
        int fieldW = PANEL_W - 98;

        // Name
        nameField = new TextFieldWidget(textRenderer,
                fieldX, py + 52, fieldW, 18,
                Text.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("e.g. Go Home"));
        if (editIndex >= 0) nameField.setText(entries.get(editIndex).getName());
        addDrawableChild(nameField);

        // Action
        actionField = new TextFieldWidget(textRenderer,
                fieldX, py + 84, fieldW, 18,
                Text.literal("Action"));
        actionField.setMaxLength(256);
        actionField.setPlaceholder(Text.literal("e.g. /home"));
        if (editIndex >= 0) actionField.setText(entries.get(editIndex).getAction());
        addDrawableChild(actionField);

        // Key bind
        pendingKeyCode = (editIndex >= 0) ? entries.get(editIndex).getKeyCode() : GLFW.GLFW_KEY_UNKNOWN;
        String kLabel  = (pendingKeyCode != GLFW.GLFW_KEY_UNKNOWN) ? keyName(pendingKeyCode) : "[ Click to bind ]";
        keyBindButton  = ButtonWidget.builder(
                Text.literal(kLabel),
                btn -> { awaitingKey = true; keyBindButton.setMessage(Text.literal("[ Press any key... ]")); }
        ).dimensions(fieldX, py + 116, fieldW, BTN_H).build();
        addDrawableChild(keyBindButton);

        // Save / Cancel
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"), btn -> saveEdit()
        ).dimensions(px + PANEL_W - 118, py + PANEL_H - 28, 54, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> { editing = false; editIndex = -1; init(); }
        ).dimensions(px + PANEL_W - 60, py + PANEL_H - 28, 52, BTN_H).build());
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    private void openEdit(int idx) {
        editing = true; editIndex = idx; init();
    }

    private void saveEdit() {
        String name   = nameField.getText().trim();
        String action = actionField.getText().trim();
        if (name.isEmpty() || action.isEmpty() || pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) return;
        MacroEntry e = new MacroEntry(name, pendingKeyCode, action);
        if (editIndex < 0 || editIndex >= entries.size()) entries.add(e);
        else entries.set(editIndex, e);
        MacroConfig.setMacros(entries);
        MacroConfig.save();
        editing = false; editIndex = -1; init();
    }

    private void deleteEntry(int idx) {
        if (idx < 0 || idx >= entries.size()) return;
        entries.remove(idx);
        scrollOffset = Math.max(0, Math.min(scrollOffset, entries.size() - MAX_ROWS));
        if (scrollOffset < 0) scrollOffset = 0;
        MacroConfig.setMacros(entries);
        MacroConfig.save();
        rebuildAll();
    }

    // ── Key handling ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
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
        return super.keyPressed(input);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        drawPanel(ctx);
        if (!editing) renderListView(ctx);
        else          renderEditView(ctx);
        super.render(ctx, mx, my, delta);
    }

    /** Gambar panel batu Minecraft: raised border + inset title */
    private void drawPanel(DrawContext ctx) {
        // Shadow luar
        ctx.fill(px + 2, py + 2, px + PANEL_W + 2, py + PANEL_H + 2, 0x88000000);

        // Panel utama (abu batu)
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL);

        // Raised border: highlight atas+kiri, shadow bawah+kanan
        ctx.fill(px, py, px + PANEL_W, py + 2, C_PANEL_LITE);          // top
        ctx.fill(px, py, px + 2, py + PANEL_H, C_PANEL_LITE);          // left
        ctx.fill(px, py + PANEL_H - 2, px + PANEL_W, py + PANEL_H, C_PANEL_DARK); // bottom
        ctx.fill(px + PANEL_W - 2, py, px + PANEL_W, py + PANEL_H, C_PANEL_DARK); // right

        // Title bar (inset, lebih gelap)
        ctx.fill(px + 2, py + 2, px + PANEL_W - 2, py + TITLE_H, C_TITLE_BG);
        // Garis pemisah bawah title
        ctx.fill(px + 2, py + TITLE_H, px + PANEL_W - 2, py + TITLE_H + 1, C_PANEL_DARK);
        ctx.fill(px + 2, py + TITLE_H + 1, px + PANEL_W - 2, py + TITLE_H + 2, C_PANEL_LITE);

        // Title text
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("custommacro.title"),
                px + PANEL_W / 2, py + 7, 0xFFFFFF);

        // Garis pemisah atas tombol bawah
        ctx.fill(px + 2, py + PANEL_H - 34, px + PANEL_W - 2, py + PANEL_H - 33, C_PANEL_DARK);
        ctx.fill(px + 2, py + PANEL_H - 33, px + PANEL_W - 2, py + PANEL_H - 32, C_PANEL_LITE);
    }

    private void renderListView(DrawContext ctx) {
        int listTop = py + TITLE_H + 24;

        // Header kolom
        ctx.fill(px + 2, py + TITLE_H + 2, px + PANEL_W - 2, py + TITLE_H + 22, 0x44000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Macro Name"), px + 10,  py + TITLE_H + 6, 0xFFFFAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key"),        px + 148, py + TITLE_H + 6, 0xFFFFAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Action"),     px + 196, py + TITLE_H + 6, 0xFFFFAA);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("custommacro.label.empty"),
                    px + PANEL_W / 2, listTop + MAX_ROWS * ROW_H / 2 - 4, 0x888888);
        } else {
            for (int i = 0; i < MAX_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MacroEntry e = entries.get(idx);
                int rowY = listTop + i * ROW_H;

                // Row bg alternating
                ctx.fill(px + 2, rowY, px + PANEL_W - 18, rowY + ROW_H - 2,
                        (i % 2 == 0) ? C_ROW_A : C_ROW_B);

                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getName(), 14)),   px + 10,  rowY + 8, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("[" + keyName(e.getKeyCode()) + "]"), px + 148, rowY + 8, 0xFFDD44);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getAction(), 10)), px + 196, rowY + 8, 0xAAFFAA);
            }
        }

        // Info scroll
        int total = entries.size();
        if (total > MAX_ROWS) {
            String info = (scrollOffset + 1) + "-"
                    + Math.min(total, scrollOffset + MAX_ROWS) + "/" + total;
            ctx.drawTextWithShadow(textRenderer, Text.literal(info),
                    px + 70, py + PANEL_H - 24, 0x888888);
        }
    }

    private void renderEditView(DrawContext ctx) {
        // Sub-title
        String sub = (editIndex < 0) ? "New Macro" : "Edit Macro";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(sub),
                px + PANEL_W / 2, py + TITLE_H + 6, 0xFFDD88);

        // Label fields
        ctx.drawTextWithShadow(textRenderer, Text.literal("Name:"),   px + 12, py + 55,  0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Action:"), px + 12, py + 87,  0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key:"),    px + 12, py + 119, 0xCCCCCC);

        // Tanda wajib merah kalau kosong
        if (nameField   != null && nameField.getText().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), px + 80, py + 55, 0xFF4444);
        if (actionField != null && actionField.getText().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), px + 80, py + 87, 0xFF4444);
        if (pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN)
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), px + 80, py + 119, 0xFF4444);

        // Hint saat awaiting key
        if (awaitingKey)
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Press a key  (ESC = cancel)"),
                    px + PANEL_W / 2, py + 148, 0xFFAA00);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String keyName(int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return "?";
        String raw = GLFW.glfwGetKeyName(k, 0);
        if (raw != null && !raw.isEmpty()) return raw.toUpperCase();
        return switch (k) {
            case GLFW.GLFW_KEY_SPACE         -> "SPACE";
            case GLFW.GLFW_KEY_BACKSPACE     -> "BKSP";
            case GLFW.GLFW_KEY_ENTER         -> "ENTER";
            case GLFW.GLFW_KEY_TAB           -> "TAB";
            case GLFW.GLFW_KEY_UP            -> "UP";
            case GLFW.GLFW_KEY_DOWN          -> "DOWN";
            case GLFW.GLFW_KEY_LEFT          -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT         -> "RIGHT";
            case GLFW.GLFW_KEY_F1            -> "F1";
            case GLFW.GLFW_KEY_F2            -> "F2";
            case GLFW.GLFW_KEY_F3            -> "F3";
            case GLFW.GLFW_KEY_F4            -> "F4";
            case GLFW.GLFW_KEY_F5            -> "F5";
            case GLFW.GLFW_KEY_F6            -> "F6";
            case GLFW.GLFW_KEY_F7            -> "F7";
            case GLFW.GLFW_KEY_F8            -> "F8";
            case GLFW.GLFW_KEY_F9            -> "F9";
            case GLFW.GLFW_KEY_F10           -> "F10";
            case GLFW.GLFW_KEY_F11           -> "F11";
            case GLFW.GLFW_KEY_F12           -> "F12";
            case GLFW.GLFW_KEY_INSERT        -> "INS";
            case GLFW.GLFW_KEY_DELETE        -> "DEL";
            case GLFW.GLFW_KEY_HOME          -> "HOME";
            case GLFW.GLFW_KEY_END           -> "END";
            case GLFW.GLFW_KEY_PAGE_UP       -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN     -> "PGDN";
            case GLFW.GLFW_KEY_LEFT_SHIFT    -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT   -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL  -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT      -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT     -> "RALT";
            default -> "K" + k;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
