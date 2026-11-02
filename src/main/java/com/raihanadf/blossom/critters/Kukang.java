package com.raihanadf.blossom.critters;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// a fuzzy slow loris with huge ringed eyes. everything it does is in slow motion: it sleeps in a ball all day, at night
// it creeps up tree trunks and hangs under the leaves, licks nectar from flowers and nibbles berries held in both
// hands. when someone rushes at it the arms go up over its head and it freezes; a pet and it leans in, eyes shut.
// babies ride around on mum's back
public class Kukang extends Critter {
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Kukang.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> HANGING = SynchedEntityData.defineId(Kukang.class, EntityDataSerializers.BOOLEAN);

	private int frozen;
	private int rideTicks;

	public Kukang(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.12);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CLIMBING, false);
		builder.define(HANGING, false);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(1, new FreezeGoal());
		goalSelector.addGoal(4, new HangGoal());
		goalSelector.addGoal(5, new ForageGoal());
		goalSelector.addGoal(6, new ClimbGoal());
	}

	@Override
	public String propBone() {
		return "hands";
	}

	@Override
	public float propScale() {
		return 0.3f;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.KUKANG_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.KUKANG_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("blink", "look", "scratch", "yawn");
	}

	// nocturnal: curled up asleep all day
	@Override
	protected boolean sleepyTime() {
		return !level().isDarkOutside();
	}

	@Override
	protected String baseAction(boolean moving) {
		if (isPassenger() && getVehicle() instanceof Kukang) {
			return "cling";
		}
		if (entityData.get(HANGING)) {
			return "hang";
		}
		if (entityData.get(CLIMBING)) {
			return "climb";
		}
		return super.baseAction(moving);
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("lick", RawAnimation.begin().thenPlay(animation("lick")));
		actions.triggerableAnim("eat", RawAnimation.begin().thenPlay(animation("eat")));
	}

	// climbs like a spider, but only up trees
	@Override
	public boolean onClimbable() {
		return entityData.get(CLIMBING);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		entityData.set(CLIMBING, horizontalCollision && !isPassenger() && beside(BlockTags.LOGS) || entityData.get(CLIMBING) && horizontalCollision && beside(BlockTags.LEAVES));
		// up under the canopy: it pulls itself through onto the top of the leaves
		BlockPos overhead = BlockPos.containing(getX(), getY() + getBbHeight() + 0.1, getZ());
		if (entityData.get(CLIMBING) && level().getBlockState(overhead).is(BlockTags.LEAVES) && level().getBlockState(overhead.above()).isAir()) {
			setPos(getX(), overhead.getY() + 1, getZ());
			setDeltaMovement(0, 0, 0);
			entityData.set(CLIMBING, false);
			playSound(BlossomSounds.KUKANG_STEP, 0.4f, getVoicePitch());
		}
		if (frozen > 0) {
			frozen--;
		}
		// someone rushing up: arms over the head, and stay very very still
		if (getCritterPose() != SLEEPING && tickCount % 5 == 0) {
			Player rushing = level().getNearestPlayer(this, 5);
			if (rushing != null && (rushing.isSprinting() || rushing.getDeltaMovement().horizontalDistanceSqr() > 0.04) && !rushing.isSpectator()) {
				frozen = 80 + getRandom().nextInt(60);
			}
		}
		// babies hop onto mum's back for a ride, and off again after a while or once grown
		if (isBaby() && !isPassenger() && getRandom().nextInt(200) == 0) {
			List<Kukang> mums = level().getEntitiesOfClass(Kukang.class, getBoundingBox().inflate(3), k -> !k.isBaby() && !k.isVehicle());
			if (!mums.isEmpty() && startRiding(mums.getFirst())) {
				rideTicks = 600 + getRandom().nextInt(1000);
			}
		}
		if (isPassenger() && getVehicle() instanceof Kukang && (!isBaby() || --rideTicks <= 0)) {
			stopRiding();
		}
		if (getCritterPose() == SLEEPING && getRandom().nextInt(200) == 0) {
			serverLevel.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.6, getZ(), 1, 0.1, 0.1, 0.1, 0);
		}
	}

	// a log (or leaves) right against it, at its feet or at its head
	private boolean beside(TagKey<Block> tag) {
		BlockPos at = blockPosition();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level().getBlockState(at.relative(side)).is(tag) || level().getBlockState(at.above().relative(side)).is(tag)) {
				return true;
			}
		}
		return false;
	}

	// a baby rides tucked down low on mum's back
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, dimensions.height() * 0.7, 0.05);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.MELON_SLICE);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.KUKANG.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? null : BlossomSounds.KUKANG_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 500;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.KUKANG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.KUKANG_DEATH;
	}

	// slow loris voices are soft and a bit lower when they're relaxed
	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * (isBaby() ? 1.3f : 1.0f);
	}

	// arms up over the head, frozen until it feels safe
	private class FreezeGoal extends Goal {
		FreezeGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return frozen > 0 && getCritterPose() == AWAKE && !isPassenger();
		}

		@Override
		public boolean canContinueToUse() {
			return frozen > 0 && getCritterPose() == SPECIAL;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setCritterPose(SPECIAL);
			playSound(BlossomSounds.KUKANG_AMBIENT, 0.8f, getVoicePitch() * 1.25f);
		}

		@Override
		public void stop() {
			if (getCritterPose() == SPECIAL) {
				setCritterPose(AWAKE);
			}
		}
	}

	// at night it walks to a tree and creeps up the trunk into the leaves
	private class ClimbGoal extends Goal {
		private BlockPos trunk;
		private int ticks;

		ClimbGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (getCritterPose() != AWAKE || isBaby() || isPassenger() || standingOnLeaves() || getRandom().nextInt(300) != 0) {
				return false;
			}
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(here.offset(-10, -1, -10), here.offset(10, 1, 10))) {
				if (level().getBlockState(pos).is(BlockTags.LOGS) && level().getBlockState(pos.below()).isSolid() && !level().getBlockState(pos.below()).is(BlockTags.LOGS)) {
					trunk = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && getCritterPose() == AWAKE && !(standingOnLeaves() && onGround());
		}

		@Override
		public void start() {
			ticks = 900;
		}

		@Override
		public void tick() {
			ticks--;
			double flat = Vec3.atBottomCenterOf(trunk).subtract(position()).horizontalDistanceSqr();
			if (flat > 2.2 && !entityData.get(CLIMBING)) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(trunk.getX() + 0.5, trunk.getY(), trunk.getZ() + 0.5, 1.0);
				}
				return;
			}
			// keep pushing into the bark: that's what makes it climb
			getNavigation().stop();
			getMoveControl().setWantedPosition(trunk.getX() + 0.5, getY() + 1, trunk.getZ() + 0.5, 0.8);
		}

		@Override
		public void stop() {
			trunk = null;
		}
	}

	private boolean standingOnLeaves() {
		return level().getBlockState(blockPosition().below()).is(BlockTags.LEAVES);
	}

	// up in the canopy at night: hangs belly-up under a leaf block with nothing below it, swaying
	private class HangGoal extends Goal {
		private int ticks;

		HangGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return getCritterPose() == AWAKE && onGround() && standingOnLeaves() && level().getBlockState(blockPosition().below(2)).isAir()
					&& getRandom().nextInt(200) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && getCritterPose() == AWAKE && hurtTime == 0 && standingOnLeaves();
		}

		@Override
		public void start() {
			ticks = 300 + getRandom().nextInt(600);
			getNavigation().stop();
			entityData.set(HANGING, true);
		}

		@Override
		public void tick() {
			ticks--;
		}

		@Override
		public void stop() {
			entityData.set(HANGING, false);
		}
	}

	// licks nectar from a flower or picks a ripe berry and nibbles it from both hands
	private class ForageGoal extends Goal {
		private BlockPos food;
		private int eating;
		private int ticks;

		ForageGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (getCritterPose() != AWAKE || isPassenger() || getRandom().nextInt(250) != 0) {
				return false;
			}
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(here.offset(-8, -2, -8), here.offset(8, 2, 8))) {
				BlockState state = level().getBlockState(pos);
				if (state.is(BlockTags.FLOWERS) || state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 2) {
					food = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return food != null && ticks > 0 && getCritterPose() == AWAKE && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 400;
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(Vec3.atCenterOf(food));
			if (eating > 0) {
				if (--eating == 0) {
					setProp(ItemStack.EMPTY);
					food = null;
				}
				return;
			}
			// it reaches in from beside the plant, never walks into a thorny bush
			if (Vec3.atBottomCenterOf(food).subtract(position()).horizontalDistanceSqr() > 2.3) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(food.getX() + 0.5, food.getY(), food.getZ() + 0.5, 1.0);
				}
				return;
			}
			getNavigation().stop();
			BlockState state = level().getBlockState(food);
			ServerLevel serverLevel = (ServerLevel) level();
			if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 2) {
				serverLevel.setBlockAndUpdate(food, state.setValue(SweetBerryBushBlock.AGE, 1));
				setProp(new ItemStack(Items.SWEET_BERRIES));
				triggerAnim("actions", "eat");
				playSound(BlossomSounds.KUKANG_HAPPY, 0.6f, getVoicePitch());
				heal(2);
				eating = 48;
			} else if (state.is(BlockTags.FLOWERS)) {
				triggerAnim("actions", "lick");
				playSound(BlossomSounds.KUKANG_LICK, 0.6f, getVoicePitch());
				serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, food.getX() + 0.5, food.getY() + 0.6, food.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
				eating = 48;
			} else {
				food = null;
			}
		}

		@Override
		public void stop() {
			food = null;
			eating = 0;
			setProp(ItemStack.EMPTY);
		}
	}
}
