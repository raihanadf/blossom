package com.raihanadf.blossom.furniture;

import com.raihanadf.blossom.registry.BlossomEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// an invisible entity the player rides while sitting on a seat block, gone as soon as they stand up
public class SeatEntity extends Entity {
	private static final double BOUNCE = 0.12;

	// the seat block this entity belongs to, not saved so a reloaded seat just cleans itself up
	private BlockPos seatPos;
	private double seatY;

	public SeatEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static boolean isTaken(Level level, BlockPos pos) {
		return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0, 1, 0)).isEmpty();
	}

	// players and village folk both sit through this
	public static boolean sit(ServerLevel level, BlockPos pos, double seatHeight, float yaw, LivingEntity sitter) {
		if (isTaken(level, pos)) {
			return false;
		}
		SeatEntity seat = new SeatEntity(BlossomEntities.SEAT, level);
		seat.seatPos = pos;
		// the rider's sitting pose already drops them down onto this point
		seat.seatY = pos.getY() + seatHeight;
		// start a bit low and settle up, a tiny plop when you sit down
		seat.setPos(pos.getX() + 0.5, seat.seatY - BOUNCE, pos.getZ() + 0.5);
		seat.setYRot(yaw);
		level.addFreshEntity(seat);
		sitter.startRiding(seat);
		sitter.setYRot(yaw);
		sitter.setYHeadRot(yaw);
		BlockState state = level.getBlockState(pos);
		level.playSound(null, pos, state.getSoundType().getStepSound(), SoundSource.BLOCKS, 0.5f, 0.7f);
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		if (seatPos == null || getPassengers().isEmpty() || !(level().getBlockState(seatPos).getBlock() instanceof SeatBlock)) {
			discard();
		} else if (getY() < seatY) {
			setPos(getX(), Math.min(seatY, getY() + BOUNCE / 3), getZ());
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return seatPos == null ? super.getDismountLocationForPassenger(passenger) : Vec3.atBottomCenterOf(seatPos.above());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}
}
