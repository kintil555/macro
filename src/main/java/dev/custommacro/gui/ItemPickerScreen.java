package dev.custommacro.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI untuk pilih item dari inventory player.
 * Tampil sebagai grid slot ala inventory Minecraft.
 * Setelah klik item, memanggil callback dengan item yang dipilih,
 * lalu kembali ke parent screen.
 */
public class ItemPickerScreen extends Screen {

    private static final int SLOT_SIZE   = 18;
    private static final int COLS        = 9;
    private static final int PADDING     = 8;
    private static final int TITLE_H     = 14;

    private final Screen parent;
    private final Consumer<ItemStack> onPick;
    private final String label; // "Item A" atau "Item B"

    // Snapshot semua item di inventory saat screen dibuka
    private final List<SlotEntry> slots = new ArrayList<>();

    // Hover
    private int hoveredIdx = -1;

    // Panel
    private int px, py, pw, ph;

    public ItemPickerScreen(Screen parent, String label, Consumer<ItemStack> onPick) {
        super(Text.literal("Pilih Item"));
        this.parent = parent;
        this.label  = label;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        slots.clear();
        if (client != null && client.player != null) {
            PlayerInventory inv = client.player.getInventory();
            // Kumpulkan semua slot non-empty: hotbar (0-8), main (9-35), armor (36-39), offhand (40)
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    slots.add(new SlotEntry(i, stack.copy()));
                }
            }
        }

        int rows = (int) Math.ceil(slots.size() / (double) COLS);
        if (rows == 0) rows = 1;

        pw = COLS * SLOT_SIZE + PADDING * 2;
        ph = rows * SLOT_SIZE + PADDING * 2 + TITLE_H + 4 + 22; // +22 untuk tombol batal
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        // Tombol batal
        addDrawableChild(ButtonWidget.builder(Text.literal("Batal"),
                btn -> close()
        ).dimensions(px + pw / 2 - 30, py + ph - 20, 60, 16).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Overlay gelap
        ctx.fill(0, 0, width, height, 0xB0000000);

        // Panel background
        ctx.fill(px,     py,      px + pw,     py + ph,     0xFF636363);
        ctx.fill(px,     py,      px + pw,     py + 2,      0xFFFFFFFF);
        ctx.fill(px,     py,      px + 2,      py + ph,     0xFFFFFFFF);
        ctx.fill(px,     py + ph - 2, px + pw, py + ph,    0xFF373737);
        ctx.fill(px + pw - 2, py, px + pw,     py + ph,    0xFF373737);

        // Title bar
        int ty = py + 2;
        ctx.fill(px + 2, ty, px + pw - 2, ty + TITLE_H, 0xFF555555);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Pilih " + label + " — klik item"),
                px + 6, ty + (TITLE_H - 8) / 2, 0xFFFFAA);

        // Grid separator
        int gridTop = py + 2 + TITLE_H + 4;
        ctx.fill(px + 2, gridTop - 2, px + pw - 2, gridTop - 1, 0xFF1A1A1A);
        ctx.fill(px + 2, gridTop - 1, px + pw - 2, gridTop,     0xFF9A9A9A);

        hoveredIdx = -1;

        for (int i = 0; i < slots.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int sx  = px + PADDING + col * SLOT_SIZE;
            int sy  = gridTop + PADDING / 2 + row * SLOT_SIZE;

            boolean hov = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hov) hoveredIdx = i;

            // Slot background (inset)
            int slotColor = hov ? 0x88FFDD00 : 0xFF555555;
            ctx.fill(sx,               sy,               sx + SLOT_SIZE,     sy + 1,           0xFF1A1A1A);
            ctx.fill(sx,               sy,               sx + 1,             sy + SLOT_SIZE,    0xFF1A1A1A);
            ctx.fill(sx,               sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE,   0xFF9A9A9A);
            ctx.fill(sx + SLOT_SIZE - 1, sy,             sx + SLOT_SIZE,   sy + SLOT_SIZE,   0xFF9A9A9A);
            ctx.fill(sx + 1,           sy + 1,           sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, slotColor);

            // Render item
            ctx.drawItem(slots.get(i).stack, sx + 1, sy + 1);
            ctx.drawItemInSlot(textRenderer, slots.get(i).stack, sx + 1, sy + 1);
        }

        // Kosong hint
        if (slots.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Inventory kosong!"),
                    px + pw / 2, gridTop + 8, 0x888888);
        }

        // Tooltip untuk item yang di-hover
        if (hoveredIdx >= 0) {
            ItemStack stack = slots.get(hoveredIdx).stack;
            ctx.drawItemTooltip(textRenderer, stack, mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            int gridTop = py + 2 + TITLE_H + 4;
            for (int i = 0; i < slots.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int sx  = px + PADDING + col * SLOT_SIZE;
                int sy  = gridTop + PADDING / 2 + row * SLOT_SIZE;
                if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                    onPick.accept(slots.get(i).stack.copy());
                    close();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }

    private record SlotEntry(int invSlot, ItemStack stack) {}
}
