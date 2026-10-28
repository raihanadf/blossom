package com.raihanadf.blossom.sports;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomItems;
import java.util.List;
import net.minecraft.core.BlockPos;
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

// a bouncy football: run into it to dribble, punch it to shoot, right click to pick it up.
// rolling it into a goal net block scores, then it pops back to where it was put down
public class Football extends Entity implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private BlockPos kickoff;
	private String lastKicker = "";
	private int celebrate;
	// visual only: how far it has rolled, for spinning the model
	public float roll;
	public float rollO;

	public Football(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (kickoff == null) {
			kickoff = blockPosition();
		}
		Vec3 before = position();
		Vec3 velocity = getDeltaMovement().add(0, -0.04, 0);
		move(MoverType.SELF, velocity);
		Vec3 moved = position().subtract(before);
		// bounce off walls and the ground, losing a bit every time
		double vx = Math.abs(moved.x) < Math.abs(velocity.x) * 0.5 ? -velocity.x * 0.6 : velocity.x;
		double vz = Math.abs(moved.z) < Math.abs(velocity.z) * 0.5 ? -velocity.z * 0.6 : velocity.z;
		double vy = velocity.y;
		if (verticalCollision && velocity.y < 0) {
			vy = velocity.y < -0.15 ? -velocity.y * 0.55 : 0;
			if (velocity.y < -0.15) {
				playSound(SoundEvents.SLIME_JUMP_SMALL, 0.4f, 1.6f);
			}
		}
		double friction = onGround() ? 0.93 : 0.99;
		setDeltaMovement(vx * friction, vy, vz * friction);

		rollO = roll;
		roll += (float) moved.horizontalDistance() / 0.25f;

		if (level() instanceof ServerLevel level) {
			dribble(level);
			checkGoal(level);
		}
	}

	// players walking into it push it along their way
	private void dribble(ServerLevel level) {
		List<Player> players = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.15), player -> !player.isSpectator());
		for (Player player : players) {
			Vec3 away = position().subtract(player.position()).multiply(1, 0, 1);
			double pace = player.position().subtract(player.xo, player.yo, player.zo).horizontalDistance();
			if (away.lengthSqr() < 1.0e-4) {
				away = player.getLookAngle().multiply(1, 0, 1);
			}
			setDeltaMovement(away.normalize().scale(0.25 + pace * 1.6).add(0, 0.08, 0));
			lastKicker = player.getName().getString();
		}
	}

	private void checkGoal(ServerLevel level) {
		if (celebrate > 0) {
			if (--celebrate == 0) {
				// back to the kick-off spot
				setPos(kickoff.getX() + 0.5, kickoff.getY(), kickoff.getZ() + 0.5);
				setDeltaMovement(Vec3.ZERO);
			}
			return;
		}
		if (!level.getBlockState(blockPosition()).is(BlossomBlocks.GOAL_NET)) {
			return;
		}
		celebrate = 50;
		setDeltaMovement(getDeltaMovement().scale(0.2));
		level.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 0.5, getZ(), 40, 0.6, 0.6, 0.6, 0.15);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 0.5, getZ(), 30, 0.8, 0.8, 0.8, 0.3);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.5f, 1f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
		Component message = lastKicker.isEmpty() ? Component.translatable("message.blossom.goal") : Component.translatable("message.blossom.goal_by", lastKicker);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < 64 * 64) {
				player.sendOverlayMessage(message);
			}
		}
	}

	// punching it is a proper shot along where you look, harder while sprinting
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player player) {
			Vec3 look = player.getLookAngle();
			double power = player.isSprinting() ? 1.35 : 1.0;
			setDeltaMovement(look.x * power, Math.max(0.25, look.y * power + 0.3), look.z * power);
			lastKicker = player.getName().getString();
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 1.3f);
		}
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (level() instanceof ServerLevel level) {
			ItemStack ball = new ItemStack(BlossomItems.FOOTBALL);
			if (!player.getInventory().add(ball)) {
				spawnAtLocation(level, ball);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1f);
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		kickoff = input.read("Kickoff", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("Kickoff", BlockPos.CODEC, kickoff);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
