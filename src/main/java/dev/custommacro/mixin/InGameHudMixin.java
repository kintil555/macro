package dev.custommacro.mixin;

import dev.custommacro.CustomMacroClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the redstone button rendering into the in-game HUD overlay.
 * This makes the button visible during normal gameplay.
 * (The pause menu button is handled separately via PauseMenuMixin.)
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        // The HUD button is rendered by CustomMacroClient's tick listener when in-game
        // This mixin is kept as a hook entry point for future use
    }
}
