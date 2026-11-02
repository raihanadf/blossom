package com.raihanadf.blossom.critters;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.raihanadf.blossom.furniture.SeatBlock;
import com.raihanadf.blossom.furniture.SeatEntity;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a kampung cat with the short kinked tail. naps curled on chairs and by warm fires, loafs in the sun, rubs round
// your legs purring, sits and stares at the cook until a fish comes its way, brings a flower to someone it likes,
// pounces (gently) on hamsters and frogs, chatters at birds and hisses at monsters
public class Kucing extends Critter {
	public static final int TABBY = 0;
	public static final int CALICO = 1;
	public static final int TUXEDO = 2;
	private static final List<String> COATS = List.of("", "calico", "tuxedo");

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Kucing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(Kucing.class, EntityDataSerializers.INT);
	private static final int STANDING = 0;
	private static final int SITTING = 1;
	private static final int STALKING = 2;

	// pets in a row, past a few it brings you a present
	private int fondness;
	private Player giftFor;

	public Kucing(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.28);
	}

	// kampung cats stay around their kampung
	public void adoptHome(BlockPos center, int radius) {
		setHomeTo(center, radius);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COAT, TABBY);
		builder.define(STANCE, STANDING);
	}

	private void setStance(int stance) {
		entityData.set(STANCE, stance);
	}

	@Override
	public String coat() {
		return COATS.get(entityData.get(COAT));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		int roll = getRandom().nextInt(10);
		entityData.set(COAT, roll < 5 ? TABBY : roll < 8 ? CALICO : TUXEDO);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(1, new HissGoal());
		goalSelector.addGoal(2, new NapGoal());
		// a loaf in a sunny spot
		goalSelector.addGoal(2, new RestGoal(this, SPECIAL, 500, 300, 900,
				critter -> !critter.level().isDarkOutside() && critter.onGround() && critter.level().canSeeSky(critter.blockPosition())));
		goalSelector.addGoal(3, new GiftGoal());
		goalSelector.addGoal(4, new EatFishGoal());
		goalSelector.addGoal(5, new BegGoal());
		goalSelector.addGoal(5, new RubGoal());
		goalSelector.addGoal(6, new PounceGoal());
		goalSelector.addGoal(6, new WatchBirdGoal());
	}

	@Override
	public String propBone() {
		return "mouth";
	}

	@Override
	public float propScale() {
		return 0.35f;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.KUCING_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.KUCING_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("groom", "yawn", "stretch", "swish", "knead", "blink");
	}

	// cats doze a lot: all night, and a nap in the afternoon
	@Override
	protected boolean sleepyTime() {
		long time = level().getOverworldClockTime() % 24000;
		return time >= 13000 || time < 500 || (time >= 6000 && time < 8000);
	}

	@Override
	protected String baseAction(boolean moving) {
		if (getCritterPose() == AWAKE) {
			int stance = entityData.get(STANCE);
			if (stance == SITTING && !moving) {
				return "sit";
			}
			if (stance == STALKING) {
				return "stalk";
			}
		}
		return super.baseAction(moving);
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("pounce", RawAnimation.begin().thenPlay(animation("pounce")));
		actions.triggerableAnim("hiss", RawAnimation.begin().thenPlay(animation("hiss")));
		actions.triggerableAnim("rub", RawAnimation.begin().thenPlay(animation("rub")));
		actions.triggerableAnim("chatter", RawAnimation.begin().thenPlay(animation("chatter")));
		actions.triggerableAnim("eat", RawAnimation.begin().thenPlay(animation("eat")));
	}

	// a pet: a purr and the slow blink, and every few pets in a row it grows fonder of you
	@Override
	protected void petted(ServerLevel level, Player player) {
		playSound(BlossomSounds.KUCING_PURR, 0.8f, getVoicePitch());
		triggerAnim("fidget", "blink");
		if (++fondness >= 4 && giftFor == null && getRandom().nextInt(2) == 0) {
			giftFor = player;
			fondness = 0;
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		// a cat on a chair: the seat goes as soon as it wakes up
		if (!level().isClientSide() && isPassenger() && getVehicle() instanceof SeatEntity && getCritterPose() != SLEEPING) {
			stopRiding();
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.CAT_FOOD);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Kucing kitten = BlossomEntities.KUCING.create(level, EntitySpawnReason.BREEDING);
		kitten.entityData.set(COAT, getRandom().nextBoolean() ? entityData.get(COAT) : ((Kucing) partner).entityData.get(COAT));
		return kitten;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? BlossomSounds.KUCING_PURR : BlossomSounds.KUCING_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.KUCING_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.KUCING_DEATH;
	}

	// cats land on their feet
	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Coat", entityData.get(COAT));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(COAT, input.getIntOr("Coat", TABBY));
	}

	private boolean idle() {
		return getCritterPose() == AWAKE && !isPassenger() && giftFor == null;
	}

	// a cosy spot nearby: a free chair, a bed, or the ground right next to a lit campfire
	private BlockPos cosySpot() {
		BlockPos here = blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(here.offset(-8, -2, -8), here.offset(8, 2, 8))) {
			BlockState state = level().getBlockState(pos);
			if (state.getBlock() instanceof SeatBlock && !SeatEntity.isTaken(level(), pos)
					|| state.is(BlockTags.BEDS)
					|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
				return pos.immutable();
			}
		}
		return null;
	}

	// walks over to a chair, a bed or the fire and curls up there; just where it stands when there's nothing cosy about
	private class NapGoal extends Goal {
		private BlockPos spot;
		private int ticks;

		NapGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!idle() || !sleepyTime() || !onGround() || isInWater() || getRandom().nextInt(80) != 0) {
				return false;
			}
			spot = cosySpot();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && hurtTime == 0 && sleepyTime() && giftFor == null;
		}

		@Override
		public void start() {
			ticks = 1200 + getRandom().nextInt(2400);
		}

		@Override
		public void tick() {
			ticks--;
			if (getCritterPose() == SLEEPING) {
				return;
			}
			if (spot != null && distanceToSqr(Vec3.atBottomCenterOf(spot)) > 2.5) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
				}
				if (ticks % 200 == 0) {
					// couldn't get there, any spot will do
					spot = null;
				}
				return;
			}
			getNavigation().stop();
			if (spot != null && level().getBlockState(spot).getBlock() instanceof SeatBlock chair) {
				SeatEntity.sit((ServerLevel) level(), spot, chair.getSeatHeight(), getYRot(), Kucing.this);
			} else if (spot != null && level().getBlockState(spot).is(BlockTags.BEDS)) {
				setPos(spot.getX() + 0.5, spot.getY() + 0.5625, spot.getZ() + 0.5);
			}
			setCritterPose(SLEEPING);
		}

		@Override
		public void stop() {
			spot = null;
			if (getCritterPose() == SLEEPING) {
				setCritterPose(AWAKE);
				// and the stretch when it gets up
				triggerAnim("fidget", "stretch");
			}
			if (isPassenger()) {
				stopRiding();
			}
		}
	}

	// sits by the dapur cook or the warung and stares; every now and then a fish comes flying its way
	private class BegGoal extends Goal {
		private Warga cook;
		private int ticks;

		BegGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (!idle() || getRandom().nextInt(300) != 0) {
				return false;
			}
			List<Warga> cooks = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(14),
					w -> w.getJob() == Warga.COOK || w.getJob() == Warga.VENDOR);
			cook = cooks.isEmpty() ? null : cooks.getFirst();
			return cook != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && cook.isAlive() && idle() && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 300 + getRandom().nextInt(400);
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(cook, 30, 30);
			if (distanceTo(cook) > 2.2) {
				setStance(STANDING);
				getNavigation().moveTo(cook, 1.0);
				return;
			}
			getNavigation().stop();
			setStance(SITTING);
			if (getRandom().nextInt(120) == 0) {
				playSound(BlossomSounds.KUCING_AMBIENT, 0.7f, getVoicePitch() * 1.15f);
			}
			// the cook gives in
			if (getRandom().nextInt(500) == 0) {
				cook.wave();
				ItemEntity fish = new ItemEntity(level(), cook.getX(), cook.getEyeY() - 0.3, cook.getZ(), new ItemStack(Items.COD));
				Vec3 toward = position().subtract(cook.position()).normalize().scale(0.15);
				fish.setDeltaMovement(toward.x, 0.2, toward.z);
				fish.setPickUpDelay(40);
				level().addFreshEntity(fish);
				ticks = 0;
			}
		}

		@Override
		public void stop() {
			setStance(STANDING);
			cook = null;
		}
	}

	// a fish on the ground: trot over and eat it, very pleased
	private class EatFishGoal extends Goal {
		private ItemEntity fish;
		private int chewing;

		EatFishGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!idle() || getRandom().nextInt(10) != 0) {
				return false;
			}
			List<ItemEntity> fishes = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8), i -> isFood(i.getItem()));
			fish = fishes.isEmpty() ? null : fishes.getFirst();
			return fish != null;
		}

		@Override
		public boolean canContinueToUse() {
			return (fish.isAlive() || chewing > 0) && hurtTime == 0;
		}

		@Override
		public void tick() {
			if (chewing > 0) {
				if (--chewing % 6 == 0 && level() instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.COD), getX(), getY() + 0.3, getZ(), 3, 0.1, 0.05, 0.1, 0.05);
				}
				return;
			}
			getLookControl().setLookAt(fish, 30, 30);
			if (distanceTo(fish) > 1.2) {
				getNavigation().moveTo(fish, 1.2);
				return;
			}
			getNavigation().stop();
			fish.getItem().shrink(1);
			if (fish.getItem().isEmpty()) {
				fish.discard();
			}
			heal(4);
			chewing = 28;
			triggerAnim("actions", "eat");
			playSound(BlossomSounds.KUCING_HAPPY, 0.8f, getVoicePitch());
		}

		@Override
		public void stop() {
			fish = null;
			chewing = 0;
		}
	}

	// winds round your legs, tail up, purring
	private class RubGoal extends Goal {
		private Player player;
		private int ticks;

		RubGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (!idle() || getRandom().nextInt(fondness > 0 ? 150 : 400) != 0) {
				return false;
			}
			player = level().getNearestPlayer(Kucing.this, 6);
			return player != null && !player.isSprinting() && !player.isSpectator();
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && player.isAlive() && !player.isSprinting() && distanceTo(player) < 8 && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 140;
		}

		@Override
		public void tick() {
			ticks--;
			// two slow laps round the player's feet
			double angle = ticks * Mth.TWO_PI / 70;
			getNavigation().moveTo(player.getX() + Math.cos(angle) * 0.9, player.getY(), player.getZ() + Math.sin(angle) * 0.9, 0.8);
			getLookControl().setLookAt(player, 30, 30);
			if (ticks % 35 == 0) {
				triggerAnim("actions", "rub");
				playSound(BlossomSounds.KUCING_PURR, 0.6f, getVoicePitch());
			}
		}

		@Override
		public void stop() {
			player = null;
			getNavigation().stop();
		}
	}

	// creeps up low on a hamster, a frog, a duckling or a stray item, then the pounce. never hurts anyone
	private class PounceGoal extends Goal {
		private Entity prey;
		private int ticks;
		private boolean leapt;

		PounceGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (!idle() || isBaby() && getRandom().nextInt(2) == 0 || getRandom().nextInt(400) != 0) {
				return false;
			}
			List<Entity> toys = level().getEntities(Kucing.this, getBoundingBox().inflate(8),
					e -> e instanceof Hamster || e instanceof Kodok || e instanceof Bebek bebek && bebek.isBaby() || e instanceof ItemEntity);
			prey = toys.isEmpty() ? null : toys.get(getRandom().nextInt(toys.size()));
			return prey != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && prey.isAlive() && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 200;
			leapt = false;
			setStance(STALKING);
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(prey, 30, 30);
			if (leapt) {
				if (onGround() && ticks < 180) {
					// the pat: the toy jumps, the cat looks very pleased with itself
					if (distanceTo(prey) < 1.6) {
						prey.push(0, 0.3, 0);
						if (prey instanceof Critter critter) {
							critter.triggerAnim("actions", "notice");
						}
					}
					playSound(BlossomSounds.KUCING_HAPPY, 0.7f, getVoicePitch());
					ticks = 0;
				}
				return;
			}
			double distance = distanceTo(prey);
			if (distance > 2.5) {
				getNavigation().moveTo(prey, 0.5);
				return;
			}
			getNavigation().stop();
			// the wiggle, then the leap
			if (ticks % 20 == 0 && getRandom().nextInt(2) == 0) {
				setStance(STANDING);
				triggerAnim("actions", "pounce");
				Vec3 leap = prey.position().subtract(position()).normalize().scale(0.45);
				setDeltaMovement(leap.x, 0.42, leap.z);
				leapt = true;
				ticks = 200;
			}
		}

		@Override
		public void stop() {
			setStance(STANDING);
			prey = null;
		}
	}

	// sits up and chatters at a bird it can't reach
	private class WatchBirdGoal extends Goal {
		private Kutilang bird;
		private int ticks;

		WatchBirdGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (!idle() || getRandom().nextInt(100) != 0) {
				return false;
			}
			List<Kutilang> birds = level().getEntitiesOfClass(Kutilang.class, getBoundingBox().inflate(10));
			bird = birds.isEmpty() ? null : birds.getFirst();
			return bird != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && bird.isAlive() && distanceTo(bird) < 14 && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 100 + getRandom().nextInt(100);
			getNavigation().stop();
			setStance(SITTING);
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(bird, 30, 30);
			if (ticks % 30 == 0 && getRandom().nextInt(2) == 0) {
				triggerAnim("actions", "chatter");
				playSound(BlossomSounds.KUCING_CHIRP, 0.7f, getVoicePitch());
			}
		}

		@Override
		public void stop() {
			setStance(STANDING);
			bird = null;
		}
	}

	// a present for its favourite person: picks a flower nearby (or a feather if there are none) and drops it at their feet
	private class GiftGoal extends Goal {
		private BlockPos flower;
		private int ticks;

		GiftGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return giftFor != null && getCritterPose() == AWAKE;
		}

		@Override
		public boolean canContinueToUse() {
			return giftFor != null && giftFor.isAlive() && ticks > 0 && hurtTime == 0;
		}

		@Override
		public void start() {
			ticks = 900;
			flower = null;
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(here.offset(-10, -2, -10), here.offset(10, 2, 10))) {
				if (level().getBlockState(pos).is(BlockTags.SMALL_FLOWERS)) {
					flower = pos.immutable();
					break;
				}
			}
			if (flower == null) {
				setProp(new ItemStack(Items.FEATHER));
			}
		}

		@Override
		public void tick() {
			ticks--;
			if (getProp().isEmpty() && flower != null) {
				if (distanceToSqr(Vec3.atBottomCenterOf(flower)) > 2.0) {
					getNavigation().moveTo(flower.getX() + 0.5, flower.getY(), flower.getZ() + 0.5, 1.0);
					return;
				}
				BlockState picked = level().getBlockState(flower);
				setProp(new ItemStack(picked.is(BlockTags.SMALL_FLOWERS) ? picked.getBlock().asItem() : Items.DANDELION));
				return;
			}
			getLookControl().setLookAt(giftFor, 30, 30);
			if (distanceTo(giftFor) > 1.8) {
				getNavigation().moveTo(giftFor, 1.1);
				return;
			}
			getNavigation().stop();
			ItemEntity gift = new ItemEntity(level(), getX(), getY() + 0.3, getZ(), getProp());
			gift.setPickUpDelay(10);
			level().addFreshEntity(gift);
			setProp(ItemStack.EMPTY);
			playSound(BlossomSounds.KUCING_AMBIENT, 0.9f, getVoicePitch() * 1.2f);
			triggerAnim("actions", "rub");
			giftFor = null;
		}

		@Override
		public void stop() {
			giftFor = null;
			if (!getProp().isEmpty() && level() instanceof ServerLevel serverLevel) {
				spawnAtLocation(serverLevel, getProp());
				setProp(ItemStack.EMPTY);
			}
		}
	}

	// a monster comes close: back arched, a hiss, then off it runs
	private class HissGoal extends AvoidEntityGoal<Monster> {
		HissGoal() {
			super(Kucing.this, Monster.class, 7, 1.1, 1.4);
		}

		@Override
		public void start() {
			super.start();
			if (getCritterPose() != AWAKE) {
				setCritterPose(AWAKE);
			}
			triggerAnim("actions", "hiss");
			playSound(BlossomSounds.KUCING_HURT, 0.8f, getVoicePitch() * 0.8f);
		}
	}
}
