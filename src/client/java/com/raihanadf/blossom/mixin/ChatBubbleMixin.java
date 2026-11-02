package com.raihanadf.blossom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.raihanadf.blossom.client.ChatBubbles;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a player's chat bubble goes up with their name tag
@Mixin(AvatarRenderer.class)
public abstract class ChatBubbleMixin {
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
	private void blossom$chatBubble(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
		ChatBubbles.submit(state.id, state.nameTag != null, pose, collector, camera);
	}
}
