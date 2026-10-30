package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.TrainSchedule.Train;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

// keeps the trains that are near a player in the world as entities. the timetable itself runs whether anyone is
// watching or not, so a train that comes into view is exactly where it should be
public final class TrainSystem {
	private static final double REACH = 200;
	private static final double DROP = 260;
	private static final Map<Key, TrainCar> CARS = new HashMap<>();

	// one lori per line, by the line's key
	private static final Map<Long, Lori> LORIS = new HashMap<>();

	private record Key(Train train, int car) {
	}


	private TrainSystem() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(TrainSystem::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(TrainSystem::firstSpawn);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CARS.clear();
			LORIS.clear();
		});
		// riders sit inside the car: on curves in a tunnel their heads can swing into the lining, they never choke on it
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity.getVehicle() instanceof TrainCar && source.is(DamageTypes.IN_WALL)));
	}

	// a fresh nusantara world starts you on the plaza of the station nearest the middle of the world
	private static void firstSpawn(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		NusantaraNetwork network = TrainCar.network(overworld);
		if (network == null || overworld.getGameTime() > 100) {
			return;
		}
		Settlement nearest = null;
		double best = Double.MAX_VALUE;
		for (int cx = -2; cx <= 1; cx++) {
			for (int cz = -2; cz <= 1; cz++) {
				Settlement settlement = network.settlement(cx, cz);
				if (settlement != null && Math.hypot(settlement.node.x(), settlement.node.z()) < best) {
					best = Math.hypot(settlement.node.x(), settlement.node.z());
					nearest = settlement;
				}
			}
		}
		if (nearest != null) {
			BlockPos plaza = new BlockPos(nearest.plazaX(), nearest.node.ground() + 1, nearest.plazaZ());
			overworld.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, plaza, 0, 0));
		}
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		NusantaraNetwork network = TrainCar.network(level);
		if (network == null) {
			return;
		}
		CARS.values().removeIf(TrainCar::isRemoved);
		LORIS.values().removeIf(Lori::isRemoved);
		long time = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			for (Edge edge : network.edgesNear(player.getBlockX(), player.getBlockZ())) {
				Chain chain = network.chainOf(edge);
				int k = edge.portA == 0 ? edge.a.node.cx() : edge.a.node.cz();
				// the train on this line now, and the ones on the lines either side (they may stand at this line's stations)
				long slot = TrainSchedule.slot(chain, time);
				for (int line = Math.max(chain.start, k - 1); line <= Math.min(chain.end - 1, k + 1); line++) {
					Train train = TrainSchedule.trainOn(chain, line, slot);
					// and the line's inspection trolley, parked or on its way
					double[] lori = Lori.place(chain, line, time - TrainSchedule.loriDelay(chain, line, time));
					long loriKey = chain.edge(line).key;
					if (lori != null && Math.hypot(lori[0] - player.getX(), lori[2] - player.getZ()) <= REACH
							&& level.isPositionEntityTicking(BlockPos.containing(lori[0], lori[1], lori[2]))
							&& (LORIS.get(loriKey) == null || LORIS.get(loriKey).isRemoved())
							&& network.chainAt(chain.row, chain.fixed, line) != null) {
						Lori spawned = Lori.create(level, network.chainAt(chain.row, chain.fixed, line), line);
						level.addFreshEntity(spawned);
						LORIS.put(loriKey, spawned);
					}
					for (int car = 0; car < TrainSchedule.CARS; car++) {
						double[] at = TrainCar.place(chain, train, car, time);
						if (at == null || Math.hypot(at[0] - player.getX(), at[2] - player.getZ()) > REACH) {
							continue;
						}
						Key key = new Key(train, car);
						TrainCar existing = CARS.get(key);
						// a car only exists where things move: outside that it would stand frozen where it appeared
						boolean ticking = level.isPositionEntityTicking(BlockPos.containing(at[0], at[1], at[2]));
						if (ticking && (existing == null || existing.isRemoved())) {
							TrainCar spawned = TrainCar.create(level, chain, train, car);
							level.addFreshEntity(spawned);
							CARS.put(key, spawned);
						}
					}
				}
			}
		}
		// cars nobody is near any more leave, along with the passengers they brought (spectators count as watching)
		for (Lori lori : List.copyOf(LORIS.values())) {
			boolean watched = level.players().stream().anyMatch(player -> player.distanceToSqr(lori) < DROP * DROP);
			if (lori.level() == level && (!watched || !level.isPositionEntityTicking(lori.blockPosition()))) {
				lori.getPassengers().forEach(Entity::discard);
				lori.discard();
			}
		}
		for (TrainCar car : List.copyOf(CARS.values())) {
			boolean watched = level.players().stream().anyMatch(player -> player.distanceToSqr(car) < DROP * DROP);
			if (car.level() == level && (!watched || !level.isPositionEntityTicking(car.blockPosition()))) {
				car.getPassengers().forEach(rider -> {
					if (!(rider instanceof ServerPlayer)) {
						rider.discard();
					}
				});
				car.discard();
			}
		}
	}
}
