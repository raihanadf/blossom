package com.raihanadf.blossom.critters;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.ArrayList;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// a chubby green dumpling with big golden eyes on top. kodok get together in little bands (biola, angklung, kendang)
// that play one song in time while others dance along, go fishing now and then in a caping hat and hand you the catch
// for a pet, hold a talas leaf over their heads in the rain and croak in chorus at dusk, one starting and the rest answering
public class Kodok extends Critter {
	public static final int NONE = 0;
	public static final int BIOLA = 1;
	public static final int ANGKLUNG = 2;
	public static final int KENDANG = 3;
	public static final int FISHING = 4;
	public static final int DANCE = 5;
	// one bar of the band's song: 8 beats at 100 bpm. every stem starts on this grid, so all bands play in time
	public static final int LOOP = 96;
	private static final List<Integer> INSTRUMENTS = List.of(BIOLA, ANGKLUNG, KENDANG);

	private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(Kodok.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> UMBRELLA = SynchedEntityData.defineId(Kodok.class, EntityDataSerializers.BOOLEAN);

	private int answerIn;
	private int lastCroak = -1000;
	// after a set or a dance it does something else for a while, so bands break up and form again
	private int restUntil;

	public Kodok(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.22);
	}

	public int getActivity() {
		return entityData.get(ACTIVITY);
	}

	private void setActivity(int activity) {
		entityData.set(ACTIVITY, activity);
	}

