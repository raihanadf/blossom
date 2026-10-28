package com.raihanadf.blossom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.cooking.CuttingBoardBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// draws the ingredient lying on the board, a bit squashed after every chop
public class CuttingBoardRenderer implements BlockEntityRenderer<CuttingBoardBlockEntity, CuttingBoardRenderer.State> {
	private final ItemModelResolver itemModelResolver;

	public CuttingBoardRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		int chops;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CuttingBoardBlockEntity board, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(board, state, partialTicks, camera, breakProgress);
		itemModelResolver.updateForTopItem(state.item, board.getItem(), ItemDisplayContext.FIXED, board.getLevel(), null, (int) board.getBlockPos().asLong());
		state.chops = board.getChops();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.item.isEmpty()) {
			return;
		}
		float squash = 1f - state.chops * 0.15f;
		poseStack.pushPose();
		poseStack.translate(0.5f, 1f / 16f + 0.01f, 0.5f);
		poseStack.rotateDegrees(Axis.XP, 90f);
		poseStack.scale(0.5f, 0.5f * squash, 0.5f);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
