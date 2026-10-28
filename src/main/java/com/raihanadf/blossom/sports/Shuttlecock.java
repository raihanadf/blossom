package com.raihanadf.blossom.sports;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a badminton shuttlecock: lots of drag so it floats up and drops steeply. hit it with a racket to keep the rally going,
// everyone nearby sees the rally count, and when it lands the rally is over
public class Shuttlecock extends Entity implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int rally;
	private boolean landed;

	public Shuttlecock(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (onGround()) {
			setDeltaMovement(Vec3.ZERO);
			if (!landed && level() instanceof ServerLevel level) {
				landed = true;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
				level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 4, 0.1, 0.02, 0.1, 0.01);
				if (rally >= 2) {
					announce(level, Component.translatable("message.blossom.rally_over", rally));
				}
				rally = 0;
			}
			return;
		}
		landed = false;
		Vec3 velocity = getDeltaMovement().scale(0.955).add(0, -0.03, 0);
		move(MoverType.SELF, velocity);
		setDeltaMovement(horizontalCollision ? velocity.multiply(0.1, 1, 0.1) : velocity);
	}

	// one swing of a racket (or a bare hand, much weaker) sends it back up along where the player looks
	public void hit(ServerLevel level, Player player, boolean racket) {
		Vec3 look = player.getLookAngle();
		double power = racket ? (player.isSprinting() ? 1.25 : 1.0) : 0.4;
		setDeltaMovement(look.x * power, Math.max(0.45, look.y * power + 0.55) * (racket ? 1 : 0.6), look.z * power);
		landed = false;
		rally++;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.6f + level.getRandom().nextFloat() * 0.3f);
		level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.1, getZ(), 4, 0.1, 0.1, 0.1, 0.1);
		if (rally >= 2) {
			announce(level, Component.translatable("message.blossom.rally", rally));
		}
	}

	private void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < 32 * 32) {
				player.sendOverlayMessage(message);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player player) {
			hit(level, player, player.getMainHandItem().is(BlossomItems.RACKET));
		}
		return false;
	}

	// pick it up once it has landed
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!onGround()) {
			return InteractionResult.PASS;
		}
		if (level() instanceof ServerLevel level) {
			ItemStack shuttle = new ItemStack(BlossomItems.SHUTTLECOCK);
			if (!player.getInventory().add(shuttle)) {
				spawnAtLocation(level, shuttle);
			}
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
