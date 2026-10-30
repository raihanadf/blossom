package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.registry.BlossomItems;
import com.raihanadf.blossom.client.BatonMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void blossom$renderBaton(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
			float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector collector, int lightCoords, CallbackInfo callback) {
		if (hand == InteractionHand.MAIN_HAND && itemStack.getItem() == BlossomItems.BATON) {
			BatonMotion.render(playerState, state, hand, poseStack, collector, lightCoords);
			callback.cancel();
		}
	}
}
