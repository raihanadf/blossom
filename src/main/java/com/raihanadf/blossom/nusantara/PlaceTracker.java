package com.raihanadf.blossom.nusantara;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// tells each player which station, desa or kota they are in, whenever that changes
public final class PlaceTracker {
	private static final Map<UUID, PlacePayload> LAST = new HashMap<>();

	private PlaceTracker() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PlacePayload.TYPE, PlacePayload.CODEC);
		ServerTickEvents.END_LEVEL_TICK.register(PlaceTracker::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % 20 != 0) {
			return;
		}
		NusantaraNetwork network = TrainCar.network(level);
		if (network == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			PlacePayload place = new PlacePayload("", "");
			for (Settlement s : network.settlementsNear(player.getBlockX(), player.getBlockZ())) {
				if (Math.hypot(player.getX() - s.node.x(), player.getZ() - s.node.z()) < NusantaraNetwork.PLATFORM_END + 12) {
					place = new PlacePayload("hud.blossom.place.station", s.name);
					break;
				}
				if (Math.hypot(player.getX() - s.centerX, player.getZ() - s.centerZ) < s.built + 10) {
					place = new PlacePayload("hud.blossom.place.tier" + s.tier, s.name);
					break;
				}
			}
			if (!place.equals(LAST.get(player.getUUID()))) {
				LAST.put(player.getUUID(), place);
				ServerPlayNetworking.send(player, place);
			}
		}
	}
}
