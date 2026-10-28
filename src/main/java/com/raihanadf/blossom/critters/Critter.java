package com.raihanadf.blossom.critters;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// shared behaviour for blossom's critters: they notice you, love pets, nap at night and have one special pose
public abstract class Critter extends Animal implements GeoEntity {
	public static final int AWAKE = 0;
	public static final int SLEEPING = 1;
	public static final int SPECIAL = 2;

	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int petCooldown;
	private boolean playerWasNear;

	protected Critter(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public int getCritterPose() {
		return entityData.get(POSE);
	}

	public void setCritterPose(int pose) {
		entityData.set(POSE, pose);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(POSE, AWAKE);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		// naps at night, until it gets bright or something bothers it
		goalSelector.addGoal(2, new RestGoal(this, SLEEPING, 120, 2400, 6000,
				critter -> critter.level().isDarkOutside() && critter.onGround() && !critter.isInWater()));
		goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		goalSelector.addGoal(4, new TemptGoal(this, 1.1, this::isFood, false));
		goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	// empty hand right click: a happy wiggle and some hearts
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!player.getItemInHand(hand).isEmpty()) {
			return super.mobInteract(player, hand);
		}
		if (level() instanceof ServerLevel serverLevel && petCooldown == 0) {
			petCooldown = 30;
			serverLevel.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0);
			playSound(getAmbientSound(), 0.8f, getVoicePitch() * 1.2f);
			if (getCritterPose() != SLEEPING) {
				triggerAnim("actions", "happy");
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		if (petCooldown > 0) {
			petCooldown--;
		}
		// perk up the moment a player walks up
		if (tickCount % 10 == 0) {
			boolean near = level().getNearestPlayer(this, 5) != null;
			if (near && !playerWasNear && getCritterPose() == AWAKE) {
				triggerAnim("actions", "notice");
			}
			playerWasNear = near;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (getCritterPose() == SLEEPING) {
			setCritterPose(AWAKE);
		}
		return super.hurtServer(level, source, damage);
	}

	// animations live in assets/blossom/geckolib/animations/entity/<name>.animation.json as animation.<name>.<action>
	protected String animation(String action) {
		return "animation." + BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath() + "." + action;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		RawAnimation idle = RawAnimation.begin().thenLoop(animation("idle"));
		RawAnimation walk = RawAnimation.begin().thenLoop(animation("walk"));
		RawAnimation sleep = RawAnimation.begin().thenLoop(animation("sleep"));
		RawAnimation special = RawAnimation.begin().thenPlayAndHold(animation("special"));
		controllers.add(new AnimationController<Critter>("base", 5, test -> {
			int pose = test.animatable().getCritterPose();
			return test.setAndContinue(pose == SLEEPING ? sleep : pose == SPECIAL ? special : test.isMoving() ? walk : idle);
		}));
		AnimationController<Critter> actions = new AnimationController<Critter>("actions", 3, test -> PlayState.STOP)
				.triggerableAnim("happy", RawAnimation.begin().thenPlay(animation("happy")))
				.triggerableAnim("notice", RawAnimation.begin().thenPlay(animation("notice")));
		addActions(actions);
		controllers.add(actions);
	}

	// critters with extra one-shot animations register them here
	protected void addActions(AnimationController<Critter> actions) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	// holds the critter in one pose (asleep, curled up, lounging) and keeps it from wandering off meanwhile
	public static class RestGoal extends Goal {
		private final Critter critter;
		private final int pose;
		private final int chance;
		private final int minTicks;
		private final int maxTicks;
		private final Predicate<Critter> condition;
		private int ticksLeft;

		public RestGoal(Critter critter, int pose, int chance, int minTicks, int maxTicks, Predicate<Critter> condition) {
			this.critter = critter;
			this.pose = pose;
			this.chance = chance;
			this.minTicks = minTicks;
			this.maxTicks = maxTicks;
			this.condition = condition;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return critter.getRandom().nextInt(chance) == 0 && critter.getCritterPose() == AWAKE && condition.test(critter);
		}

		@Override
		public boolean canContinueToUse() {
			return ticksLeft > 0 && critter.getCritterPose() == pose && critter.hurtTime == 0 && condition.test(critter);
		}

		@Override
		public void start() {
			ticksLeft = minTicks + critter.getRandom().nextInt(maxTicks - minTicks + 1);
			critter.getNavigation().stop();
			critter.setCritterPose(pose);
		}

		@Override
		public void tick() {
			ticksLeft--;
		}

		@Override
		public void stop() {
			if (critter.getCritterPose() == pose) {
				critter.setCritterPose(AWAKE);
			}
		}
	}
}
