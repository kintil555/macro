package dev.custommacro.gui;

import dev.custommacro.config.MacroConfig;
import dev.custommacro.config.MacroEntry;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MacroManagerScreen extends Screen {

    // ── Panel size ────────────────────────────────────────────────────────────
    private static final int PANEL_W  = 360;
    private static final int PANEL_H  = 280;
    private static final int TITLE_H  = 16;
    private static final int ROW_H    = 24;
    private static final int MAX_ROWS = 6;
    private static final int BTN_H    = 20;
    private static final int BORDER   = 8;

    // Header kolom tinggi
    private static final int HEADER_H = 18;

    // Kolom X positions (relatif terhadap cx)
    private static final int COL_NAME   = 4;
    private static final int COL_KEY    = 130;
    private static final int COL_ACTION = 210;
    private static final int COL_BTNS   = 290; // edit + del buttons

    // ── Warna ─────────────────────────────────────────────────────────────────
    private static final int C_OVERLAY   = 0xC0101010;
    private static final int C_OUTER     = 0xFF8B8B8B;
    private static final int C_OUTER_LT  = 0xFFFFFFFF;
    private static final int C_OUTER_DK  = 0xFF373737;
    private static final int C_OUTER_MD  = 0xFF5A5A5A;
    private static final int C_INNER     = 0xFF636363;
    private static final int C_INNER_DK  = 0xFF1A1A1A;
    private static final int C_INNER_LT  = 0xFF9A9A9A;
    private static final int C_HEADER_BG = 0x88000000;
    private static final int C_ROW_ODD   = 0x33000000;
    private static final int C_ROW_EVEN  = 0x11FFFFFF;
    private static final int C_ROW_HOV   = 0x44FFDD00;
    private static final int C_ROW_SEL   = 0x33FFAA00;
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

    // Action type + swap fields
    private MacroEntry.ActionType pendingActionType = MacroEntry.ActionType.CHAT;
    private String  pendingSlot  = "";
    private String  pickedItemA  = "";
    private String  pickedItemB  = "";

    // Modifier toggles
    private ButtonWidget shiftToggle;
    private ButtonWidget ctrlToggle;
    private ButtonWidget altToggle;

    // Scroll & selection
    private int scrollOffset = 0;
    private int selectedRow  = -1;

    // Panel origin
    private int px, py;
    // Content area (di dalam border)
    private int cx, cy, cw, ch;
    // List area (di bawah header)
    private int listTop;

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
        cx = px + BORDER;
        cy = py + BORDER + TITLE_H + 4;
        cw = PANEL_W - BORDER * 2;
        ch = PANEL_H - BORDER * 2 - TITLE_H - 4;
        listTop = cy + HEADER_H;

        clearChildren();
        if (!editing) initListView();
        else          initEditView();
    }

    // ─── LIST VIEW ────────────────────────────────────────────────────────────
    private void initListView() {
        int bottomY = cy + ch - BTN_H - 4;

        // Tombol Edit & Del — hanya untuk rows yang punya data
        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            int rowY = listTop + i * ROW_H;
            int btnY = rowY + (ROW_H - 16) / 2;
            final int fi = idx;

            addDrawableChild(ButtonWidget.builder(Text.literal("✎"),
                    btn -> openEdit(fi)
            ).dimensions(cx + COL_BTNS, btnY, 20, 16).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                    btn -> deleteEntry(fi)
            ).dimensions(cx + COL_BTNS + 22, btnY, 20, 16).build());
        }

        // Scroll arrows — hanya kalau entries > MAX_ROWS
        if (entries.size() > MAX_ROWS) {
            int sbX = cx + cw - 12;
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                selectedRow = -1;
                rebuildList();
            }).dimensions(sbX, listTop, 12, 12).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
                scrollOffset = Math.min(Math.max(0, entries.size() - MAX_ROWS), scrollOffset + 1);
                selectedRow = -1;
                rebuildList();
            }).dimensions(sbX, listTop + MAX_ROWS * ROW_H - 12, 12, 12).build());
        }

        // Bottom buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Tambah"),
                btn -> openEdit(-1)
        ).dimensions(cx, bottomY, 80, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Tutup"),
                btn -> close()
        ).dimensions(cx + cw - 62, bottomY, 62, BTN_H).build());
    }

    private void rebuildList() {
        clearChildren();
        initListView();
    }

    // ─── EDIT VIEW ────────────────────────────────────────────────────────────
    private void initEditView() {
        int labelX  = cx + 4;
        int fieldX  = cx + 60;
        int fieldW  = cw - 64;
        int startY  = cy + 4;
        int rowGap  = 28;

        // Restore dari entry yang diedit
        if (editIndex >= 0) {
            pendingActionType = entries.get(editIndex).getActionType();
            if (entries.get(editIndex).isSwapAction()) {
                String[] sp = entries.get(editIndex).getAction().split("\\|", 3);
                if (sp.length >= 1 && pendingSlot.isEmpty())  pendingSlot  = sp[0].trim();
                if (sp.length >= 2 && pickedItemA.isEmpty())  pickedItemA  = sp[1].trim();
                if (sp.length >= 3 && pickedItemB.isEmpty())  pickedItemB  = sp[2].trim();
            }
        }

        // ── Row 0: Nama ──
        nameField = new TextFieldWidget(textRenderer, fieldX, startY, fieldW, 18, Text.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("Nama macro..."));
        if (editIndex >= 0) nameField.setText(entries.get(editIndex).getName());
        addDrawableChild(nameField);

        // ── Row 1: Tipe Aksi + Aksi ──
        int typeY = startY + rowGap;
        int typeW = 68;
        addDrawableChild(ButtonWidget.builder(actionTypeLabel(),
                btn -> toggleActionType()
        ).dimensions(fieldX, typeY, typeW, 18).build());

        if (pendingActionType == MacroEntry.ActionType.CHAT) {
            actionField = new TextFieldWidget(textRenderer, fieldX + typeW + 2, typeY, fieldW - typeW - 2, 18, Text.literal("Action"));
            actionField.setMaxLength(256);
            actionField.setPlaceholder(Text.literal("/command atau teks..."));
            if (editIndex >= 0 && !entries.get(editIndex).isSwapAction())
                actionField.setText(entries.get(editIndex).getAction());
            addDrawableChild(actionField);
        } else {
            // Swap: tombol slot + item A + item B
            int slotW  = 62;
            int remain = fieldW - typeW - 2 - slotW - 2;
            int itemW  = remain / 2 - 1;
            int slotX  = fieldX + typeW + 2;
            int itemAX = slotX + slotW + 2;
            int itemBX = itemAX + itemW + 2;

            addDrawableChild(ButtonWidget.builder(slotLabel(),
                    btn -> { cycleSlot(); rebuildEdit(); }
            ).dimensions(slotX, typeY, slotW, 18).build());

            addDrawableChild(ButtonWidget.builder(itemPickerLabel(pickedItemA, "Item A"),
                    btn -> openItemPicker("Item A", stack -> {
                        pickedItemA = stack.getItem().getTranslationKey();
                        rebuildEdit();
                    })
            ).dimensions(itemAX, typeY, itemW, 18).build());

            addDrawableChild(ButtonWidget.builder(itemPickerLabel(pickedItemB, "Item B"),
                    btn -> openItemPicker("Item B", stack -> {
                        pickedItemB = stack.getItem().getTranslationKey();
                        rebuildEdit();
                    })
            ).dimensions(itemBX, typeY, itemW, 18).build());
        }

        // ── Row 2: Modifier keys ──
        int modY  = startY + rowGap * 2;
        int modW  = 50;
        pendingKeyCode = (editIndex >= 0) ? entries.get(editIndex).getKeyCode()  : GLFW.GLFW_KEY_UNKNOWN;
        pendingShift   = (editIndex >= 0) && entries.get(editIndex).isModShift();
        pendingCtrl    = (editIndex >= 0) && entries.get(editIndex).isModCtrl();
        pendingAlt     = (editIndex >= 0) && entries.get(editIndex).isModAlt();

        ctrlToggle = ButtonWidget.builder(modLabel("CTRL", pendingCtrl),
                btn -> { pendingCtrl  = !pendingCtrl;  updateModButtons(); }
        ).dimensions(fieldX, modY, modW, 18).build();
        addDrawableChild(ctrlToggle);

        altToggle = ButtonWidget.builder(modLabel("ALT", pendingAlt),
                btn -> { pendingAlt   = !pendingAlt;   updateModButtons(); }
        ).dimensions(fieldX + modW + 2, modY, modW, 18).build();
        addDrawableChild(altToggle);

        shiftToggle = ButtonWidget.builder(modLabel("SHIFT", pendingShift),
                btn -> { pendingShift = !pendingShift; updateModButtons(); }
        ).dimensions(fieldX + (modW + 2) * 2, modY, modW, 18).build();
        addDrawableChild(shiftToggle);

        // ── Row 3: Key bind ──
        int keyY = startY + rowGap * 3;
        keyBindButton = ButtonWidget.builder(Text.literal(buildKeyLabel()),
                btn -> { awaitingKey = true; keyBindButton.setMessage(Text.literal("[ Tekan tombol... ]")); }
        ).dimensions(fieldX, keyY, fieldW, BTN_H).build();
        addDrawableChild(keyBindButton);

        // ── Bottom: Simpan / Batal ──
        int bottomY = cy + ch - BTN_H - 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Simpan"),
                btn -> saveEdit()
        ).dimensions(cx + cw - 126, bottomY, 60, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Batal"),
                btn -> cancelEdit()
        ).dimensions(cx + cw - 64, bottomY, 64, BTN_H).build());
    }

    private void cancelEdit() {
        editing       = false;
        editIndex     = -1;
        pickedItemA   = "";
        pickedItemB   = "";
        pendingSlot   = "";
        pendingActionType = MacroEntry.ActionType.CHAT;
        init();
    }

    private void rebuildEdit() {
        clearChildren();
        initEditView();
    }

    private void toggleActionType() {
        pendingActionType = (pendingActionType == MacroEntry.ActionType.CHAT)
                ? MacroEntry.ActionType.SWAP_ITEM : MacroEntry.ActionType.CHAT;
        pickedItemA = "";
        pickedItemB = "";
        pendingSlot  = "";
        clearChildren();
        initEditView();
    }

    private void updateModButtons() {
        if (ctrlToggle  != null) ctrlToggle.setMessage(modLabel("CTRL",  pendingCtrl));
        if (altToggle   != null) altToggle.setMessage(modLabel("ALT",    pendingAlt));
        if (shiftToggle != null) shiftToggle.setMessage(modLabel("SHIFT", pendingShift));
        if (keyBindButton != null && !awaitingKey)
            keyBindButton.setMessage(Text.literal(buildKeyLabel()));
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    private void openEdit(int idx) {
        editing       = true;
        editIndex     = idx;
        pickedItemA   = "";
        pickedItemB   = "";
        pendingSlot   = "";
        pendingActionType = MacroEntry.ActionType.CHAT;
        init();
    }

    private void saveEdit() {
        if (nameField == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty() || pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) return;

        String action;
        if (pendingActionType == MacroEntry.ActionType.SWAP_ITEM) {
            if (pendingSlot.isEmpty() || pickedItemA.isEmpty() || pickedItemB.isEmpty()) return;
            action = pendingSlot + "|" + pickedItemA + "|" + pickedItemB;
        } else {
            if (actionField == null) return;
            action = actionField.getText().trim();
            if (action.isEmpty()) return;
        }

        MacroEntry e = new MacroEntry(name, pendingKeyCode, pendingShift, pendingCtrl, pendingAlt, action);
        e.setActionType(pendingActionType);
        if (editIndex >= 0 && editIndex < entries.size()) {
            entries.set(editIndex, e);
        } else {
            entries.add(e);
        }
        MacroConfig.setMacros(entries);
        MacroConfig.save();

        editing       = false;
        editIndex     = -1;
        pickedItemA   = "";
        pickedItemB   = "";
        pendingSlot   = "";
        init();
    }

    private void deleteEntry(int idx) {
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            MacroConfig.setMacros(entries);
            MacroConfig.save();
            scrollOffset = Math.max(0, Math.min(scrollOffset, entries.size() - MAX_ROWS));
            if (scrollOffset < 0) scrollOffset = 0;
            selectedRow  = -1;
        }
        rebuildList();
    }

    // ── Slot cycling ──────────────────────────────────────────────────────────
    private static final String[] SLOT_ORDER = {
        "head", "chest", "legs", "feet", "offhand",
        "hotbar0","hotbar1","hotbar2","hotbar3","hotbar4",
        "hotbar5","hotbar6","hotbar7","hotbar8"
    };

    private Text slotLabel() {
        return pendingSlot.isEmpty()
                ? Text.literal("§7[Slot]")
                : Text.literal("§b" + pendingSlot);
    }

    private void cycleSlot() {
        if (pendingSlot.isEmpty()) { pendingSlot = SLOT_ORDER[0]; return; }
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            if (SLOT_ORDER[i].equals(pendingSlot)) {
                pendingSlot = SLOT_ORDER[(i + 1) % SLOT_ORDER.length];
                return;
            }
        }
        pendingSlot = SLOT_ORDER[0];
    }

    // ── Item Picker ───────────────────────────────────────────────────────────
    private Text itemPickerLabel(String itemKey, String fallback) {
        if (itemKey == null || itemKey.isEmpty()) return Text.literal("§7+ " + fallback);
        String[] parts = itemKey.split("[.:]");
        return Text.literal("§e" + parts[parts.length - 1]);
    }

    private void openItemPicker(String label, java.util.function.Consumer<ItemStack> callback) {
        if (client != null)
            client.setScreen(new ItemPickerScreen(this, label, callback));
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
            if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT  || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT  ||
                keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL||
                keyCode == GLFW.GLFW_KEY_LEFT_ALT     || keyCode == GLFW.GLFW_KEY_RIGHT_ALT)
                return true;
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

    private void drawInventoryPanel(DrawContext ctx) {
        int x = px, y = py, w = PANEL_W, h = PANEL_H;

        // Shadow
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
        // Body
        ctx.fill(x, y, x + w, y + h, C_OUTER);
        // Border 3D
        ctx.fill(x,     y,         x + w, y + 2,     C_OUTER_LT);
        ctx.fill(x,     y,         x + 2, y + h,     C_OUTER_LT);
        ctx.fill(x,     y + h - 2, x + w, y + h,     C_OUTER_DK);
        ctx.fill(x + w - 2, y,     x + w, y + h,     C_OUTER_DK);
        ctx.fill(x + 2, y + 2,     x + w - 2, y + 3, C_OUTER_MD);
        ctx.fill(x + 2, y + 2,     x + 3, y + h - 2, C_OUTER_MD);

        // Inner content area (inset)
        int ix = x + BORDER - 2, iy = y + BORDER - 2;
        int iw = w - (BORDER - 2) * 2, ih = h - (BORDER - 2) * 2;
        ctx.fill(ix, iy,          ix + iw, iy + 2,    C_INNER_DK);
        ctx.fill(ix, iy,          ix + 2,  iy + ih,   C_INNER_DK);
        ctx.fill(ix, iy + ih - 2, ix + iw, iy + ih,   C_INNER_LT);
        ctx.fill(ix + iw - 2, iy, ix + iw, iy + ih,   C_INNER_LT);
        ctx.fill(ix + 2, iy + 2, ix + iw - 2, iy + ih - 2, C_INNER);

        // Title bar
        int ty = y + BORDER - 2;
        ctx.fill(ix + 2, ty + 2, ix + iw - 2, ty + TITLE_H + 2, 0xFF555555);
        ctx.fill(ix + 2, ty + TITLE_H + 2, ix + iw - 2, ty + TITLE_H + 3, C_INNER_DK);
        ctx.fill(ix + 2, ty + TITLE_H + 3, ix + iw - 2, ty + TITLE_H + 4, C_INNER_LT);
        String titleStr = editing ? (editIndex < 0 ? "Macro Baru" : "Edit Macro") : "Custom Macro";
        ctx.drawTextWithShadow(textRenderer, Text.literal(titleStr),
                ix + 6, ty + (TITLE_H - 8) / 2 + 2, 0xFFFFAA);

        // Bottom separator
        int sepY = y + h - BORDER - BTN_H - 8;
        ctx.fill(ix + 2, sepY,     ix + iw - 2, sepY + 1, C_INNER_DK);
        ctx.fill(ix + 2, sepY + 1, ix + iw - 2, sepY + 2, C_INNER_LT);
    }

    // ── Render List ───────────────────────────────────────────────────────────
    private void renderListView(DrawContext ctx, int mx, int my) {
        // Header background
        ctx.fill(cx, cy, cx + cw, cy + HEADER_H, C_HEADER_BG);
        ctx.fill(cx, cy + HEADER_H - 1, cx + cw, cy + HEADER_H, 0xFF1A1A1A);

        // Header labels
        int hTextY = cy + (HEADER_H - 8) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama"),   cx + COL_NAME,   hTextY, C_COL_HDR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Tombol"), cx + COL_KEY,    hTextY, C_COL_HDR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi"),   cx + COL_ACTION, hTextY, C_COL_HDR);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Belum ada macro — klik + Tambah"),
                    cx + cw / 2, listTop + MAX_ROWS * ROW_H / 2, C_EMPTY);
        } else {
            for (int i = 0; i < MAX_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MacroEntry e   = entries.get(idx);
                int rowY       = listTop + i * ROW_H;
                boolean isHov  = mx >= cx && mx < cx + cw && my >= rowY && my < rowY + ROW_H;
                boolean isSel  = selectedRow == idx;

                int rowBg = isHov ? C_ROW_HOV : (isSel ? C_ROW_SEL : (i % 2 == 0 ? C_ROW_ODD : C_ROW_EVEN));
                ctx.fill(cx, rowY, cx + cw, rowY + ROW_H - 1, rowBg);
                ctx.fill(cx, rowY + ROW_H - 1, cx + cw, rowY + ROW_H, 0x22000000);

                int textY = rowY + (ROW_H - 8) / 2;
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getName(), 10)),
                        cx + COL_NAME, textY, C_NAME_TXT);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getKeyComboDisplay(k -> keyName(k)), 9)),
                        cx + COL_KEY, textY, C_KEY_TXT);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getAction(), 8)),
                        cx + COL_ACTION, textY, C_ACT_TXT);
            }
        }

        // Scroll info
        if (entries.size() > MAX_ROWS) {
            int vis = Math.min(entries.size(), scrollOffset + MAX_ROWS);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal((scrollOffset + 1) + "–" + vis + "/" + entries.size()),
                    cx + cw / 2, cy + ch - BTN_H - 18, 0x888888);
        }
    }

    // ── Render Edit ───────────────────────────────────────────────────────────
    private void renderEditView(DrawContext ctx) {
        int labelX = cx + 4;
        int fieldX = cx + 60;
        int startY = cy + 4;
        int rowGap = 28;

        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama:"),  labelX, startY + 5,         C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi:"),  labelX, startY + rowGap + 5, C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Mod:"),   labelX, startY + rowGap*2+5, C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key:"),   labelX, startY + rowGap*3+5, C_LABEL);

        // Validasi wajib
        if (nameField != null && nameField.getText().trim().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c*"), fieldX - 9, startY + 5, C_WARN);
        if (pendingActionType == MacroEntry.ActionType.CHAT && actionField != null && actionField.getText().trim().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c*"), fieldX - 9, startY + rowGap + 5, C_WARN);
        if (pendingActionType == MacroEntry.ActionType.SWAP_ITEM) {
            if (pendingSlot.isEmpty() || pickedItemA.isEmpty() || pickedItemB.isEmpty())
                ctx.drawTextWithShadow(textRenderer, Text.literal("§c*"), fieldX - 9, startY + rowGap + 5, C_WARN);
        }
        if (pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN)
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c*"), fieldX - 9, startY + rowGap*3+5, C_WARN);

        // Hint awaiting key
        if (awaitingKey) {
            int hy = cy + ch - BTN_H - 26;
            ctx.fill(cx, hy, cx + cw, hy + 12, 0x88FF8800);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Tekan tombol... (ESC = batal)"),
                    cx + cw / 2, hy + 2, 0xFFDD00);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Text actionTypeLabel() {
        return pendingActionType == MacroEntry.ActionType.CHAT
                ? Text.literal("§9[CHAT]") : Text.literal("§6[SWAP]");
    }

    private Text modLabel(String name, boolean active) {
        return Text.literal(active ? "§a[" + name + "]" : "§7" + name);
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

    private static String keyName(int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return "?";
        String raw = GLFW.glfwGetKeyName(k, 0);
        if (raw != null && !raw.isEmpty()) return raw.toUpperCase();
        return switch (k) {
            case GLFW.GLFW_KEY_SPACE     -> "SPACE";
            case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
            case GLFW.GLFW_KEY_ENTER     -> "ENTER";
            case GLFW.GLFW_KEY_TAB       -> "TAB";
            case GLFW.GLFW_KEY_UP        -> "↑";
            case GLFW.GLFW_KEY_DOWN      -> "↓";
            case GLFW.GLFW_KEY_LEFT      -> "←";
            case GLFW.GLFW_KEY_RIGHT     -> "→";
            case GLFW.GLFW_KEY_ESCAPE    -> "ESC";
            case GLFW.GLFW_KEY_DELETE    -> "DEL";
            case GLFW.GLFW_KEY_INSERT    -> "INS";
            case GLFW.GLFW_KEY_HOME      -> "HOME";
            case GLFW.GLFW_KEY_END       -> "END";
            case GLFW.GLFW_KEY_PAGE_UP   -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_F1  -> "F1";  case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";  case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";  case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";  case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";  case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "K" + k;
        };
    }

    private static String truncate(String s, int maxChars) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > maxChars ? s.substring(0, maxChars - 1) + "…" : s;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!editing && click.button() == 0) {
            for (int i = 0; i < MAX_ROWS; i++) {
                int idx  = i + scrollOffset;
                if (idx >= entries.size()) break;
                int rowY = listTop + i * ROW_H;
                if (click.x() >= cx && click.x() < cx + cw
                 && click.y() >= rowY && click.y() < rowY + ROW_H) {
                    selectedRow = idx;
                    break;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

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
