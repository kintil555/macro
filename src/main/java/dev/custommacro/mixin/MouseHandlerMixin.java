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
 * Intercepts in-game mouse clicks to detect clicks on the ◉ overlay button.
 * MC 1.21.11: onMouseButton(long window, MouseInput input, int action)
 * input.button() returns the button code (0=left, 1=right, 2=middle).
 */
@Mixin(Mouse.class)
public class MouseHandlerMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        // action: 1 = press, 0 = release
        if (action != 1) return;

        MinecraftClient client = MinecraftClient.getInstance();
        // Only intercept when no screen is open (pure in-game)
        if (client.currentScreen != null) return;

        double mouseX = client.mouse.getScaledX(client.getWindow());
        double mouseY = client.mouse.getScaledY(client.getWindow());

        if (CustomMacroClient.handleOverlayClick(client, mouseX, mouseY, input.button())) {
            ci.cancel(); // consume click so vanilla doesn't process it
        }
    }
}
