package com.raihanadf.blossom.kampung;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

// the layout of one kampung, worked out from its seed alone so every chunk of it agrees.
// a main street runs in from the gapura to the beringin in the middle, narrow gang branch off it and every house
// stands along one of them, facing its street. the fence then goes round whatever got built.
// coordinates are relative to the village centre, the ground level is shared by everything
public class KampungPlan {

	// homes, the dapur, a sawah plot, the satpam's post, a warung, the musholla, the pos ronda, a well, a chicken coop
	// and a patio where the harvested rice is spread out to dry
	public enum Kind { HOUSE, KITCHEN, FIELD, POST, WARUNG, MUSHOLLA, RONDA, SUMUR, KANDANG, JEMURAN }

	// where in the archipelago the village is: every house in it is built the way they build there
	public enum Region { JAWA, SUNDA, BUGIS, TORAJA, MINANG, BALI }

	// javanese limasan, joglo and gable kampung houses, the low sundanese stilt house, bugis rumah panggung,
	// the toraja tongkonan, the minang rumah gadang and the balinese brick and stone house
	public enum Style {
		LIMASAN, JOGLO, KAMPUNG, SUNDA, PANGGUNG, TONGKONAN, GADANG, BALI;

		// how far the floor sits above the ground: everything outside java and bali stands on stilts
		public static int raise(Style style) {
			return switch (style) {
				case SUNDA -> 1;
				case PANGGUNG, GADANG -> 2;
				case TONGKONAN -> 3;
				default -> 0;
			};
		}
	}

	// a rectangle x0..x1, z0..z1 with its door (or front) facing `front`
	public record Building(Kind kind, int x0, int z0, int x1, int z1, Direction front, Style style, int palette, int residents) {
		public int centerX() {
			return (x0 + x1) / 2;
		}

		public int centerZ() {
			return (z0 + z1) / 2;
		}

		// local frame: lx runs along the front, lz from the back wall (0) to the front edge (depth - 1)
		public int width() {
			return front.getAxis() == Direction.Axis.Z ? x1 - x0 + 1 : z1 - z0 + 1;
		}

		public int depth() {
			return front.getAxis() == Direction.Axis.Z ? z1 - z0 + 1 : x1 - x0 + 1;
		}

		public int x(int lx, int lz) {
			return switch (front) {
				case SOUTH -> x0 + lx;
				case NORTH -> x1 - lx;
				case EAST -> x0 + lz;
				default -> x1 - lz;
			};
		}

		public int z(int lx, int lz) {
			return switch (front) {
				case SOUTH -> z0 + lz;
				case NORTH -> z1 - lz;
				case EAST -> z1 - lx;
				default -> z0 + lx;
			};
		}

		// turns a direction given in the local frame (south = towards the front) into the world
		public Direction turn(Direction local) {
			int steps = switch (front) {
				case SOUTH -> 0;
				case WEST -> 1;
				case NORTH -> 2;
				default -> 3;
			};
			Direction out = local;
			for (int i = 0; i < steps; i++) {
				out = out.getClockWise();
			}
			return out;
		}
	}

	// a street `half` blocks either side of the line at `across`, running from `from` to `to` along x (or along z)
	public record Lane(boolean alongX, int across, int from, int to, int half, boolean main) {
		public boolean contains(int x, int z) {
			int along = alongX ? x : z;
			int side = alongX ? z : x;
			return along >= from && along <= to && Math.abs(side - across) <= half;
		}
	}

	// the front yard between a street and the house: a hedge or a low wall, then a step of garden
	public static final int SETBACK = 2;
	// between two gang there is room for two rows of houses standing back to back
	private static final int SPACING = 30;
	// the square round the beringin in the middle
	public static final int PLAZA = 5;
	private static final int GRID = 120;

