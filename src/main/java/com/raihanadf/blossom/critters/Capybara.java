package com.raihanadf.blossom.critters;

import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// a calm loaf with a tiny head-top: always chewing, lounges in the sun for ages, goes for a soak on warm days and shakes
// itself dry after, wears any flower you give it, and lets small critters doze on and against it
public class Capybara extends Critter {
	private boolean wasInWater;

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
		goalSelector.addGoal(6, new SoakGoal());
	}

	@Override
	public String propBone() {
		return "hat";
	}

	@Override
	public float propScale() {
		return 0.65f;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.CAPYBARA_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.CAPYBARA_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("flick", "yawn", "sniff", "shake");
	}

	@Override
	protected String baseAction(boolean moving) {
		return isInWater() && getCritterPose() == AWAKE ? "swim" : super.baseAction(moving);
	}

	// a flower goes on its head; sneak with an empty hand to take it back
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() instanceof BlockItem flower && flower.getBlock().defaultBlockState().is(BlockTags.SMALL_FLOWERS) && getProp().isEmpty()) {
			if (!level().isClientSide()) {
				setProp(held.consumeAndReturn(1, player));
				playSound(SoundEvents.AZALEA_LEAVES_PLACE, 0.8f, 1.3f);
				playSound(happySound(), 0.8f, getVoicePitch());
				triggerAnim("actions", "happy");
			}
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && player.isSecondaryUseActive() && !getProp().isEmpty()) {
			if (!level().isClientSide()) {
				player.getInventory().placeItemBackInInventory(getProp(), Prediction.SERVER_ONLY);
				setProp(ItemStack.EMPTY);
				playSound(SoundEvents.AZALEA_LEAVES_BREAK, 0.8f, 1.3f);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		// out of the water, a good shake
		boolean wet = isInWater();
		if (wasInWater && !wet && level() instanceof ServerLevel serverLevel) {
			triggerAnim("fidget", "shake");
			playSound(SoundEvents.WOLF_SHAKE, 0.6f, 0.8f);
			serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + getBbHeight() * 0.7, getZ(), 20, getBbWidth() * 0.4, 0.2, getBbWidth() * 0.4, 0.1);
		}
		wasInWater = wet;
		if (getCritterPose() != SLEEPING && getRandom().nextInt(120) == 0) {
			playSound(BlossomSounds.CAPYBARA_CHEW, 0.35f, getVoicePitch());
		}
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Hamster && getPassengers().isEmpty();
	}

	// the hamster sits right on top of its back, a bit lower while lounging
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, (getCritterPose() == AWAKE ? 12 : 9.4) / 16.0 * scale, 0.05);
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
		return 300;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return BlossomSounds.CAPYBARA_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.CAPYBARA_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.CAPYBARA_DEATH;
	}

	// on a sunny day it ambles to the nearest pond and floats there for a long soak
	private class SoakGoal extends Goal {
		private BlockPos water;
		private int ticks;

		SoakGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (getRandom().nextInt(800) != 0 || getCritterPose() != AWAKE || isInWater() || !level().isBrightOutside()) {
				return false;
			}
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.withinManhattan(here, 10)) {
				if (Math.abs(pos.getY() - here.getY()) <= 3 && level().getFluidState(pos).is(FluidTags.WATER) && level().getBlockState(pos.above()).isAir()) {
					water = pos.immutable();
					ticks = 600 + getRandom().nextInt(1200);
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && getCritterPose() == AWAKE && hurtTime == 0;
		}

		@Override
		public void start() {
			getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
		}

		@Override
		public void tick() {
			if (!isInWater()) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
					ticks -= 20;
				}
				return;
			}
			// just float there, chewing
			getNavigation().stop();
			ticks--;
		}

		@Override
		public void stop() {
			water = null;
		}
	}
}
