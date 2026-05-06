package dev.custommacro.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI untuk pilih item dari SEMUA item Minecraft (bukan cuma inventory).
 * Fitur: search box, scroll, grid slot ala inventory MC.
 */
public class ItemPickerScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS      = 9;
    private static final int VISIBLE_ROWS = 6;
    private static final int PADDING   = 8;
    private static final int TITLE_H   = 14;
    private static final int SEARCH_H  = 18;

    private final Screen parent;
    private final Consumer<ItemStack> onPick;
    private final String label;

    // Semua item dari registry
    private final List<ItemStack> allItems = new ArrayList<>();
    // Item setelah filter search
    private final List<ItemStack> filteredItems = new ArrayList<>();

    private TextFieldWidget searchField;
    private int scrollOffset = 0;
    private int hoveredIdx   = -1;

    // Panel
    private int px, py, pw, ph;
    private int gridTop;

    public ItemPickerScreen(Screen parent, String label, Consumer<ItemStack> onPick) {
        super(Text.literal("Pilih Item"));
        this.parent = parent;
        this.label  = label;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        // Kumpulkan semua item dari Minecraft Registry (sekali saja)
        if (allItems.isEmpty()) {
            for (Item item : Registries.ITEM) {
                allItems.add(new ItemStack(item));
            }
        }

        pw = COLS * SLOT_SIZE + PADDING * 2 + 12; // +12 untuk scrollbar
        ph = TITLE_H + 4 + SEARCH_H + 4 + VISIBLE_ROWS * SLOT_SIZE + PADDING + 22;
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        gridTop = py + 2 + TITLE_H + 4 + SEARCH_H + 4;

        // Search field
        int sfX = px + PADDING;
        int sfY = py + 2 + TITLE_H + 4;
        int sfW = pw - PADDING * 2;
        searchField = new TextFieldWidget(textRenderer, sfX, sfY, sfW, SEARCH_H - 2, Text.literal("Cari..."));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal("§7Ketik nama item..."));
        searchField.setChangedListener(s -> {
            scrollOffset = 0;
            applyFilter(s);
        });
        addDrawableChild(searchField);

        // Tombol batal
        addDrawableChild(ButtonWidget.builder(Text.literal("Batal"),
                btn -> close()
        ).dimensions(px + pw / 2 - 30, py + ph - 20, 60, 16).build());

        applyFilter("");
    }

    private void applyFilter(String query) {
        filteredItems.clear();
        String q = query.toLowerCase().trim();
        for (ItemStack stack : allItems) {
            if (q.isEmpty()) {
                filteredItems.add(stack);
            } else {
                String name = stack.getName().getString().toLowerCase();
                String key  = stack.getItem().getTranslationKey().toLowerCase();
                if (name.contains(q) || key.contains(q)) {
                    filteredItems.add(stack);
                }
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Overlay gelap
        ctx.fill(0, 0, width, height, 0xB0000000);

        // Panel background
        ctx.fill(px,           py,           px + pw,     py + ph,     0xFF636363);
        ctx.fill(px,           py,           px + pw,     py + 2,      0xFFFFFFFF);
        ctx.fill(px,           py,           px + 2,      py + ph,     0xFFFFFFFF);
        ctx.fill(px,           py + ph - 2,  px + pw,     py + ph,     0xFF373737);
        ctx.fill(px + pw - 2,  py,           px + pw,     py + ph,     0xFF373737);

        // Title bar
        int ty = py + 2;
        ctx.fill(px + 2, ty, px + pw - 2, ty + TITLE_H, 0xFF555555);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Pilih " + label + " — klik item"),
                px + 6, ty + (TITLE_H - 8) / 2, 0xFFFFAA);

        // Grid area background
        int gridW = COLS * SLOT_SIZE;
        ctx.fill(px + PADDING - 1, gridTop - 1, px + PADDING + gridW + 1, gridTop + VISIBLE_ROWS * SLOT_SIZE + 1, 0xFF1A1A1A);
        ctx.fill(px + PADDING, gridTop, px + PADDING + gridW, gridTop + VISIBLE_ROWS * SLOT_SIZE, 0xFF555555);

        // Hitung item yang terlihat
        hoveredIdx = -1;
        int startItem = scrollOffset * COLS;
        int endItem   = Math.min(startItem + VISIBLE_ROWS * COLS, filteredItems.size());

        for (int i = startItem; i < endItem; i++) {
            int localIdx = i - startItem;
            int col = localIdx % COLS;
            int row = localIdx / COLS;
            int sx  = px + PADDING + col * SLOT_SIZE;
            int sy  = gridTop + row * SLOT_SIZE;

            boolean hov = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hov) hoveredIdx = i;

            // Slot border (inset)
            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF1A1A1A);
            ctx.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF1A1A1A);
            ctx.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF9A9A9A);
            ctx.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF9A9A9A);

            int slotBg = hov ? 0x88FFDD00 : 0xFF636363;
            ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, slotBg);

            ctx.drawItem(filteredItems.get(i), sx + 1, sy + 1);
            ctx.drawItemInSlot(textRenderer, filteredItems.get(i), sx + 1, sy + 1);
        }

        // Scrollbar
        int sbX     = px + PADDING + COLS * SLOT_SIZE + 2;
        int sbH     = VISIBLE_ROWS * SLOT_SIZE;
        int totalRows = (int) Math.ceil(filteredItems.size() / (double) COLS);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        ctx.fill(sbX, gridTop, sbX + 8, gridTop + sbH, 0xFF3A3A3A);
        if (maxScroll > 0) {
            int thumbH  = Math.max(10, sbH * VISIBLE_ROWS / totalRows);
            int thumbY  = gridTop + (sbH - thumbH) * scrollOffset / maxScroll;
            ctx.fill(sbX + 1, thumbY, sbX + 7, thumbY + thumbH, 0xFFAAAAAA);
        }

        // Kosong hint
        if (filteredItems.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§7Tidak ada item ditemukan"),
                    px + pw / 2, gridTop + VISIBLE_ROWS * SLOT_SIZE / 2 - 4, 0x888888);
        }

        // Info jumlah item
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("§7" + filteredItems.size() + " item"),
                px + PADDING, py + ph - 30, 0x888888);

        // Tooltip hover
        if (hoveredIdx >= 0 && hoveredIdx < filteredItems.size()) {
            ItemStack stack = filteredItems.get(hoveredIdx);
            ctx.drawItemTooltip(textRenderer, stack, mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            int startItem = scrollOffset * COLS;
            int endItem   = Math.min(startItem + VISIBLE_ROWS * COLS, filteredItems.size());
            for (int i = startItem; i < endItem; i++) {
                int localIdx = i - startItem;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int sx  = px + PADDING + col * SLOT_SIZE;
                int sy  = gridTop + row * SLOT_SIZE;
                if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                    onPick.accept(filteredItems.get(i).copy());
                    close();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int totalRows = (int) Math.ceil(filteredItems.size() / (double) COLS);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (vAmount < 0) scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        else             scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }
}