	public final long seed;
	public final int size;
	public final Region region;
	public final float gateAngle;
	public final int gateX;
	public final int gateZ;
	public final boolean bambooFence;
	public final List<Building> buildings = new ArrayList<>();
	public final List<Lane> lanes = new ArrayList<>();
	// how far the plan reaches from its middle (the fence's furthest point plus a little)
	public final int extent;
	private final float[] radii = new float[24];
	private final int outX;
	private final int outZ;
	private final List<Integer> laneOf = new ArrayList<>();
	private boolean[][] taken;

	public KampungPlan(long seed) {
		this(seed, Float.NaN, 3 + RandomSource.create(seed ^ 0x5157EL).nextInt(28));
	}

	// `gate` points the gate a given way (radians, x = cos, z = sin), NaN leaves it to chance. `size` is how many
	// houses it gets (3 to 30), the shared buildings come on top and the fence grows to fit them all
	public KampungPlan(long seed, float gate, int size) {
		this.seed = seed;
		this.size = size;
		RandomSource random = RandomSource.create(seed);
		float rolled = random.nextFloat() * Mth.TWO_PI;
		// the main street runs straight out to the gapura, so the gate snaps to the nearest of the four directions
		int quarter = Math.floorMod(Math.round((Float.isNaN(gate) ? rolled : gate) / Mth.HALF_PI), 4);
		gateAngle = quarter * Mth.HALF_PI;
		outX = Math.round(Mth.cos(gateAngle));
		outZ = Math.round(Mth.sin(gateAngle));
		region = Region.values()[random.nextInt(Region.values().length)];
		bambooFence = random.nextInt(4) != 0;

		// lay the streets out a little longer each time until every building has its spot
		long layoutSeed = random.nextLong();
		// a village of n houses needs streets about n / 8 steps long, so start there
		int grow = size / 8;
		while (!layout(RandomSource.create(layoutSeed), grow)) {
			grow++;
			if (grow > size / 8 + 12) {
				throw new IllegalStateException("kampung " + seed + " has no room for " + size + " houses");
			}
		}
		taken = null;

		// the fence: every direction reaches just past the furthest thing built that way, with a little wobble
		float step = Mth.TWO_PI / radii.length;
		for (Building b : buildings) {
			int pad = pad(b.style()) + 2;
			cover(b.x0 - pad, b.z0 - pad, b.x1 + pad, b.z1 + pad, step);
		}
		cover(-PLAZA - 2, -PLAZA - 2, PLAZA + 2, PLAZA + 2, step);
		for (int pass = 0; pass < 3; pass++) {
			for (int i = 0; i < radii.length; i++) {
				float neighbours = Math.max(radii[(i + radii.length - 1) % radii.length], radii[(i + 1) % radii.length]);
				radii[i] = Math.max(radii[i], neighbours - 4);
			}
		}
		float widest = 0;
		for (int i = 0; i < radii.length; i++) {
			radii[i] = Math.max(10, radii[i]) + random.nextFloat() * 1.5f;
			widest = Math.max(widest, radii[i]);
		}
		extent = Mth.ceil(widest) + 2;
		float gateRadius = radiusAt(gateAngle) - 0.5f;
		gateX = Math.round(outX * gateRadius);
		gateZ = Math.round(outZ * gateRadius);

		// streets only run as far as the houses along them, the main street goes on out through the gate
		List<Lane> all = new ArrayList<>(lanes);
		lanes.clear();
		for (int i = 0; i < all.size(); i++) {
			Lane lane = all.get(i);
			int from = Integer.MAX_VALUE;
			int to = Integer.MIN_VALUE;
			for (int k = 0; k < buildings.size(); k++) {
				if (laneOf.get(k) == i) {
					Building b = buildings.get(k);
					from = Math.min(from, lane.alongX ? b.x0 : b.z0);
					to = Math.max(to, lane.alongX ? b.x1 : b.z1);
				}
			}
			if (lane.main) {
				// it reaches every gang that has houses, and the gate
				for (Lane other : all) {
					if (!other.main && other != lane && built(all.indexOf(other))) {
						from = Math.min(from, other.across - other.half);
						to = Math.max(to, other.across + other.half);
					}
				}
				int gateAt = lane.alongX ? gateX : gateZ;
				from = Math.min(Math.min(from, -PLAZA), gateAt);
				to = Math.max(Math.max(to, PLAZA), gateAt);
			} else if (from > to) {
				continue;
			} else {
				from = Math.min(from - 1, -2);
				to = Math.max(to + 1, 2);
			}
			lanes.add(new Lane(lane.alongX, lane.across, from, to, lane.half, lane.main));
		}
	}

