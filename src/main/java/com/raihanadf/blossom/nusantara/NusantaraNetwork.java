package com.raihanadf.blossom.nusantara;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

// the railway network of a nusantara world, worked out from the seed and the natural terrain so that every chunk and
// every train agrees on it. the world is cut into big square cells, each with at most one settlement (on the best bit of
// land in it), and lines join neighbouring settlements. every line takes its own winding way and gets its own height
// profile through the hills: open line in cuttings and on embankments, tunnels, bridges and viaducts
public class NusantaraNetwork {
	public static final int CELL = 1536;
	private static final int JITTER = 300;
	public static final int SEA = 63;
	// the station hall's half size, where the tracks end, where the platforms start and how long they are
	public static final int HALL = 6;
	// the lori's siding beside the first track: where it leaves and rejoins it (samples from the end of a line), how
	// long it takes to swing out and how far out it lies
	public static final int SIDING_LANE = 2;
	private static final double SIDING_IN = 74;
	public static final double SIDING_OUT = 108;
	private static final double SIDING_RAMP = 10;
	private static final double SIDING_SPACING = 4;
	public static final int TRACK_START = 7;
	// how far either side of a step in the bed the even grade ramps
	private static final double RAMP = 6;
	public static final int PLATFORM_START = 12;
	public static final int PLATFORM_END = 78;
	// directions of the four ports of a station: east, south, west, north
	public static final int[] DX = {1, 0, -1, 0};
	public static final int[] DZ = {0, 1, 0, -1};

	public final long seed;
	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor heights;
	private final Map<Long, Optional<Node>> nodes = new ConcurrentHashMap<>();
	private final Map<Long, Settlement> settlements = lru(64);
	private final Map<Long, Edge> edges = lru(256);

	public NusantaraNetwork(long seed, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights) {
		this.seed = seed;
		this.generator = generator;
		this.randomState = randomState;
		this.heights = heights;
	}

