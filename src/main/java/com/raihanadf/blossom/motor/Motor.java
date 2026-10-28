package com.raihanadf.blossom.motor;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a little motor bebek: W to go, S to brake and back up, A/D to steer, space for the horn and a hop.
// two can ride, the second one sits behind (boncengan). sneak-click with an empty hand to pick it up, use dye to repaint it
public class Motor extends Mob implements GeoEntity, PlayerRideableJumping {
	private static final EntityDataAccessor<DyeColor> COLOR = SynchedEntityData.defineId(Motor.class, EntityDataSerializers.DYE_COLOR);
	private static final RawAnimation DRIVE = RawAnimation.begin().thenLoop("animation.motor.drive");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	// -0.35 (reversing) .. 1 (full speed), worked out wherever the movement is simulated
	private float throttle;
	// visual only: how far it tilts into a turn
	private float lean;
	private float leanO;

	public Motor(EntityType<? extends Mob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.STEP_HEIGHT, 1.0);
	}

	public DyeColor getColor() {
		return entityData.get(COLOR);
	}

	public void setColor(DyeColor color) {
		entityData.set(COLOR, color);
	}

	public float getLean(float partialTick) {
		return Mth.lerp(partialTick, leanO, lean);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COLOR, DyeColor.RED);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		DyeColor dye = held.get(DataComponents.DYE);
		if (dye != null && dye != getColor()) {
			if (!level().isClientSide()) {
				setColor(dye);
				held.consume(1, player);
				playSound(SoundEvents.DYE_USE, 1f, 1f);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive() && held.isEmpty() && !isVehicle()) {
			if (level() instanceof ServerLevel) {
				ItemStack item = new ItemStack(BlossomItems.MOTOR);
				item.set(DataComponents.BASE_COLOR, getColor());
				if (!player.getInventory().add(item)) {
					spawnAtLocation((ServerLevel) level(), item);
				}
				playSound(SoundEvents.ITEM_PICKUP, 0.8f, 0.8f);
				discard();
			}
			return InteractionResult.SUCCESS;
		}
		if (getPassengers().size() < 2 && !player.isPassenger()) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player driver ? driver : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < 2;
	}

	// the driver sits in the middle, a second rider right behind
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int seat = getPassengers().indexOf(passenger);
		return new Vec3(0, 0.45, seat == 0 ? 0.05 : -0.5).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void tickRidden(Player driver, Vec3 riddenInput) {
		super.tickRidden(driver, riddenInput);
		float target = driver.zza > 0 ? 1f : driver.zza < 0 ? -0.35f : 0f;
		float step = Math.signum(target - throttle) == Math.signum(throttle) || target == 0 ? 0.03f : 0.08f;
		throttle = Math.abs(target - throttle) < step ? target : throttle + Math.signum(target - throttle) * step;
		// steering only works while rolling, flipped when backing up
		float turn = driver.xxa * 5f * Mth.clamp(Math.abs(throttle) * 3, 0, 1) * Math.signum(throttle == 0 ? 1 : throttle);
		setYRot(getYRot() - turn);
		yRotO = getYRot() + turn;
		setYBodyRot(getYRot());
		setYHeadRot(getYRot());
		setXRot(0);
	}

	@Override
	protected Vec3 getRiddenInput(Player driver, Vec3 selfInput) {
		return new Vec3(0, 0, throttle);
	}

	@Override
	protected float getRiddenSpeed(Player driver) {
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	public void tick() {
		super.tick();
		if (getControllingPassenger() == null) {
			throttle = 0;
		}
		leanO = lean;
		float turning = Mth.wrapDegrees(yRotO - getYRot());
		lean += (Mth.clamp(turning * 4, -22, 22) - lean) * 0.25f;
		if (level().isClientSide() && getDeltaMovement().horizontalDistanceSqr() > 0.002 && random.nextInt(3) == 0) {
			Vec3 pipe = new Vec3(0.2, 0.35, -0.75).yRot(-getYRot() * Mth.DEG_TO_RAD).add(position());
			level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.02, 0);
		}
	}

	@Override
	public boolean canJump() {
		return onGround();
	}

	// the rider's client simulates the motor, so the hop happens there; the horn plays from the server for everyone
	@Override
	public void onPlayerJump(int power) {
		if (onGround()) {
			setDeltaMovement(getDeltaMovement().add(0, 0.35, 0));
		}
	}

	@Override
	public void handleStartJump(int power) {
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 1f, 1.2f);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 1f, 1.0f);
	}

	@Override
	public void handleStopJump() {
	}

	public float getThrottle() {
		return throttle;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		ItemStack item = new ItemStack(BlossomItems.MOTOR);
		item.set(DataComponents.BASE_COLOR, getColor());
		spawnAtLocation(level, item);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Color", DyeColor.CODEC, getColor());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setColor(input.read("Color", DyeColor.CODEC).orElse(DyeColor.RED));
	}

	// the wheels turn as fast as it rolls
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Motor>("wheels", 0, test -> {
			Motor motor = test.animatable();
			double speed = motor.getDeltaMovement().horizontalDistance();
			if (speed < 0.01) {
				return PlayState.STOP;
			}
			test.setControllerSpeed((float) speed * 6f);
			return test.setAndContinue(DRIVE);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
