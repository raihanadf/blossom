package com.raihanadf.blossom.client;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.core.particles.ParticleTypes;
import java.util.Arrays;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// the baton keeps decimation's katana draw, inspect, chained slashes and wide hit.
public final class BatonMotion {
	public static final int NONE = 0, DRAW = 1, INSPECT = 2, SLASH_A = 3, SLASH_B = 4;
	private static final float[][] DRAW_KEYS = {
		{0.00f, .55f, -.35f, 0, 0, 0, 0, 0, 0, 75, 0, 0, 0},
		{.12f, .50f, .10f, -.15f, 0, 0, -15, 0, 0, 140, -.02f, .10f, 0},
		{.24f, .48f, .05f, -.15f, 0, 0, -8, -10, 0, 250, -.05f, .32f, -.05f},
		{.36f, .46f, .02f, -.15f, 0, 0, -4, -10, 0, 435, -.06f, .38f, -.05f},
		{.46f, .44f, .06f, -.15f, 0, 0, 0, -5, 0, 470, -.10f, .22f, -.05f},
		{.56f, .42f, .03f, -.12f, 0, 0, 6, 0, 0, 520, -.12f, .05f, 0},
		{.66f, .22f, .08f, -.08f, 0, 0, 4, 0, 0, 615, -.18f, .15f, 0},
		{.78f, .05f, 0, 0, 0, 0, 2, 0, 0, 700, -.10f, 0, 0},
		{.88f, 0, -.03f, 0, 0, 0, 0, 0, 0, 725, 0, 0, 0},
		{1.00f, -.01f, .01f, 0, 0, 0, 0, 0, 0, 718, 0, 0, 0},
		{1.12f, 0, 0, 0, 0, 0, 0, 0, 0, 720, 0, 0, 0}};
	private static final float[][] INSPECT_KEYS = {
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		{.35f, .12f, .02f, 0, 0, 0, -30, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		{.60f, .12f, .04f, 0, 0, 0, -25, 0, 0, -60, 0, .05f, 0, 0, 0, 0},
		{.80f, .10f, 0, 0, 0, 0, -10, 0, 0, -240, -.05f, .25f, 0, 0, 0, 0},
		{1.00f, .10f, .03f, 0, 0, 0, 0, 0, 0, -420, -.05f, .30f, 0, 0, 0, 0},
		{1.15f, .12f, .08f, 0, 0, 0, 0, 0, 0, -540, 0, .12f, 0, 0, 0, 0},
		{1.30f, .20f, -.02f, 0, 0, 0, 0, 0, 0, -560, 0, .06f, 0, 0, 0, 0},
		{1.55f, .30f, .18f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0},
		{1.80f, .30f, .18f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0, .05f, .06f, -.05f, 35, 0, 30, 1},
		{2.40f, .30f, .18f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0, -.20f, .10f, -.15f, 35, 0, 30, 1},
		{3.00f, .30f, .18f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0, -.42f, .14f, -.25f, 35, 0, 30, 1},
		{3.40f, .30f, .18f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0, -.44f, .15f, -.26f, 35, 0, 30, 1},
		{3.60f, .30f, .16f, 0, 0, 0, 0, 0, 0, -560, 0, 0, 0, 0, 0, 0, -.42f, 0, -.26f, 35, 0, 30, 0},
		{3.85f, .10f, .05f, 0, 0, 0, 0, 0, 0, -620, 0, .04f, 0, 0, 0, 0},
		{4.15f, 0, 0, 0, 0, 0, 0, 0, 0, -715, 0, 0, 0, 0, 0, 0},
		{4.30f, 0, 0, 0, 0, 0, 0, 0, 0, -720, 0, 0, 0, 0, 0, 0}};
	private static final float[][] SLASH_A_KEYS = {
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		{.06f, .12f, .15f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
		{.13f, .3f, .22f, 0, 0, 0, -30, 0, 0, 0, 0, 0, 0, -15, 0, 0, 0, 0, 0, 0, 0, 1},
		{.17f, 0, .3f, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 1},
		{.22f, -.35f, .18f, 0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 35, -6, 0, 0, 0, 0, 0, 0, 1},
		{.28f, -.8f, .06f, -.1f, 0, 0, 72, 0, 0, 0, 0, 0, 0, 62, -8, 0, 0, 0, 0, 0, 0, 1},
		{.36f, -.85f, .02f, -.1f, 0, 0, 74, 0, 0, 0, 0, 0, 0, 64, -8, 0, 0, 0, 0, 0, 0, 1},
		{.50f, -.05f, .05f, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
		{.58f, .22f, .02f, 0, 0, 0, 0, 0, 0, 0, -.5f, .12f, 0, 0, 0, 0, -.5f, .12f, 0, 0, 0, 0, 1},
		{.65f, .24f, 0, 0, 0, 0, 0, 0, 0, 0, -.58f, .08f, 0, 0, 0, 0, -.58f, .08f, 0, 0, 0, 0, 1},
		{.73f, .24f, 0, 0, 0, 0, 0, 0, 0, 8, -.22f, .3f, 0, 0, 0, 0, -.6f, -.05f, 0, 0, 0, 0, .4f},
		{.81f, .22f, .02f, 0, 0, 0, 0, 0, 0, 4, -.02f, .12f, 0, 0, 0, 0, -.6f, -.2f, 0, 0, 0, 0, 0},
		{.87f, .16f, -.05f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		{.95f, .14f, .03f, 0, 0, 0, 0, 0, 0, 90, 0, .08f, 0, 0, 0, 0},
		{1.04f, .10f, .04f, 0, 0, 0, 0, 0, 0, 260, 0, .12f, 0, 0, 0, 0},
		{1.12f, .05f, -.02f, 0, 0, 0, 0, 0, 0, 360, 0, 0, 0, 0, 0, 0},
		{1.22f, 0, 0, 0, 0, 0, 0, 0, 0, 360, 0, 0, 0, 0, 0, 0}};
	private static final float[][] SLASH_B_KEYS = {
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		{.07f, .25f, .2f, 0, 0, 0, -40, 0, 0, 0, 0, 0, 0, -15, 0, 0},
		{.12f, -.05f, .22f, 0, 0, 0, 20, 0, 0, 0, 0, 0, 0, 10, 0, 0},
		{.18f, -.28f, .12f, 0, 0, 0, 75, 0, 0, 0, 0, 0, 0, 22, -4, 0},
		{.34f, .22f, .3f, 0, 0, 0, 0, 0, 0, 140, 0, 0, 0, 0, 0, 0},
		{.48f, .2f, .28f, 0, 0, 0, 0, 0, 0, 150, 0, 0, 0, 0, 0, 0},
		{.66f, 0, 0, 0, 0, 0, 0, 0, 0, 360, 0, 0, 0, 0, 0, 0}};
	private static final float[][][] ANIMS = {DRAW_KEYS, INSPECT_KEYS, SLASH_A_KEYS, SLASH_B_KEYS};
	private static final float[] STRIKE = {.20f, .12f};
	private static final float[] READY = {.47f, .50f};
	private static final float COMBO = .6f;
	private static final float SLIDE = 35, BODY = 55;
	private static final double BORDER = .1;
	private static final float[] pose = new float[22];
	public static final KeyMapping INSPECT_KEY = new KeyMapping(
		"key.blossom.inspect", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, KeyMapping.Category.register(Blossom.id("blossom")));
	private static int animation;
	private static long startedAt, lastSlash;
	private static int combo;
	private static boolean swept, queued;
	private static float[] blendFrom;
	private static int lastSlot = -1;
	private static ItemStack lastHeld = ItemStack.EMPTY;

	private BatonMotion() {
	}

	public static void init() {
		BatonHeldPhysics.init();
		ClientTickEvents.END_CLIENT_TICK.register(BatonMotion::tick);
	}

	public static void click() {
		queued = true;
	}

	public static boolean isPlaying() {
		return animation != NONE;
	}

	private static float elapsed() {
		return (System.nanoTime() - startedAt) / 1e9f;
	}

	private static float[][] keys(int animation) {
		return ANIMS[animation - 1];
	}

	private static float length(int animation) {
		float[][] keys = keys(animation);
		return keys[keys.length - 1][0];
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) return;
		ItemStack held = player.getMainHandItem();
		boolean baton = BatonHeldPhysics.isBaton(held);
		if (animation != NONE && (!baton || elapsed() > length(animation))) animation = NONE;
		if (baton && (held.getItem() != lastHeld.getItem() || player.getInventory().getSelectedSlot() != lastSlot)) {
			animation = DRAW;
			startedAt = System.nanoTime();
		}
		lastHeld = held.copy();
		lastSlot = player.getInventory().getSelectedSlot();
		while (INSPECT_KEY.consumeClick()) {
			if (baton && animation != DRAW) {
				animation = INSPECT;
				startedAt = System.nanoTime();
			}
		}
		if (!baton) queued = false;
		boolean wants = baton && minecraft.gui.screen() == null && (queued || minecraft.options.keyAttack.isDown());
		if (wants && ready()) {
			long now = System.nanoTime();
			boolean chain = animation >= SLASH_A && (now - lastSlash) / 1e9 < READY[combo] + COMBO;
			blendFrom = posture();
			combo = chain ? (combo + 1) % 2 : 0;
			lastSlash = now;
			animation = SLASH_A + combo;
			startedAt = now;
			swept = false;
			queued = false;
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		}
		if (animation >= SLASH_A && !swept && elapsed() >= STRIKE[animation - SLASH_A]) {
			swept = true;
			Entity struck = hit(minecraft);
			impact(minecraft, player, animation - SLASH_A, struck);
			Vec3 at = player.getEyePosition().add(player.getViewVector(1).scale(2.2)).add(0, -.35, 0);
			minecraft.level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, .6, 0, 0);
		}
	}

	private static boolean ready() {
		return animation == DRAW ? false : animation < SLASH_A || elapsed() >= READY[animation - SLASH_A];
	}

	private static float[] posture() {
		float[] output = new float[22];
		if (animation != NONE) curve(keys(animation), Math.min(elapsed(), length(animation)), output);
		for (int i : new int[] {3, 4, 5, 6, 7, 8, 12, 13, 14, 18, 19, 20}) output[i] = Mth.wrapDegrees(output[i]);
		return output;
	}

	public static float[] pose() {
		Arrays.fill(pose, 0);
		if (animation != NONE) {
			curve(keys(animation), Math.min(elapsed(), length(animation)), pose);
			if (blendFrom != null && elapsed() < .1f) {
				float f = elapsed() / .1f;
				f = f * f * (3 - 2 * f);
				for (int i = 0; i < pose.length; i++) pose[i] = blendFrom[i] + (pose[i] - blendFrom[i]) * f;
			}
		}
		double breathing = System.nanoTime() / 1e9;
		pose[1] += (float) (.008 * Math.sin(breathing * 1.7));
		pose[3] += (float) (.5 * Math.sin(breathing * 1.3));
		pose[5] += (float) (.8 * Math.sin(breathing * 1.1));
		return pose;
	}

	private static void curve(float[][] keyframes, float time, float[] output) {
		int right = 1;
		while (right < keyframes.length && keyframes[right][0] < time) right++;
		if (right >= keyframes.length) right = keyframes.length - 1;
		int left = Math.max(0, right - 1), before = Math.max(0, left - 1), after = Math.min(keyframes.length - 1, right + 1);
		float span = keyframes[right][0] - keyframes[left][0];
		float t = span <= 0 ? 0 : (time - keyframes[left][0]) / span;
		for (int i = 0; i < output.length; i++) {
			float p0 = value(keyframes[before], i), p1 = value(keyframes[left], i), p2 = value(keyframes[right], i), p3 = value(keyframes[after], i);
			output[i] = .5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
		}
	}

	private static float value(float[] keyframe, int index) {
		return index < keyframe.length - 1 ? keyframe[index + 1] : 0;
	}

	private static Entity hit(Minecraft minecraft) {
		if (minecraft.hitResult instanceof EntityHitResult entityHit) {
			minecraft.gameMode.attack(minecraft.player, entityHit.getEntity());
			return entityHit.getEntity();
		}
		if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) return null;
		LocalPlayer player = minecraft.player;
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1), far = eye.add(look.scale(player.entityInteractionRange()));
		Entity best = null;
		double bestDistance = player.entityInteractionRange();
		for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().expandTowards(look.scale(bestDistance)).inflate(2))) {
			if (!(entity instanceof LivingEntity) || !entity.isPickable()) continue;
			double width = entity.getBbWidth() / 2 + BORDER;
			AABB box = entity.getBoundingBox().inflate(width, BORDER, width);
			double distance = box.clip(eye, far).map(eye::distanceTo).orElse(Double.MAX_VALUE);
			if (distance < bestDistance) {
				best = entity;
				bestDistance = distance;
			}
		}
		if (best != null) minecraft.gameMode.attack(player, best);
		return best;
	}

	private static void impact(Minecraft minecraft, LocalPlayer player, int slash, Entity struck) {
		float up = slash * .08f + (player.getRandom().nextFloat() - .5f) * .08f;
		Vec3 eye = player.getEyePosition().add(player.getViewVector(1).scale(1.2));
		sound(minecraft, eye, SoundEvents.PLAYER_ATTACK_SWEEP, .6f, 1.25f + up);
		if (struck == null) return;
		Vec3 at = struck.position();
		boolean flesh = struck instanceof LivingEntity;
		sound(minecraft, at, flesh ? SoundEvents.TRIDENT_HIT : SoundEvents.TRIDENT_HIT_GROUND, .75f, 1.3f + up);
		sound(minecraft, at, flesh ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.ANVIL_LAND, flesh ? .52f : .09f, 1.5f + up);
		for (int i = 0; i < 4; i++) minecraft.level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z,
				(player.getRandom().nextDouble() - .5) * .6, player.getRandom().nextDouble() * .4, (player.getRandom().nextDouble() - .5) * .6);
	}

	private static void sound(Minecraft minecraft, Vec3 at, SoundEvent event, float volume, float pitch) {
		minecraft.level.playLocalSound(at.x, at.y, at.z, event, SoundSource.PLAYERS, volume, pitch, false);
	}

	public static void render(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, InteractionHand hand,
			PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
		if (hand != InteractionHand.MAIN_HAND || !state.mainHandItem.is(BlossomItems.BATON)) return;
		AvatarRenderState avatarState = playerState.avatarRenderState;
		if (avatarState == null) return;
		AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(avatarState);
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float[] motion = pose();
		poseStack.pushPose();
		BatonHeldPhysics.apply(player, partialTicks, poseStack);
		poseStack.translate(.64f, -.6f, -.72f);
		poseStack.rotateDegrees(Axis.YP, 45);
		poseStack.translate(-1, 3.6f, 3.5f);
		poseStack.rotateDegrees(Axis.ZP, 120);
		poseStack.rotateDegrees(Axis.XP, 200);
		poseStack.rotateDegrees(Axis.YP, -135);
		poseStack.translate(5.6f, 0, 0);
		renderer.renderRightHand(poseStack, collector, lightCoords, avatarState.skin.body().texturePath(), avatarState.showRightSleeve);
		poseStack.popPose();
		poseStack.pushPose();
		BatonHeldPhysics.apply(player, partialTicks, poseStack);
		poseStack.translate(.45f + motion[0] * .15f, -.55f + motion[1] * .15f, -.9f + motion[2]);
		poseStack.rotateDegrees(Axis.YP, motion[4]);
		poseStack.rotateDegrees(Axis.XP, motion[3]);
		poseStack.rotateDegrees(Axis.ZP, motion[5] + 45);
		poseStack.scale(.55f, .55f, .55f);
		state.mainHandRenderState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
