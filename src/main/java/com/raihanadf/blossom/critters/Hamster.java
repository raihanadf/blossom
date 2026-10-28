package com.raihanadf.blossom.critters;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// a round little hamster: scurries around, stuffs its cheeks with seeds it finds and hitches rides on lounging capybaras
public class Hamster extends Critter {
	private static final EntityDataAccessor<Boolean> STUFFED = SynchedEntityData.defineId(Hamster.class, EntityDataSerializers.BOOLEAN);
	private static final int CHEEK_TICKS = 1200;

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
		goalSelector.addGoal(6, new ForageGoal());
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
		return SoundEvents.RABBIT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RABBIT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RABBIT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.5f;
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
			List<ItemEntity> seeds = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8), item -> isFood(item.getItem()));
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
			playSound(SoundEvents.GENERIC_EAT.value(), 0.5f, 1.6f);
			triggerAnim("actions", "nibble");
		}

		@Override
		public void stop() {
			target = null;
			getNavigation().stop();
		}
	}
}
