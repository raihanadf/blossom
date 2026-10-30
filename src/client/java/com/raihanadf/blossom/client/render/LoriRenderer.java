package com.raihanadf.blossom.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.math.Axis;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.nusantara.Lori;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

// the inspection trolley: tilted with the grade like the train cars, glass in its cab
public class LoriRenderer extends GeoEntityRenderer<Lori, EntityRenderState> {
	private static final DataTicket<Float> GRADE = DataTicket.create("blossom_lori_grade", Float.class);

	public LoriRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<>(Blossom.id("train/lori")));
		shadowRadius = 0.9f;
	}

	@Override
	public void addRenderData(Lori lori, Void related, EntityRenderState state, float partialTick) {
		state.addGeckolibData(GRADE, lori.getXRot(partialTick));
	}

	@Override
	public RenderType getRenderType(EntityRenderState state, Identifier texture) {
		return RenderTypes.entityTranslucent(texture, false);
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
		super.adjustRenderPose(info);
		info.poseStack().rotateDegrees(Axis.XP, -info.renderState().getOrDefaultGeckolibData(GRADE, 0f));
	}
}
