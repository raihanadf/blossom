package com.raihanadf.blossom.client;

import com.raihanadf.blossom.registry.BlossomItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

// the baton follows turns, jumps and steps with the katana's springy hold.
public final class BatonHeldPhysics {
	private static final float[] position = new float[4];
	private static final float[] velocity = new float[4];
	private static final float[] previous = new float[4];
	private static final float[] stiffness = {0.32f, 0.32f, 0.22f, 0.18f};
	private static final float[] damping = {0.48f, 0.48f, 0.30f, 0.55f};
	private static float lastYaw;
	private static float lastPitch;
	private static boolean started;

	private BatonHeldPhysics() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BatonHeldPhysics::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			started = false;
			return;
		}
		if (!started) {
			started = true;
			lastYaw = player.getYRot();
			lastPitch = player.getXRot();
		}
		float yaw = Mth.wrapDegrees(player.getYRot() - lastYaw);
		float pitch = player.getXRot() - lastPitch;
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
		float[] target = {
			Mth.clamp(-yaw * 0.0045f, -0.14f, 0.14f),
			Mth.clamp(pitch * 0.0035f, -0.09f, 0.09f),
			player.onGround() ? 0 : Mth.clamp(-(float) player.getDeltaMovement().y * 0.22f, -0.10f, 0.12f),
			player.isSprinting() && !player.isCrouching() ? 1 : 0
		};
		for (int i = 0; i < position.length; i++) {
			previous[i] = position[i];
			velocity[i] += stiffness[i] * (target[i] - position[i]) - damping[i] * velocity[i];
			position[i] += velocity[i];
		}
	}

	public static float state(int index, float partialTicks) {
		return Mth.lerp(partialTicks, previous[index], position[index]);
	}

	public static void apply(AbstractClientPlayer player, float partialTicks, PoseStack poseStack) {
		float x = state(0, partialTicks);
		float y = state(1, partialTicks);
		float drop = state(2, partialTicks);
		float sprint = state(3, partialTicks);
		float walk = player.avatarState().getBackwardsInterpolatedWalkDistance(partialTicks);
		float bob = player.avatarState().getInterpolatedBob(partialTicks);
		float stepX = Mth.sin(walk * (float) Math.PI) * bob * 0.10f;
		float stepY = -Math.abs(Mth.cos(walk * (float) Math.PI)) * bob * 0.08f;
		poseStack.translate(x + stepX + 0.04f * sprint, y + drop + stepY - 0.05f * sprint, 0.03f * sprint);
		poseStack.rotateDegrees(Axis.ZP, -x * 70 + stepX * 30 + 6 * sprint);
		poseStack.rotateDegrees(Axis.XP, y * 45 + drop * 30 - 7 * sprint);
	}

	public static boolean isBaton(ItemStack stack) {
		return stack.is(BlossomItems.BATON);
	}
}
