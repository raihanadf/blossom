package com.raihanadf.blossom.nusantara;


import com.raihanadf.blossom.kampung.KampungPlan;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// one settlement of the network: its station, then beside it either a single kampung (a desa) or a town with an
// alun-alun in the middle and kampungs all around it, with sawah and pastures outside. how big it is comes from the seed:
// from a desa of five buildings to a kota of seventy
public class Settlement {
	private static final String[] FRONT = {"Suka", "Karang", "Tanjung", "Wono", "Sido", "Kali", "Banyu", "Gunung", "Pagar", "Sumber",
			"Tegal", "Pasir", "Rawa", "Jati", "Kebon", "Ngadi", "Purwo", "Mojo", "Cikal", "Lebak", "Bandar", "Sri", "Muara", "Pringgo"};
	private static final String[] BACK = {"sari", "jati", "rejo", "mulyo", "asih", "maju", "harjo", "agung", "waras", "makmur",
			"dadi", "wangi", "luhur", "kerto", "jaya", "mukti", "rahayu", "sono", "laya", "tirto", "kencana", "arum", "lestari"};

	// a kampung of the settlement: its plan, where its middle is and how high its yard is
	public record Cluster(KampungPlan plan, int x, int z, int ground) {
	}

	// a sawah plot or a pasture outside the kampungs, levelled to its own height
	public record Plot(int x0, int z0, int level, int kind) {
		public static final int RICE = 0;
		public static final int PASTURE = 1;
		public static final int PEN = 2;
	}

	// how far the settlement's land keeps from the station corner (the hall, the plaza and the platforms)
	private static final int GAP = 26;
	public static final int PLOT_X = 12;
	public static final int PLOT_Z = 10;

	public final Node node;
	public final String name;
	// 0 desa, 1 desa besar, 2 kota kecil, 3 kota
	public final int tier;
	public final int size;
	public final boolean[] ports = new boolean[4];
	public final int qx;
	public final int qz;
	public final int centerX;
	public final int centerZ;
	public final int coreGround;
	// the town square's half size (0 for a desa) and the kampungs
	public final int coreHalf;
	public final List<Cluster> clusters = new ArrayList<>();
	// the kampungs and town reach this far from the middle, the fields beyond them reach out to `reach`
	public final int built;
	public final int reach;
	private final int[] exits = new int[4];
	private final NusantaraNetwork network;
	private List<Plot> plots;
	private List<int[]> roadList;
	private List<int[]> roadProfiles;

