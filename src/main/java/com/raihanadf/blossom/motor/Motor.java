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
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a little motor bebek, a vehicle like a boat or minecart (no health bar, it wobbles when hit and breaks into its item).
// W to go, S to brake and back up, A/D to steer, space for the horn and a hop. two can ride, the second one sits behind
// (boncengan). sneak-click with an empty hand to pick it up, use dye to repaint it
public class Motor extends VehicleEntity implements GeoEntity, PlayerRideableJumping {
	private static final EntityDataAccessor<DyeColor> COLOR = SynchedEntityData.defineId(Motor.class, EntityDataSerializers.DYE_COLOR);
	private static final RawAnimation DRIVE = RawAnimation.begin().thenLoop("animation.motor.drive");
	private static final double TOP_SPEED = 0.55;
	private static final double REVERSE_SPEED = 0.15;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	// -1 (reversing) .. 1 (full speed), only meaningful where the movement is simulated (the driver's client)
	private float throttle;
	// how much it turned this tick, passed on to the riders so they turn with it
	private float turned;
	// visual only: how far it tilts into a turn
	private float lean;
	private float leanO;

	public Motor(EntityType<?> type, Level level) {
		super(type, level);
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

	// how fast it actually travelled last tick, the same on every client however it is moved
	public double getSpeed() {
		return Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COLOR, DyeColor.RED);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
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
			if (level() instanceof ServerLevel serverLevel) {
				ItemStack item = asItem();
				if (!player.getInventory().add(item)) {
					spawnAtLocation(serverLevel, item);
				}
				playSound(SoundEvents.ITEM_PICKUP, 0.8f, 0.8f);
				discard();
			}
			return InteractionResult.SUCCESS;
		}
		if (getPassengers().size() < 2 && !player.isPassenger() && !player.isSecondaryUseActive()) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private ItemStack asItem() {
		ItemStack item = new ItemStack(BlossomItems.MOTOR);
		item.set(DataComponents.BASE_COLOR, getColor());
		item.set(DataComponents.CUSTOM_NAME, getCustomName());
		return item;
	}

	@Override
	protected Item getDropItem() {
		return BlossomItems.MOTOR;
	}

	// broken motors drop their item in their own colour
	@Override
	protected void destroy(ServerLevel level, DamageSource source) {
		kill(level);
		spawnAtLocation(level, asItem());
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

	// riders turn along with the motor and their bodies always face forward
	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() + turned);
			passenger.setYHeadRot(passenger.getYHeadRot() + turned);
		}
		if (passenger instanceof LivingEntity rider) {
			rider.setYBodyRot(getYRot());
		}
	}

	@Override
	public void tick() {
		if (getHurtTime() > 0) {
			setHurtTime(getHurtTime() - 1);
		}
		if (getDamage() > 0) {
			setDamage(getDamage() - 1);
		}
		super.tick();
		leanO = lean;
		turned = 0;
		if (isLocalInstanceAuthoritative()) {
			// the driver's client steers; on the server an empty motor just rolls to a stop
			if (level().isClientSide() && getControllingPassenger() instanceof Player driver) {
				steer(driver);
			} else {
				throttle = 0;
			}
			Vec3 forward = Vec3.directionFromRotation(0, getYRot());
			double target = throttle >= 0 ? throttle * TOP_SPEED : throttle * REVERSE_SPEED;
			Vec3 motion = getDeltaMovement();
			Vec3 flat = new Vec3(motion.x, 0, motion.z).lerp(forward.scale(target), onGround() ? 0.25 : 0.04);
			double fall = isInWater() ? motion.y * 0.5 - 0.01 : motion.y - 0.08;
			setDeltaMovement(flat.x, fall, flat.z);
			move(MoverType.SELF, getDeltaMovement());
			if (onGround() && getDeltaMovement().y < 0) {
				setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
			}
		} else {
			setDeltaMovement(Vec3.ZERO);
		}
		lean += (Mth.clamp(Mth.wrapDegrees(yRotO - getYRot()) * 4, -22, 22) - lean) * 0.25f;
		if (level().isClientSide() && getSpeed() > 0.04 && random.nextInt(3) == 0) {
			Vec3 pipe = new Vec3(0.2, 0.35, -0.75).yRot(-getYRot() * Mth.DEG_TO_RAD).add(position());
			level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.02, 0);
		}
	}

	private void steer(Player driver) {
		float target = driver.zza > 0 ? 1f : driver.zza < 0 ? -1f : 0f;
		// eases into full throttle, brakes harder than it accelerates
		float step = target == 0 || Math.signum(target) != Math.signum(throttle) ? 0.08f : 0.03f;
		throttle = Math.abs(target - throttle) < step ? target : throttle + Math.signum(target - throttle) * step;
		// steering only works while rolling, flipped when backing up
		float rolling = (float) Mth.clamp(getDeltaMovement().horizontalDistance() * 8, 0, 1);
		turned = -driver.xxa * 5f * rolling * (throttle < 0 ? -1 : 1);
		setYRot(getYRot() + turned);
	}

	// other players see it glide smoothly between the positions the driver sends
	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public float maxUpStep() {
		return 1.0f;
	}

	@Override
	public boolean canJump() {
		return onGround();
	}

	// the driver's client simulates the motor, so the hop happens there; the horn plays from the server for everyone
	@Override
	public void onPlayerJump(int power) {
		if (onGround()) {
			setDeltaMovement(getDeltaMovement().add(0, 0.42, 0));
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

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public ItemStack getPickResult() {
		return asItem();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("Color", DyeColor.CODEC, getColor());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setColor(input.read("Color", DyeColor.CODEC).orElse(DyeColor.RED));
	}

	// the wheels turn as fast as it rolls
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Motor>("wheels", 0, test -> {
			double speed = test.animatable().getSpeed();
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
