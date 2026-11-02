package com.raihanadf.blossom.critters;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a plump little sawah duck. bebek waddle single file behind whoever leads (an older duck, or the angon with his
// bamboo pole), dabble tail-up in the water, shake themselves dry after, quack in chorus, flap happily in the rain
// and now and then lay an egg with a proud quack. white, brown-speckled, and fluffy yellow ducklings
public class Bebek extends Critter {
	public static final int WHITE = 0;
	public static final int BROWN = 1;

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Bebek.class, EntityDataSerializers.INT);

	// the duck (or angon) this one waddles behind, server side only
	private Entity ahead;
	// ticks left leading a walk, the others line up behind
	private int leading;
	// after a spell in line it potters about on its own for a while
	private int following;
	private int restFromLine;
	private int answerIn;
	private int eggTime = 6000 + (int) (Math.random() * 6000);
	private int layIn;
	private boolean wasWet;

	public Bebek(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.22);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COAT, WHITE);
	}

	@Override
	public String coat() {
		return isBaby() ? "baby" : entityData.get(COAT) == BROWN ? "brown" : "";
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		entityData.set(COAT, getRandom().nextInt(10) < 3 ? BROWN : WHITE);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// dabbling: tips up in the water for a snack from the bottom
		goalSelector.addGoal(2, new RestGoal(this, SPECIAL, 160, 60, 160, critter -> critter.isInWater()));
		goalSelector.addGoal(5, new FollowLineGoal());
		goalSelector.addGoal(6, new LeadGoal());
	}

	@Override
	public String propBone() {
		return "carry";
	}

	@Override
	protected SoundEvent stepSound() {
		return BlossomSounds.BEBEK_STEP;
	}

	@Override
	protected SoundEvent happySound() {
		return BlossomSounds.BEBEK_HAPPY;
	}

	@Override
	protected List<String> fidgets() {
		return List.of("preen", "flap", "look", "shake");
	}

	@Override
	protected void fidgeted(String name) {
		if (name.equals("flap")) {
			playSound(BlossomSounds.BEBEK_FLAP, 0.5f, getVoicePitch());
		}
	}

	@Override
	protected String baseAction(boolean moving) {
		if (getCritterPose() == AWAKE && isInWater()) {
			return "swim";
		}
		return super.baseAction(moving);
	}

	@Override
	protected void addActions(AnimationController<Critter> actions) {
		actions.triggerableAnim("quack", RawAnimation.begin().thenPlay(animation("quack")));
		actions.triggerableAnim("lay", RawAnimation.begin().thenPlay(animation("lay")));
		actions.triggerableAnim("flap", RawAnimation.begin().thenPlay(animation("flap")));
		actions.triggerableAnim("shake", RawAnimation.begin().thenPlay(animation("shake")));
	}

	// one quacks and the others around answer, one after another
	@Override
	public void playAmbientSound() {
		super.playAmbientSound();
		triggerAnim("actions", "quack");
		if (level() instanceof ServerLevel && getRandom().nextInt(2) == 0) {
			for (Bebek other : level().getEntitiesOfClass(Bebek.class, getBoundingBox().inflate(10), b -> b != this)) {
				if (other.answerIn == 0 && other.getCritterPose() == AWAKE && getRandom().nextInt(3) != 0) {
					other.answerIn = 6 + getRandom().nextInt(30);
				}
			}
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (answerIn > 0 && --answerIn == 0 && getCritterPose() == AWAKE) {
			playSound(BlossomSounds.BEBEK_AMBIENT, getSoundVolume(), getVoicePitch());
			triggerAnim("actions", "quack");
		}
		boolean wet = isInWater();
		if (wet && getCritterPose() == SPECIAL && getRandom().nextInt(25) == 0) {
			serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 6, 0.25, 0.05, 0.25, 0.1);
			playSound(BlossomSounds.BEBEK_SPLASH, 0.4f, getVoicePitch());
		}
		// straight out of the water: a good shake and a flap, droplets everywhere
		if (wasWet && !wet && onGround()) {
			triggerAnim("actions", "shake");
			playSound(BlossomSounds.BEBEK_SPLASH, 0.6f, getVoicePitch() * 1.1f);
			serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 18, 0.3, 0.2, 0.3, 0.2);
		}
		wasWet = wet;
		// ducks love the rain: a happy flap and a quack now and then
		if (getCritterPose() == AWAKE && !wet && getRandom().nextInt(400) == 0 && level().isRainingAt(blockPosition().above())) {
			triggerAnim("actions", "flap");
			playSound(BlossomSounds.BEBEK_FLAP, 0.6f, getVoicePitch());
			playSound(BlossomSounds.BEBEK_HAPPY, 0.8f, getVoicePitch());
		}
		// laying: a squat and a wobble, then the egg and a proud quack
		if (!isBaby() && getCritterPose() == AWAKE && onGround() && !wet && --eggTime <= 0 && layIn == 0) {
			layIn = 26;
			getNavigation().stop();
			triggerAnim("actions", "lay");
		}
		if (layIn > 0 && --layIn == 0) {
			spawnAtLocation(serverLevel, new ItemStack(Items.EGG));
			playSound(BlossomSounds.BEBEK_HAPPY, 1.0f, getVoicePitch());
			eggTime = 6000 + getRandom().nextInt(6000);
		}
		if (leading > 0) {
			leading--;
		}
		if (restFromLine > 0) {
			restFromLine--;
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.CHICKEN_FOOD);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Bebek baby = BlossomEntities.BEBEK.create(level, EntitySpawnReason.BREEDING);
		baby.entityData.set(COAT, getRandom().nextBoolean() ? entityData.get(COAT) : ((Bebek) partner).entityData.get(COAT));
		return baby;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return getCritterPose() == SLEEPING ? null : BlossomSounds.BEBEK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BlossomSounds.BEBEK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BlossomSounds.BEBEK_DEATH;
	}

	// ducks flutter down, they never get hurt by a fall
	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Coat", entityData.get(COAT));
		output.putInt("EggTime", eggTime);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(COAT, input.getIntOr("Coat", WHITE));
		eggTime = input.getIntOr("EggTime", 6000);
	}

	private static boolean angon(Entity entity) {
		return entity instanceof Warga warga && warga.getJob() == Warga.ANGON && warga.isAlive();
	}

	// someone at the head of a line: an angon at work, or a duck out leading a walk
	private static boolean leads(Entity entity) {
		return angon(entity) || entity instanceof Bebek bebek && bebek.isAlive() && (bebek.leading > 0 || bebek.ahead != null);
	}

	// the last one in the line that starts at `head`, so a newcomer joins at the back
	private Entity tailOf(Entity head) {
		List<Bebek> ducks = level().getEntitiesOfClass(Bebek.class, getBoundingBox().inflate(20), b -> b != this);
		Entity tail = head;
		for (int i = 0; i < 20; i++) {
			Entity current = tail;
			Bebek next = ducks.stream().filter(b -> b.ahead == current).findFirst().orElse(null);
			if (next == null) {
				return tail;
			}
			tail = next;
		}
		return tail;
	}

	// waddles in single file: lines up at the back of the nearest line and keeps a step behind the duck in front
	private class FollowLineGoal extends Goal {
		FollowLineGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public boolean canUse() {
			if (leading > 0 || restFromLine > 0 || getCritterPose() != AWAKE || getRandom().nextInt(20) != 0) {
				return false;
			}
			// an angon at work comes first, then a duck leading a walk (ducklings follow any grown-up out walking)
			List<Warga> herders = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(12), Bebek::angon);
			Entity head = herders.isEmpty() ? null : herders.getFirst();
			if (head == null) {
				List<Bebek> leaders = level().getEntitiesOfClass(Bebek.class, getBoundingBox().inflate(10), b -> b != Bebek.this && b.leading > 0);
				head = leaders.isEmpty() ? null : leaders.getFirst();
			}
			if (head == null) {
				return false;
			}
			ahead = tailOf(head);
			return ahead != Bebek.this;
		}

		@Override
		public boolean canContinueToUse() {
			return ahead != null && leads(ahead) && distanceTo(ahead) < 16 && getCritterPose() == AWAKE && following > 0;
		}

		@Override
		public void start() {
			following = 600 + getRandom().nextInt(900);
		}

		@Override
		public void tick() {
			following--;
			getLookControl().setLookAt(ahead, 20, 20);
			double distance = distanceTo(ahead);
			if (distance > 1.6) {
				getNavigation().moveTo(ahead, distance > 5 ? 1.3 : 1.0);
			} else {
				getNavigation().stop();
			}
		}

		@Override
		public void stop() {
			ahead = null;
			restFromLine = 300 + getRandom().nextInt(600);
			getNavigation().stop();
		}
	}

	// a grown-up takes the flock for a walk, to the water if there's some about
	private class LeadGoal extends Goal {
		LeadGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !isBaby() && ahead == null && getCritterPose() == AWAKE && getRandom().nextInt(500) == 0
					&& level().getEntitiesOfClass(Bebek.class, getBoundingBox().inflate(10), b -> b != Bebek.this).size() >= 1;
		}

		@Override
		public boolean canContinueToUse() {
			return leading > 0 && getCritterPose() == AWAKE;
		}

		@Override
		public void start() {
			leading = 400 + getRandom().nextInt(400);
			walk();
		}

		@Override
		public void tick() {
			if (getNavigation().isDone() && getRandom().nextInt(30) == 0) {
				walk();
			}
		}

		private void walk() {
			Vec3 to = null;
			// half the time somewhere wet
			if (getRandom().nextBoolean()) {
				for (BlockPos pos : BlockPos.withinManhattan(blockPosition(), 14)) {
					if (Math.abs(pos.getY() - getBlockY()) <= 3 && level().getFluidState(pos).is(FluidTags.WATER) && level().getBlockState(pos.above()).isAir()) {
						to = Vec3.atBottomCenterOf(pos);
						break;
					}
				}
			}
			if (to == null) {
				to = DefaultRandomPos.getPos(Bebek.this, 12, 4);
			}
			if (to != null) {
				getNavigation().moveTo(to.x, to.y, to.z, 0.8);
			}
		}

		@Override
		public void stop() {
			leading = 0;
		}
	}
}
