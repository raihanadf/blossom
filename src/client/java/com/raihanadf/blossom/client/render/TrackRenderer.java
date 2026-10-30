package com.raihanadf.blossom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.nusantara.TrackBlockEntity;
import com.raihanadf.blossom.nusantara.TrackBlockEntity.Piece;
import com.raihanadf.blossom.nusantara.TrainCar;
import com.raihanadf.blossom.registry.BlossomItems;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TrackRenderer implements BlockEntityRenderer<TrackBlockEntity, TrackRenderer.State> {
	private final ItemModelResolver itemModelResolver;
	// made on first use: item components are not ready yet when renderers are built
	private ItemStack trackItem;
	private ItemStack deckItem;

	public TrackRenderer(BlockEntityRendererProvider.Context context) {
		itemModelResolver = context.itemModelResolver();
	}

	public static class State extends BlockEntityRenderState {
		final List<RenderPiece> pieces = new ArrayList<>();
		// a turntable deck: the planks under its rails, and the way it points now (the loco on it, if any)
		final ItemStackRenderState deck = new ItemStackRenderState();
		boolean turntable;
		float swing;
	}

	private static class RenderPiece {
		final Piece piece;
		final ItemStackRenderState model = new ItemStackRenderState();

		RenderPiece(Piece piece) {
			this.piece = piece;
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrackBlockEntity track, State state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(track, state, partialTicks, camera, breakProgress);
		state.pieces.clear();
		if (trackItem == null) {
			trackItem = new ItemStack(BlossomItems.TRACK_PIECE);
			deckItem = new ItemStack(Items.DARK_OAK_SLAB);
		}
		state.turntable = track.turntable();
		if (state.turntable && !track.pieces().isEmpty()) {
			itemModelResolver.updateForTopItem(state.deck, deckItem, ItemDisplayContext.FIXED, track.getLevel(), null, (int) track.getBlockPos().asLong());
			// the deck lines up with a loco standing on it while it swings round, otherwise it lies along the line
			state.swing = track.pieces().getFirst().yaw();
			Vec3 middle = Vec3.atBottomCenterOf(track.getBlockPos());
			for (TrainCar car : track.getLevel().getEntitiesOfClass(TrainCar.class, new AABB(track.getBlockPos()).inflate(2, 3, 2))) {
				if (car.isLoco() && Math.hypot(car.getX() - middle.x, car.getZ() - middle.z) < 1.5) {
					state.swing = -car.getViewYRot(partialTicks);
				}
			}
		}
		for (Piece piece : track.pieces()) {
			RenderPiece renderPiece = new RenderPiece(piece);
			itemModelResolver.updateForTopItem(renderPiece.model, trackItem, ItemDisplayContext.FIXED, track.getLevel(), null,
					(int) track.getBlockPos().asLong());
			state.pieces.add(renderPiece);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (RenderPiece renderPiece : state.pieces) {
			Piece piece = renderPiece.piece;
			poseStack.pushPose();
			poseStack.translate(piece.x(), piece.y(), piece.z());
			poseStack.rotateDegrees(Axis.YP, state.turntable ? state.swing : piece.yaw());
			if (state.turntable) {
				// planks under the rails, as long as the pit is wide
				poseStack.pushPose();
				poseStack.translate(0, -0.42, 0);
				poseStack.scale(3.2f, 0.5f, piece.length() + 0.4f);
				state.deck.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			poseStack.rotateDegrees(Axis.XP, piece.pitch());
			poseStack.scale(1f, 1f, piece.length());
			renderPiece.model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
