package dev.custommacro.mixin;

import dev.custommacro.CustomMacroClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts mouse clicks while in-game to detect clicks on the
 * top-left overlay button (the redstone icon).
 */
@Mixin(Mouse.class)
public class MouseHandlerMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return; // don't interfere with open screens
        if (action != 1) return; // only on press, not release

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth()
                / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight()
                / client.getWindow().getHeight();

        if (CustomMacroClient.handleOverlayClick(client, mouseX, mouseY, button)) {
            ci.cancel();
        }
    }
}
