package com.raihanadf.blossom.kampung;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

// the layout of one kampung, worked out from its seed alone so every chunk of it agrees.
// coordinates are relative to the village centre, the ground level is shared by everything
public class KampungPlan {
	public static final int EXTENT = 32;

	public enum Kind { HOUSE, KITCHEN, FIELD, POST }

	// javanese limasan and joglo, a kampung gable house, toraja tongkonan and bugis rumah panggung
	public enum Style { LIMASAN, JOGLO, KAMPUNG, TONGKONAN, PANGGUNG }

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

	public final long seed;
	private final float[] radii = new float[12];
	public final float gateAngle;
	public final int gateX;
	public final int gateZ;
	public final boolean bambooFence;
	public final List<Building> buildings = new ArrayList<>();
	private final boolean[][] taken = new boolean[EXTENT * 2 + 1][EXTENT * 2 + 1];

	public KampungPlan(long seed) {
		this.seed = seed;
		RandomSource random = RandomSource.create(seed);
		float base = 20 + random.nextInt(5);
		for (int i = 0; i < radii.length; i++) {
			radii[i] = base * (0.85f + random.nextFloat() * 0.25f);
		}
		gateAngle = random.nextFloat() * Mth.TWO_PI;
		float gateRadius = radiusAt(gateAngle) - 0.5f;
		gateX = Math.round(Mth.cos(gateAngle) * gateRadius);
		gateZ = Math.round(Mth.sin(gateAngle) * gateRadius);
		bambooFence = random.nextInt(4) != 0;

		// keep the edges, the gate corridor and the middle plaza free
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				boolean nearFence = Mth.sqrt(x * x + z * z) > radiusAt((float) Mth.atan2(z, x)) - 2.5f;
				float along = x * Mth.cos(gateAngle) + z * Mth.sin(gateAngle);
				float across = Math.abs(-x * Mth.sin(gateAngle) + z * Mth.cos(gateAngle));
				boolean corridor = across < 2 && along > 0;
				taken[x + EXTENT][z + EXTENT] = nearFence || corridor || (Math.abs(x) <= 1 && Math.abs(z) <= 1);
			}
		}

		// the satpam's post sits just inside the gate, on whichever side has room
		float sideX = -Mth.sin(gateAngle);
		float sideZ = Mth.cos(gateAngle);
		for (int side : new int[] {1, -1}) {
			int px = Math.round(gateX - Mth.cos(gateAngle) * 3 + sideX * side * 3.5f);
			int pz = Math.round(gateZ - Mth.sin(gateAngle) * 3 + sideZ * side * 3.5f);
			if (inside(px - 1, pz - 1) && inside(px + 1, pz + 1) && inside(px - 1, pz + 1) && inside(px + 1, pz - 1)) {
				buildings.add(new Building(Kind.POST, px - 1, pz - 1, px + 1, pz + 1, facing(Mth.cos(gateAngle), Mth.sin(gateAngle)), Style.KAMPUNG, 0, 0));
				mark(px - 1, pz - 1, px + 1, pz + 1);
				break;
			}
		}

		place(random, Kind.FIELD, 8 + random.nextInt(4), 6 + random.nextInt(2), 0, Style.KAMPUNG);
		place(random, Kind.KITCHEN, 5 + random.nextInt(2), 5, 0, Style.LIMASAN);
		int houses = 3 + random.nextInt(3);
		for (int i = 0; i < houses; i++) {
			Style style = Style.values()[random.nextInt(Style.values().length)];
			// walls plus a two block porch in front
			place(random, Kind.HOUSE, 5 + random.nextInt(3), 5 + random.nextInt(3) + 2, 1 + random.nextInt(2), style);
		}
	}

	// goes through every spot in a shuffled order and takes the first one where the footprint
	// (plus a one block walkway around it) is free. the front is always the `width` side,
	// facing whichever way leads back to the middle of the village
	private void place(RandomSource random, Kind kind, int width, int depth, int residents, Style style) {
		int span = EXTENT * 2;
		int[] spots = new int[span * span * 2];
		for (int i = 0; i < spots.length; i++) {
			spots[i] = i;
		}
		for (int i = spots.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int swap = spots[i];
			spots[i] = spots[j];
			spots[j] = swap;
		}
		for (int spot : spots) {
			boolean turned = spot % 2 == 1;
			int sizeX = turned ? depth : width;
			int sizeZ = turned ? width : depth;
			int x0 = (spot / 2) % span - EXTENT;
			int z0 = (spot / 2) / span - EXTENT;
			int x1 = x0 + sizeX - 1;
			int z1 = z0 + sizeZ - 1;
			if (!free(x0 - 1, z0 - 1, x1 + 1, z1 + 1)) {
				continue;
			}
			Direction front = turned ? (x0 + x1 > 0 ? Direction.WEST : Direction.EAST) : (z0 + z1 > 0 ? Direction.NORTH : Direction.SOUTH);
			mark(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
			buildings.add(new Building(kind, x0, z0, x1, z1, front, style, random.nextInt(1000), residents));
			return;
		}
	}

	private static Direction facing(float dx, float dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private boolean free(int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (Math.abs(x) > EXTENT || Math.abs(z) > EXTENT || taken[x + EXTENT][z + EXTENT]) {
					return false;
				}
			}
		}
		return true;
	}

	private void mark(int x0, int z0, int x1, int z1) {
		for (int x = Math.max(x0, -EXTENT); x <= Math.min(x1, EXTENT); x++) {
			for (int z = Math.max(z0, -EXTENT); z <= Math.min(z1, EXTENT); z++) {
				taken[x + EXTENT][z + EXTENT] = true;
			}
		}
	}

	// the fence wobbles around a rough circle: twelve radii, smoothly blended between
	public float radiusAt(float angle) {
		float position = (angle / Mth.TWO_PI + 1) % 1 * radii.length;
		int i = (int) position;
		float t = position - i;
		float smooth = (1 - Mth.cos(t * Mth.PI)) / 2;
		return Mth.lerp(smooth, radii[i % radii.length], radii[(i + 1) % radii.length]);
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

	// the gap in the fence and the two gate pillars either side of it
	public boolean isGateOpening(int x, int z) {
		return (x - gateX) * (x - gateX) + (z - gateZ) * (z - gateZ) <= 4.9f;
	}

	public boolean isGatePillar(int x, int z) {
		for (int side : new int[] {1, -1}) {
			int px = Math.round(gateX - Mth.sin(gateAngle) * side * 2.8f);
			int pz = Math.round(gateZ + Mth.cos(gateAngle) * side * 2.8f);
			if (x == px && z == pz) {
				return true;
			}
		}
		return false;
	}

	public Building building(int x, int z) {
		for (Building building : buildings) {
			if (x >= building.x0 && x <= building.x1 && z >= building.z0 && z <= building.z1) {
				return building;
			}
		}
		return null;
	}
}
