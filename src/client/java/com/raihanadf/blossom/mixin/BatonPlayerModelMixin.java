package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the katana's low ready pose, with the vanilla swing only as the brief third-person cue.
@Mixin(HumanoidModel.class)
public abstract class BatonPlayerModelMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void blossom$poseBaton(HumanoidRenderState state, CallbackInfo callback) {
		if (!state.getMainHandItemStack().is(BlossomItems.BATON)) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		model.rightArm.xRot = -0.47f;
		model.rightArm.yRot = 0;
		model.rightArm.zRot = -0.15f;
		model.leftArm.xRot = -0.20f;
		model.leftArm.yRot = 0.1f;
		model.leftArm.zRot = 0.10f;
		if (state.swingAnimation > 0 && state.currentSwing != null) {
			float swing = Mth.sin(Ease.outQuart(state.swingAnimation) * (float) Math.PI);
			model.rightArm.xRot -= swing * 1.2f;
			model.rightArm.yRot += Mth.sin(state.swingAnimation * (float) Math.PI) * 0.4f;
		}
	}
}