	public Settlement(NusantaraNetwork network, Node node) {
		this.network = network;
		this.node = node;
		int cx = node.cx();
		int cz = node.cz();
		name = FRONT[Math.floorMod(network.hash(cx, cz, 1), FRONT.length)] + BACK[Math.floorMod(network.hash(cx, cz, 2), BACK.length)];
		int roll = Math.floorMod(network.hash(cx, cz, 3), 100);
		tier = roll < 30 ? 0 : roll < 58 ? 1 : roll < 82 ? 2 : 3;
		size = switch (tier) {
			case 0 -> 5 + Math.floorMod(network.hash(cx, cz, 4), 6);
			case 1 -> 11 + Math.floorMod(network.hash(cx, cz, 4), 10);
			case 2 -> 25 + Math.floorMod(network.hash(cx, cz, 4), 21);
			default -> 50 + Math.floorMod(network.hash(cx, cz, 4), 21);
		};
		for (int port = 0; port < 4; port++) {
			ports[port] = network.linked(cx, cz, port);
		}
		// the settlement sits in the corner between two ports, preferably beside the lines so the hall is by the platforms
		int bestQ = 0;
		int bestScore = Integer.MAX_VALUE;
		int[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		for (int q = 0; q < 4; q++) {
			int score = (ports[corners[q][0] > 0 ? 0 : 2] ? 1 : 0) + (ports[corners[q][1] > 0 ? 1 : 3] ? 1 : 0);
			score = (2 - score) * 4 + Math.floorMod(network.hash(cx, cz, 5 + q), 4);
			if (score < bestScore) {
				bestScore = score;
				bestQ = q;
			}
		}
		qx = corners[bestQ][0];
		qz = corners[bestQ][1];
		long seed = network.hash(cx, cz, 6) * 0x9E3779B97F4A7C15L;

		int fields;
		int builtReach;
		if (tier < 2) {
			coreHalf = 0;
			KampungPlan plan = new KampungPlan(seed, Float.NaN, size);
			builtReach = plan.extent;
			fields = tier == 0 ? 34 : 40;
			int center = builtReach + fields + GAP;
			centerX = node.x() + qx * center;
			centerZ = node.z() + qz * center;
			// the gate looks back at the station
			float gate = (float) Mth.atan2(-qz, -qx);
			clusters.add(new Cluster(new KampungPlan(seed, gate, size), centerX, centerZ, levelAt(centerX, centerZ)));
			coreGround = clusters.getFirst().ground();
		} else {
			coreHalf = tier == 3 ? 15 : 11;
			int coreBuildings = tier == 3 ? 18 : 10;
			int left = Math.max(10, size - coreBuildings);
			int count = tier == 3 ? Math.max(4, Mth.ceil(left / 12.0)) : Math.max(2, Mth.ceil(left / 11.0));
			int each = Math.max(6, left / count);
			List<KampungPlan> plans = new ArrayList<>();
			int widest = 0;
			for (int i = 0; i < count; i++) {
				KampungPlan plan = new KampungPlan(seed + i * 7919L, Float.NaN, each);
				plans.add(plan);
				widest = Math.max(widest, plan.extent);
			}
			int coreReach = TownCore.reach(coreHalf);
			int ring = (int) Math.max(coreReach * 1.42 + widest + 6, (widest + 6) / Math.sin(Math.PI / count));
			builtReach = ring + widest;
			fields = tier == 3 ? 12 : 18;
			int center = builtReach + fields + GAP;
			centerX = node.x() + qx * center;
			centerZ = node.z() + qz * center;
			coreGround = levelAt(centerX, centerZ);
			// kampungs all around the town square, leaving the way to the station open
			double toStation = Math.atan2(-qz, -qx);
			for (int i = 0; i < count; i++) {
				double angle = toStation + Math.PI / count + i * Mth.TWO_PI / count;
				int x = centerX + (int) Math.round(Math.cos(angle) * ring);
				int z = centerZ + (int) Math.round(Math.sin(angle) * ring);
				float gate = (float) Math.atan2(centerZ - z, centerX - x);
				clusters.add(new Cluster(new KampungPlan(seed + i * 7919L, gate, each), x, z, levelAt(x, z)));
			}
		}
		built = builtReach;
		reach = builtReach + fields;
		int center = reach + GAP;
		for (int port = 0; port < 4; port++) {
			boolean nextToTown = (NusantaraNetwork.DX[port] == qx && qx != 0) || (NusantaraNetwork.DZ[port] == qz && qz != 0);
			exits[port] = Math.max(110, nextToTown ? center + reach + 24 : 110);
		}
	}

	// the ground a kampung or the town square is levelled to: the land there, but never below the sea
	private int levelAt(int x, int z) {
		int sum = 0;
		for (int dx = -12; dx <= 12; dx += 12) {
			for (int dz = -12; dz <= 12; dz += 12) {
				sum += network.ground(x + dx, z + dz);
			}
		}
		return Math.max(NusantaraNetwork.SEA + 2, sum / 9);
	}

	// how far the straight stretch out of the station reaches before the line may start to bend
	public int exit(int port) {
		return exits[port];
	}

	public boolean isTown() {
		return tier >= 2;
	}

	// the station hall stands in the corner between the platforms, facing the settlement; the plaza is past it
	public int hallX() {
		return node.x() + qx * 20;
	}

	public int hallZ() {
		return node.z() + qz * 20;
	}

	public int plazaX() {
		return node.x() + qx * 32;
	}

	public int plazaZ() {
		return node.z() + qz * 32;
	}

	// the arm of a line (row: east 0 or west 2, column: south 1 or north 3) the platforms of this station are on:
	// the one on the settlement's side when there is a line that way
	// the way through a line's station fence: the ticket gate (in) and the exit gate on the platform on the settlement's
	// side, the spots just outside and just inside each, and where the far platform is reached over the walkway
	// between the tracks. `side` is which side of the line the near platform is on (as in StationBuilder.platforms)
	public record Gates(BlockPos entryOut, BlockPos entryIn, BlockPos exitIn, BlockPos exitOut, BlockPos crossNear, BlockPos crossFar, int side) {
	}

	public static final int ENTRY_ALONG = 20;
	public static final int EXIT_ALONG = 23;
	public static final int WALKWAY_ALONG = NusantaraNetwork.PLATFORM_START + 1;
	// the platforms run 4 to 6 blocks out from the middle of the tracks, the fence stands at 7
	public static final int FENCE = 7;

	public @Nullable Gates gates(boolean row) {
		if (!(row ? ports[0] || ports[2] : ports[1] || ports[3])) {
			return null;
		}
		int port = platformArm(row);
		int dx = NusantaraNetwork.DX[port];
		int dz = NusantaraNetwork.DZ[port];
		int ax = -dz;
		int az = dx;
		int side = Integer.signum(ax * qx + az * qz);
		int g = node.ground();
		BiFunction<Integer, Integer, BlockPos> at = (along, k) -> new BlockPos(node.x() + dx * along + ax * side * k,
				g + (Math.abs(k) < FENCE ? 2 : 1), node.z() + dz * along + az * side * k);
		return new Gates(at.apply(ENTRY_ALONG, FENCE + 2), at.apply(ENTRY_ALONG, FENCE - 2), at.apply(EXIT_ALONG, FENCE - 2), at.apply(EXIT_ALONG, FENCE + 2),
				at.apply(WALKWAY_ALONG, FENCE - 2), at.apply(WALKWAY_ALONG, -(FENCE - 2)), side);
	}

	public int platformArm(boolean row) {
		int toward = row ? (qx > 0 ? 0 : 2) : (qz > 0 ? 1 : 3);
		return ports[toward] ? toward : (toward + 2) % 4;
	}

	// sawah plots and pastures in the ring between the kampungs and the edge of the settlement's land. each plot is
	// levelled to the land at its middle and left out where the land is too steep, wet, or on the way to the station
	// whether a column belongs to the station yard, the town square or a kampung other than `self`
	public boolean builtAt(int x, int z, Cluster self) {
		if (StationBuilder.yard(this, x, z) <= 2) {
			return true;
		}
		if (isTown() && Math.abs(x - centerX) <= TownCore.reach(coreHalf) + 2 && Math.abs(z - centerZ) <= TownCore.reach(coreHalf) + 2) {
			return true;
		}
		for (Cluster cluster : clusters) {
			if (cluster != self && cluster.plan().distanceOutside(x - cluster.x(), z - cluster.z()) <= 2) {
				return true;
			}
		}
		return false;
	}

	public synchronized List<Plot> plots() {
		if (plots != null) {
			return plots;
		}
		List<Plot> out = new ArrayList<>();
		int inner = built + 4;
		for (int px = Math.floorDiv(centerX - reach, PLOT_X); px * PLOT_X <= centerX + reach; px++) {
			for (int pz = Math.floorDiv(centerZ - reach, PLOT_Z); pz * PLOT_Z <= centerZ + reach; pz++) {
				int x0 = px * PLOT_X;
				int z0 = pz * PLOT_Z;
				int mx = x0 + PLOT_X / 2;
				int mz = z0 + PLOT_Z / 2;
				double d = Math.hypot(mx - centerX, mz - centerZ);
				if (d < inner || d > reach) {
					continue;
				}
				// keep off the way from the station and the roads between the kampungs and the town square
				if (nearRoad(mx, mz, 9)) {
					continue;
				}
				int level = network.ground(mx, mz);
				if (level <= NusantaraNetwork.SEA || network.surface(mx, mz) > level) {
					continue;
				}
				boolean flat = true;
				for (int[] c : new int[][] {{x0, z0}, {x0 + PLOT_X, z0}, {x0, z0 + PLOT_Z}, {x0 + PLOT_X, z0 + PLOT_Z}}) {
					if (Math.abs(network.ground(c[0], c[1]) - level) > 3) {
						flat = false;
						break;
					}
				}
				if (!flat) {
					continue;
				}
				int roll = Math.floorMod(network.hash(px, pz, 40), 100);
				int kind = isTown() ? (roll < 60 ? Plot.RICE : roll < 85 ? Plot.PASTURE : Plot.PEN) : (roll < 80 ? Plot.RICE : roll < 92 ? Plot.PASTURE : Plot.PEN);
				out.add(new Plot(x0, z0, level, kind));
			}
		}
		plots = out;
		return out;
	}

	// the roads of the settlement as straight lines: station to the town or kampung, town square to each kampung
	public synchronized List<int[]> roads() {
		if (roadList != null) {
			return roadList;
		}
		List<int[]> out = new ArrayList<>();
		if (!isTown()) {
			Cluster only = clusters.getFirst();
			out.add(new int[] {plazaX(), plazaZ(), only.x() + only.plan().gateX, only.z() + only.plan().gateZ});
		} else {
			int corner = TownCore.reach(coreHalf);
			out.add(new int[] {plazaX(), plazaZ(), centerX - qx * corner, centerZ - qz * corner});
			for (Cluster cluster : clusters) {
				int gx = cluster.x() + cluster.plan().gateX;
				int gz = cluster.z() + cluster.plan().gateZ;
				double dx = centerX - gx;
				double dz = centerZ - gz;
				double len = Math.hypot(dx, dz);
				// stop at the edge of the town square's block
				double stop = Math.max(0, len - corner * 1.05);
				out.add(new int[] {gx, gz, gx + (int) Math.round(dx / len * stop), gz + (int) Math.round(dz / len * stop)});
			}
		}
		roadList = out;
		return out;
	}

	// a smoothed, grade-limited height for each block along a road. endpoints are the plaza, square or gate levels.
	public synchronized int[] roadProfile(int[] road) {
		int index = roads().indexOf(road);
		if (roadProfiles == null) {
			roadProfiles = new ArrayList<>();
			for (int[] segment : roads()) {
				roadProfiles.add(buildRoadProfile(segment));
			}
		}
		return roadProfiles.get(index < 0 ? 0 : index);
	}

	// the land along the road is sampled every few blocks (the noise is slow), smoothed, then held to a climb of one
	// block every two between the two fixed ends
	private int[] buildRoadProfile(int[] road) {
		int steps = Math.max(2, (int) Math.ceil(Math.hypot(road[2] - road[0], road[3] - road[1])));
		int samples = steps / 4 + 2;
		int[] land = new int[samples];
		for (int k = 0; k < samples; k++) {
			double t = Math.min(1, k * 4.0 / steps);
			int x = (int) Math.round(Mth.lerp(t, road[0], road[2]));
			int z = (int) Math.round(Mth.lerp(t, road[1], road[3]));
			// over water the road rides a plank bridge just above it
			land[k] = Math.max(network.ground(x, z), network.surface(x, z) + 1);
		}
		int[] profile = new int[steps + 1];
		for (int i = 0; i <= steps; i++) {
			double at = i / 4.0;
			int k = Math.min(samples - 2, (int) at);
			double smooth = 0;
			for (int j = -1; j <= 1; j++) {
				smooth += Mth.lerp(at - k, land[Mth.clamp(k + j, 0, samples - 1)], land[Mth.clamp(k + 1 + j, 0, samples - 1)]);
			}
			profile[i] = (int) Math.round(smooth / 3);
		}
		profile[0] = roadEndpointLevel(road[0], road[1], profile[0]);
		profile[steps] = roadEndpointLevel(road[2], road[3], profile[steps]);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 1; i < steps; i++) {
				int back = i >= 2 ? profile[i - 2] : profile[0];
				profile[i] = Mth.clamp(profile[i], Math.max(profile[i - 1] - 1, back - 1), Math.min(profile[i - 1] + 1, back + 1));
			}
			for (int i = steps - 1; i >= 1; i--) {
				int ahead = i + 2 <= steps ? profile[i + 2] : profile[steps];
				profile[i] = Mth.clamp(profile[i], Math.max(profile[i + 1] - 1, ahead - 1), Math.min(profile[i + 1] + 1, ahead + 1));
			}
		}
		return profile;
	}

	private int roadEndpointLevel(int x, int z, int fallback) {
		if (x == plazaX() && z == plazaZ()) {
			return node.ground();
		}
		if (isTown()) {
			int corner = TownCore.reach(coreHalf);
			if (Math.hypot(x - centerX, z - centerZ) <= corner + 2) {
				return coreGround;
			}
		}
		for (Cluster cluster : clusters) {
			int gx = cluster.x() + cluster.plan().gateX;
			int gz = cluster.z() + cluster.plan().gateZ;
			if (x == gx && z == gz) {
				return cluster.ground();
			}
		}
		return fallback;
	}

	public boolean nearRoad(int x, int z, double margin) {
		for (int[] road : roads()) {
			if (distanceToSegment(x, z, road) < margin) {
				return true;
			}
		}
		return false;
	}

	public static double distanceToSegment(double x, double z, int[] seg) {
		double dx = seg[2] - seg[0];
		double dz = seg[3] - seg[1];
		double len2 = dx * dx + dz * dz;
		double t = len2 == 0 ? 0 : Mth.clamp(((x - seg[0]) * dx + (z - seg[1]) * dz) / len2, 0, 1);
		return Math.hypot(x - (seg[0] + dx * t), z - (seg[1] + dz * t));
	}

	// a point a traveller can walk to: the middle of the town square or the kampung
	public int destinationX() {
		return centerX;
	}

	public int destinationZ() {
		return centerZ;
	}

	// whether anything of this settlement or its station may stand at this column
	public boolean covers(int x, int z) {
		return Math.hypot(x - centerX, z - centerZ) <= reach + 8 || (Math.abs(x - node.x()) <= 30 && Math.abs(z - node.z()) <= 30);
	}
}