	private boolean built(int lane) {
		return laneOf.contains(lane);
	}

	// one try at the streets, `grow` makes them longer. false if something didn't fit
	private boolean layout(RandomSource random, int grow) {
		buildings.clear();
		lanes.clear();
		laneOf.clear();
		taken = new boolean[GRID * 2 + 1][GRID * 2 + 1];
		int front = 16 + grow * 7;
		int back = 12 + grow * 7;
		int wide = 14 + grow * 7;
		int sign = outX + outZ;
		boolean mainAlongX = outX != 0;
		lanes.add(new Lane(mainAlongX, 0, Math.min(-back * sign, front * sign), Math.max(-back * sign, front * sign), 1, true));
		for (int u = 0; u <= front - 10; u += SPACING) {
			lanes.add(new Lane(!mainAlongX, u * sign, -wide, wide, 1, false));
		}
		for (int u = -SPACING; u >= -back + 6; u -= SPACING) {
			lanes.add(new Lane(!mainAlongX, u * sign, -wide, wide, 1, false));
		}
		for (Lane lane : lanes) {
			mark(lane.alongX ? lane.from : lane.across - lane.half, lane.alongX ? lane.across - lane.half : lane.from,
					lane.alongX ? lane.to : lane.across + lane.half, lane.alongX ? lane.across + lane.half : lane.to);
		}
		mark(-PLAZA, -PLAZA, PLAZA, PLAZA);

		List<Kind> shared = List.of(Kind.KITCHEN, Kind.FIELD, Kind.WARUNG, Kind.SUMUR, Kind.MUSHOLLA, Kind.RONDA, Kind.KANDANG, Kind.JEMURAN);
		List<Kind> has = shared.subList(0, Mth.clamp(2 + size / 4, 2, shared.size()));
		// the musholla and the dapur by the beringin in the middle
		if (has.contains(Kind.MUSHOLLA) && !place(random, Kind.MUSHOLLA, 7, 9, 0, Style.JOGLO, 0)
				|| !place(random, Kind.KITCHEN, 5 + random.nextInt(2), 5, 0, Style.LIMASAN, 0)
				|| has.contains(Kind.SUMUR) && !place(random, Kind.SUMUR, 3, 3, 0, Style.KAMPUNG, 0)) {
			return false;
		}
		// the homes, filling in from the middle outwards
		for (int i = 0; i < size; i++) {
			Style style = style(random, i);
			int[] shape = shape(random, style);
			// walls plus a two block porch in front, raised houses also need room for the stairs down
			if (!place(random, Kind.HOUSE, shape[0], shape[1] + 2 + Style.raise(style), 1 + random.nextInt(2), style, 0)) {
				return false;
			}
		}
		// the satpam's post, the warung and the pos ronda just past the last houses towards the gate,
		// the sawah, the chickens and the rice drying out just past them at the back
		int gateU = 0;
		int backU = 0;
		for (Building b : buildings) {
			int u = b.centerX() * outX + b.centerZ() * outZ;
			gateU = Math.max(gateU, u + 8);
			backU = Math.min(backU, u - 8);
		}
		return place(random, Kind.POST, 3, 3, 0, Style.KAMPUNG, gateU)
				&& (!has.contains(Kind.WARUNG) || place(random, Kind.WARUNG, 5, 5, 1, Style.KAMPUNG, gateU))
				&& (!has.contains(Kind.RONDA) || place(random, Kind.RONDA, 3, 3, 0, Style.KAMPUNG, gateU))
				&& place(random, Kind.FIELD, 9 + random.nextInt(3), 7 + random.nextInt(2), 0, Style.KAMPUNG, backU)
				&& (!has.contains(Kind.KANDANG) || place(random, Kind.KANDANG, 5, 4, 0, Style.KAMPUNG, backU))
				&& (!has.contains(Kind.JEMURAN) || place(random, Kind.JEMURAN, 5, 4, 0, Style.KAMPUNG, backU));
	}

