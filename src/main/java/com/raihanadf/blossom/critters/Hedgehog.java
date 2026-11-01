package com.raihanadf.blossom.critters;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// a pincushion bun with a pointy nose: snuffles through the grass rooting for treats, wears any fruit it rolls over
// on its spines (and gives it to you for a pet), and curls into a ball when someone runs at it, peeking out to check
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
		goalSelector.addGoal(6, new RootGoal());
		goalSelector.addGoal(6, new CuddleGoal(this));
	}

	@Override
	public String propBone() {
		return "fruit";
	}

	@Override
	public float propScale() {
		return 0.45f;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.HEDGEHOG_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.HEDGEHOG_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("sniff", "shake", "scratch");
	}

	private boolean isScared() {
		return tickCount - lastScared < CALM_DOWN_TICKS;
	}

	private static boolean isFruit(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE);
	}

	// whatever was stuck on its back is yours
	@Override
	protected void petted(ServerLevel level, Player player) {
		ItemStack fruit = getProp();
		if (fruit.isEmpty()) {
			return;
		}
		setProp(ItemStack.EMPTY);
		triggerAnim("fidget", "shake");
		ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.5, getZ(), fruit);
		Vec3 toward = player.position().subtract(position()).normalize().scale(0.2);
		drop.setDeltaMovement(toward.x, 0.25, toward.z);
		drop.setPickUpDelay(10);
		level.addFreshEntity(drop);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		int pose = getCritterPose();
		if (pose == SPECIAL && lastPose != SPECIAL) {
			playSound(BlossomSounds.HEDGEHOG_CURL, 0.7f, getVoicePitch());
		}
		// curled up, now and then it peeks out to see if it's safe
		if (pose == SPECIAL && getRandom().nextInt(30) == 0) {
			triggerAnim("actions", "peek");
		}
		lastPose = pose;
		if (tickCount % 5 != 0) {
			return;
		}
		boolean runningPlayer = !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(4),
				player -> player.isSprinting() && !player.isSpectator()).isEmpty();
		boolean monster = !level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(4)).isEmpty();
		if (runningPlayer || monster) {
			lastScared = tickCount;
		}
		// walking over a fallen berry or apple spikes it onto its quills
		if (getProp().isEmpty() && pose == AWAKE && !isBaby()) {
			List<ItemEntity> fruit = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.3), item -> isFruit(item.getItem()) && item.getAge() > 20);
			if (!fruit.isEmpty()) {
				ItemEntity item = fruit.getFirst();
				setProp(item.getItem().split(1));
				if (item.getItem().isEmpty()) {
					item.discard();
				}
				playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.6f);
				triggerAnim("actions", "happy");
			}
		}
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
		return getCritterPose() == AWAKE ? BlossomSounds.HEDGEHOG_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.HEDGEHOG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.HEDGEHOG_DEATH;
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("dig", RawAnimation.begin().thenPlay(animation("dig")));
		actions.triggerableAnim("peek", RawAnimation.begin().thenPlay(animation("peek")));
	}

	// stops on a patch of grass or leaves and roots about with its nose; sometimes it turns up a berry
	private class RootGoal extends Goal {
		private int ticks;

		RootGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private BlockState ground() {
			return level().getBlockState(blockPosition().below());
		}

		@Override
		public boolean canUse() {
			return getRandom().nextInt(300) == 0 && getCritterPose() == AWAKE && onGround()
					&& (ground().is(BlockTags.SUBSTRATE_OVERWORLD) || ground().is(BlockTags.LEAVES));
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && getCritterPose() == AWAKE && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 32;
			getNavigation().stop();
			triggerAnim("actions", "dig");
			playSound(BlossomSounds.HEDGEHOG_DIG, 0.6f, getVoicePitch());
		}

		@Override
		public void tick() {
			ticks--;
			Vec3 nose = position().add(Vec3.directionFromRotation(0, getYRot()).scale(0.4));
			if (ticks % 4 == 0 && level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground()), nose.x, getY() + 0.05, nose.z, 3, 0.1, 0.02, 0.1, 0.05);
			}
			if (ticks == 0 && getRandom().nextInt(5) == 0 && level() instanceof ServerLevel serverLevel) {
				BlockPos at = BlockPos.containing(nose);
				ItemEntity berry = new ItemEntity(serverLevel, at.getX() + 0.5, getY() + 0.2, at.getZ() + 0.5, new ItemStack(Items.SWEET_BERRIES));
				berry.setDeltaMovement(0, 0.2, 0);
				serverLevel.addFreshEntity(berry);
				playSound(BlossomSounds.HEDGEHOG_HAPPY, 0.6f, getVoicePitch());
			}
		}
	}
}
