package com.raihanadf.blossom.critters;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// burung kutilang, the sooty-headed bulbul: a round little songbird in a dark hood with a crest and a yellow patch
// under its tail. flocks hop and peck on the ground, perch on fences, walls, leaves and the kampung's power lines,
// sing a dawn chorus (one starts, the rest answer), burst up into the air when someone sprints past and splash about
// in shallow water. feed one seeds and it trusts you: it rides on your shoulder (sneak to let it off) and sings for you
public class Kutilang extends Critter {
	// the id of the player whose shoulder it's sitting on, -1 for none (players can't carry passengers, so it's held there by hand)
	private static final EntityDataAccessor<Integer> SHOULDER = SynchedEntityData.defineId(Kutilang.class, EntityDataSerializers.INT);

	private UUID trusted;
	private Vec3 perch;
	private int shoulderTicks;
	private int startled;
	private int answerIn;
	private int lastSong = -1000;
	private boolean wasFlying;

	public Kutilang(EntityType<? extends Animal> type, Level level) {
		super(type, level);
		moveControl = new FlyingMoveControl<>(this, 10, false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 5.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FLYING_SPEED, 0.45);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SHOULDER, -1);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(0, new ShoulderGoal());
		goalSelector.addGoal(1, new StartleGoal());
		goalSelector.addGoal(5, new PerchGoal());
		goalSelector.addGoal(6, new BatheGoal());
		goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		goalSelector.addGoal(7, new HopGoal());
	}

	@Override
	public String propBone() {
		return "beak";
	}

	@Override
	public float propScale() {
		return 0.25f;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.KUTILANG_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.KUTILANG_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("preen", "stretch", "look", "bob", "peck");
	}

	private Player shoulderOf() {
		int id = entityData.get(SHOULDER);
		return id < 0 ? null : level().getEntity(id) instanceof Player player ? player : null;
	}

	@Override
	protected String baseAction(boolean moving) {
		if (getCritterPose() == AWAKE && isNoGravity() && shoulderOf() == null && perch == null && !isInWater()) {
			return "fly";
		}
		return super.baseAction(moving && shoulderOf() == null);
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("sing", RawAnimation.begin().thenPlay(animation("sing")));
		actions.triggerableAnim("chirp", RawAnimation.begin().thenPlay(animation("chirp")));
		actions.triggerableAnim("shake", RawAnimation.begin().thenPlay(animation("shake")));
	}

	private boolean dawn() {
		long time = level().getOverworldClockTime() % 24000;
		return time >= 22500 || time < 1500;
	}

	// at dawn, or near the player it trusts, a whole song, and every bulbul around answers one after another.
	// otherwise a short chirp
	@Override
	public void playAmbientSound() {
		if (getCritterPose() == SLEEPING) {
			return;
		}
		boolean friend = trusted != null && level().getPlayerByUUID(trusted) instanceof Player player && distanceTo(player) < 8;
		if (dawn() || friend && getRandom().nextInt(3) == 0) {
			sing();
			if (dawn() && level() instanceof ServerLevel) {
				for (Kutilang other : level().getEntitiesOfClass(Kutilang.class, getBoundingBox().inflate(20), k -> k != this)) {
					if (other.answerIn == 0 && other.tickCount - other.lastSong > 300 && other.getCritterPose() == AWAKE) {
						other.answerIn = 30 + getRandom().nextInt(80);
					}
				}
			}
			return;
		}
		super.playAmbientSound();
		triggerAnim("actions", "chirp");
	}

	private void sing() {
		lastSong = tickCount;
		playSound(BlossomSounds.KUTILANG_SONG, 1.0f, getVoicePitch());
		triggerAnim("actions", "sing");
	}

	@Override
	public int getAmbientSoundInterval() {
		return dawn() ? 200 : 320;
	}

	// seeds win its trust: it hops up onto your shoulder. once it trusts you an empty hand calls it back up
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		boolean friend = player.getUUID().equals(trusted);
		if (!isBaby() && shoulderOf() == null && (isFood(held) && !friend || held.isEmpty() && friend && !player.isShiftKeyDown())) {
			if (level() instanceof ServerLevel serverLevel) {
				if (!held.isEmpty()) {
					usePlayerItem(player, hand, held);
				}
				trusted = player.getUUID();
				hopOn(player);
				serverLevel.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.4, getZ(), 3, 0.2, 0.1, 0.2, 0);
				playSound(BlossomSounds.KUTILANG_HAPPY, 0.9f, getVoicePitch());
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private void hopOn(Player player) {
		perch = null;
		getNavigation().stop();
		setCritterPose(AWAKE);
		entityData.set(SHOULDER, player.getId());
		shoulderTicks = 1200 + getRandom().nextInt(2400);
		playSound(BlossomSounds.KUTILANG_FLAP, 0.5f, getVoicePitch());
	}

	private void hopOff() {
		entityData.set(SHOULDER, -1);
		setDeltaMovement(getRandom().nextGaussian() * 0.1, 0.3, getRandom().nextGaussian() * 0.1);
		playSound(BlossomSounds.KUTILANG_FLAP, 0.5f, getVoicePitch());
	}

	@Override
	public void tick() {
		super.tick();
		// held on the shoulder on both sides, every tick, so it never lags behind its player
		Player player = shoulderOf();
		if (player != null) {
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			setPos(player.getX() - Mth.cos(yaw) * 0.38, player.getY() + player.getBbHeight() * 0.76, player.getZ() - Mth.sin(yaw) * 0.38);
			setYRot(player.yBodyRot);
			yBodyRot = player.yBodyRot;
			setDeltaMovement(Vec3.ZERO);
			resetFallDistance();
		} else if (perch != null) {
			setPos(perch.x, perch.y, perch.z);
			setDeltaMovement(Vec3.ZERO);
			resetFallDistance();
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (answerIn > 0 && --answerIn == 0 && getCritterPose() == AWAKE) {
			sing();
		}
		if (startled > 0) {
			startled--;
		}
		Player player = shoulderOf();
		if (entityData.get(SHOULDER) >= 0 && (player == null || !player.isAlive() || player.isShiftKeyDown() || player.isSleeping() || --shoulderTicks <= 0)) {
			hopOff();
		}
		// sings for the player it trusts every now and then
		if (player != null && getRandom().nextInt(700) == 0) {
			sing();
		}
		// someone sprinting by: everybody up into the air
		if (player == null && getCritterPose() != SLEEPING && tickCount % 5 == 0 && startled == 0) {
			Player runner = level().getNearestPlayer(this, 6);
			if (runner != null && runner.isSprinting() && !runner.isSpectator()) {
				for (Kutilang bird : level().getEntitiesOfClass(Kutilang.class, getBoundingBox().inflate(8), b -> b.shoulderOf() == null)) {
					bird.startled = 50 + getRandom().nextInt(20);
				}
			}
		}
		boolean flying = isNoGravity();
		if (flying && !wasFlying) {
			playSound(BlossomSounds.KUTILANG_FLAP, 0.35f, getVoicePitch());
		}
		wasFlying = flying;
		if (getCritterPose() == SPECIAL && getRandom().nextInt(6) == 0) {
			serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 4, 0.15, 0.05, 0.15, 0.1);
			if (getRandom().nextInt(4) == 0) {
				playSound(BlossomSounds.KUTILANG_FLAP, 0.4f, getVoicePitch() * 1.2f);
			}
		}
	}

	// sitting on a shoulder it doesn't shove its player about
	@Override
	protected void pushEntities() {
		if (shoulderOf() == null) {
			super.pushEntities();
		}
	}

	@Override
	public boolean isPushable() {
		return shoulderOf() == null && perch == null && super.isPushable();
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.PARROT_FOOD);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.KUTILANG.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? null : BlossomSounds.KUTILANG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.KUTILANG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.KUTILANG_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (trusted != null) {
			output.store("Trusted", UUIDUtil.CODEC, trusted);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		trusted = input.read("Trusted", UUIDUtil.CODEC).orElse(null);
	}

	// holds everything else still while it rides along
	private class ShoulderGoal extends Goal {
		ShoulderGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return entityData.get(SHOULDER) >= 0;
		}

		@Override
		public void start() {
			getNavigation().stop();
		}
	}

	// up and away from whoever came running, chirping the alarm
	private class StartleGoal extends Goal {
		StartleGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return startled > 0 && shoulderOf() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return startled > 0;
		}

		@Override
		public void start() {
			perch = null;
			if (getCritterPose() != AWAKE) {
				setCritterPose(AWAKE);
			}
			Player runner = level().getNearestPlayer(Kutilang.this, 10);
			Vec3 away = runner == null ? new Vec3(getRandom().nextGaussian(), 0, getRandom().nextGaussian()) : position().subtract(runner.position());
			away = away.multiply(1, 0, 1).normalize().scale(6);
			getMoveControl().setWantedPosition(getX() + away.x, getY() + 4 + getRandom().nextInt(3), getZ() + away.z, 1.6);
			playSound(BlossomSounds.KUTILANG_AMBIENT, 0.9f, getVoicePitch() * 1.3f);
			playSound(BlossomSounds.KUTILANG_FLAP, 0.7f, getVoicePitch());
		}

		@Override
		public void tick() {
			if (getMoveControl().hasWanted()) {
				return;
			}
			getMoveControl().setWantedPosition(getX() + getRandom().nextGaussian() * 2, getY() + 0.5, getZ() + getRandom().nextGaussian() * 2, 1.2);
		}
	}

	// the spot to sit on top of a perch block, null if it isn't one
	private static Double perchHeight(BlockState state) {
		if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)) {
			return 1.5;
		}
		if (state.is(Blocks.TRIPWIRE)) {
			return 0.06;
		}
		if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
			return 0.55;
		}
		if (state.is(BlockTags.LEAVES)) {
			return 1.0;
		}
		return null;
	}

	// flies over to a fence, a wall, a lantern, a wire or the top of a tree and sits there a while.
	// it roosts there for the night too
	private class PerchGoal extends Goal {
		private Vec3 spot;
		private int ticks;

		PerchGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (getCritterPose() != AWAKE || shoulderOf() != null || getRandom().nextInt(sleepyTime() ? 20 : 160) != 0) {
				return false;
			}
			BlockPos here = blockPosition();
			List<Vec3> spots = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(here.offset(-10, -4, -10), here.offset(10, 8, 10))) {
				Double height = perchHeight(level().getBlockState(pos));
				if (height != null && level().getBlockState(pos.above()).isAir()) {
					spots.add(new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5));
					if (spots.size() > 24) {
						break;
					}
				}
			}
			// no two birds on the very same spot
			spots.removeIf(s -> !level().getEntitiesOfClass(Kutilang.class, getBoundingBox().move(s.subtract(position())).inflate(0.3), b -> b != Kutilang.this).isEmpty());
			spot = spots.isEmpty() ? null : spots.get(getRandom().nextInt(spots.size()));
			return spot != null;
		}

		@Override
		public boolean canContinueToUse() {
			return (ticks > 0 || sleepyTime() && perch != null) && startled == 0 && hurtTime == 0 && shoulderOf() == null && perchHeight(level().getBlockState(BlockPos.containing(spot.x, spot.y - 0.1, spot.z))) != null;
		}

		@Override
		public void start() {
			ticks = 400 + getRandom().nextInt(900);
		}

		@Override
		public void tick() {
			if (perch == null) {
				if (position().distanceToSqr(spot) < 0.2) {
					perch = spot;
					getNavigation().stop();
					return;
				}
				// path round trees and walls from afar, the last bit straight onto the spot
				if (position().distanceToSqr(spot) > 2.5) {
					if (getNavigation().isDone()) {
						getNavigation().moveTo(spot.x, spot.y + 0.5, spot.z, 1.0);
					}
				} else {
					getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.8);
				}
				ticks--;
				return;
			}
			ticks--;
			// roosting at night
			if (sleepyTime() && getCritterPose() == AWAKE && getRandom().nextInt(100) == 0) {
				setCritterPose(SLEEPING);
			} else if (!sleepyTime() && getCritterPose() == SLEEPING) {
				setCritterPose(AWAKE);
			}
		}

		@Override
		public void stop() {
			perch = null;
			spot = null;
			if (getCritterPose() == SLEEPING) {
				setCritterPose(AWAKE);
			}
		}
	}

	// a little splash bath in shallow water on a sunny day, then a good shake
	private class BatheGoal extends Goal {
		private BlockPos pond;
		private int ticks;

		BatheGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (getCritterPose() != AWAKE || shoulderOf() != null || level().isDarkOutside() || getRandom().nextInt(600) != 0) {
				return false;
			}
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(here.offset(-10, -4, -10), here.offset(10, 3, 10))) {
				if (level().getFluidState(pos).is(FluidTags.WATER) && level().getBlockState(pos.below()).isSolid() && level().getBlockState(pos.above()).isAir()) {
					pond = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && startled == 0 && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 400;
		}

		@Override
		public void tick() {
			ticks--;
			if (getCritterPose() == SPECIAL) {
				if (ticks < 0 || !isInWater()) {
					ticks = 0;
				}
				return;
			}
			if (isInWater()) {
				getNavigation().stop();
				setCritterPose(SPECIAL);
				ticks = 60 + getRandom().nextInt(80);
				return;
			}
			getMoveControl().setWantedPosition(pond.getX() + 0.5, pond.getY() + 0.7, pond.getZ() + 0.5, 1.0);
		}

		@Override
		public void stop() {
			if (getCritterPose() == SPECIAL) {
				setCritterPose(AWAKE);
				triggerAnim("actions", "shake");
				if (level() instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 14, 0.2, 0.1, 0.2, 0.2);
				}
				playSound(BlossomSounds.KUTILANG_FLAP, 0.6f, getVoicePitch());
				getMoveControl().setWantedPosition(getX() + getRandom().nextGaussian(), getY() + 1.5, getZ() + getRandom().nextGaussian(), 1.0);
			}
			pond = null;
		}
	}

	// on the ground: a few hops this way and that, and a peck at whatever's there
	private class HopGoal extends Goal {
		private int hops;

		HopGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return getCritterPose() == AWAKE && onGround() && shoulderOf() == null && perch == null && getRandom().nextInt(60) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return hops > 0 && getCritterPose() == AWAKE;
		}

		@Override
		public void start() {
			hops = 2 + getRandom().nextInt(4);
		}

		@Override
		public void tick() {
			if (!onGround() || getRandom().nextInt(8) != 0) {
				return;
			}
			hops--;
			if (getRandom().nextInt(3) == 0) {
				triggerAnim("fidget", "peck");
				return;
			}
			float angle = getYRot() * Mth.DEG_TO_RAD + (getRandom().nextFloat() - 0.5f) * 2.4f;
			setYRot(angle * Mth.RAD_TO_DEG);
			yBodyRot = getYRot();
			setDeltaMovement(-Mth.sin(angle) * 0.13, 0.28, Mth.cos(angle) * 0.13);
		}
	}
}
