package com.raihanadf.blossom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.kampung.Satpam;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// kampung folk use the player body, kids at half size (its outer layer parts ride along with the limbs), each with one of blossom's own skins
public class WargaRenderer extends HumanoidMobRenderer<Warga, WargaRenderer.State, HumanoidModel<WargaRenderer.State>> {
	private final ItemModelResolver itemModelResolver;

	public WargaRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		itemModelResolver = context.getItemModelResolver();
		addLayer(new SheathedBatonLayer(this));
	}

	public static class State extends HumanoidRenderState {
		int skin;
		boolean satpam;
		boolean drawn;
		final ItemStackRenderState sheathedBaton = new ItemStackRenderState();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Warga warga, State state, float partialTicks) {
		super.extractRenderState(warga, state, partialTicks);
		state.skin = warga.getSkin();
		state.satpam = warga instanceof Satpam;
		state.drawn = state.satpam && ((Satpam) warga).drawn();
		if (state.satpam && !state.drawn) {
			itemModelResolver.updateForLiving(state.sheathedBaton, new ItemStack(BlossomItems.BATON), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, warga);
		} else {
			state.sheathedBaton.clear();
		}
		if (Warga.isKidSkin(state.skin)) {
			state.scale *= 0.55f;
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return Blossom.id("textures/entity/warga/" + Warga.skinName(state.skin) + ".png");
	}

	private static final class SheathedBatonLayer extends RenderLayer<State, HumanoidModel<State>> {
		private SheathedBatonLayer(WargaRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (!state.satpam || state.drawn || state.sheathedBaton.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			getParentModel().body.translateAndRotate(poseStack);
			poseStack.translate(3.0F / 16.0F, 8.0F / 16.0F, 2.0F / 16.0F);
			poseStack.rotateDegrees(Axis.XP, -90.0F);
			poseStack.rotateDegrees(Axis.YP, 180.0F);
			poseStack.rotateDegrees(Axis.ZP, 12.0F);
			state.sheathedBaton.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
