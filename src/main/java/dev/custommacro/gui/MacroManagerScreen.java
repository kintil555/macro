package dev.custommacro.gui;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Macro Manager GUI — Minecraft inventory style.
 * Mirip inventory Minecraft: background textured, slot-style rows, border batu halus.
 */
public class MacroManagerScreen extends Screen {

    // ── Ukuran panel (mirip inventory MC) ────────────────────────────────────
    private static final int PANEL_W  = 340;
    private static final int PANEL_H  = 240;
    private static final int TITLE_H  = 14;  // tinggi area title di dalam border
    private static final int ROW_H    = 22;
    private static final int MAX_ROWS = 7;
    private static final int BTN_H    = 20;
    private static final int BORDER   = 8;   // border luar

    // ── Warna MC inventory style ──────────────────────────────────────────────
    // Background overlay gelap
    private static final int C_OVERLAY   = 0xC0101010;
    // Panel luar: batu kasar (seperti inventory chest)
    private static final int C_OUTER     = 0xFF8B8B8B;
    private static final int C_OUTER_LT  = 0xFFFFFFFF;
    private static final int C_OUTER_DK  = 0xFF373737;
    private static final int C_OUTER_MD  = 0xFF5A5A5A;
    // Panel dalam: sedikit lebih gelap (seperti slot area)
    private static final int C_INNER     = 0xFF636363;
    private static final int C_INNER_DK  = 0xFF1A1A1A;
    private static final int C_INNER_LT  = 0xFF9A9A9A;
    // Row & header
    private static final int C_HEADER_BG = 0x88000000;
    private static final int C_ROW_ODD   = 0x33000000;
    private static final int C_ROW_EVEN  = 0x11FFFFFF;
    private static final int C_ROW_SEL   = 0x44FFDD00;
    // Text
    private static final int C_TITLE     = 0x404040;
    private static final int C_COL_HDR   = 0xFFFFAA;
    private static final int C_NAME_TXT  = 0xFFFFFF;
    private static final int C_KEY_TXT   = 0xFFDD44;
    private static final int C_ACT_TXT   = 0xAAFFAA;
    private static final int C_LABEL     = 0xCCCCCC;
    private static final int C_EMPTY     = 0x888888;
    private static final int C_WARN      = 0xFF5555;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private List<MacroEntry> entries;
    private boolean editing   = false;
    private int     editIndex = -1;

    // Form fields
    private TextFieldWidget nameField;
    private TextFieldWidget actionField;
    private ButtonWidget    keyBindButton;
    private int     pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean pendingShift   = false;
    private boolean pendingCtrl    = false;
    private boolean pendingAlt     = false;
    private boolean awaitingKey    = false;

    // Modifier toggle buttons (saat edit)
    private ButtonWidget shiftToggle;
    private ButtonWidget ctrlToggle;
    private ButtonWidget altToggle;

    // Scroll & selection
    private int scrollOffset = 0;
    private int selectedRow  = -1;

    // Panel origin (dihitung saat init)
    private int px, py;
    // Content area (di dalam border besar)
    private int cx, cy, cw, ch;

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
        // Content area (di dalam border luar 4px)
        cx = px + BORDER;
        cy = py + BORDER + TITLE_H + 4;
        cw = PANEL_W - BORDER * 2;
        ch = PANEL_H - BORDER * 2 - TITLE_H - 4;

