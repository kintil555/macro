package dev.custommacro.mixin;

import dev.custommacro.CustomMacroClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts mouse clicks while in-game to detect clicks on the overlay button.
 * MC 1.21.11: onMouseButton signature changed to (long window, MouseInput input, int action)
 */
@Mixin(Mouse.class)
public class MouseHandlerMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action != 1) return; // only on press
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth()
                / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight()
                / client.getWindow().getHeight();

        if (CustomMacroClient.handleOverlayClick(client, mouseX, mouseY, input.button())) {
            ci.cancel();
        }
    }
}