	private Style style(RandomSource random, int index) {
		return switch (region) {
			// the biggest house in a javanese village is pak lurah's joglo
			case JAWA -> index == 0 && size >= 6 ? Style.JOGLO : random.nextBoolean() ? Style.LIMASAN : Style.KAMPUNG;
			case SUNDA -> Style.SUNDA;
			case BUGIS -> Style.PANGGUNG;
			case TORAJA -> Style.TONGKONAN;
			case MINANG -> Style.GADANG;
			case BALI -> Style.BALI;
		};
	}

	// width along the front and depth of the walls
	private static int[] shape(RandomSource random, Style style) {
		return switch (style) {
			case LIMASAN -> new int[] {6 + random.nextInt(3), 5 + random.nextInt(2)};
			case KAMPUNG -> new int[] {5 + random.nextInt(3), 5 + random.nextInt(2)};
			case JOGLO -> new int[] {9, 7};
			case SUNDA, BALI -> new int[] {6 + random.nextInt(2), 5 + random.nextInt(2)};
			case PANGGUNG -> new int[] {6 + random.nextInt(2), 6 + random.nextInt(2)};
			case TONGKONAN -> new int[] {5, 7};
			case GADANG -> new int[] {9 + random.nextInt(2) * 2, 6};
		};
	}

	// roofs that reach past the walls need more room round the house
	private static int pad(Style style) {
		return style == Style.TONGKONAN || style == Style.GADANG ? 2 : 0;
	}

	// tries every spot along every street, both sides, closest to `towardsU` on the main street first (with a bit
	// of chance so rows don't come out too even), and takes the first one where the house, its front yard and a
	// walkway round it are all free. the front faces the street
	private boolean place(RandomSource random, Kind kind, int width, int depth, int residents, Style style, int towardsU) {
		int px = towardsU * outX;
		int pz = towardsU * outZ;
		int margin = 1 + pad(style);
		int setback = SETBACK + (kind == Kind.HOUSE ? random.nextInt(2) : 0);
		List<int[]> spots = new ArrayList<>();
		List<Float> scores = new ArrayList<>();
		for (int index = 0; index < lanes.size(); index++) {
			Lane lane = lanes.get(index);
			for (int side : new int[] {1, -1}) {
				for (int t = lane.from; t + width - 1 <= lane.to; t++) {
					int near = lane.across + side * (lane.half + 1 + setback);
					int far = near + side * (depth - 1);
					int a0 = Math.min(near, far);
					int a1 = Math.max(near, far);
					int x0 = lane.alongX ? t : a0;
					int x1 = lane.alongX ? t + width - 1 : a1;
					int z0 = lane.alongX ? a0 : t;
					int z1 = lane.alongX ? a1 : t + width - 1;
					float dx = (x0 + x1) / 2f - px;
					float dz = (z0 + z1) / 2f - pz;
					spots.add(new int[] {x0, z0, x1, z1, index, side});
					scores.add(Mth.sqrt(dx * dx + dz * dz) + random.nextFloat() * 5);
				}
			}
		}
		Integer[] order = new Integer[spots.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Float.compare(scores.get(a), scores.get(b)));
		for (int i : order) {
			int[] s = spots.get(i);
			Lane lane = lanes.get(s[4]);
			int side = s[5];
			// the front yard runs from the house to the edge of the street, the walkway (and the overhang of
			// a big roof) goes round the other three sides
			int edge = lane.across + side * (lane.half + 1);
			int x0 = s[0] - margin;
			int z0 = s[1] - margin;
			int x1 = s[2] + margin;
			int z1 = s[3] + margin;
			if (lane.alongX && side > 0) {
				z0 = edge;
			} else if (lane.alongX) {
				z1 = edge;
			} else if (side > 0) {
				x0 = edge;
			} else {
				x1 = edge;
			}
			if (!free(x0, z0, x1, z1)) {
				continue;
			}
			mark(x0, z0, x1, z1);
			Direction front = lane.alongX ? (side > 0 ? Direction.NORTH : Direction.SOUTH) : (side > 0 ? Direction.WEST : Direction.EAST);
			buildings.add(new Building(kind, s[0], s[1], s[2], s[3], front, style, random.nextInt(1000), residents));
			laneOf.add(s[4]);
			return true;
		}
		return false;
	}

