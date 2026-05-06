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

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_W   = 380;
    private static final int PANEL_H   = 280;
    // Title bar height (at top of panel, inside border)
    private static final int TITLE_H   = 18;
    // Thick border around the whole panel
    private static final int BORDER    = 8;
    // Column header height
    private static final int HEADER_H  = 16;
    // Row height in list view
    private static final int ROW_H     = 22;
    private static final int MAX_ROWS  = 7;
    // Button height
    private static final int BTN_H     = 20;

    // List view column X offsets (relative to cx)
    private static final int COL_NAME   = 4;
    private static final int COL_KEY    = 140;
    private static final int COL_ACT    = 220;
    // Buttons start 44px from right edge
    private static final int COL_BTNS_R = 44;  // distance from cx+cw

    // Edit view row Y offsets (relative to content top = cy)
    private static final int EDIT_ROW_H = 26;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int C_OVERLAY   = 0xC0101010;
    private static final int C_PANEL     = 0xFF8B8B8B;
    private static final int C_PANEL_LT  = 0xFFCCCCCC;
    private static final int C_PANEL_DK  = 0xFF373737;
    private static final int C_PANEL_MD  = 0xFF5A5A5A;
    private static final int C_CONTENT   = 0xFF636363;
    private static final int C_EDGE_DK   = 0xFF1A1A1A;
    private static final int C_EDGE_LT   = 0xFF9A9A9A;
    private static final int C_TITLE_BG  = 0xFF4A4A4A;
    private static final int C_HEADER_BG = 0x99000000;
    private static final int C_ROW_ODD   = 0x22000000;
    private static final int C_ROW_EVEN  = 0x11FFFFFF;
    private static final int C_ROW_HOV   = 0x44FFDD00;
    private static final int C_ROW_SEL   = 0x33FFAA00;
    private static final int C_HDR_TXT   = 0xFFFFAA;
    private static final int C_NAME_TXT  = 0xFFFFFF;
    private static final int C_KEY_TXT   = 0xFFDD44;
    private static final int C_ACT_TXT   = 0xAAFFAA;
    private static final int C_LABEL     = 0xDDDDDD;
    private static final int C_EMPTY     = 0x888888;
    private static final int C_WARN      = 0xFF5555;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private List<MacroEntry> entries;

    // View state
    private boolean editing   = false;
    private int     editIndex = -1;
    private int     scrollOffset = 0;
    private int     selectedRow  = -1;

    // Edit form state
    private TextFieldWidget nameField;
    private TextFieldWidget actionField;
    private ButtonWidget    keyBindButton;
    private int     pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean pendingShift   = false;
    private boolean pendingCtrl    = false;
    private boolean pendingAlt     = false;
    private boolean awaitingKey    = false;

    private MacroEntry.ActionType pendingActionType = MacroEntry.ActionType.CHAT;
    private String  pendingSlot  = "";
    private String  pickedItemA  = "";
    private String  pickedItemB  = "";

    private ButtonWidget shiftToggle, ctrlToggle, altToggle;

    // ── Computed layout (recalculated in init) ────────────────────────────────
    // px,py = top-left of panel
    private int px, py;
    // Content area rectangle (inside border + title bar)
    private int cx, cy, cw, ch;
    // Top of the list rows (below column header)
    private int listTop;

    // ──────────────────────────────────────────────────────────────────────────
    public MacroManagerScreen(Screen parent) {
        super(Text.translatable("custommacro.title"));
        this.parent  = parent;
        this.entries = new ArrayList<>(MacroConfig.getMacros());
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        // Centre panel
        px = (width  - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        // Content area starts after border + title bar
        cx = px + BORDER;
        cy = py + BORDER + TITLE_H;   // just below title bar
        cw = PANEL_W - BORDER * 2;
        ch = PANEL_H - BORDER * 2 - TITLE_H;

        // List rows start after the column header
        listTop = cy + HEADER_H;

        clearChildren();
        if (!editing) initListView();
        else          initEditView();
    }

    // ─── LIST VIEW ────────────────────────────────────────────────────────────
    private void initListView() {
        int bottomY = py + PANEL_H - BORDER - BTN_H - 2;

        // Buttons for each visible entry
        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            int rowY = listTop + i * ROW_H;
            int btnY = rowY + (ROW_H - 16) / 2;
            final int fi = idx;

            addDrawableChild(ButtonWidget.builder(Text.literal("✎"),
                    btn -> openEdit(fi)
            ).dimensions(cx + cw - COL_BTNS_R, btnY, 20, 16).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                    btn -> deleteEntry(fi)
            ).dimensions(cx + cw - COL_BTNS_R + 22, btnY, 20, 16).build());
        }

        // Scroll arrows only when needed
        if (entries.size() > MAX_ROWS) {
            int sbX = cx + cw - 10;
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                selectedRow  = -1; rebuildList();
            }).dimensions(sbX, listTop, 10, 10).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
                scrollOffset = Math.min(entries.size() - MAX_ROWS, scrollOffset + 1);
                selectedRow  = -1; rebuildList();
            }).dimensions(sbX, listTop + MAX_ROWS * ROW_H - 10, 10, 10).build());
        }

        // Bottom buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Tambah"),
                btn -> openEdit(-1)
        ).dimensions(cx, bottomY, 80, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Tutup"),
                btn -> close()
        ).dimensions(cx + cw - 62, bottomY, 62, BTN_H).build());
    }

    private void rebuildList() { clearChildren(); initListView(); }

    // ─── EDIT VIEW ────────────────────────────────────────────────────────────
    private void initEditView() {
        // Left label column width = 56px, field starts at cx+56
        int labelW  = 56;
        int fieldX  = cx + labelW;
        int fieldW  = cw - labelW - 4;
        int startY  = cy + 6;
        int bottomY = py + PANEL_H - BORDER - BTN_H - 2;

        // Restore from existing entry
        if (editIndex >= 0 && editIndex < entries.size()) {
            MacroEntry e = entries.get(editIndex);
            if (pendingSlot.isEmpty() && e.isSwapAction()) {
                String[] sp = e.getAction().split("\\|", 3);
                if (sp.length >= 1) pendingSlot  = sp[0].trim();
                if (sp.length >= 2) pickedItemA  = sp[1].trim();
                if (sp.length >= 3) pickedItemB  = sp[2].trim();
            }
            pendingActionType = e.getActionType();
        }

        // ── Row 0: Nama ──────────────────────────────────────────────────────
        int row0Y = startY;
        nameField = new TextFieldWidget(textRenderer, fieldX, row0Y, fieldW, 18, Text.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("Nama macro..."));
        if (editIndex >= 0 && editIndex < entries.size())
            nameField.setText(entries.get(editIndex).getName());
        addDrawableChild(nameField);

        // ── Row 1: Aksi ──────────────────────────────────────────────────────
        int row1Y = startY + EDIT_ROW_H;
        int typeW = 60;
        addDrawableChild(ButtonWidget.builder(actionTypeLabel(),
                btn -> toggleActionType()
        ).dimensions(fieldX, row1Y, typeW, 18).build());

        int afterType = fieldX + typeW + 2;
        int afterTypeW = fieldW - typeW - 2;

        if (pendingActionType == MacroEntry.ActionType.CHAT) {
            actionField = new TextFieldWidget(textRenderer, afterType, row1Y, afterTypeW, 18, Text.literal("Action"));
            actionField.setMaxLength(256);
            actionField.setPlaceholder(Text.literal("/command atau teks..."));
            if (editIndex >= 0 && editIndex < entries.size() && !entries.get(editIndex).isSwapAction())
                actionField.setText(entries.get(editIndex).getAction());
            addDrawableChild(actionField);
        } else {
            // Swap: slot + item A + item B, each with equal width
            int swapW  = afterTypeW;
            int partW  = (swapW - 4) / 3;  // 3 parts, 2 gaps of 2px each
            int slotX  = afterType;
            int itemAX = slotX  + partW + 2;
            int itemBX = itemAX + partW + 2;

            addDrawableChild(ButtonWidget.builder(slotLabel(),
                    btn -> { cycleSlot(); rebuildEdit(); }
            ).dimensions(slotX, row1Y, partW, 18).build());

            addDrawableChild(ButtonWidget.builder(itemPickerLabel(pickedItemA, "Item A"),
                    btn -> openItemPicker("Item A", stack -> {
                        pickedItemA = stack.getItem().getTranslationKey(); rebuildEdit();
                    })
            ).dimensions(itemAX, row1Y, partW, 18).build());

            addDrawableChild(ButtonWidget.builder(itemPickerLabel(pickedItemB, "Item B"),
                    btn -> openItemPicker("Item B", stack -> {
                        pickedItemB = stack.getItem().getTranslationKey(); rebuildEdit();
                    })
            ).dimensions(itemBX, row1Y, partW, 18).build());
        }

        // ── Row 2: Modifier keys ─────────────────────────────────────────────
        int row2Y = startY + EDIT_ROW_H * 2;
        if (editIndex >= 0 && editIndex < entries.size() && pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN) {
            MacroEntry e = entries.get(editIndex);
            pendingKeyCode = e.getKeyCode();
            pendingShift   = e.isModShift();
            pendingCtrl    = e.isModCtrl();
            pendingAlt     = e.isModAlt();
        }
        int modW = (fieldW - 4) / 3;

        ctrlToggle = ButtonWidget.builder(modLabel("CTRL", pendingCtrl),
                btn -> { pendingCtrl  = !pendingCtrl;  updateModButtons(); }
        ).dimensions(fieldX, row2Y, modW, 18).build();
        addDrawableChild(ctrlToggle);

        altToggle = ButtonWidget.builder(modLabel("ALT", pendingAlt),
                btn -> { pendingAlt   = !pendingAlt;   updateModButtons(); }
        ).dimensions(fieldX + modW + 2, row2Y, modW, 18).build();
        addDrawableChild(altToggle);

        shiftToggle = ButtonWidget.builder(modLabel("SHIFT", pendingShift),
                btn -> { pendingShift = !pendingShift; updateModButtons(); }
        ).dimensions(fieldX + (modW + 2) * 2, row2Y, modW, 18).build();
        addDrawableChild(shiftToggle);

        // ── Row 3: Key bind ──────────────────────────────────────────────────
        int row3Y = startY + EDIT_ROW_H * 3;
        keyBindButton = ButtonWidget.builder(Text.literal(buildKeyLabel()),
                btn -> { awaitingKey = true; keyBindButton.setMessage(Text.literal("[ Tekan tombol... ]")); }
        ).dimensions(fieldX, row3Y, fieldW, BTN_H).build();
        addDrawableChild(keyBindButton);

        // ── Bottom: Simpan / Batal ───────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("Simpan"),
                btn -> saveEdit()
        ).dimensions(cx + cw - 130, bottomY, 62, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Batal"),
                btn -> cancelEdit()
        ).dimensions(cx + cw - 66, bottomY, 66, BTN_H).build());
    }

    private void rebuildEdit() { clearChildren(); initEditView(); }

    private void cancelEdit() {
        editing = false; editIndex = -1;
        pickedItemA = ""; pickedItemB = ""; pendingSlot = "";
        pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        pendingShift = false; pendingCtrl = false; pendingAlt = false;
        pendingActionType = MacroEntry.ActionType.CHAT;
        init();
    }

    private void toggleActionType() {
        pendingActionType = (pendingActionType == MacroEntry.ActionType.CHAT)
                ? MacroEntry.ActionType.SWAP_ITEM : MacroEntry.ActionType.CHAT;
        pickedItemA = ""; pickedItemB = ""; pendingSlot = "";
        rebuildEdit();
    }

    private void updateModButtons() {
        if (ctrlToggle  != null) ctrlToggle.setMessage(modLabel("CTRL",   pendingCtrl));
        if (altToggle   != null) altToggle.setMessage(modLabel("ALT",     pendingAlt));
        if (shiftToggle != null) shiftToggle.setMessage(modLabel("SHIFT",  pendingShift));
        if (keyBindButton != null && !awaitingKey)
            keyBindButton.setMessage(Text.literal(buildKeyLabel()));
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    private void openEdit(int idx) {
        editing = true; editIndex = idx;
        pickedItemA = ""; pickedItemB = ""; pendingSlot = "";
        pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        pendingShift = false; pendingCtrl = false; pendingAlt = false;
        awaitingKey  = false;
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
        if (editIndex >= 0 && editIndex < entries.size()) entries.set(editIndex, e);
        else entries.add(e);

        MacroConfig.setMacros(entries);
        MacroConfig.save();

        editing = false; editIndex = -1;
        pickedItemA = ""; pickedItemB = ""; pendingSlot = "";
        pendingKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        pendingShift = false; pendingCtrl = false; pendingAlt = false;
        pendingActionType = MacroEntry.ActionType.CHAT;
        init();
    }

    private void deleteEntry(int idx) {
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            MacroConfig.setMacros(entries);
            MacroConfig.save();
            int maxOff = Math.max(0, entries.size() - MAX_ROWS);
            scrollOffset = Math.min(scrollOffset, maxOff);
            selectedRow = -1;
        }
        rebuildList();
    }

    private void openItemPicker(String label, java.util.function.Consumer<ItemStack> callback) {
        if (client != null)
            client.setScreen(new ItemPickerScreen(this, label, callback));
    }

    // ── Slot cycling ──────────────────────────────────────────────────────────
    private static final String[] SLOT_ORDER = {
        "head","chest","legs","feet","offhand",
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

    // ── Key handling ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (awaitingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                awaitingKey = false;
                if (keyBindButton != null) keyBindButton.setMessage(Text.literal(buildKeyLabel()));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT  || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT ||
                keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL ||
                keyCode == GLFW.GLFW_KEY_LEFT_ALT     || keyCode == GLFW.GLFW_KEY_RIGHT_ALT)
                return true;
            pendingKeyCode = keyCode;
            awaitingKey = false;
            if (keyBindButton != null) keyBindButton.setMessage(Text.literal(buildKeyLabel()));
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
        drawPanel(ctx);
        if (!editing) renderListView(ctx, mx, my);
        else          renderEditView(ctx);
        super.render(ctx, mx, my, delta);
    }

    /** Draw the outer panel frame + title bar. */
    private void drawPanel(DrawContext ctx) {
        int x = px, y = py, w = PANEL_W, h = PANEL_H;

        // Drop shadow
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000);

        // Panel body
        ctx.fill(x, y, x + w, y + h, C_PANEL);

        // Outer 3-D bevel
        ctx.fill(x,         y,         x + w,     y + 2,     C_PANEL_LT);
        ctx.fill(x,         y,         x + 2,     y + h,     C_PANEL_LT);
        ctx.fill(x,         y + h - 2, x + w,     y + h,     C_PANEL_DK);
        ctx.fill(x + w - 2, y,         x + w,     y + h,     C_PANEL_DK);

        // Title bar (inside top border)
        int titleX1 = x + BORDER;
        int titleY1 = y + BORDER;
        int titleX2 = x + w - BORDER;
        int titleY2 = titleY1 + TITLE_H;
        ctx.fill(titleX1, titleY1, titleX2, titleY2, C_TITLE_BG);
        // Title bar bottom divider
        ctx.fill(titleX1, titleY2 - 1, titleX2, titleY2,     C_EDGE_DK);

        // Title text centred
        String title = editing ? (editIndex < 0 ? "✦ Macro Baru" : "✦ Edit Macro") : "✦ Custom Macro";
        int titleTextX = titleX1 + (titleX2 - titleX1) / 2;
        int titleTextY = titleY1 + (TITLE_H - 8) / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), titleTextX, titleTextY, 0xFFFFAA);

        // Content area sunken border
        ctx.fill(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, C_EDGE_DK);
        ctx.fill(cx,     cy,     cx + cw,     cy + ch,     C_CONTENT);

        // Bottom divider (above bottom buttons)
        int sepY = py + h - BORDER - BTN_H - 5;
        ctx.fill(cx, sepY, cx + cw, sepY + 1, C_EDGE_DK);
        ctx.fill(cx, sepY + 1, cx + cw, sepY + 2, C_EDGE_LT);
    }

    // ── Render List ───────────────────────────────────────────────────────────
    private void renderListView(DrawContext ctx, int mx, int my) {
        // Column header bar
        ctx.fill(cx, cy, cx + cw, listTop, C_HEADER_BG);
        ctx.fill(cx, listTop - 1, cx + cw, listTop, C_EDGE_DK);

        int hTextY = cy + (HEADER_H - 8) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama"),   cx + COL_NAME, hTextY, C_HDR_TXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Tombol"), cx + COL_KEY,  hTextY, C_HDR_TXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi"),   cx + COL_ACT,  hTextY, C_HDR_TXT);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Belum ada macro — klik + Tambah"),
                    cx + cw / 2, listTop + (MAX_ROWS * ROW_H) / 2 - 4, C_EMPTY);
        } else {
            for (int i = 0; i < MAX_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MacroEntry e  = entries.get(idx);
                int rowY      = listTop + i * ROW_H;
                boolean isHov = mx >= cx && mx < cx + cw - COL_BTNS_R - 2
                             && my >= rowY && my < rowY + ROW_H;
                boolean isSel = selectedRow == idx;

                int bg = isHov ? C_ROW_HOV
                       : isSel ? C_ROW_SEL
                       : (i % 2 == 0 ? C_ROW_ODD : C_ROW_EVEN);
                ctx.fill(cx, rowY, cx + cw, rowY + ROW_H - 1, bg);
                ctx.fill(cx, rowY + ROW_H - 1, cx + cw, rowY + ROW_H, 0x22000000);

                int textY = rowY + (ROW_H - 8) / 2;
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getName(), 12)),
                        cx + COL_NAME, textY, C_NAME_TXT);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getKeyComboDisplay(k -> keyName(k)), 9)),
                        cx + COL_KEY, textY, C_KEY_TXT);
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(truncate(e.getAction(), 9)),
                        cx + COL_ACT, textY, C_ACT_TXT);
            }
        }

        // Scroll indicator
        if (entries.size() > MAX_ROWS) {
            int vis = Math.min(entries.size(), scrollOffset + MAX_ROWS);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal((scrollOffset + 1) + "–" + vis + "/" + entries.size()),
                    cx + cw / 2, cy + ch - BTN_H - 16, 0x888888);
        }
    }

    // ── Render Edit ───────────────────────────────────────────────────────────
    private void renderEditView(DrawContext ctx) {
        int labelX = cx + 4;
        int startY = cy + 6;

        // Row labels
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nama:"),  labelX, startY + 5,                C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Aksi:"),  labelX, startY + EDIT_ROW_H + 5,   C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Mod:"),   labelX, startY + EDIT_ROW_H*2 + 5, C_LABEL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key:"),   labelX, startY + EDIT_ROW_H*3 + 5, C_LABEL);

        // Required field indicators
        int warnX = cx + 52;
        if (nameField != null && nameField.getText().trim().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c✖"), warnX, startY + 5, C_WARN);
        if (pendingActionType == MacroEntry.ActionType.SWAP_ITEM
                && (pendingSlot.isEmpty() || pickedItemA.isEmpty() || pickedItemB.isEmpty()))
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c✖"), warnX, startY + EDIT_ROW_H + 5, C_WARN);
        if (pendingActionType == MacroEntry.ActionType.CHAT
                && actionField != null && actionField.getText().trim().isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c✖"), warnX, startY + EDIT_ROW_H + 5, C_WARN);
        if (pendingKeyCode == GLFW.GLFW_KEY_UNKNOWN)
            ctx.drawTextWithShadow(textRenderer, Text.literal("§c✖"), warnX, startY + EDIT_ROW_H*3 + 5, C_WARN);

        // Awaiting key overlay
        if (awaitingKey) {
            int hy = cy + ch - BTN_H - 22;
            ctx.fill(cx, hy, cx + cw, hy + 14, 0xAA884400);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§eTekan tombol... §7(ESC = batal)"),
                    cx + cw / 2, hy + 3, 0xFFFFFF);
        }
    }

    // ── Helper text builders ──────────────────────────────────────────────────
    private Text actionTypeLabel() {
        return pendingActionType == MacroEntry.ActionType.CHAT
                ? Text.literal("§9[CHAT]") : Text.literal("§6[SWAP]");
    }

    private Text modLabel(String name, boolean active) {
        return Text.literal(active ? "§a[" + name + "]" : "§7" + name);
    }

    private Text itemPickerLabel(String itemKey, String fallback) {
        if (itemKey == null || itemKey.isEmpty()) return Text.literal("§7+ " + fallback);
        String[] parts = itemKey.split("[.:]");
        return Text.literal("§e" + parts[parts.length - 1]);
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
                    selectedRow = idx; break;
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
            selectedRow = -1; rebuildList();
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