	private boolean playing() {
		return INSTRUMENTS.contains(getActivity());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTIVITY, NONE);
		builder.define(UMBRELLA, false);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(6, new BandGoal());
		goalSelector.addGoal(6, new DanceGoal());
		goalSelector.addGoal(6, new FishGoal());
	}

	@Override
	public String propBone() {
		return "catch";
	}

	@Override
	public float propScale() {
		return 0.75f;
	}

	@Override
	public List<String> hiddenBones() {
		int activity = getActivity();
		List<String> hidden = new ArrayList<>(6);
		if (activity != BIOLA) {
			hidden.add("violin");
			hidden.add("bow");
		}
		if (activity != ANGKLUNG) hidden.add("angklung");
		if (activity != KENDANG) hidden.add("kendang");
		if (activity != FISHING) {
			hidden.add("rod");
			hidden.add("caping");
		}
		if (!entityData.get(UMBRELLA)) hidden.add("leaf");
		return hidden;
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.KODOK_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.KODOK_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("blink", "tongue", "look", "scratch");
	}

	@Override
	protected void fidgeted(String name) {
		if (name.equals("tongue")) {
			playSound(BlossomSounds.KODOK_TONGUE, 0.6f, getVoicePitch());
		}
	}

	// up late croaking, asleep only in the small hours
	@Override
	protected boolean sleepyTime() {
		long time = level().getOverworldClockTime() % 24000;
		return time >= 18000 && time < 23000;
	}

	private boolean dusk() {
		long time = level().getOverworldClockTime() % 24000;
		return time >= 11500 && time < 18000;
	}

	@Override
	protected String baseAction(boolean moving) {
		return switch (getActivity()) {
			case BIOLA -> "play_violin";
			case ANGKLUNG -> "play_angklung";
			case KENDANG -> "play_kendang";
			case FISHING -> "fish";
			case DANCE -> "dance";
			default -> super.baseAction(moving);
		};
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		super.registerControllers(controllers);
		// the talas leaf goes up over the head in the rain, on top of whatever else it's doing
		RawAnimation umbrella = RawAnimation.begin().thenLoop(animation("umbrella"));
		controllers.add(new AnimationController<Kodok>("umbrella", 6,
				test -> test.animatable().entityData.get(UMBRELLA) ? test.setAndContinue(umbrella) : PlayState.STOP));
		// a catch is held up for everyone to see until someone pets it
		RawAnimation hold = RawAnimation.begin().thenLoop(animation("hold"));
		controllers.add(new AnimationController<Kodok>("hold", 5,
				test -> !test.animatable().getProp().isEmpty() && test.animatable().getActivity() == NONE ? test.setAndContinue(hold) : PlayState.STOP));
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("croak", RawAnimation.begin().thenPlay(animation("croak")));
		actions.triggerableAnim("cast", RawAnimation.begin().thenPlay(animation("cast")));
		actions.triggerableAnim("reel", RawAnimation.begin().thenPlay(animation("reel")));
	}

	// a croak puffs the throat out, and kodok nearby answer one after another
	@Override
	public void playAmbientSound() {
		if (playing()) {
			return;
		}
		super.playAmbientSound();
		lastCroak = tickCount;
		triggerAnim("actions", "croak");
		if (dusk() && level() instanceof ServerLevel) {
			for (Kodok other : level().getEntitiesOfClass(Kodok.class, getBoundingBox().inflate(16), k -> k != this)) {
				if (other.answerIn == 0 && other.tickCount - other.lastCroak > 200 && other.getCritterPose() == AWAKE) {
					other.answerIn = 10 + getRandom().nextInt(40);
				}
			}
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return dusk() ? 160 : 360;
	}

	// a pet while it's holding a fish: the fish is yours
	@Override
	protected void petted(ServerLevel level, Player player) {
		ItemStack fish = getProp();
		if (fish.isEmpty()) {
			return;
		}
		setProp(ItemStack.EMPTY);
		ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.6, getZ(), fish);
		Vec3 toward = player.position().subtract(position()).normalize().scale(0.2);
		drop.setDeltaMovement(toward.x, 0.25, toward.z);
		drop.setPickUpDelay(10);
		level.addFreshEntity(drop);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (answerIn > 0 && --answerIn == 0 && getCritterPose() == AWAKE) {
			playAmbientSound();
		}
		if (tickCount % 20 == 0) {
			entityData.set(UMBRELLA, level().isRainingAt(blockPosition().above()));
		}
		if (playing()) {
			// every stem starts on the shared loop grid, so all the kodok within earshot play one song together
			if (level().getGameTime() % LOOP == 0) {
				SoundEvent stem = switch (getActivity()) {
					case BIOLA -> BlossomSounds.KODOK_BIOLA;
					case ANGKLUNG -> BlossomSounds.KODOK_ANGKLUNG;
					default -> BlossomSounds.KODOK_KENDANG;
				};
				level().playSound(null, getX(), getY(), getZ(), stem, SoundSource.RECORDS, 2.0f, 1.0f);
			}
			if (getRandom().nextInt(14) == 0) {
				serverLevel.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.4, getZ(), 0, getRandom().nextInt(24) / 24.0, 0, 0, 1);
			}
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SLIME_BALL);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return BlossomEntities.KODOK.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? null : BlossomSounds.KODOK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.KODOK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.KODOK_DEATH;
	}

	// a kodok that could do something with its hands right now
	private boolean free() {
		return getActivity() == NONE && getCritterPose() == AWAKE && !isBaby() && getProp().isEmpty() && onGround();
	}

	// instruments already taken by the band around `player`
	private List<Integer> bandAround(Kodok player) {
		List<Integer> taken = new ArrayList<>();
		for (Kodok k : level().getEntitiesOfClass(Kodok.class, player.getBoundingBox().inflate(5), Kodok::playing)) {
			taken.add(k.getActivity());
		}
		return taken;
	}

	// gets an instrument out and plays: joins a band nearby that still misses one, or starts a new one on the spot
	private class BandGoal extends Goal {
		private Kodok band;
		private int ticks;
		private int travel;

		BandGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private boolean fine() {
			return level().isBrightOutside() && !entityData.get(UMBRELLA) && hurtTime == 0 && getCritterPose() == AWAKE;
		}

		@Override
		public boolean canUse() {
			if (!free() || !fine() || tickCount < restUntil) {
				return false;
			}
			List<Kodok> playing = level().getEntitiesOfClass(Kodok.class, getBoundingBox().inflate(16), Kodok::playing);
			band = null;
			for (Kodok k : playing) {
				if (bandAround(k).size() < INSTRUMENTS.size()) {
					band = k;
					break;
				}
			}
			// a band that still misses someone draws others in fast, starting one is rarer
			return band != null ? getRandom().nextInt(20) == 0 : playing.isEmpty() && getRandom().nextInt(900) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && travel > 0 && fine();
		}

		@Override
		public void start() {
			ticks = 600 + getRandom().nextInt(800);
			travel = 200;
			if (band == null) {
				setActivity(INSTRUMENTS.get(getRandom().nextInt(INSTRUMENTS.size())));
			}
		}

		@Override
		public void tick() {
			if (playing()) {
				getNavigation().stop();
				ticks--;
				Kodok mate = band;
				if (mate == null || !mate.isAlive()) {
					List<Kodok> others = level().getEntitiesOfClass(Kodok.class, getBoundingBox().inflate(5), k -> k != Kodok.this && k.playing());
					mate = others.isEmpty() ? null : others.getFirst();
				}
				if (mate != null) {
					getLookControl().setLookAt(mate);
				}
				return;
			}
			travel--;
			getLookControl().setLookAt(band);
			if (distanceToSqr(band) > 2.5 * 2.5) {
				if (getNavigation().isDone()) {
					// stand round the band in a little circle, not on top of each other
					double angle = getRandom().nextDouble() * Mth.TWO_PI;
					getNavigation().moveTo(band.getX() + Math.cos(angle) * 1.8, band.getY(), band.getZ() + Math.sin(angle) * 1.8, 1.1);
				}
				return;
			}
			// arrived: pick up whatever the band still lacks, someone may have just taken it
			List<Integer> taken = bandAround(band);
			List<Integer> missing = new ArrayList<>(INSTRUMENTS);
			missing.removeAll(taken);
			if (missing.isEmpty()) {
				ticks = 0;
				return;
			}
			getNavigation().stop();
			setActivity(missing.get(getRandom().nextInt(missing.size())));
			triggerAnim("actions", "croak");
			playSound(BlossomSounds.KODOK_HAPPY, 0.6f, getVoicePitch());
		}

		@Override
		public void stop() {
			band = null;
			if (playing()) {
				setActivity(NONE);
				restUntil = tickCount + 1200 + getRandom().nextInt(1200);
			}
		}
	}

	// a band playing nearby: hop over and dance along
	private class DanceGoal extends Goal {
		private Kodok band;
		private int ticks;

		DanceGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private Kodok nearestBand() {
			List<Kodok> playing = level().getEntitiesOfClass(Kodok.class, getBoundingBox().inflate(10), Kodok::playing);
			return playing.isEmpty() ? null : playing.getFirst();
		}

		@Override
		public boolean canUse() {
			if (getActivity() != NONE || getCritterPose() != AWAKE || tickCount < restUntil || getRandom().nextInt(120) != 0) {
				return false;
			}
			band = nearestBand();
			return band != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && band.isAlive() && band.playing() && hurtTime == 0 && getCritterPose() == AWAKE;
		}

		@Override
		public void start() {
			ticks = 200 + getRandom().nextInt(400);
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(band);
			if (distanceToSqr(band) > 4.5 * 4.5) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(band, 1.1);
				}
				return;
			}
			getNavigation().stop();
			if (getActivity() != DANCE) {
				setActivity(DANCE);
			}
		}

		@Override
		public void stop() {
			band = null;
			if (getActivity() == DANCE) {
				setActivity(NONE);
				restUntil = tickCount + 400 + getRandom().nextInt(600);
			}
		}
	}

	// now and then it walks to the water's edge, puts its caping on and fishes; a catch is held up and given to whoever pets it
	private class FishGoal extends Goal {
		private BlockPos water;
		private BlockPos shore;
		private int travel;
		private int wait;
		private int casts;

		FishGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (!free() || sleepyTime() || getRandom().nextInt(700) != 0) {
				return false;
			}
			BlockPos here = blockPosition();
			for (BlockPos pos : BlockPos.withinManhattan(here, 14)) {
				if (Math.abs(pos.getY() - here.getY()) > 3 || !level().getFluidState(pos).is(FluidTags.WATER) || !level().getBlockState(pos.above()).isAir()) {
					continue;
				}
				// a dry spot right beside the water, one block up, with ground under it
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos stand = pos.relative(side).above();
					if (level().getBlockState(stand).isAir() && level().getBlockState(stand.above()).isAir()
							&& level().getBlockState(stand.below()).isFaceSturdy(level(), stand.below(), Direction.UP)) {
						water = pos.immutable();
						shore = stand.immutable();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return water != null && travel > 0 && hurtTime == 0 && getCritterPose() == AWAKE && getProp().isEmpty();
		}

		@Override
		public void start() {
			travel = 300;
			casts = 1 + getRandom().nextInt(3);
			wait = 0;
			getNavigation().moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5, 1.0);
		}

		@Override
		public void tick() {
			Vec3 target = Vec3.atBottomCenterOf(water).add(0, 1, 0);
			if (getActivity() != FISHING) {
				travel--;
				if (distanceToSqr(Vec3.atBottomCenterOf(shore)) > 1.2) {
					if (getNavigation().isDone()) {
						getNavigation().moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5, 1.0);
					}
					return;
				}
				getNavigation().stop();
				setActivity(FISHING);
				cast(target);
				return;
			}
			// face the water, sit and wait for a bite
			float yaw = (float) (Mth.atan2(target.z - getZ(), target.x - getX()) * Mth.RAD_TO_DEG) - 90;
			setYRot(yaw);
			setYBodyRot(yaw);
			setYHeadRot(yaw);
			getLookControl().setLookAt(target.x, target.y - 0.5, target.z);
			if (--wait > 0) {
				return;
			}
			ServerLevel level = (ServerLevel) level();
			level.sendParticles(ParticleTypes.SPLASH, target.x, target.y, target.z, 12, 0.2, 0, 0.2, 0.1);
			triggerAnim("actions", "reel");
			if (getRandom().nextFloat() < 0.55f) {
				// a bite: held up in its hand until someone pets it
				int roll = getRandom().nextInt(20);
				Item fish = roll < 11 ? Items.COD : roll < 18 ? Items.SALMON : roll < 19 ? Items.TROPICAL_FISH : Items.PUFFERFISH;
				setProp(new ItemStack(fish));
				playSound(BlossomSounds.KODOK_CATCH, 0.8f, 1.0f);
				playSound(BlossomSounds.KODOK_HAPPY, 0.8f, getVoicePitch());
				travel = 0;
				return;
			}
			if (--casts <= 0) {
				travel = 0;
				return;
			}
			cast(target);
		}

		private void cast(Vec3 target) {
			triggerAnim("actions", "cast");
			playSound(BlossomSounds.KODOK_CAST, 0.6f, 1.0f);
			((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, target.x, target.y, target.z, 6, 0.1, 0, 0.1, 0.05);
			wait = 100 + getRandom().nextInt(220);
		}

		@Override
		public void stop() {
			water = null;
			shore = null;
			getNavigation().stop();
			if (getActivity() == FISHING) {
				setActivity(NONE);
			}
			if (!getProp().isEmpty()) {
				triggerAnim("actions", "happy");
			}
		}
	}
}
