package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.TrainSchedule.State;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// what happens while a train stands at a station: people get off and head into the settlement, the ones waiting on
// the platform climb aboard, and the station master whistles it off
public final class StationEvents {
	private StationEvents() {
	}

	public static void carTick(ServerLevel level, TrainCar car, Chain chain) {
		State state = car.state();
		if (state == null || !state.standing()) {
			return;
		}
		Settlement station = chain.station(state.station());
		int waited = state.waited();
		Vec3 door = car.door();

		if (car.isLoco()) {
			if (car.car() == 0 && (waited == TrainSchedule.DWELL - 40 || waited == 15)) {
				AABB hall = new AABB(station.node.x() - 50, station.node.ground() - 4, station.node.z() - 50, station.node.x() + 50, station.node.ground() + 8,
						station.node.z() + 50);
				for (Warga ppka : level.getEntitiesOfClass(Warga.class, hall, w -> w.getJob() == Warga.PPKA)) {
					if (waited == 15) {
						ppka.wave();
					} else {
						ppka.whistle();
					}
				}
			}
			return;
		}
		if (waited == 20) {
			alight(level, car, door, station, chain.row);
		}
		if (waited >= 40 && waited <= TrainSchedule.DWELL - 30 && waited % 10 == 0) {
			board(level, car, door, chain, station.gates(chain.row));
		}
	}

	private static void alight(ServerLevel level, TrainCar car, Vec3 door, Settlement station, boolean row) {
		// keep the platforms from filling up with people
		int waiting = level.getEntitiesOfClass(Warga.class, new AABB(door, door).inflate(60, 6, 60), w -> w.getJob() == Warga.PASSENGER && !w.isAboard()).size();
		if (waiting >= 12) {
			return;
		}
		for (Entity rider : List.copyOf(car.getPassengers())) {
			if (!(rider instanceof Warga warga) || !warga.isAboard() || level.getRandom().nextInt(3) != 0) {
				continue;
			}
			BlockPos destination = new BlockPos(station.destinationX(), station.coreGround + 1, station.destinationZ());
			BlockPos platform = BlockPos.containing(door);
			// out of the station through the exit gate (over the walkway first from the far platform), past the plaza;
			// and the way back in through the ticket gate to this platform
			Settlement.Gates gates = station.gates(row);
			BlockPos plaza = new BlockPos(station.plazaX(), station.node.ground() + 1, station.plazaZ());
			List<BlockPos> out = new ArrayList<>();
			List<BlockPos> back = new ArrayList<>();
			if (gates != null) {
				boolean far = farSide(gates, door.x, door.z);
				if (far) {
					out.add(gates.crossFar());
					out.add(gates.crossNear());
				}
				out.addAll(List.of(gates.exitIn(), gates.exitOut(), plaza));
				back.addAll(List.of(plaza, gates.entryOut(), gates.entryIn()));
				if (far) {
					back.add(gates.crossNear());
					back.add(gates.crossFar());
				}
			}
			warga.alight(door.add(level.getRandom().nextDouble() - 0.5, 0, level.getRandom().nextDouble() - 0.5), platform, destination, destination,
					station.reach + 60, out, back);
		}
	}

	private static void board(ServerLevel level, TrainCar car, Vec3 door, Chain chain, Settlement.@Nullable Gates gates) {
		long slot = TrainSchedule.slot(chain, level.getGameTime());
		AABB nearby = new AABB(door, door).inflate(9, 3, 9);
		// only those on this door's platform: nobody cuts across the tracks to a train on the other side
		boolean doorFar = gates != null && farSide(gates, door.x, door.z);
		for (Warga warga : level.getEntitiesOfClass(Warga.class, nearby, w -> w.getJob() == Warga.PASSENGER && !w.isPassenger()
				&& (gates == null || farSide(gates, w.getX(), w.getZ()) == doorFar))) {
			// not everyone takes this train
			if (Math.floorMod(warga.getId() + slot + car.car(), 3) == 0) {
				continue;
			}
			if (warga.position().distanceToSqr(door.x, warga.getY(), door.z) < 3.5 * 3.5 && car.getPassengers().size() < 12) {
				warga.board(car);
			} else {
				warga.waitAt(BlockPos.containing(door));
			}
		}
	}

	// whether a spot lies across the tracks from the station's gates: the way the walkway crosses to
	private static boolean farSide(Settlement.Gates gates, double x, double z) {
		double mx = (gates.crossNear().getX() + gates.crossFar().getX()) / 2.0 + 0.5;
		double mz = (gates.crossNear().getZ() + gates.crossFar().getZ()) / 2.0 + 0.5;
		return (x - mx) * (gates.crossFar().getX() - gates.crossNear().getX()) + (z - mz) * (gates.crossFar().getZ() - gates.crossNear().getZ()) > 0;
	}
}
