package com.raihanadf.blossom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.kampung.Satpam;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// kampung folk use the player body, kids at half size (its outer layer parts ride along with the limbs), each with one of
// blossom's own skins. the model breathes, tilts its head now and then, kids bounce, and a wave is a hand up high
public class WargaRenderer extends HumanoidMobRenderer<Warga, WargaRenderer.State, HumanoidModel<WargaRenderer.State>> {
	private final ItemModelResolver itemModelResolver;

	public WargaRenderer(EntityRendererProvider.Context context) {
		super(context, new LivelyModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		itemModelResolver = context.getItemModelResolver();
		addLayer(new SheathedBatonLayer(this));
	}

	public static class State extends HumanoidRenderState {
		int skin;
		boolean satpam;
		boolean drawn;
		boolean kid;
		float wave;
		float waveTime;
		float phase;
		final ItemStackRenderState sheathedBaton = new ItemStackRenderState();
	}

	private static final class LivelyModel extends HumanoidModel<State> {
		private LivelyModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.ageInTicks + state.phase;
			float walking = Math.min(1, state.walkAnimationSpeed * 2);
			// breathing: the chest rises a touch, the arms follow
			float breath = Mth.sin(t * 0.08f);
			body.y += breath * 0.15f * (1 - walking);
			head.y += breath * 0.15f * (1 - walking);
			// every few seconds the head tilts a little, as if listening
			float tilt = Mth.sin(t * 0.021f) * Mth.sin(t * 0.013f);
			head.zRot += tilt * 0.12f * (1 - walking);
			if (state.kid && walking > 0) {
				// kids bounce along and swing their arms wide
				float bounce = Math.abs(Mth.sin(state.walkAnimationPos * 0.6662f)) * walking;
				body.y -= bounce * 1.2f;
				head.y -= bounce * 1.2f;
				rightArm.xRot *= 1.4f;
				leftArm.xRot *= 1.4f;
			}
			if (state.wave > 0) {
				// the right hand goes up high and swings side to side, the head tips toward whoever it's for
				rightArm.xRot = Mth.lerp(state.wave, rightArm.xRot, -2.9f);
				rightArm.yRot = 0;
				rightArm.zRot = Mth.lerp(state.wave, rightArm.zRot, -0.25f + Mth.sin(state.waveTime * 0.9f) * 0.35f);
				head.zRot += state.wave * 0.08f;
			}
		}
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
		state.kid = Warga.isKidSkin(state.skin);
		state.wave = state.drawn ? 0 : warga.waveAmount(partialTicks);
		state.waveTime = warga.waveTime(partialTicks);
		state.phase = (warga.getId() * 37) % 400;
		if (state.satpam && !state.drawn) {
			itemModelResolver.updateForLiving(state.sheathedBaton, new ItemStack(BlossomItems.BATON), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, warga);
		} else {
			state.sheathedBaton.clear();
		}
		if (state.kid) {
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