	// the two fence radii either side of every covered cell reach at least as far as it
	private void cover(int x0, int z0, int x1, int z1, float step) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				float angle = ((float) Mth.atan2(z, x) + Mth.TWO_PI) % Mth.TWO_PI;
				int i = (int) (angle / step) % radii.length;
				float distance = Mth.sqrt(x * x + z * z);
				radii[i] = Math.max(radii[i], distance);
				radii[(i + 1) % radii.length] = Math.max(radii[(i + 1) % radii.length], distance);
			}
		}
	}

	private boolean free(int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (Math.abs(x) > GRID || Math.abs(z) > GRID || taken[x + GRID][z + GRID]) {
					return false;
				}
			}
		}
		return true;
	}

	private void mark(int x0, int z0, int x1, int z1) {
		for (int x = Math.max(x0, -GRID); x <= Math.min(x1, GRID); x++) {
			for (int z = Math.max(z0, -GRID); z <= Math.min(z1, GRID); z++) {
				taken[x + GRID][z + GRID] = true;
			}
		}
	}

	// the fence: twenty four radii, smoothly blended between
	public float radiusAt(float angle) {
		float position = (angle / Mth.TWO_PI + 1) % 1 * radii.length;
		int i = (int) position;
		float t = position - i;
		float smooth = (1 - Mth.cos(t * Mth.PI)) / 2;
		return Mth.lerp(smooth, radii[i % radii.length], radii[(i + 1) % radii.length]);
	}

	// the furthest the fence gets from the middle
	public float reach() {
		float widest = 0;
		for (float radius : radii) {
			widest = Math.max(widest, radius);
		}
		return widest;
	}

	public float distanceOutside(int x, int z) {
		return Mth.sqrt(x * x + z * z) - radiusAt((float) Mth.atan2(z, x));
	}

	public boolean inside(int x, int z) {
		return distanceOutside(x, z) <= 0;
	}

	// any inside cell touching the outside, also diagonally, so the ring has no diagonal-only steps and every rail connects
	public boolean isFence(int x, int z) {
		if (!inside(x, z)) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!inside(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	// the gap in the fence where the main street goes through the gapura
	public boolean isGateOpening(int x, int z) {
		return (x - gateX) * (x - gateX) + (z - gateZ) * (z - gateZ) <= 4.9f;
	}

	// the gapura's two pillars either side of the street, just inside the fence line
	public boolean isGatePillar(int x, int z) {
		for (int side : new int[] {1, -1}) {
			if (x == gatePillarX(side) && z == gatePillarZ(side)) {
				return true;
			}
		}
		return false;
	}

	public int gatePillarX(int side) {
		return gateX - outZ * side * 3;
	}

	public int gatePillarZ(int side) {
		return gateZ + outX * side * 3;
	}

	// which way the gate faces out of the village
	public Direction gateFacing() {
		return outX > 0 ? Direction.EAST : outX < 0 ? Direction.WEST : outZ > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	public Building building(int x, int z) {
		for (Building building : buildings) {
			if (x >= building.x0 && x <= building.x1 && z >= building.z0 && z <= building.z1) {
				return building;
			}
		}
		return null;
	}

	public Lane lane(int x, int z) {
		for (Lane lane : lanes) {
			if (lane.contains(x, z)) {
				return lane;
			}
		}
		return null;
	}
}
