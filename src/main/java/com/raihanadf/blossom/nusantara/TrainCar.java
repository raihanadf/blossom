package com.raihanadf.blossom.nusantara;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.TrainSchedule.State;
import com.raihanadf.blossom.nusantara.TrainSchedule.Train;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// one car of a running train: the cc201 at the front or one of the four passenger coaches behind it. the server puts it where the
// timetable says every tick, following the curves and grades of its line; clients glide it smoothly between updates.
// cars are never saved: the timetable brings them back whenever someone is near the line
public class TrainCar extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TrainCar.class, EntityDataSerializers.INT);
	private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("animation.train.roll");

	// seats, in blocks from the middle of the car: (across, along). the loco has the driver's seat and a second one
	private static final List<double[]> COACH_SEATS = List.of(
			new double[] {-0.75, -5}, new double[] {0.75, -5}, new double[] {-0.75, -3}, new double[] {0.75, -3},
			new double[] {-0.75, -1}, new double[] {0.75, -1}, new double[] {-0.75, 1}, new double[] {0.75, 1},
			new double[] {-0.75, 3}, new double[] {0.75, 3}, new double[] {-0.75, 5}, new double[] {0.75, 5});
	private static final List<double[]> PANORAMIC_SEATS = List.of(
			new double[] {-0.75, -5}, new double[] {0.75, -5}, new double[] {-0.75, -3}, new double[] {0.75, -3},
			new double[] {-0.75, -1}, new double[] {0.75, -1}, new double[] {-0.75, 1}, new double[] {0.75, 1},
			new double[] {-0.75, 3}, new double[] {0.75, 3}, new double[] {-0.75, 5}, new double[] {0.75, 5});
	private static final List<double[]> LOCO_SEATS = List.of(new double[] {-0.6, -2.4}, new double[] {0.6, -2.4});
	// the coach benches sit lower than the driver's seat up in the cab
	private static final double SEAT_HEIGHT = 1.25;
	private static final double CAB_SEAT_HEIGHT = 1.4;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// the server's side of things: which chain (a row or column and one of its lines), which train and which car
	private boolean row;
	private int fixed;
	private int line;
	private Train train;
	private int car;
	private boolean populated;
	// whether the line ahead was still waiting to be radioed clear last time the loco looked
	private boolean waitingForLine;
	private @Nullable State state;

	public TrainCar(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static TrainCar create(ServerLevel level, Chain chain, Train train, int car) {
		TrainCar entity = new TrainCar(BlossomEntities.TRAIN_CAR, level);
		entity.row = chain.row;
		entity.fixed = chain.fixed;
		State at = TrainSchedule.state(chain, train, level.getGameTime());
		entity.line = at == null ? chain.anchor : at.line();
		entity.train = train;
		entity.car = car;
		int kind = car == 0 ? 0 : (car == 2 && Math.floorMod(train.id(), 3) == 0 ? 2 : 1);
		entity.entityData.set(KIND, kind);
		entity.follow(chain, level.getGameTime());
		entity.setOldPosAndRot();
		return entity;
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public boolean isLoco() {
		return kind() == 0;
	}

	public @Nullable Train train() {
		return train;
	}

	public int car() {
		return car;
	}

	public @Nullable State state() {
		return state;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 1);
	}

	public static @Nullable NusantaraNetwork network(Level level) {
		if (level instanceof ServerLevel server && server.getChunkSource().getGenerator().getBiomeSource() instanceof TropicalBiomes tropical) {
			return tropical.network(server);
		}
		return null;
	}

	public @Nullable Chain chain() {
		NusantaraNetwork network = network(level());
		return network == null || train == null ? null : network.chainAt(row, fixed, line);
	}

	// where the timetable puts a car right now: x, y, z, yaw and pitch (null when the train is not on this chain now)
	public static double @Nullable [] place(Chain chain, Train train, int car, double time) {
		State state = TrainSchedule.state(chain, train, time);
		if (state == null) {
			return null;
		}
		int lane = TrainSchedule.laneOf(state);
		double along;
		double offset = Double.NaN;
		// the loco faces the way the train heads (the coaches don't care which way round they are)
		boolean backwards = car == 0 && !state.along();
		// how far a loco on the turntable has swung round from facing the way it came in
		double turned = -1;
		if (car == 0 && state.turning()) {
			double[] loco = TrainSchedule.locoTurning(chain, state);
			along = loco[0];
			offset = loco[1];
			turned = loco[2];
			// it came in facing against the way it leaves, and faces the way it leaves once round
			backwards = turned < 0.5 ? state.along() : !state.along();
		} else {
			along = TrainSchedule.carAlong(state, car);
		}
		double[] p = chain.point(along, lane);
		if (Double.isNaN(offset)) {
			offset = p[5];
		}
		double x = p[0] - p[3] * offset;
		double z = p[1] + p[2] * offset;
		// on the turntable it swings smoothly from the way it came in round to the way it leaves
		float swing = state.along() ? (float) (180 * (1 - turned)) : (float) (180 * turned);
		float yaw = (float) (Mth.atan2(-p[2], p[3]) * Mth.RAD_TO_DEG) + (turned >= 0 ? swing : backwards ? 180 : 0);
		double rise = chain.point(along + 5, lane)[4] - chain.point(along - 5, lane)[4];
		float pitch = (float) (-Math.atan2(rise, 10) * Mth.RAD_TO_DEG) * (backwards ? -1 : 1);
		return new double[] {x, p[4] + 1, z, yaw, pitch};
	}

	// stand where the timetable puts this car, turned along the line and tilted with its grade
	private boolean follow(Chain chain, double time) {
		state = TrainSchedule.state(chain, train, time);
		double[] at = place(chain, train, car, time);
		if (state == null || at == null) {
			return false;
		}
		// from now on look at the line from where the train is
		line = state.line();
		setPos(at[0], at[1], at[2]);
		setYRot((float) at[3]);
		setXRot((float) at[4]);
		return true;
	}

	public double speed() {
		if (level().isClientSide()) {
			return Math.hypot(getX() - xo, getZ() - zo);
		}
		return state == null ? 0 : state.speed();
	}

	// the hitbox covers the car whichever way it is turned
	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		double half = (isLoco() ? 11 : 12) / 2.0;
		double yaw = getYRot() * Mth.DEG_TO_RAD;
		double hx = Math.abs(Math.sin(yaw)) * half + Math.abs(Math.cos(yaw)) * 1.4;
		double hz = Math.abs(Math.cos(yaw)) * half + Math.abs(Math.sin(yaw)) * 1.4;
		return new AABB(position.x - hx, position.y, position.z - hz, position.x + hx, position.y + 3.7, position.z + hz);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		Vec3 before = position();
		float yawBefore = getYRot();
		AABB boxBefore = getBoundingBox();
		super.tick();
		if (level() instanceof ServerLevel level) {
			Chain chain = chain();
			// the train has reached the end of its chain and turned back as a new train: this one is done
			if (chain == null || !follow(chain, level.getGameTime())) {
				for (Entity rider : List.copyOf(getPassengers())) {
					if (rider instanceof ServerPlayer player) {
						player.stopRiding();
						player.sendOverlayMessage(Component.translatable("message.blossom.train.terminus"));
					} else {
						rider.discard();
					}
				}
				discard();
				return;
			}
			serverTick(level, chain);
		}
		setBoundingBox(makeBoundingBox(position()));
		carry(before, yawBefore, boxBefore);
	}

	// whoever stands on the roof rides along: moved by how far the car went this tick and turned with it. the server
	// moves mobs, a player's own game moves the player (players move themselves)
	private void carry(Vec3 before, float yawBefore, AABB boxBefore) {
		Vec3 moved = position().subtract(before);
		float turned = getYRot() - yawBefore;
		if (moved.lengthSqr() < 1.0e-6 && Math.abs(turned) < 1.0e-3) {
			return;
		}
		AABB roof = new AABB(boxBefore.minX, boxBefore.maxY - 0.3, boxBefore.minZ, boxBefore.maxX, boxBefore.maxY + 0.8, boxBefore.maxZ);
		for (Entity entity : level().getEntities(this, roof, e -> e.getVehicle() == null && !(e instanceof TrainCar) && !e.isSpectator())) {
			boolean own = level().isClientSide() ? entity.isLocalInstanceAuthoritative() : !(entity instanceof Player);
			if (!own) {
				continue;
			}
			// turn the spot it stands on round the middle of the car, then carry it along
			double rad = -turned * Mth.DEG_TO_RAD;
			double ox = entity.getX() - before.x;
			double oz = entity.getZ() - before.z;
			double rx = ox * Math.cos(rad) - oz * Math.sin(rad);
			double rz = ox * Math.sin(rad) + oz * Math.cos(rad);
			entity.setPos(position().x + rx, Math.max(entity.getY() + moved.y, getBoundingBox().maxY), position().z + rz);
			entity.setYRot(entity.getYRot() + turned);
			entity.resetFallDistance();
		}
	}

	private void serverTick(ServerLevel level, Chain chain) {
		if (!populated && !isLoco()) {
			populated = true;
			// the passengers it brings along
			int riders = 3 + getRandom().nextInt(6);
			for (int i = 0; i < riders; i++) {
				Warga warga = BlossomEntities.WARGA.create(level, EntitySpawnReason.EVENT);
				warga.snapTo(position());
				warga.settle(Warga.PASSENGER, Warga.randomSkin(getRandom()), null, null, null, blockPosition(), 8);
				level.addFreshEntity(warga);
				warga.board(this);
			}
		}
		if (car == 0) {
			// the station hears on the radio that the lori is in: the line is clear and this train may go
			boolean waiting = state.standing() && state.progress() == 0 && !TrainSchedule.loriDone(chain, state.line(), level.getGameTime());
			if (waitingForLine && !waiting && state.standing()) {
				int next = state.along() ? state.line() + 1 : state.line();
				Component words = Component.translatable("message.blossom.train.cleared", chain.station(state.station()).name, chain.station(next).name);
				for (ServerPlayer player : level.players()) {
					if (player.distanceToSqr(this) < 100 * 100) {
						player.sendSystemMessage(words);
					}
				}
				level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 1f, 1.8f);
			}
			waitingForLine = waiting;
			if (state.standing() && state.waited() == TrainSchedule.DWELL - 1) {
				horn(level, 0.85f);
			}
			if (!state.standing() && level.getGameTime() % 600 == 0) {
				horn(level, 0.8f);
			}
			// running round at a terminus: the clank of uncoupling and coupling up again
			if (state.turning() && (state.waited() == TrainSchedule.UNCOUPLE || state.waited() == TrainSchedule.COUPLE)) {
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.5f, 0.6f);
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1f, 0.7f);
			}
			pushAside(level);
		}
		if (level.getGameTime() % 40 == 0) {
			tellRiders(level, chain);
		}
		StationEvents.carTick(level, this, chain);
	}

	private void horn(ServerLevel level, float pitch) {
		level.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.NEUTRAL, 6f, pitch);
	}

	// players riding the train see which train it is and where it stops next
	private void tellRiders(ServerLevel level, Chain chain) {
		String name = TrainSchedule.name(train);
		Component message;
		if (state.standing() && state.progress() == 0 && !TrainSchedule.loriDone(chain, state.line(), level.getGameTime())) {
			// still at the platform it leaves from, the line ahead not yet radioed clear
			message = Component.translatable("message.blossom.train.waiting", name, chain.station(state.station()).name);
		} else if (state.standing()) {
			message = Component.translatable("message.blossom.train.stopped", name, chain.station(state.station()).name);
		} else {
			int next = state.along() ? state.line() + 1 : state.line();
			message = Component.translatable("message.blossom.train.next", name, chain.station(next).name);
		}
		for (Entity rider : getPassengers()) {
			if (rider instanceof ServerPlayer player) {
				player.sendOverlayMessage(message);
			}
		}
	}

	// people standing on the track get shoved off it, with a fright
	private void pushAside(ServerLevel level) {
		Vec3 forward = Vec3.directionFromRotation(0, getYRot());
		if (Math.hypot(getX() - xo, getZ() - zo) < 0.05) {
			return;
		}
		AABB front = getBoundingBox().move(forward.scale(2)).inflate(0.2);
		// players too, in creative as well (they just aren't hurt): the roof is for riding, not the way ahead
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, front, e -> !(e.getVehicle() instanceof TrainCar) && !e.isSpectator()
				&& e.getY() < getBoundingBox().maxY - 0.3)) {
			Vec3 side = new Vec3(-forward.z, 0, forward.x);
			Vec3 away = entity.position().subtract(position());
			if (away.dot(side) < 0) {
				side = side.scale(-1);
			}
			entity.setDeltaMovement(side.scale(0.9).add(forward.scale(0.3)).add(0, 0.45, 0));
			entity.needsSync = true;
			if (entity instanceof ServerPlayer player) {
				player.connection.send(new ClientboundSetEntityMotionPacket(player));
			}
			if (!(entity instanceof Warga)) {
				entity.hurtServer(level, level.damageSources().generic(), 2);
			}
		}
	}

	// which side the platform next to this train's station track is on (left of the chain's way, or right)
	public int platformSide() {
		return state == null || TrainSchedule.laneOf(state) == 0 ? -1 : 1;
	}

	// a spot on the platform next to the middle of this car
	public Vec3 door() {
		Vec3 forward = Vec3.directionFromRotation(0, getYRot());
		Vec3 left = new Vec3(-forward.z, 0, forward.x);
		// the platforms start a block past the station tracks, stand in the middle of them
		return position().add(left.scale(platformSide() * 3)).add(0, 1, 0);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide()) {
			if (getPassengers().size() >= seats().size()) {
				player.sendOverlayMessage(Component.translatable("message.blossom.train.full"));
				return InteractionResult.SUCCESS;
			}
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	private List<double[]> seats() {
		return switch (kind()) {
			case 0 -> LOCO_SEATS;
			case 2 -> PANORAMIC_SEATS;
			default -> COACH_SEATS;
		};
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < seats().size();
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		int index = getPassengers().indexOf(passenger);
		if (index < 0) {
			return;
		}
		double[] seat = seats().get(index % seats().size());
		Vec3 at = seat(this, seat[0], -seat[1], isLoco() ? CAB_SEAT_HEIGHT : SEAT_HEIGHT);
		moveFunction.accept(passenger, at.x, at.y - passenger.getVehicleAttachmentPoint(this).y, at.z);
		if (passenger instanceof Warga warga) {
			// facing seats: the front half looks back, the back half looks forward
			float yaw = getYRot() + (seat[1] < 0 ? 180 : 0);
			warga.setYRot(yaw);
			warga.setYHeadRot(yaw);
			warga.setYBodyRot(yaw);
		}
	}

	// where a seat `left`, `forward` and `up` from the middle of a car's floor is, with the car turned along the line
	// and tilted with its grade (on a climb the front seats are higher, the back ones lower)
	public static Vec3 seat(Entity car, double left, double forward, double up) {
		double climb = Math.toRadians(-car.getXRot());
		double ahead = forward * Math.cos(climb) - up * Math.sin(climb);
		double above = forward * Math.sin(climb) + up * Math.cos(climb);
		Vec3 facing = Vec3.directionFromRotation(0, car.getYRot());
		Vec3 side = new Vec3(-facing.z, 0, facing.x);
		return car.position().add(facing.scale(ahead)).add(side.scale(left)).add(0, above, 0);
	}

	// step off onto the platform
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		if (train == null) {
			return super.getDismountLocationForPassenger(passenger);
		}
		return door();
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	// the wheels turn as fast as the train rolls
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<TrainCar>("wheels", 0, test -> {
			double speed = speed();
			if (speed < 0.01) {
				return PlayState.STOP;
			}
			test.setControllerSpeed((float) Mth.clamp(speed * 2.2, 0, 4));
			return test.setAndContinue(ROLL);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
