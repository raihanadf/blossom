package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.Blossom;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the title screen shows blossom's own logo: the word with a blossom for each o, "nusantara" in gold underneath
@Mixin(LogoRenderer.class)
public abstract class LogoMixin {
	private static final Identifier BLOSSOM_LOGO = Blossom.id("textures/gui/title/blossom_logo.png");

	@Shadow
	@Final
	private boolean keepLogoThroughFade;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void blossom$logo(GuiGraphicsExtractor graphics, int width, float alpha, int heightOffset, CallbackInfo callback) {
		int color = ARGB.white(keepLogoThroughFade ? 1.0F : alpha);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BLOSSOM_LOGO, width / 2 - 128, heightOffset - 6, 0.0F, 0.0F, 256, 64, 256, 64, color);
		callback.cancel();
	}
}
