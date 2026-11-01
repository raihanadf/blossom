package com.raihanadf.blossom.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.raihanadf.blossom.critters.Critter;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

// draws the little thing a critter wears or carries (a flower on a capybara, a berry on a hedgehog) on its prop bone
public class CritterPropLayer<T extends Critter> extends BlockAndItemGeoLayer<T, Void, LivingEntityRenderState> {
	private static final DataTicket<Float> SCALE = DataTicket.create("blossom_prop_scale", Float.class);

	public CritterPropLayer(EntityRendererProvider.Context context, GeoRenderer<T, Void, LivingEntityRenderState> renderer) {
		super(context, renderer);
	}

	@Override
	protected List<RenderData> getRelevantBones(T critter, Void related, LivingEntityRenderState state, float partialTick) {
		if (critter.getProp().isEmpty()) {
			return List.of();
		}
		ItemStackRenderState item = RenderUtil.createRenderStateForItem(critter.getProp(), itemModelResolver, ItemDisplayContext.FIXED, critter);
		return List.of(RenderData.item(critter.propBone(), ItemDisplayContext.FIXED, item));
	}

	@Override
	public void addRenderData(T critter, Void related, LivingEntityRenderState state, float partialTick) {
		List<RenderData> contents = getRelevantBones(critter, related, state, partialTick);
		if (!contents.isEmpty()) {
			state.addGeckolibData(CONTENTS, contents);
			state.addGeckolibData(SCALE, critter.propScale());
		}
	}

	@Override
	protected void submitItemStackRender(PoseStack pose, GeoBone bone, ItemStackRenderState item, ItemDisplayContext context,
			LivingEntityRenderState state, SubmitNodeCollector collector, int light) {
		float scale = state.getOrDefaultGeckolibData(SCALE, 0.4f);
		pose.pushPose();
		pose.scale(scale, scale, scale);
		super.submitItemStackRender(pose, bone, item, context, state, collector, light);
		pose.popPose();
	}
}
