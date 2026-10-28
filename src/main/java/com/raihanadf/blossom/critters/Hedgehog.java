package com.raihanadf.blossom.critters;

import com.raihanadf.blossom.registry.BlossomEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

// a shy hedgehog: snuffles around and rolls into a spiky ball when someone runs at it or a monster comes close
public class Hedgehog extends Critter {
	private static final int CALM_DOWN_TICKS = 60;

	private int lastScared = -CALM_DOWN_TICKS;
	private int lastPose = AWAKE;

	public Hedgehog(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(1, new RestGoal(this, SPECIAL, 1, 40, 40, hedgehog -> ((Hedgehog) hedgehog).isScared()));
	}

	private boolean isScared() {
		return tickCount - lastScared < CALM_DOWN_TICKS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() || tickCount % 5 != 0) {
			return;
		}
		boolean runningPlayer = !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(4),
				player -> player.isSprinting() && !player.isSpectator()).isEmpty();
		boolean monster = !level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(4)).isEmpty();
		if (runningPlayer || monster) {
			lastScared = tickCount;
		}
		int pose = getCritterPose();
		if (pose == SPECIAL && lastPose != SPECIAL) {
			playSound(SoundEvents.ARMADILLO_ROLL, 0.7f, 1.4f);
		}
		lastPose = pose;
	}

	// curled up it takes a lot less damage
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		lastScared = tickCount;
		return super.hurtServer(level, source, getCritterPose() == SPECIAL ? damage * 0.3f : damage);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.HEDGEHOG.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SPECIAL ? null : SoundEvents.ARMADILLO_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMADILLO_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ARMADILLO_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.3f;
	}
}