        clearChildren();
        if (!editing) initListView();
        else          initEditView();
    }

    // ─── LIST VIEW ────────────────────────────────────────────────────────────
    private void initListView() {
        int listTop  = cy + 20; // di bawah header kolom
        int listH    = MAX_ROWS * ROW_H;
        int bottomY  = cy + ch - BTN_H - 2;

        // Row: Edit & Del buttons
        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            int rowY  = listTop + i * ROW_H;
            int btnY  = rowY + (ROW_H - 16) / 2;
            final int fi = idx;

            addDrawableChild(ButtonWidget.builder(Text.literal("✎"),
                    btn -> openEdit(fi)
            ).dimensions(cx + cw - 44, btnY, 20, 16).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                    btn -> deleteEntry(fi)
            ).dimensions(cx + cw - 22, btnY, 20, 16).build());
        }

        // Scroll arrows — di sisi kanan content area
        int scrollX  = cx + cw - 10;
        int scrollT  = listTop;
        int scrollB  = listTop + listH - 12;

        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> {
            scrollOffset = Math.max(0, scrollOffset - 1);
            selectedRow = -1;
            rebuildList();
        }).dimensions(scrollX, scrollT, 12, 12).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
            scrollOffset = Math.min(Math.max(0, entries.size() - MAX_ROWS), scrollOffset + 1);
            selectedRow = -1;
            rebuildList();
        }).dimensions(scrollX, scrollB, 12, 12).build());

        // Bottom buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Tambah"),
                btn -> openEdit(-1)
        ).dimensions(cx, bottomY, 80, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Tutup"),
                btn -> close()
        ).dimensions(cx + cw - 60, bottomY, 60, BTN_H).build());
    }

    private void rebuildList() {
        clearChildren();
        initListView();
    }

    // ─── EDIT VIEW ────────────────────────────────────────────────────────────
    private void initEditView() {
        int labelX  = cx + 4;
        int fieldX  = cx + 58;
        int fieldW  = cw - 62;
        int startY  = cy + 4;
        int rowGap  = 28;

        // Name field
        nameField = new TextFieldWidget(textRenderer, fieldX, startY, fieldW, 18, Text.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("Nama macro..."));
        if (editIndex >= 0) nameField.setText(entries.get(editIndex).getName());
        addDrawableChild(nameField);

        // Action field
        actionField = new TextFieldWidget(textRenderer, fieldX, startY + rowGap, fieldW, 18, Text.literal("Action"));
        actionField.setMaxLength(256);
        actionField.setPlaceholder(Text.literal("/command atau teks..."));
        if (editIndex >= 0) actionField.setText(entries.get(editIndex).getAction());
        addDrawableChild(actionField);

        // Modifier toggles (Ctrl / Alt / Shift)
        pendingKeyCode = (editIndex >= 0) ? entries.get(editIndex).getKeyCode()   : GLFW.GLFW_KEY_UNKNOWN;
        pendingShift   = (editIndex >= 0) && entries.get(editIndex).isModShift();
        pendingCtrl    = (editIndex >= 0) && entries.get(editIndex).isModCtrl();
        pendingAlt     = (editIndex >= 0) && entries.get(editIndex).isModAlt();

        int modY   = startY + rowGap * 2;
        int modW   = 46;
        int modGap = 2;

        ctrlToggle = ButtonWidget.builder(modLabel("CTRL", pendingCtrl),
                btn -> { pendingCtrl = !pendingCtrl; updateModButtons(); }
        ).dimensions(fieldX, modY, modW, 16).build();
        addDrawableChild(ctrlToggle);

        altToggle = ButtonWidget.builder(modLabel("ALT", pendingAlt),
                btn -> { pendingAlt = !pendingAlt; updateModButtons(); }
        ).dimensions(fieldX + modW + modGap, modY, modW, 16).build();
        addDrawableChild(altToggle);

        shiftToggle = ButtonWidget.builder(modLabel("SHIFT", pendingShift),
                btn -> { pendingShift = !pendingShift; updateModButtons(); }
        ).dimensions(fieldX + (modW + modGap) * 2, modY, modW, 16).build();
        addDrawableChild(shiftToggle);

        // Keybind button
        int keyY = startY + rowGap * 3;
        String kLabel = buildKeyLabel();
        keyBindButton = ButtonWidget.builder(Text.literal(kLabel),
                btn -> { awaitingKey = true; keyBindButton.setMessage(Text.literal("[ Tekan tombol... ]")); }
        ).dimensions(fieldX, keyY, fieldW, BTN_H).build();
        addDrawableChild(keyBindButton);

        // Save / Cancel
        int bottomY = cy + ch - BTN_H - 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Simpan"),
                btn -> saveEdit()
        ).dimensions(cx + cw - 122, bottomY, 58, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Batal"),
                btn -> { editing = false; editIndex = -1; init(); }
        ).dimensions(cx + cw - 62, bottomY, 62, BTN_H).build());
    }

    private Text modLabel(String name, boolean active) {
        return Text.literal(active ? "§a[" + name + "]" : "§7" + name);
    }

    private void updateModButtons() {
        if (ctrlToggle  != null) ctrlToggle.setMessage(modLabel("CTRL",  pendingCtrl));
        if (altToggle   != null) altToggle.setMessage(modLabel("ALT",    pendingAlt));
        if (shiftToggle != null) shiftToggle.setMessage(modLabel("SHIFT", pendingShift));
        if (keyBindButton != null && !awaitingKey)
            keyBindButton.setMessage(Text.literal(buildKeyLabel()));
    }

    private String buildKeyLabel() {
        if (pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) return "[ Klik untuk bind... ]";
        StringBuilder sb = new StringBuilder();
        if (pendingCtrl)  sb.append("CTRL+");
        if (pendingAlt)   sb.append("ALT+");
        if (pendingShift) sb.append("SHIFT+");
        sb.append(keyName(pendingKeyCode));
        return sb.toString();
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    private void openEdit(int idx) {
        editing   = true;
        editIndex = idx;
        init();
    }

    private void saveEdit() {
        if (nameField == null || actionField == null) return;
        String name   = nameField.getText().trim();
        String action = actionField.getText().trim();
        if (name.isEmpty() || action.isEmpty() || pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) return;

        MacroEntry e = new MacroEntry(name, pendingKeyCode, pendingShift, pendingCtrl, pendingAlt, action);
        if (editIndex >= 0 && editIndex < entries.size()) {
            entries.set(editIndex, e);
        } else {
            entries.add(e);
        }
        MacroConfig.setMacros(entries);
        MacroConfig.save();
        editing   = false;
        editIndex = -1;
        init();
    }

    private void deleteEntry(int idx) {
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            MacroConfig.setMacros(entries);
            MacroConfig.save();
            scrollOffset = Math.max(0, Math.min(scrollOffset, entries.size() - MAX_ROWS));
            selectedRow  = -1;
        }
        rebuildList();
    }

    // ── Key handling ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (awaitingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                awaitingKey = false;
                keyBindButton.setMessage(Text.literal(buildKeyLabel()));
                return true;
            }
            // Jangan capture modifier saja sebagai main key
            if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT  || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT  ||
                keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL||
                keyCode == GLFW.GLFW_KEY_LEFT_ALT     || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
                return true;
            }
            pendingKeyCode = keyCode;
            awaitingKey    = false;
            keyBindButton.setMessage(Text.literal(buildKeyLabel()));
            return true;
        }
        return super.keyPressed(input);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        // Gelap tipis di belakang
        ctx.fill(0, 0, width, height, C_OVERLAY);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        drawInventoryPanel(ctx);
        if (!editing) renderListView(ctx, mx, my);
        else          renderEditView(ctx);
        super.render(ctx, mx, my, delta);
    }

    /**
     * Gambar panel ala inventory Minecraft:
     * - Border luar raised (3D batu)
     * - Area dalam inset (lebih gelap, seperti slot area)
     * - Title bar di atas dengan teks bayangan
     */
    private void drawInventoryPanel(DrawContext ctx) {
        int x = px, y = py, w = PANEL_W, h = PANEL_H;

        // ── Layer 1: Shadow luar ─────────────────────────────────────────────
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);

        // ── Layer 2: Bodi panel abu-abu batu ─────────────────────────────────
        ctx.fill(x, y, x + w, y + h, C_OUTER);

        // ── Layer 3: Raised border 3px (atas+kiri terang, bawah+kanan gelap) ─
        // Atas terang (2px)
        ctx.fill(x, y,         x + w, y + 2,     C_OUTER_LT);
        // Kiri terang (2px)
        ctx.fill(x, y,         x + 2, y + h,     C_OUTER_LT);
        // Bawah gelap (2px)
        ctx.fill(x, y + h - 2, x + w, y + h,     C_OUTER_DK);
        // Kanan gelap (2px)
        ctx.fill(x + w - 2, y, x + w, y + h,     C_OUTER_DK);
        // Garis medium di tengah border (seperti MC)
        ctx.fill(x + 2, y + 2, x + w - 2, y + 3,     C_OUTER_MD);
        ctx.fill(x + 2, y + 2, x + 3,     y + h - 2, C_OUTER_MD);
        ctx.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, C_OUTER_MD);
        ctx.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, C_OUTER_MD);

        // ── Layer 4: Area konten dalam (inset effect) ─────────────────────────
        int ix = x + BORDER - 2, iy = y + BORDER - 2;
        int iw = w - (BORDER - 2) * 2, ih = h - (BORDER - 2) * 2;
        // Border dalam: atas+kiri gelap, bawah+kanan terang (inset/sunken look)
        ctx.fill(ix, iy,           ix + iw, iy + 2,     C_INNER_DK);
        ctx.fill(ix, iy,           ix + 2,  iy + ih,    C_INNER_DK);
        ctx.fill(ix, iy + ih - 2,  ix + iw, iy + ih,    C_INNER_LT);
        ctx.fill(ix + iw - 2, iy,  ix + iw, iy + ih,    C_INNER_LT);
        // Isi dalam
        ctx.fill(ix + 2, iy + 2, ix + iw - 2, iy + ih - 2, C_INNER);

        // ── Layer 5: Title bar ────────────────────────────────────────────────
        int ty = y + BORDER - 2;
        ctx.fill(ix + 2, ty + 2, ix + iw - 2, ty + TITLE_H + 2, 0xFF555555);
        // Garis bawah title
        ctx.fill(ix + 2, ty + TITLE_H + 2,     ix + iw - 2, ty + TITLE_H + 3, C_INNER_DK);
        ctx.fill(ix + 2, ty + TITLE_H + 3,     ix + iw - 2, ty + TITLE_H + 4, C_INNER_LT);

        // Title text (bayangan kecil, warna kuning gelap seperti chest label MC)
        String titleStr = editing
                ? (editIndex < 0 ? "Macro Baru" : "Edit Macro")
                : "Custom Macro";
        ctx.drawTextWithShadow(textRenderer, Text.literal(titleStr),
                ix + 6, ty + (TITLE_H - 8) / 2 + 2, 0xFFFFAA);

        // ── Layer 6: Garis pemisah bawah (atas area tombol bawah) ────────────
        int sepY = y + h - BORDER - BTN_H - 6;
        ctx.fill(ix + 2, sepY,     ix + iw - 2, sepY + 1, C_INNER_DK);
        ctx.fill(ix + 2, sepY + 1, ix + iw - 2, sepY + 2, C_INNER_LT);
    }

    // ── Render List ───────────────────────────────────────────────────────────
    private void renderListView(DrawContext ctx, int mx, int my) {
        int listTop = cy + 20;
        int colKey  = cx + 130;
        int colAct  = cx + 200;
        int colBtn  = cx + cw - 46;

        // Header kolom
        ctx.fill(cx, cy + 2, cx + cw, cy + 18, C_HEADER_BG);
        // Baris bawah header
        ctx.fill(cx, cy + 18, cx + cw, cy + 19, 0xFF1A1A1A);
        ctx.fill(cx, cy + 19, cx + cw, cy + 20, 0xFF9A9A9A);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama"),   cx + 4,   cy + 6, C_COL_HDR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Tombol"), colKey,   cy + 6, C_COL_HDR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi"),   colAct,   cy + 6, C_COL_HDR);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Belum ada macro. Klik + Tambah!"),
                    cx + cw / 2, listTop + MAX_ROWS * ROW_H / 2 - 4, C_EMPTY);
        } else {
            for (int i = 0; i < MAX_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MacroEntry e  = entries.get(idx);
                int rowY       = listTop + i * ROW_H;
                boolean isHov  = mx >= cx && mx <= cx + cw - 12
                              && my >= rowY && my < rowY + ROW_H;
                boolean isSel  = selectedRow == idx;

                // Row background
                int rowColor = isHov ? C_ROW_SEL : (isSel ? 0x33FFAA00 : (i % 2 == 0 ? C_ROW_ODD : C_ROW_EVEN));
                ctx.fill(cx, rowY, cx + cw - 12, rowY + ROW_H - 1, rowColor);
                // Garis tipis bawah row
                ctx.fill(cx, rowY + ROW_H - 1, cx + cw - 12, rowY + ROW_H, 0x22000000);

                int textY = rowY + (ROW_H - 8) / 2;
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getName(), 12)),
                        cx + 4, textY, C_NAME_TXT);

                String combo = e.getKeyComboDisplay(k -> keyName(k));
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(combo, 10)),
                        colKey, textY, C_KEY_TXT);

                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getAction(), 8)),
                        colAct, textY, C_ACT_TXT);
            }
        }

        // Scroll info
        if (entries.size() > MAX_ROWS) {
            int vis = Math.min(entries.size(), scrollOffset + MAX_ROWS);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal((scrollOffset + 1) + "-" + vis + "/" + entries.size()),
                    cx + cw / 2 - 15, cy + ch - BTN_H - 14, 0x888888);
        }
    }

    // ── Render Edit ───────────────────────────────────────────────────────────
    private void renderEditView(DrawContext ctx) {
        int labelX  = cx + 4;
        int fieldX  = cx + 58;
        int startY  = cy + 4;
        int rowGap  = 28;

        // Labels
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama:"),   labelX, startY + 5,         C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi:"),   labelX, startY + rowGap + 5, C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Mod:"),    labelX, startY + rowGap*2+4, C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key:"),    labelX, startY + rowGap*3+6, C_LABEL);

        // Tanda * wajib
        if (nameField   != null && nameField.getText().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), fieldX - 8, startY + 5, C_WARN);
        if (actionField != null && actionField.getText().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), fieldX - 8, startY + rowGap + 5, C_WARN);
        if (pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN)
            ctx.drawTextWithShadow(textRenderer, Text.literal("*"), fieldX - 8, startY + rowGap*3+6, C_WARN);

        // Hint awaiting key
        if (awaitingKey) {
            ctx.fill(cx, cy + ch - 36, cx + cw, cy + ch - 26, 0x88FF8800);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Tekan tombol... (ESC = batal)"),
                    cx + cw / 2, cy + ch - 34, 0xFFDD00);
        }

        // Info combo yang sudah dipilih
        if (!awaitingKey && pendingKeyCode != GLFW.GLFW_KEY_UNKNOWN) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("▶ " + buildKeyLabel()),
                    cx + 4, cy + ch - 36, 0x88FFFF);
        }
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
            case GLFW.GLFW_KEY_UP            -> "↑";
            case GLFW.GLFW_KEY_DOWN          -> "↓";
            case GLFW.GLFW_KEY_LEFT          -> "←";
            case GLFW.GLFW_KEY_RIGHT         -> "→";
            case GLFW.GLFW_KEY_ESCAPE        -> "ESC";
            case GLFW.GLFW_KEY_DELETE        -> "DEL";
            case GLFW.GLFW_KEY_INSERT        -> "INS";
            case GLFW.GLFW_KEY_HOME          -> "HOME";
            case GLFW.GLFW_KEY_END           -> "END";
            case GLFW.GLFW_KEY_PAGE_UP       -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN     -> "PGDN";
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
            default -> "K" + k;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    // ── Mouse click untuk select row ──────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!editing && button == 0) {
            int listTop = cy + 20;
            for (int i = 0; i < MAX_ROWS; i++) {
                int rowY = listTop + i * ROW_H;
                if (mx >= cx && mx <= cx + cw - 12 && my >= rowY && my < rowY + ROW_H) {
                    selectedRow = i + scrollOffset;
                    break;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ── Scroll wheel ─────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (!editing) {
            if (vAmount < 0) scrollOffset = Math.min(Math.max(0, entries.size() - MAX_ROWS), scrollOffset + 1);
            else             scrollOffset = Math.max(0, scrollOffset - 1);
            selectedRow = -1;
            rebuildList();
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
