package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.client.BatonMotion;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void blossom$queueBatonAttack(CallbackInfoReturnable<Boolean> callback) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (minecraft.player != null && minecraft.player.getMainHandItem().is(BlossomItems.BATON)) {
			BatonMotion.click();
			callback.setReturnValue(true);
		}
	}
}
