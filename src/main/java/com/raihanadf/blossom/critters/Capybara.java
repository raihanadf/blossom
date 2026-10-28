package com.raihanadf.blossom.critters;

import com.raihanadf.blossom.registry.BlossomEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// the calmest critter around: lounges in the sun for ages, and a hamster may hop on its back while it does
public class Capybara extends Critter {
	public Capybara(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.18);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(6, new RestGoal(this, SPECIAL, 300, 300, 1200, capybara -> capybara.level().isBrightOutside() && capybara.onGround()));
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Hamster && getPassengers().isEmpty();
	}

	// the hamster sits right on top of its back, a bit lower while lounging
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, (getCritterPose() == AWAKE ? 10 : 7.2) / 16.0 * scale, 0);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.MELON_SLICE) || stack.is(Items.SUGAR_CANE);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.CAPYBARA.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CAMEL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.CAMEL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CAMEL_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.4f;
	}
}
