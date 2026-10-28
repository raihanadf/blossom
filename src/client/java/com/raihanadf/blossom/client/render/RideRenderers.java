package com.raihanadf.blossom.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.motor.Motor;
import com.raihanadf.blossom.sports.Football;
import com.raihanadf.blossom.sports.Shuttlecock;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

// geckolib renderers for the things you ride and play with: the motor leans into turns and is painted in its dye colour,
// the football rolls the way it travels, the shuttlecock flies nose first
public final class RideRenderers {
	private static final DataTicket<Float> LEAN = DataTicket.create("blossom_lean", Float.class);
	private static final DataTicket<DyeColor> COLOR = DataTicket.create("blossom_color", DyeColor.class);
	private static final DataTicket<Float> ROLL = DataTicket.create("blossom_roll", Float.class);
	private static final DataTicket<Float> HEADING = DataTicket.create("blossom_heading", Float.class);
	private static final DataTicket<Float> PITCH = DataTicket.create("blossom_pitch", Float.class);

	private RideRenderers() {
	}

	public static EntityRendererProvider<Motor> motor() {
		DefaultedEntityGeoModel<Motor> model = new DefaultedEntityGeoModel<>(Blossom.id("motor")) {
			@Override
			public Identifier getTextureResource(GeoRenderState state) {
				return Blossom.id("textures/entity/motor/motor_" + state.getOrDefaultGeckolibData(COLOR, DyeColor.RED).getSerializedName() + ".png");
			}
		};
		return context -> new GeoEntityRenderer<Motor, EntityRenderState>(context, model) {
			{
				shadowRadius = 0.5f;
			}

			@Override
			public void addRenderData(Motor motor, Void related, EntityRenderState state, float partialTick) {
				// leaning into turns, plus a little shake when it gets hit (like a boat)
				float hurt = motor.getHurtTime() - partialTick;
				float wobble = hurt > 0 ? Mth.sin(hurt) * hurt * Math.min(motor.getDamage(), 10) / 10 * motor.getHurtDir() : 0;
				state.addGeckolibData(LEAN, motor.getLean(partialTick) + wobble);
				state.addGeckolibData(COLOR, motor.getColor());
			}

			@Override
			public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
				super.adjustRenderPose(info);
				info.poseStack().rotateDegrees(Axis.ZP, info.renderState().getOrDefaultGeckolibData(LEAN, 0f));
			}
		};
	}

	public static EntityRendererProvider<Football> football() {
		DefaultedEntityGeoModel<Football> model = new DefaultedEntityGeoModel<>(Blossom.id("football"));
		return context -> new GeoEntityRenderer<Football, EntityRenderState>(context, model) {
			{
				shadowRadius = 0.25f;
			}

			@Override
			public void addRenderData(Football ball, Void related, EntityRenderState state, float partialTick) {
				Vec3 motion = ball.getDeltaMovement();
				state.addGeckolibData(ROLL, Mth.lerp(partialTick, ball.rollO, ball.roll));
				state.addGeckolibData(HEADING, motion.horizontalDistanceSqr() > 1.0e-4 ? (float) Mth.atan2(motion.x, motion.z) : 0f);
			}

			@Override
			public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
				super.adjustRenderPose(info);
				// spin around its own middle, about the axis across the way it rolls
				PoseStack pose = info.poseStack();
				pose.translate(0, 0.25, 0);
				pose.rotate(Axis.YP, info.renderState().getOrDefaultGeckolibData(HEADING, 0f));
				pose.rotate(Axis.XP, info.renderState().getOrDefaultGeckolibData(ROLL, 0f));
				pose.translate(0, -0.25, 0);
			}
		};
	}

	public static EntityRendererProvider<Shuttlecock> shuttlecock() {
		DefaultedEntityGeoModel<Shuttlecock> model = new DefaultedEntityGeoModel<>(Blossom.id("shuttlecock"));
		return context -> new GeoEntityRenderer<Shuttlecock, EntityRenderState>(context, model) {
			{
				shadowRadius = 0.1f;
			}

			@Override
			public void addRenderData(Shuttlecock shuttle, Void related, EntityRenderState state, float partialTick) {
				Vec3 motion = shuttle.getDeltaMovement();
				boolean flying = !shuttle.onGround() && motion.lengthSqr() > 1.0e-4;
				state.addGeckolibData(HEADING, flying ? (float) Mth.atan2(motion.x, motion.z) : 0f);
				// the model points up, tip it so the cork leads; lying on its side once landed
				state.addGeckolibData(PITCH, flying ? (float) Mth.atan2(motion.horizontalDistance(), -motion.y) : Mth.HALF_PI);
			}

			@Override
			public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
				super.adjustRenderPose(info);
				PoseStack pose = info.poseStack();
				pose.translate(0, 0.12, 0);
				pose.rotate(Axis.YP, info.renderState().getOrDefaultGeckolibData(HEADING, 0f));
				pose.rotate(Axis.XP, Mth.PI - info.renderState().getOrDefaultGeckolibData(PITCH, 0f));
				pose.translate(0, -0.12, 0);
			}
		};
	}
}