	private static <V> Map<Long, V> lru(int size) {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
				return size() > size;
			}
		};
	}

	// computed outside the lock: two threads may both work it out, they get the same answer
	private static <V> V cached(Map<Long, V> map, long key, Function<Long, V> compute) {
		synchronized (map) {
			V value = map.get(key);
			if (value != null) {
				return value;
			}
		}
		V value = compute.apply(key);
		synchronized (map) {
			map.put(key, value);
		}
		return value;
	}

	public static long key(int a, int b) {
		return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
	}

	public int hash(int a, int b, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + a * 0x632BE59BD9B4E019L + b * 0x85157AF5L + salt * 0x27D4EB2FL;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return (int) h;
	}

	public double unit(int a, int b, int salt) {
		return Math.floorMod(hash(a, b, salt), 1_000_000) / 1_000_000.0;
	}

	// the natural ground (or sea floor) and the water surface of the terrain before anything is built
	public int ground(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState) - 1;
	}

	public int surface(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState) - 1;
	}

	public static int cellOf(double c) {
		return Mth.floor(c / CELL);
	}

	// ---------------------------------------------------------------- settlements

	// where the station of a cell's settlement is, on dry, not too rough land
	public record Node(int cx, int cz, int x, int z, int ground) {
	}

	public Optional<Node> node(int cx, int cz) {
		return nodes.computeIfAbsent(key(cx, cz), k -> Optional.ofNullable(findNode(cx, cz)));
	}

	private @Nullable Node findNode(int cx, int cz) {
		Node best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 24; i++) {
			int x = cx * CELL + CELL / 2 + Math.floorMod(hash(cx, cz, 100 + i), JITTER * 2 + 1) - JITTER;
			int z = cz * CELL + CELL / 2 + Math.floorMod(hash(cx, cz, 200 + i), JITTER * 2 + 1) - JITTER;
			int lowest = Integer.MAX_VALUE;
			int highest = Integer.MIN_VALUE;
			int sum = 0;
			int wet = 0;
			for (int dx = -64; dx <= 64; dx += 64) {
				for (int dz = -64; dz <= 64; dz += 64) {
					int g = ground(x + dx, z + dz);
					if (surface(x + dx, z + dz) > g || g <= SEA) {
						wet++;
					}
					lowest = Math.min(lowest, g);
					highest = Math.max(highest, g);
					sum += g;
				}
			}
			// a cell all sea still gets its station, on a little island of its own (the line must go on), but any land wins
			int middle = sum / 9;
			// flat land near the usual lowland height: a station up a mountain leaves its lines on towering viaducts
			double score = (highest - lowest) + Math.abs(middle - 72) * 1.2 + wet * 200;
			if (score < bestScore) {
				bestScore = score;
				best = new Node(cx, cz, x, z, Math.max(SEA + 2, ground(x, z)));
			}
		}
		return best;
	}

	// a line from a cell to its east (port 0) or south (port 1) neighbour. every settlement links east, so a row of
	// them is one endless line; some link south too, and a settlement that would be left alone gets linked to its
	// first neighbour
	private boolean baseLink(int cx, int cz, boolean east) {
		int nx = east ? cx + 1 : cx;
		int nz = east ? cz : cz + 1;
		if (node(cx, cz).isEmpty() || node(nx, nz).isEmpty()) {
			return false;
		}
		if (east) {
			return true;
		}
		return Math.floorMod(cx + hash(cz, 0, 12), 3) == 0 || Math.floorMod(hash(cx, cz, 13), 4) == 0;
	}

	private boolean baseLinkTowards(int cx, int cz, int port) {
		return switch (port) {
			case 0 -> baseLink(cx, cz, true);
			case 1 -> baseLink(cx, cz, false);
			case 2 -> baseLink(cx - 1, cz, true);
			default -> baseLink(cx, cz - 1, false);
		};
	}

	private int lonelyPort(int cx, int cz) {
		for (int port = 0; port < 4; port++) {
			if (baseLinkTowards(cx, cz, port)) {
				return -1;
			}
		}
		for (int port = 0; port < 4; port++) {
			if (node(cx + DX[port], cz + DZ[port]).isPresent()) {
				return port;
			}
		}
		return -1;
	}

	public boolean linked(int cx, int cz, int port) {
		if (node(cx, cz).isEmpty() || node(cx + DX[port], cz + DZ[port]).isEmpty()) {
			return false;
		}
		return baseLinkTowards(cx, cz, port) || lonelyPort(cx, cz) == port || lonelyPort(cx + DX[port], cz + DZ[port]) == (port + 2) % 4;
	}

	public @Nullable Settlement settlement(int cx, int cz) {
		Optional<Node> node = node(cx, cz);
		if (node.isEmpty()) {
			return null;
		}
		return cached(settlements, key(cx, cz), k -> new Settlement(this, node.get()));
	}

	// ---------------------------------------------------------------- lines

	public enum Mode { OPEN, TUNNEL, VIADUCT, STEEL }

	// a line between the settlement of cell (cx, cz) and its east or south neighbour
	public final class Edge {
		public final long key;
		public final Settlement a;
		public final Settlement b;
		public final int portA;
		public final int portB;
		public final boolean doubleTrack;
		public final String deck;
		public final String lining;
		// one sample per block along the line: position, direction, bed height and how it gets through the land
		public final double[] xs;
		public final double[] zs;
		public final double[] txs;
		public final double[] tzs;
		private int[] bed;
		// the bed as an even grade (see `grade`): where the track and the top of the ballast lie
		private double[] even;
		private Mode[] modes;
		public final int minX;
		public final int minZ;
		public final int maxX;
		public final int maxZ;

		Edge(long key, Settlement a, Settlement b, boolean east) {
			this.key = key;
			this.a = a;
			this.b = b;
			this.portA = east ? 0 : 1;
			this.portB = east ? 2 : 3;
			int h = hash(a.node.cx(), a.node.cz(), east ? 300 : 301);
			// most lines are single track, only some main lines between two towns are doubled. a line into a terminus
			// stays single, so the loco can run round its train on the station loop there
			boolean terminus = !linked(a.node.cx(), a.node.cz(), east ? 2 : 3) || !linked(b.node.cx(), b.node.cz(), east ? 0 : 1);
			doubleTrack = !terminus && (a.tier >= 2 && b.tier >= 2 ? Math.floorMod(h, 100) < 60 : Math.floorMod(h, 100) < 8);
			deck = List.of("stone_bricks", "bricks", "mud_bricks", "stone_bricks").get(Math.floorMod(h >> 10, 4));
			lining = List.of("stone_bricks", "andesite", "bricks", "deepslate_bricks").get(Math.floorMod(h >> 12, 4));

			List<double[]> path = path(east, h);
			int n = path.size();
			xs = new double[n];
			zs = new double[n];
			txs = new double[n];
			tzs = new double[n];
			int x0 = Integer.MAX_VALUE;
			int z0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z1 = Integer.MIN_VALUE;
			for (int i = 0; i < n; i++) {
				xs[i] = path.get(i)[0];
				zs[i] = path.get(i)[1];
				double[] before = path.get(Math.max(0, i - 2));
				double[] after = path.get(Math.min(n - 1, i + 2));
				double tx = after[0] - before[0];
				double tz = after[1] - before[1];
				double len = Math.sqrt(tx * tx + tz * tz);
				txs[i] = tx / len;
				tzs[i] = tz / len;
				x0 = Math.min(x0, Mth.floor(xs[i]));
				z0 = Math.min(z0, Mth.floor(zs[i]));
				x1 = Math.max(x1, Mth.floor(xs[i]));
				z1 = Math.max(z1, Mth.floor(zs[i]));
			}
			minX = x0;
			minZ = z0;
			maxX = x1;
			maxZ = z1;
		}

		public int length() {
			return xs.length;
		}

		// straight out of the first station, a winding way through the country, straight into the second station
		private List<double[]> path(boolean east, int h) {
			int[] da = {DX[portA], DZ[portA]};
			int[] db = {DX[portB], DZ[portB]};
			double sx = a.node.x() + da[0] * TRACK_START;
			double sz = a.node.z() + da[1] * TRACK_START;
			double q0x = a.node.x() + da[0] * a.exit(portA);
			double q0z = a.node.z() + da[1] * a.exit(portA);
			double q1x = b.node.x() + db[0] * b.exit(portB);
			double q1z = b.node.z() + db[1] * b.exit(portB);
			double ex = b.node.x() + db[0] * TRACK_START;
			double ez = b.node.z() + db[1] * TRACK_START;

			// the way through the country follows the land like a surveyor would: every ~120 blocks a knot slides sideways
			// so the line climbs and drops as little as it can from one knot to the next (along the contours, round the
			// hills), keeps out of the water and turns gently. the choice is made over the whole line at once (cheapest path through the knot
			// choices), with a little noise of its own so no two lines wind the same way
			double dx = q1x - q0x;
			double dz = q1z - q0z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			double px = -dz / dist;
			double pz = dx / dist;
			int count = Math.max(1, (int) Math.round(dist / 120) - 1);
			int choices = 13;
			double reach = Math.min(300, dist * 0.25);
			double ya = a.node.ground();
			double yb = b.node.ground();
			double[][] cost = new double[count][choices];
			int[][] from = new int[count][choices];
			double[][][] at = new double[count][choices][];
			int[][] height = new int[count][choices];
			for (int k = 0; k < count; k++) {
				double f = (k + 1) / (double) (count + 1);
				for (int c = 0; c < choices; c++) {
					double side = (c / (double) (choices - 1) * 2 - 1) * reach;
					double x = q0x + dx * f + px * side;
					double z = q0z + dz * f + pz * side;
					at[k][c] = new double[] {x, z};
					int g = ground((int) x, (int) z);
					height[k][c] = g;
					double here = (surface((int) x, (int) z) > g ? 40 : 0) + Math.abs(g - Mth.lerp(f, ya, yb)) * 0.3 + unit(h, k * 31 + c, 312) * 4
							+ Math.abs(side) / reach * 3;
					if (k == 0) {
						cost[k][c] = here + Math.abs(g - ya) * 1.5;
						continue;
					}
					cost[k][c] = Double.MAX_VALUE;
					for (int d = Math.max(0, c - 2); d <= Math.min(choices - 1, c + 2); d++) {
						// climbing or dropping between two knots is what makes banks, viaducts and tunnels: along the
						// contours is cheapest, and every step sideways is a sharper bend
						double total = cost[k - 1][d] + here + Math.abs(g - height[k - 1][d]) * 1.5 + Math.abs(c - d) * 4;
						if (total < cost[k][c]) {
							cost[k][c] = total;
							from[k][c] = d;
						}
					}
				}
			}
			int pick = 0;
			for (int c = 1; c < choices; c++) {
				if (cost[count - 1][c] + Math.abs(height[count - 1][c] - yb) * 1.5 < cost[count - 1][pick] + Math.abs(height[count - 1][pick] - yb) * 1.5) {
					pick = c;
				}
			}
			double[][] chosen = new double[count][];
			for (int k = count - 1; k >= 0; k--) {
				chosen[k] = at[k][pick];
				pick = from[k][pick];
			}
			List<double[]> knots = new ArrayList<>();
			knots.add(new double[] {q0x, q0z});
			for (double[] knot : chosen) {
				knots.add(knot);
			}
			knots.add(new double[] {q1x, q1z});

			List<double[]> fine = new ArrayList<>();
			line(fine, sx, sz, q0x, q0z);
			for (int k = 0; k < knots.size() - 1; k++) {
				double[] p0 = knots.get(k);
				double[] p1 = knots.get(k + 1);
				double seg = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
				double[] m0 = tangent(knots, k, da, db, seg);
				double[] m1 = tangent(knots, k + 1, da, db, seg);
				int steps = Math.max(8, (int) (seg * 2));
				for (int s = 1; s <= steps; s++) {
					double t = s / (double) steps;
					double t2 = t * t;
					double t3 = t2 * t;
					double h00 = 2 * t3 - 3 * t2 + 1;
					double h10 = t3 - 2 * t2 + t;
					double h01 = -2 * t3 + 3 * t2;
					double h11 = t3 - t2;
					fine.add(new double[] {h00 * p0[0] + h10 * m0[0] + h01 * p1[0] + h11 * m1[0], h00 * p0[1] + h10 * m0[1] + h01 * p1[1] + h11 * m1[1]});
				}
			}
			line(fine, q1x, q1z, ex, ez);
			return resample(fine);
		}

		// catmull-rom tangents inside, the station directions at the two ends
		private double[] tangent(List<double[]> knots, int k, int[] da, int[] db, double seg) {
			if (k == 0) {
				return new double[] {da[0] * seg, da[1] * seg};
			}
			if (k == knots.size() - 1) {
				return new double[] {-db[0] * seg, -db[1] * seg};
			}
			double[] before = knots.get(k - 1);
			double[] after = knots.get(k + 1);
			double tx = (after[0] - before[0]) / 2;
			double tz = (after[1] - before[1]) / 2;
			double scale = seg / Math.max(1, Math.hypot(tx, tz));
			return new double[] {tx * scale, tz * scale};
		}

		private static void line(List<double[]> out, double x0, double z0, double x1, double z1) {
			int steps = Math.max(1, (int) (Math.hypot(x1 - x0, z1 - z0) * 2));
			for (int s = out.isEmpty() ? 0 : 1; s <= steps; s++) {
				double t = s / (double) steps;
				out.add(new double[] {x0 + (x1 - x0) * t, z0 + (z1 - z0) * t});
			}
		}

		// one point per block of distance along the way
		private static List<double[]> resample(List<double[]> fine) {
			List<double[]> out = new ArrayList<>();
			out.add(fine.getFirst());
			double carried = 0;
			for (int i = 1; i < fine.size(); i++) {
				double[] p0 = fine.get(i - 1);
				double[] p1 = fine.get(i);
				double seg = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
				while (carried + seg >= 1) {
					double t = (1 - carried) / seg;
					double[] point = {p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t};
					out.add(point);
					seg -= 1 - carried;
					p0 = point;
					carried = 0;
				}
				carried += seg;
			}
			return out;
		}

		public int bed(int i) {
			profile();
			return bed[Mth.clamp(i, 0, bed.length - 1)];
		}

		public Mode mode(int i) {
			profile();
			return modes[Mth.clamp(i, 0, modes.length - 1)];
		}

		// the height of the bed at any distance along, on its even grade: the top of the ballast is one block over it.
		// the track, the trains and the lori all ride on this
		public double bedAt(double s) {
			profile();
			int i = Mth.clamp(Mth.floor(s), 0, even.length - 2);
			double f = Mth.clamp(s - i, 0, 1);
			return Mth.lerp(f, even[i], even[i + 1]);
		}

		// the height profile: level through both stations, then following the land at a gentle grade, raised over
		// water and sometimes up on a long viaduct; tunnels where the hills stand well above it, bridges where the
		// land drops away
		// the wanted heights, held at the stations at both ends and kept within the grade from one sample to the next
		private static double[] graded(double[] want, boolean[] pinned, int pinA, double ya, double yb, double grade, int step) {
			int coarse = want.length;
			double[] y = new double[coarse];
			for (int c = 0; c < coarse; c++) {
				y[c] = pinned[c] ? (c * step <= pinA ? ya : yb) : Mth.clamp(want[c], 40, 220);
			}
			for (int pass = 0; pass < 4; pass++) {
				for (int c = 1; c < coarse; c++) {
					if (!pinned[c]) {
						y[c] = Mth.clamp(y[c], y[c - 1] - grade, y[c - 1] + grade);
					}
				}
				for (int c = coarse - 2; c >= 0; c--) {
					if (!pinned[c]) {
						y[c] = Mth.clamp(y[c], y[c + 1] - grade, y[c + 1] + grade);
					}
				}
			}
			return y;
		}

		private synchronized void profile() {
			if (bed != null) {
				return;
			}
			int n = length();
			int step = 8;
			int coarse = n / step + 2;
			double[] land = new double[coarse];
			double[] water = new double[coarse];
			boolean[] pinned = new boolean[coarse];
			int pinA = a.exit(portA) - TRACK_START + 6;
			int pinB = n - (b.exit(portB) - TRACK_START + 6);
			for (int c = 0; c < coarse; c++) {
				int i = Math.min(n - 1, c * step);
				int x = Mth.floor(xs[i]);
				int z = Mth.floor(zs[i]);
				land[c] = ground(x, z);
				int top = surface(x, z);
				water[c] = top > land[c] ? top : -999;
				pinned[c] = c * step <= pinA || c * step >= pinB;
			}
			int h = (int) (key ^ (key >>> 32));
			boolean hugging = Math.floorMod(hash(h, 0, 320), 10) < 4;
			int window = hugging ? 3 : 6;
			double[] want = new double[coarse];
			for (int c = 0; c < coarse; c++) {
				double sum = 0;
				int count = 0;
				for (int w = -window; w <= window; w++) {
					int k = Mth.clamp(c + w, 0, coarse - 1);
					sum += Math.max(land[k], SEA);
					count++;
				}
				want[c] = sum / count;
				if (water[c] > -999) {
					want[c] = Math.max(want[c], water[c] + 4);
				}
			}
			// sometimes a long viaduct over the lowlands
			if (Math.floorMod(hash(h, 1, 321), 100) < 35) {
				int span = 20 + Math.floorMod(hash(h, 2, 322), 20);
				int from = Mth.clamp((int) (coarse * (0.25 + unit(h, 3, 323) * 0.5)) - span / 2, 0, coarse - 1);
				for (int c = from; c < Math.min(coarse, from + span); c++) {
					want[c] = Math.max(want[c], land[c] + 9);
				}
			}
			// a long stretch of water (a lake, a strait or the sea) is crossed on a long steel bridge or, half the time,
			// in a tube along the bottom with glass where the water is, so the riders see the fish
			double[] plain = want.clone();
			boolean[] tube = new boolean[coarse];
			for (int c = 0; c < coarse; c++) {
				if (water[c] <= -999) {
					continue;
				}
				int end = c;
				double floor = land[c];
				while (end < coarse && water[end] > -999) {
					floor = Math.min(floor, land[end]);
					end++;
				}
				if (end - c >= 10 && Math.floorMod(hash(h, c, 324), 2) == 0) {
					for (int k = c; k < end; k++) {
						tube[k] = true;
						want[k] = floor - 1;
					}
					// the line starts going down well before the shore (in a tunnel) and comes back up after it
					for (int k = 0; k < coarse; k++) {
						int away = k < c ? c - k : k >= end ? k - end + 1 : 0;
						want[k] = Math.min(want[k], floor - 1 + away * 0.09 * 8);
					}
				}
				c = end;
			}
			double ya = a.node.ground();
			double yb = b.node.ground();
			double free = Math.max(1, pinB - pinA);
			// a gentle grade: the route itself already winds along the land, so banks, viaducts and tunnels stay small
			double grade = Math.max(0.09, Math.abs(yb - ya) / free * 1.15) * step;
			double[] y = graded(want, pinned, pinA, ya, yb, grade, step);
			// a tube the grade can't get down to the bottom would stand in the open water: then the line crosses on bridges
			for (int c = 0; c < coarse; c++) {
				if (!tube[c]) {
					continue;
				}
				int end = c;
				boolean deep = true;
				while (end < coarse && tube[end]) {
					deep &= y[end] + 8 <= water[end];
					end++;
				}
				if (!deep) {
					// every crossing of this line goes back to bridges, and the dives towards them go
					tube = new boolean[coarse];
					y = graded(plain, pinned, pinA, ya, yb, grade, step);
					break;
				}
				c = end;
			}
			// how each stretch gets through the land, short stretches folded into their neighbours
			Mode[] cm = new Mode[coarse];
			for (int c = 0; c < coarse; c++) {
				double above = land[c] - y[c];
				// open line along the platforms; beyond them a station in the hills is left through a tunnel, not a canyon
				boolean platforms = c * step <= PLATFORM_END + 24 || c * step >= n - (PLATFORM_END + 24);
				if (platforms) {
					cm[c] = Mode.OPEN;
				} else if (tube[c]) {
					cm[c] = Mode.TUNNEL;
				} else if (water[c] > -999 && water[c] >= y[c] - 6) {
					cm[c] = Mode.STEEL;
				} else if (above >= 7) {
					cm[c] = Mode.TUNNEL;
				} else if (water[c] > -999) {
					// over water it is always a steel bridge on piers, never a stone viaduct
					cm[c] = Mode.STEEL;
				} else if (-above >= 5) {
					cm[c] = Mode.VIADUCT;
				} else {
					cm[c] = Mode.OPEN;
				}
			}
			for (int c = 0; c < coarse; c++) {
				if (cm[c] != Mode.OPEN) {
					continue;
				}
				int end = c;
				while (end < coarse && cm[end] == Mode.OPEN) {
					end++;
				}
				// a short bit of open line between two tunnels stays underground
				if (end - c < 3 && c > 0 && end < coarse && cm[c - 1] == Mode.TUNNEL && cm[end] == Mode.TUNNEL) {
					for (int k = c; k < end; k++) {
						cm[k] = Mode.TUNNEL;
					}
				}
				c = end;
			}
			for (int c = 0; c < coarse; c++) {
				if (cm[c] != Mode.TUNNEL) {
					continue;
				}
				int end = c;
				while (end < coarse && cm[end] == Mode.TUNNEL) {
					end++;
				}
				if (end - c < 3) {
					for (int k = c; k < end; k++) {
						cm[k] = Mode.OPEN;
					}
				}
				c = end;
			}
			int[] beds = new int[n];
			Mode[] fine = new Mode[n];
			for (int i = 0; i < n; i++) {
				int c = i / step;
				double f = (i - c * step) / (double) step;
				beds[i] = (int) Math.round(Mth.lerp(f, y[c], y[Math.min(coarse - 1, c + 1)]));
				fine[i] = cm[Math.min(coarse - 1, (i + step / 2) / step)];
			}
			modes = fine;
			even = grade(beds);
			bed = beds;
		}

		// a real line climbs at an even grade, not in steps: between the middles of two level stretches of the bed the
		// height ramps straight from one to the other, over at most 6 blocks either side of the step, so long level
		// stretches (the stations) stay level right up to it
		private static double[] grade(int[] beds) {
			int n = beds.length;
			double[] out = new double[n];
			int a = 0;
			while (a < n) {
				int e = a;
				while (e + 1 < n && beds[e + 1] == beds[a]) {
					e++;
				}
				double c = (a + e) / 2.0;
				for (int i = a; i <= e; i++) {
					out[i] = beds[a];
					if (i >= c && e + 1 < n) {
						// towards the next stretch
						int b = e + 1;
						while (b + 1 < n && beds[b + 1] == beds[e + 1]) {
							b++;
						}
						double lo = Math.max(c, e + 0.5 - RAMP);
						double hi = Math.min((e + 1 + b) / 2.0, e + 0.5 + RAMP);
						if (i >= lo) {
							out[i] = Mth.lerp((i - lo) / (hi - lo), beds[a], beds[e + 1]);
						}
					} else if (i < c && a > 0) {
						// coming off the stretch before
						int b = a - 1;
						while (b - 1 >= 0 && beds[b - 1] == beds[a - 1]) {
							b--;
						}
						double lo = Math.max((b + a - 1) / 2.0, a - 0.5 - RAMP);
						double hi = Math.min(c, a - 0.5 + RAMP);
						if (i <= hi) {
							out[i] = Mth.lerp((i - lo) / (hi - lo), beds[a - 1], beds[a]);
						}
					}
				}
				a = e + 1;
			}
			return out;
		}


		// how far into a station's track layout a point of the line is: 1 along the platforms, easing to 0 out on the
		// line, where the station tracks have all merged back into the line's own
		public double station(double i) {
			double zone = PLATFORM_END - TRACK_START + 4;
			double ease = 28;
			double fromEnd = Math.min(i, length() - 1 - i);
			double t = Mth.clamp((zone + ease - fromEnd) / ease, 0, 1);
			return t * t * (3 - 2 * t);
		}

		// the tracks: where each lies across the line out in the country and at the stations. trains heading along
		// the line (east or south) run on the first, trains heading back on the second. every station has the same two
		// tracks between its platforms, so trains run straight on through it from one line to the next: a single line
		// splits into them (a passing loop, where its trains cross), a double line simply runs into them
		// the third is the inspection trolley's siding: a loop beside the first track just out of each station, where
		// the line's lori inspeksi parks between runs
		public double[][] lanes() {
			if (doubleTrack) {
				return new double[][] {{-2, -2}, {2, 2}, {-2, -2}};
			}
			return new double[][] {{0, -2}, {0, 2}, {0, -2}};
		}

		// whether a lane is laid at this sample: station loops only where they have split away from the line's own
		// track, the siding only where it has split away from the first
		public boolean laid(int lane, double i) {
			if (lane == SIDING_LANE) {
				return siding(i) >= 0.02;
			}
			return doubleTrack || lane == 0 || station(i) >= 0.02;
		}

		public double laneOffset(int lane, double i) {
			double[] l = lanes()[lane];
			double offset = Mth.lerp(station(i), l[0], l[1]);
			return lane == SIDING_LANE ? offset - SIDING_SPACING * siding(i) : offset;
		}

		// how far the siding has swung out beside the first track: it leaves it SIDING_IN samples from either end of
		// the line, runs alongside, and joins it again by SIDING_OUT
		public double siding(double i) {
			double fromEnd = Math.min(i, length() - 1 - i);
			double in = Mth.clamp((fromEnd - SIDING_IN) / SIDING_RAMP, 0, 1);
			double out = Mth.clamp((SIDING_OUT - fromEnd) / SIDING_RAMP, 0, 1);
			double t = Math.min(in, out);
			return t * t * (3 - 2 * t);
		}

		// where the lori parks in the siding at the start (or end) of the line, as a sample
		public double parking(boolean atStart) {
			double middle = (SIDING_IN + SIDING_OUT) / 2.0;
			return atStart ? middle : length() - 1 - middle;
		}

		// half the width of the ballast here: wider at stations where more tracks lie side by side
		public int ballast(double i) {
			int line = doubleTrack ? 3 : 1;
			int yard = stationBallast();
			int base = (int) Math.round(Mth.lerp(station(i), line, yard));
			// wider where the siding runs beside the line
			return Math.max(base, (int) Math.round(Mth.lerp(siding(i), base, Math.abs(lanes()[0][0]) + SIDING_SPACING + 1)));
		}

		// the ballast along the platforms, the platforms start just beyond it
		public int stationBallast() {
			return 3;
		}
	}

	public @Nullable Edge edge(int cx, int cz, boolean east) {
		int port = east ? 0 : 1;
		if (!linked(cx, cz, port)) {
			return null;
		}
		long k = (key(cx, cz) << 1) ^ (east ? 1 : 0);
		return cached(edges, k, kk -> new Edge(k, settlement(cx, cz), settlement(cx + DX[port], cz + DZ[port]), east));
	}

	// every line that might pass near a block position
	public List<Edge> edgesNear(int x, int z) {
		List<Edge> out = new ArrayList<>();
		int cx = cellOf(x);
		int cz = cellOf(z);
		for (int i = cx - 2; i <= cx + 1; i++) {
			for (int j = cz - 2; j <= cz + 1; j++) {
				for (boolean east : new boolean[] {true, false}) {
					Edge edge = edge(i, j, east);
					if (edge != null && x >= edge.minX - 64 && x <= edge.maxX + 64 && z >= edge.minZ - 64 && z <= edge.maxZ + 64) {
						out.add(edge);
					}
				}
			}
		}
		return out;
	}

	public @Nullable Edge edgeByKey(long k) {
		boolean east = (k & 1) == 1;
		long cell = k >> 1;
		int cx = (int) (cell >> 32);
		int cz = (int) cell;
		return edge(cx, cz, east);
	}

	// a line: the stations along one row (east-west) or one column (north-south) joined one after another, as far as
	// the links go, which may be for ever. trains run along all of it, through every station, and only turn back at a
	// real end (where the land runs out of links). a Chain is a window onto it around one line, `anchor`: distances
	// along it are measured from that line, and only reach as far as anyone near it can see
	public final class Chain {
		// how many lines either side of the anchor the window looks for an end
		private static final int SCAN = 6;
		public final boolean row;
		public final int fixed;
		public final int anchor;
		public final int start;
		public final int end;
		// whether start and end are real ends of the line (a terminus), or just the edge of the window
		public final boolean startReal;
		public final boolean endReal;
		// the same for every window onto this row or column
		public final long key;
		// where each line between two stations starts, as a distance from the anchor line, filled as needed
		private final Map<Integer, Double> offsets = new ConcurrentHashMap<>();

		Chain(boolean row, int fixed, int anchor) {
			this.row = row;
			this.fixed = fixed;
			this.anchor = anchor;
			this.key = ((long) (row ? 1 : 0) << 62) ^ (key(fixed, 0) & ((1L << 62) - 1));
			int a = anchor;
			while (a > anchor - SCAN && linked(row ? a : fixed, row ? fixed : a, row ? 2 : 3)) {
				a--;
			}
			startReal = a > anchor - SCAN;
			int e = anchor + 1;
			while (e < anchor + 1 + SCAN && linked(row ? e : fixed, row ? fixed : e, row ? 0 : 1)) {
				e++;
			}
			endReal = e < anchor + 1 + SCAN;
			start = a;
			end = e;
			offsets.put(anchor, 0.0);
		}

		public Edge edge(int k) {
			return row ? NusantaraNetwork.this.edge(k, fixed, true) : NusantaraNetwork.this.edge(fixed, k, false);
		}

		public Settlement station(int j) {
			return row ? settlement(j, fixed) : settlement(fixed, j);
		}

		// a line and its two station stretches: hub to hub
		private double span(int k) {
			return edge(k).length() - 1 + 2 * TRACK_START;
		}

		public double offset(int k) {
			Double known = offsets.get(k);
			if (known != null) {
				return known;
			}
			// walk out from the anchor, one line at a time
			double d = 0;
			if (k > anchor) {
				for (int i = anchor; i < k; i++) {
					d += span(i);
					offsets.putIfAbsent(i + 1, d);
				}
			} else {
				for (int i = anchor - 1; i >= k; i--) {
					d -= span(i);
					offsets.putIfAbsent(i, d);
				}
			}
			return d;
		}

		// the line a distance along the chain lies on; between two lines (inside a station) the one after it
		public int lineAt(double d) {
			int k = Mth.clamp(anchor, start, end - 1);
			if (d < offset(k) + edge(k).length() - 1) {
				while (k > start && d < offset(k - 1) + edge(k - 1).length() - 1) {
					k--;
				}
				return k;
			}
			while (k < end - 1 && d >= offset(k) + edge(k).length() - 1) {
				k++;
			}
			return k;
		}

		// where the hub of station j lies along the chain
		public double nodeAt(int j) {
			return offset(j) - TRACK_START;
		}

		// a point along the chain: x, z, direction x, direction z, bed height, and the lane offset of a track there.
		// beyond its two ends it carries straight on through the end station into the headshunt
		public double[] point(double d, int lane) {
			boolean pastStart = startReal && d < offset(start);
			boolean pastEnd = endReal && d > offset(end) - 2 * TRACK_START;
			if (pastStart || pastEnd) {
				int j = pastStart ? start : end;
				Settlement here = station(j);
				Edge edge = edge(pastStart ? start : end - 1);
				double past = d - nodeAt(j);
				return new double[] {here.node.x() + (row ? past : 0), here.node.z() + (row ? 0 : past), row ? 1 : 0, row ? 0 : 1, here.node.ground(),
						edge.lanes()[lane][1]};
			}
			int k = lineAt(d);
			if (d >= offset(k) || k == start) {
				Edge edge = edge(k);
				double along = Mth.clamp(d - offset(k), 0, edge.length() - 1);
				int i = Mth.floor(along);
				int j = Math.min(edge.length() - 1, i + 1);
				double f = along - i;
				return new double[] {Mth.lerp(f, edge.xs[i], edge.xs[j]), Mth.lerp(f, edge.zs[i], edge.zs[j]),
						Mth.lerp(f, edge.txs[i], edge.txs[j]), Mth.lerp(f, edge.tzs[i], edge.tzs[j]), edge.bedAt(along), edge.laneOffset(lane, along)};
			}
			// straight through the station between line k - 1 and line k, at the station's track spacing
			Edge before = edge(k - 1);
			Edge after = edge(k);
			Settlement here = station(k);
			double f = (d - (offset(k) - 2 * TRACK_START)) / (2.0 * TRACK_START);
			int last = before.length() - 1;
			return new double[] {Mth.lerp(f, before.xs[last], after.xs[0]), Mth.lerp(f, before.zs[last], after.zs[0]),
					row ? 1 : 0, row ? 0 : 1, here.node.ground(), before.lanes()[lane][1]};
		}

		// where trains stop at station j: the middle of its platforms (on the arm towards the settlement)
		public double stop(int j) {
			Settlement here = station(j);
			int arm = here.platformArm(row);
			int forward = row ? 0 : 1;
			double toMiddle = PLATFORM_START - TRACK_START + (PLATFORM_END - PLATFORM_START) / 2.0;
			if (arm == forward) {
				return offset(j) + toMiddle;
			}
			Edge before = edge(j - 1);
			return offset(j - 1) + before.length() - 1 - toMiddle;
		}

		public boolean doubleTrack(int k) {
			return edge(k).doubleTrack;
		}
	}

	private final Map<Long, Chain> chains = lru(64);

	// the window onto the row or column of a line, around that line
	public Chain chainOf(Edge edge) {
		boolean row = edge.portA == 0;
		int fixed = row ? edge.a.node.cz() : edge.a.node.cx();
		int k = row ? edge.a.node.cx() : edge.a.node.cz();
		return chainAround(row, fixed, k);
	}

	private Chain chainAround(boolean row, int fixed, int k) {
		long chainKey = ((long) (row ? 1 : 0) << 62) ^ (key(fixed, k) & ((1L << 62) - 1));
		return cached(chains, chainKey, x -> new Chain(row, fixed, k));
	}

	// the window around line k of a row (or column) `fixed`, or null when there is no such line
	public @Nullable Chain chainAt(boolean row, int fixed, int k) {
		Edge edge = row ? edge(k, fixed, true) : edge(fixed, k, false);
		return edge == null ? null : chainOf(edge);
	}

	public List<Settlement> settlementsNear(int x, int z) {
		List<Settlement> out = new ArrayList<>();
		int cx = cellOf(x);
		int cz = cellOf(z);
		for (int i = cx - 1; i <= cx + 1; i++) {
			for (int j = cz - 1; j <= cz + 1; j++) {
				Settlement settlement = settlement(i, j);
				if (settlement != null) {
					out.add(settlement);
				}
			}
		}
		return out;
	}
}
