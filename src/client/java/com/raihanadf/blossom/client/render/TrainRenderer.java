package com.raihanadf.blossom.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.math.Axis;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.nusantara.TrainCar;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

// the train uses dedicated geometry for the locomotive, economy coach and panoramic coach
public class TrainRenderer extends GeoEntityRenderer<TrainCar, EntityRenderState> {
	private static final DataTicket<Integer> KIND = DataTicket.create("blossom_train_kind", Integer.class);
	private static final DataTicket<Float> GRADE = DataTicket.create("blossom_grade", Float.class);
	private static final DefaultedEntityGeoModel<TrainCar> CC201 = new DefaultedEntityGeoModel<>(Blossom.id("train/cc201"));
	private static final DefaultedEntityGeoModel<TrainCar> PANORAMIC = new DefaultedEntityGeoModel<>(Blossom.id("train/panoramic"));

	public TrainRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<TrainCar>(Blossom.id("train/coach")) {
			@Override
			public Identifier getModelResource(GeoRenderState state) {
				return switch (state.getOrDefaultGeckolibData(KIND, 1)) {
					case 0 -> CC201.getModelResource(state);
					case 2 -> PANORAMIC.getModelResource(state);
					default -> super.getModelResource(state);
				};
			}

			@Override
			public Identifier getTextureResource(GeoRenderState state) {
				return switch (state.getOrDefaultGeckolibData(KIND, 1)) {
					case 0 -> CC201.getTextureResource(state);
					case 2 -> PANORAMIC.getTextureResource(state);
					default -> super.getTextureResource(state);
				};
			}

			@Override
			public Identifier getAnimationResource(TrainCar car) {
				return switch (car.kind()) {
					case 0 -> CC201.getAnimationResource(car);
					case 2 -> PANORAMIC.getAnimationResource(car);
					default -> super.getAnimationResource(car);
				};
			}
		});
		shadowRadius = 1.6f;
	}

	@Override
	public void addRenderData(TrainCar car, Void related, EntityRenderState state, float partialTick) {
		state.addGeckolibData(KIND, car.kind());
		state.addGeckolibData(GRADE, car.getXRot(partialTick));
	}
	@Override
	public RenderType getRenderType(EntityRenderState state, Identifier texture) {
		return RenderTypes.entityTranslucent(texture, false);
	}

	// tilt the car with the grade of the line
	@Override
	public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
		super.adjustRenderPose(info);
		info.poseStack().rotateDegrees(Axis.XP, -info.renderState().getOrDefaultGeckolibData(GRADE, 0f));
	}
}
