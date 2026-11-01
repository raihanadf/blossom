package com.raihanadf.blossom.critters;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// a fist-sized peach with ears: scurries about, stuffs its cheeks with seeds it finds (and shares one if you pet it),
// sits up and begs when you hold seeds, gets the zoomies, naps against capybaras and rides them
public class Hamster extends Critter {
	private static final EntityDataAccessor<Boolean> STUFFED = SynchedEntityData.defineId(Hamster.class, EntityDataSerializers.BOOLEAN);
	private static final int CHEEK_TICKS = 2400;

	private int cheekTicks;
	private int rideTicks;
	private int rideLimit;

	public Hamster(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(STUFFED, false);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(3, new BegGoal());
		goalSelector.addGoal(6, new ForageGoal());
		goalSelector.addGoal(6, new ZoomiesGoal());
		goalSelector.addGoal(6, new CuddleGoal(this));
	}

	@Override
	public String propBone() {
		return "head";
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.HAMSTER_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.HAMSTER_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("groom", "scratch", "look", "yawn");
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.CHICKEN_FOOD);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.HAMSTER.create(level, EntitySpawnReason.BREEDING);
	}

	// it rides along, the capybara decides where to go
	@Override
	public boolean canControlVehicle() {
		return false;
	}

	// full cheeks and a pet: it digs one seed out and gives it to you
	@Override
	protected void petted(ServerLevel level, Player player) {
		if (!entityData.get(STUFFED)) {
			return;
		}
		entityData.set(STUFFED, false);
		cheekTicks = 0;
		ItemEntity seed = new ItemEntity(level, getX(), getY() + 0.3, getZ(), new ItemStack(getRandom().nextBoolean() ? Items.WHEAT_SEEDS : BlossomBlocks.RICE));
		Vec3 toward = player.position().subtract(position()).normalize().scale(0.18);
		seed.setDeltaMovement(toward.x, 0.22, toward.z);
		seed.setPickUpDelay(10);
		level.addFreshEntity(seed);
		playSound(BlossomSounds.HAMSTER_MUNCH, 0.6f, 1.3f);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		if (cheekTicks > 0 && --cheekTicks == 0) {
			entityData.set(STUFFED, false);
		}
		if (getVehicle() instanceof Capybara capybara) {
			// hop off when the capybara gets up or after a while
			if (rideTicks++ == 0) {
				rideLimit = 400 + getRandom().nextInt(800);
			}
			if (rideTicks > rideLimit || capybara.getCritterPose() != SPECIAL) {
				stopRiding();
			}
			return;
		}
		rideTicks = 0;
		if (getRandom().nextInt(400) == 0 && getCritterPose() == AWAKE && !isBaby()) {
			List<Capybara> lounging = level().getEntitiesOfClass(Capybara.class, getBoundingBox().inflate(3),
					capybara -> capybara.getCritterPose() == SPECIAL && !capybara.isVehicle());
			if (!lounging.isEmpty()) {
				startRiding(lounging.getFirst());
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? null : BlossomSounds.HAMSTER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.HAMSTER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.HAMSTER_DEATH;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		super.registerControllers(controllers);
		RawAnimation cheeks = RawAnimation.begin().thenLoop(animation("cheeks"));
		controllers.add(new AnimationController<Hamster>("cheeks", 4,
				test -> test.animatable().entityData.get(STUFFED) ? test.setAndContinue(cheeks) : PlayState.STOP));
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("nibble", RawAnimation.begin().thenPlay(animation("nibble")));
	}

	// sits up on its hind legs and begs whenever someone close by holds seeds
	private class BegGoal extends Goal {
		private Player player;

		BegGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		private boolean tempting(Player player) {
			return player != null && player.isAlive() && !player.isSpectator() && distanceToSqr(player) < 3.5 * 3.5
					&& (isFood(player.getMainHandItem()) || isFood(player.getOffhandItem()));
		}

		@Override
		public boolean canUse() {
			player = level().getNearestPlayer(Hamster.this, 3.5);
			return getCritterPose() == AWAKE && !isPassenger() && tempting(player);
		}

		@Override
		public boolean canContinueToUse() {
			return tempting(player) && hurtTime == 0;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setCritterPose(SPECIAL);
		}

		@Override
		public void tick() {
			getLookControl().setLookAt(player);
			if (getRandom().nextInt(50) == 0) {
				playSound(BlossomSounds.HAMSTER_AMBIENT, 0.5f, getVoicePitch() * 1.1f);
			}
		}

		@Override
		public void stop() {
			player = null;
			if (getCritterPose() == SPECIAL) {
				setCritterPose(AWAKE);
			}
		}
	}

	// spots seeds lying on the ground, runs over and stuffs them in its cheeks
	private class ForageGoal extends Goal {
		private ItemEntity target;

		ForageGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (cheekTicks > 0 || getCritterPose() != AWAKE || getRandom().nextInt(5) != 0) {
				return false;
			}
			List<ItemEntity> seeds = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8), item -> isFood(item.getItem()) && item.getAge() > 40);
			target = seeds.isEmpty() ? null : seeds.getFirst();
			return target != null;
		}

		@Override
		public boolean canContinueToUse() {
			return target.isAlive() && cheekTicks == 0 && getCritterPose() == AWAKE;
		}

		@Override
		public void tick() {
			getLookControl().setLookAt(target);
			if (distanceToSqr(target) > 1.2) {
				getNavigation().moveTo(target, 1.2);
				return;
			}
			target.getItem().shrink(1);
			if (target.getItem().isEmpty()) {
				target.discard();
			}
			cheekTicks = CHEEK_TICKS;
			entityData.set(STUFFED, true);
			playSound(BlossomSounds.HAMSTER_MUNCH, 0.6f, getVoicePitch());
			triggerAnim("actions", "nibble");
		}

		@Override
		public void stop() {
			target = null;
			getNavigation().stop();
		}
	}

	// every so often it bolts around in little circles for no reason at all
	private class ZoomiesGoal extends Goal {
		private int laps;

		ZoomiesGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return getRandom().nextInt(1200) == 0 && getCritterPose() == AWAKE && onGround() && !isPassenger();
		}

		@Override
		public boolean canContinueToUse() {
			return laps > 0 && getCritterPose() == AWAKE;
		}

		@Override
		public void start() {
			laps = 4 + getRandom().nextInt(4);
			playSound(BlossomSounds.HAMSTER_HAPPY, 0.7f, getVoicePitch() * 1.2f);
		}

		@Override
		public void tick() {
			if (!getNavigation().isDone()) {
				return;
			}
			Vec3 to = DefaultRandomPos.getPos(Hamster.this, 4, 1);
			if (to != null) {
				getNavigation().moveTo(to.x, to.y, to.z, 2.0);
			}
			if (level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 2, 0.1, 0, 0.1, 0.01);
			}
			laps--;
		}

		@Override
		public void stop() {
			getNavigation().stop();
			triggerAnim("actions", "happy");
		}
	}
}
