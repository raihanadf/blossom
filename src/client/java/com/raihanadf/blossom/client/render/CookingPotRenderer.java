package com.raihanadf.blossom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.cooking.CookingPotBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// ingredients bob around inside the pot, the finished dish floats above it
public class CookingPotRenderer implements BlockEntityRenderer<CookingPotBlockEntity, CookingPotRenderer.State> {
	private final ItemModelResolver itemModelResolver;

	public CookingPotRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public static class State extends BlockEntityRenderState {
		final List<ItemStackRenderState> ingredients = new ArrayList<>();
		final ItemStackRenderState result = new ItemStackRenderState();
		float time;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CookingPotBlockEntity pot, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(pot, state, partialTicks, camera, breakProgress);
		int seed = (int) pot.getBlockPos().asLong();
		state.ingredients.clear();
		for (ItemStack stack : pot.getItems()) {
			if (!stack.isEmpty()) {
				ItemStackRenderState itemState = new ItemStackRenderState();
				itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, pot.getLevel(), null, seed + state.ingredients.size());
				state.ingredients.add(itemState);
			}
		}
		itemModelResolver.updateForTopItem(state.result, pot.getResult(), ItemDisplayContext.FIXED, pot.getLevel(), null, seed);
		state.time = pot.getLevel().getGameTime() + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int count = state.ingredients.size();
		for (int i = 0; i < count; i++) {
			// spread them in a slowly turning ring, each bobbing on its own beat
			float angle = state.time * 2f + i * 360f / count;
			float bob = Mth.sin((state.time + i * 7f) * 0.15f) * 0.03f;
			poseStack.pushPose();
			poseStack.translate(0.5f, 0.42f + bob, 0.5f);
			poseStack.rotateDegrees(Axis.YP, angle);
			poseStack.translate(0f, 0f, count > 1 ? 0.2f : 0f);
			poseStack.scale(0.3f, 0.3f, 0.3f);
			state.ingredients.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}

		if (!state.result.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5f, 0.85f + Mth.sin(state.time * 0.1f) * 0.05f, 0.5f);
			poseStack.rotateDegrees(Axis.YP, state.time * 3f);
			poseStack.scale(0.45f, 0.45f, 0.45f);
			state.result.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
