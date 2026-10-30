package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import com.raihanadf.blossom.nusantara.Settlement.Plot;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

// the station of a settlement and the country around it: a white station hall in the middle of a ring road, a
// platform beside the line out of each port, a little plaza on the side facing the settlement, the roads to the
// kampungs and the town square, and the sawah and pastures outside
public final class StationBuilder {
	// how far out from the yard its slopes back to the land reach
	private static final int SLOPES = 40;

	private StationBuilder() {
	}

	// how far a column lies outside the station yard: the hub, both arms of every line through it (platforms, loops,
	// headshunt) and the hall and plaza corner on the settlement's side. 0 inside
	public static double yard(Settlement s, int x, int z) {
		int x0 = s.node.x();
		int z0 = s.node.z();
		int arm = Math.max(NusantaraNetwork.PLATFORM_END, TrainSchedule.HEADSHUNT) + 6;
		int bx = x0 + s.qx * 46;
		int bz = z0 + s.qz * 46;
		double out = outside(x, z, x0 - 14, z0 - 14, x0 + 14, z0 + 14);
		out = Math.min(out, outside(x, z, Math.min(x0, bx), Math.min(z0, bz), Math.max(x0, bx), Math.max(z0, bz)));
		if (s.ports[0] || s.ports[2]) {
			out = Math.min(out, outside(x, z, x0 - arm, z0 - 12, x0 + arm, z0 + 12));
		}
		if (s.ports[1] || s.ports[3]) {
			out = Math.min(out, outside(x, z, x0 - 12, z0 - arm, x0 + 12, z0 + arm));
		}
		return out;
	}

	private static double outside(int x, int z, int x0, int z0, int x1, int z1) {
		return Math.hypot(Math.max(0, Math.max(x0 - x, x - x1)), Math.max(0, Math.max(z0 - z, z - z1)));
	}

	// flat ground over the whole yard, sloping back into the land over a width that grows with the height to make up,
	// with a wavy edge so it looks grown. trees on the yard and its slopes go whole, not just the part in the way
	public static void level(Site site, Settlement s) {
		int g = s.node.ground();
		for (int x = site.box.minX(); x <= site.box.maxX(); x++) {
			for (int z = site.box.minZ(); z <= site.box.maxZ(); z++) {
				if (!site.has(x, z)) {
					continue;
				}
				double out = yard(s, x, z);
				if (out > SLOPES) {
					continue;
				}
				clearTrees(site, x, z);
				if (out <= 0) {
					site.level(x, g, z, "grass_block");
					continue;
				}
				int land = site.ground(x, z, true);
				double width = Mth.clamp(Math.abs(land - g) * 2, 8, SLOPES) * (1 + 0.25 * Math.sin(x * 0.21 + z * 0.13) * Math.cos(z * 0.17 - x * 0.07));
				site.blend(x, z, g, out, (int) Math.round(width));
			}
		}
	}

	// whole trees in a column: trunks, leaves, vines, bamboo and big mushrooms, from the ground up
	private static void clearTrees(Site site, int x, int z) {
		int ground = site.ground(x, z, false);
		for (int y = ground + 1; y <= ground + 36; y++) {
			BlockState state = site.get(x, y, z);
			if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.VINE) || state.is(Blocks.BAMBOO) || state.is(Blocks.MUSHROOM_STEM)
					|| state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.COCOA)) {
				site.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	public static void build(Site site, NusantaraNetwork network, Settlement s, RandomSource random) {
		int x0 = s.node.x();
		int z0 = s.node.z();
		int g = s.node.ground();
		if (site.overlaps(x0 - 90, z0 - 90, x0 + 90, z0 + 90)) {
			hub(site, s, x0, z0, g);
			for (boolean row : new boolean[] {true, false}) {
				// a terminus: a line comes in from one side only, the loco runs round its train through a headshunt on the other
				if (s.ports[row ? 0 : 1] != s.ports[row ? 2 : 3]) {
					int port = s.ports[row ? 0 : 1] ? (row ? 0 : 1) : (row ? 2 : 3);
					Edge edge = port < 2 ? network.edge(s.node.cx(), s.node.cz(), port == 0)
							: network.edge(s.node.cx() + NusantaraNetwork.DX[port], s.node.cz() + NusantaraNetwork.DZ[port], port == 2);
					if (edge != null) {
						Chain chain = network.chainOf(edge);
						headshunt(site, row, s.ports[row ? 0 : 1] ? -1 : 1, x0, z0, g, TrainSchedule.turntable(chain, chain.station(chain.start).node.equals(s.node)));
					}
				}
			}
			hall(site, s, s.hallX(), s.hallZ(), g);
			plaza(site, s, g);
			// trains run straight on through the station: one set of platforms per line, on the settlement's side
			for (boolean row : new boolean[] {true, false}) {
				if (!s.ports[row ? 0 : 1] && !s.ports[row ? 2 : 3]) {
					continue;
				}
				int port = s.platformArm(row);
				Edge edge = port < 2 ? network.edge(s.node.cx(), s.node.cz(), port == 0)
						: network.edge(s.node.cx() + NusantaraNetwork.DX[port], s.node.cz() + NusantaraNetwork.DZ[port], port == 2);
				if (edge != null) {
					platforms(site, s, edge, port, x0, z0, g, random);
				}
			}
			staff(site, s, s.hallX(), s.hallZ(), g, random);
		}
		roads(site, s);
		fields(site, network, s);
	}

	// the middle of the station, where the lines meet: the two station tracks of each line run straight across it,
	// with planks where a row line and a column line cross
	private static void hub(Site site, Settlement s, int x0, int z0, int g) {
		int reach = NusantaraNetwork.TRACK_START;
		boolean row = s.ports[0] || s.ports[2];
		boolean column = s.ports[1] || s.ports[3];
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (!site.has(x, z)) {
					continue;
				}
				boolean onRow = row && Math.abs(dz) <= 3;
				boolean onColumn = column && Math.abs(dx) <= 3;
				if (!onRow && !onColumn) {
					continue;
				}
				site.set(x, g, z, onRow && onColumn ? "spruce_planks" : "gravel");
				site.clearAbove(x, g, z);
			}
		}
		// the station tracks straight across (where a row and a column line cross, both run over the planks)
		for (int lane : new int[] {-2, 2}) {
			if (row) {
				TrackBuilder.lay(site, straight(x0 - reach, z0 + lane, x0 + reach + 1, z0 + lane, g + 1));
			}
			if (column) {
				TrackBuilder.lay(site, straight(x0 - lane, z0 - reach, x0 - lane, z0 + reach + 1, g + 1));
			}
		}
	}

	// points a quarter block apart on a straight line at the height of the ballast top
	private static List<double[]> straight(double x0, double z0, double x1, double z1, double y) {
		List<double[]> points = new ArrayList<>();
		int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) * 4);
		for (int k = 0; k <= steps; k++) {
			double t = k / (double) steps;
			points.add(new double[] {Mth.lerp(t, x0, x1), y, Mth.lerp(t, z0, z1)});
		}
		return points;
	}

	// beyond the hub at a terminus: both station tracks run on to buffer stops, with a crossover between them, so the
	// loco can pull forward, cross to the other track and run back past its coaches
	// the headshunt of a terminus: the track trains come in on runs out to a turntable (meja putar), the other one ends
	// at buffers just past the crossover between the two. `table` is the turntable's middle and the line's direction
	private static void headshunt(Site site, boolean row, int dead, int x0, int z0, int g, double[] table) {
		int far = TrainSchedule.HEADSHUNT;
		double radius = TrainSchedule.TABLE_RADIUS;
		// which of the two headshunt tracks leads onto the turntable
		double[] left = shuntPoint(row, dead, x0, z0, TrainSchedule.SHUNT_AT, -2);
		int in = Math.hypot(left[0] - table[0], left[1] - table[1]) < 1 ? -2 : 2;
		int out = -in;
		int stop = TrainSchedule.CROSS_FAR + 3;
		for (int w = NusantaraNetwork.TRACK_START + 1; w <= far + 1; w++) {
			for (int l = -3; l <= 3; l++) {
				int[] at = shuntCell(row, dead, x0, z0, w, l);
				if (!site.has(at[0], at[1]) || Math.hypot(at[0] + 0.5 - table[0], at[1] + 0.5 - table[1]) <= radius + 1) {
					continue;
				}
				// past the buffers only the ballast of the track to the turntable
				if (w > stop + 1 && l * in <= 0) {
					continue;
				}
				site.level(at[0], g, at[1], "gravel");
				if (l == out && w == stop) {
					site.set(at[0], g + 1, at[1], "red_nether_brick_wall");
				}
			}
		}
		int near = NusantaraNetwork.TRACK_START + 1;
		double[] from = shuntPoint(row, dead, x0, z0, near, in);
		double[] to = shuntPoint(row, dead, x0, z0, TrainSchedule.SHUNT_AT - radius, in);
		TrackBuilder.lay(site, straight(from[0], from[1], to[0], to[1], g + 1));
		from = shuntPoint(row, dead, x0, z0, near, out);
		to = shuntPoint(row, dead, x0, z0, stop - 0.5, out);
		TrackBuilder.lay(site, straight(from[0], from[1], to[0], to[1], g + 1));
		// a smooth crossover from one to the other between 10 and 18 out
		List<double[]> crossover = new ArrayList<>();
		for (double w = 10; w <= 18; w += 0.25) {
			double t = (w - 10) / 8;
			double[] at = shuntPoint(row, dead, x0, z0, w, Mth.lerp(t * t * (3 - 2 * t), -2, 2));
			crossover.add(new double[] {at[0], g + 1, at[1]});
		}
		TrackBuilder.lay(site, crossover);
		turntable(site, table, g);
	}

	// a round pit lined with stone, a stone curb round its rim, a pivot in the middle and on it the deck: one long
	// track piece the loco drives onto, which the renderer swings round with it
	private static void turntable(Site site, double[] table, int g) {
		double radius = TrainSchedule.TABLE_RADIUS;
		int cx = Mth.floor(table[0]);
		int cz = Mth.floor(table[1]);
		int reach = (int) Math.ceil(radius) + 1;
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (!site.has(x, z)) {
					continue;
				}
				double r = Math.hypot(x + 0.5 - table[0], z + 0.5 - table[1]);
				if (r <= radius) {
					site.set(x, g - 1, z, "stone_bricks");
					site.set(x, g, z, x == cx && z == cz ? "polished_andesite" : "air");
					site.clearAbove(x, g, z);
				} else if (r <= radius + 1) {
					site.level(x, g, z, "polished_andesite");
				}
			}
		}
		if (!site.has(cx, cz)) {
			return;
		}
		BlockPos pos = new BlockPos(cx, g + 1, cz);
		site.set(cx, g + 1, cz, BlossomBlocks.TRACK.defaultBlockState());
		if (site.level.getBlockEntity(pos) instanceof TrackBlockEntity deck) {
			float yaw = (float) Math.toDegrees(Math.atan2(table[3], table[4]));
			deck.makeTurntable();
			deck.addPiece((float) (table[0] - cx), 0.5f, (float) (table[1] - cz), yaw, 0, (float) (radius * 2));
		}
	}

	// a point of the headshunt, `w` out from the hub on the dead side and `l` across (like the lines' lane offsets)
	private static double[] shuntPoint(boolean row, int dead, int x0, int z0, double w, double l) {
		return row ? new double[] {x0 + dead * w, z0 + l} : new double[] {x0 - l, z0 + dead * w};
	}

	// trees grown next door after the station was built: over the yard and its slopes they go whole
	public static void sweep(Site site, Site around, Settlement s) {
		int reach = Math.max(NusantaraNetwork.PLATFORM_END, TrainSchedule.HEADSHUNT) + 6 + SLOPES;
		int x0 = s.node.x();
		int z0 = s.node.z();
		if (!around.overlaps(x0 - reach, z0 - reach, x0 + reach, z0 + reach)) {
			return;
		}
		for (int x = Math.max(x0 - reach, around.box.minX()); x <= Math.min(x0 + reach, around.box.maxX()); x++) {
			for (int z = Math.max(z0 - reach, around.box.minZ()); z <= Math.min(z0 + reach, around.box.maxZ()); z++) {
				if (!site.has(x, z) && around.has(x, z) && yard(s, x, z) <= SLOPES) {
					clearTrees(around, x, z);
				}
			}
		}
	}


	// a block of the headshunt, `w` out from the hub on the dead side and `l` across (like the lines' lane offsets)
	private static int[] shuntCell(boolean row, int dead, int x0, int z0, int w, int l) {
		return row ? new int[] {x0 + dead * w, z0 + l} : new int[] {x0 - l, z0 + dead * w};
	}

	// the station hall: white walls with green trim, arcades on every side, a red tiled hip roof with a clock tower
	private static void hall(Site site, Settlement s, int x0, int z0, int g) {
		int h = NusantaraNetwork.HALL;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				site.set(x, g, z, (dx + dz) % 2 == 0 ? "polished_diorite" : "polished_andesite");
				boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
				boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
				for (int y = g + 1; y <= g + 5; y++) {
					if (!edge) {
						site.set(x, y, z, "air");
						continue;
					}
					int along = Math.abs(dx) == h ? dz : dx;
					boolean arch = !corner && y <= g + 3 && Math.abs(along) <= h - 2 && Math.floorMod(along, 3) != 0;
					site.set(x, y, z, arch ? "air" : y == g + 1 || corner ? "green_terracotta" : "white_concrete");
				}
				site.set(x, g + 6, z, edge ? "green_terracotta" : "white_concrete");
			}
		}
		for (int ring = 0; ring <= h + 1; ring++) {
			int r = h + 1 - ring;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Direction up = Math.abs(dz) == r ? (dz < 0 ? Direction.SOUTH : Direction.NORTH) : (dx < 0 ? Direction.EAST : Direction.WEST);
					boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
					site.set(x0 + dx, g + 7 + ring, z0 + dz, corner || r == 0 ? Site.block("bricks") : Site.facing("brick_stairs", up));
				}
			}
		}
		// loket, benches, lamps inside
		for (int dx = -3; dx <= -1; dx++) {
			site.set(x0 + dx, g + 1, z0 - 3, "dark_oak_planks");
			site.set(x0 + dx, g + 2, z0 - 3, "iron_bars");
		}
		site.set(x0 - 2, g + 1, z0 - 4, Site.facing("lectern", Direction.SOUTH));
		for (int dx = 1; dx <= 3; dx++) {
			site.set(x0 + dx, g + 1, z0 + 3, BlossomBlocks.CHAIRS.get("dark_oak").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}
		site.set(x0, g + 5, z0, Site.block("lantern").setValue(BlockStateProperties.HANGING, true));
		// the name on the side facing the settlement
		Direction toTown = s.qz > 0 ? Direction.SOUTH : Direction.NORTH;
		site.sign(x0, g + 5, z0 + toTown.getStepZ() * (h + 1), toTown, true, "STASIUN", s.name.toUpperCase(), "+" + (g + 1) + " m", "");
	}

	private static void plaza(Site site, Settlement s, int g) {
		int px = s.plazaX() - s.qx * 5;
		int pz = s.plazaZ() - s.qz * 5;
		for (int i = 0; i <= 10; i++) {
			for (int j = 0; j <= 10; j++) {
				int x = px + s.qx * i;
				int z = pz + s.qz * j;
				boolean bed = (i == 10 || j == 10) && (i + j) % 3 != 0 && Math.abs(i - j) > 2;
				site.set(x, g, z, bed ? "grass_block" : (i * j) % 7 == 0 ? "cobblestone" : "smooth_stone");
				if (bed) {
					site.set(x, g + 1, z, (i + j) % 2 == 0 ? "red_tulip" : "oxeye_daisy");
				}
			}
		}
		int lx = s.plazaX();
		int lz = s.plazaZ() + s.qz * 3;
		for (int y = g + 1; y <= g + 3; y++) {
			site.set(lx, y, lz, "dark_oak_fence");
		}
		site.set(lx, g + 4, lz, BlossomBlocks.LAMP.defaultBlockState().setValue(LampBlock.LIT, true).setValue(LampBlock.GLOW, 4)
				.setValue(LampBlock.COLOR, DyeColor.WHITE));
		BlockPos motorAt = new BlockPos(s.plazaX() + s.qx * 3, g + 1, s.plazaZ() - s.qz * 2);
		if (site.has(motorAt.getX(), motorAt.getZ())) {
			var motor = BlossomEntities.MOTOR.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
			motor.snapTo(motorAt.getX() + 0.5, motorAt.getY(), motorAt.getZ() + 0.5, 0, 0);
			motor.setColor(DyeColor.values()[Math.floorMod(s.node.cx() * 7 + s.node.cz(), DyeColor.values().length)]);
			site.level.addFreshEntity(motor);
		}
	}

	// platforms beside the line out of one port: raised paving, canopies near the hall, benches, lamps and name boards
	private static void platforms(Site site, Settlement s, Edge edge, int port, int x0, int z0, int g, RandomSource random) {
		int dx = NusantaraNetwork.DX[port];
		int dz = NusantaraNetwork.DZ[port];
		// across: the direction to the left of the line as it leaves the station
		int ax = -dz;
		int az = dx;
		int inner = edge.stationBallast() + 1;
		for (int along = NusantaraNetwork.PLATFORM_START; along <= NusantaraNetwork.PLATFORM_END; along++) {
			for (int side : new int[] {-1, 1}) {
				for (int k = inner; k <= inner + 2; k++) {
					int x = x0 + dx * along + ax * side * k;
					int z = z0 + dz * along + az * side * k;
					site.set(x, g, z, "stone_bricks");
					site.set(x, g + 1, z, k == inner ? "smooth_sandstone" : "smooth_stone");
					for (int y = g + 2; y <= g + 5; y++) {
						site.set(x, y, z, "air");
					}
				}
				int outer = inner + 2;
				int bx = x0 + dx * along + ax * side * outer;
				int bz = z0 + dz * along + az * side * outer;
				int from = along - NusantaraNetwork.PLATFORM_START;
				if (from > 2 && from < 40) {
					// the canopy, a post every eight blocks with a lantern hanging from it
					for (int k = inner; k <= outer; k++) {
						site.set(x0 + dx * along + ax * side * k, g + 6, z0 + dz * along + az * side * k,
								Site.block("dark_oak_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
					}
					if (from % 8 == 4) {
						for (int y = g + 2; y <= g + 5; y++) {
							site.set(bx, y, bz, "green_terracotta");
						}
						site.set(x0 + dx * along + ax * side * inner, g + 5, z0 + dz * along + az * side * inner,
								Site.block("lantern").setValue(BlockStateProperties.HANGING, true));
					}
				}
				if (from % 8 == 0 && from > 0 && from < 60) {
					Direction toTrack = Direction.getNearest(-ax * side, 0, -az * side, null);
					site.set(bx, g + 2, bz, BlossomBlocks.CHAIRS.get("spruce").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toTrack));
				}
				if (from == 6 || from == 58) {
					Direction toTrack = Direction.getNearest(-ax * side, 0, -az * side, null);
					for (int y = g + 2; y <= g + 3; y++) {
						site.set(bx, y, bz, "dark_oak_fence");
					}
					site.set(bx, g + 4, bz, "white_concrete");
					site.sign(bx - ax * side, g + 4, bz - az * side, toTrack, true, "", s.name.toUpperCase(), "", "");
				}
			}
		}
		fenceAndGates(site, s, port, x0, z0, g, inner);
		// people waiting for the train
		for (int i = 0; i < 2 + random.nextInt(2); i++) {
			int along = NusantaraNetwork.PLATFORM_START + 8 + random.nextInt(48);
			int side = random.nextBoolean() ? 1 : -1;
			int x = x0 + dx * along + ax * side * (inner + 1);
			int z = z0 + dz * along + az * side * (inner + 1);
			site.person(x, g + 2, z, Warga.PASSENGER, Warga.randomSkin(random), new BlockPos(x, g + 2, z), new BlockPos(s.hallX(), g + 1, s.hallZ()), 96);
		}
	}

	// the platforms are closed off: a fence along the outside of both and across their ends (the tracks stay open), a
	// plank walkway over the tracks between the two, and on the settlement's side the ticket gate and the exit gate,
	// with a path out to the hall and signs over them
	private static void fenceAndGates(Site site, Settlement s, int port, int x0, int z0, int g, int inner) {
		int dx = NusantaraNetwork.DX[port];
		int dz = NusantaraNetwork.DZ[port];
		int ax = -dz;
		int az = dx;
		Settlement.Gates gates = s.gates(port % 2 == 0);
		int near = gates.side();
		int fence = Settlement.FENCE;
		for (int side : new int[] {-1, 1}) {
			for (int along = NusantaraNetwork.PLATFORM_START - 1; along <= NusantaraNetwork.PLATFORM_END + 1; along++) {
				int x = x0 + dx * along + ax * side * fence;
				int z = z0 + dz * along + az * side * fence;
				boolean gate = side == near && (along == Settlement.ENTRY_ALONG || along == Settlement.EXIT_ALONG);
				if (gate) {
					Direction in = Direction.getNearest(-ax * side, 0, -az * side, null);
					site.set(x, g + 1, z, (along == Settlement.ENTRY_ALONG ? BlossomBlocks.TICKET_GATE : BlossomBlocks.EXIT_GATE).defaultBlockState()
							.setValue(StationGate.FACING, in));
					site.set(x, g + 2, z, "air");
					// a path from the gate out towards the hall, and a sign over the way in and out
					for (int k = fence + 1; k <= fence + 7; k++) {
						site.set(x0 + dx * along + ax * side * k, g, z0 + dz * along + az * side * k, "dirt_path");
						site.clearAbove(x0 + dx * along + ax * side * k, g, z0 + dz * along + az * side * k);
					}
					int sx = x0 + dx * (along + 1) + ax * side * (fence + 1);
					int sz = z0 + dz * (along + 1) + az * side * (fence + 1);
					site.set(sx, g + 1, sz, "dark_oak_fence");
					site.sign(sx, g + 2, sz, in.getOpposite(), false, "", along == Settlement.ENTRY_ALONG ? "MASUK" : "KELUAR",
							along == Settlement.ENTRY_ALONG ? "karcis: 1 besi" : "", "");
					continue;
				}
				site.set(x, g + 1, z, "spruce_fence");
			}
			// across both ends of the platform
			for (int k = inner; k < fence; k++) {
				for (int along : new int[] {NusantaraNetwork.PLATFORM_START - 1, NusantaraNetwork.PLATFORM_END + 1}) {
					site.set(x0 + dx * along + ax * side * k, g + 1, z0 + dz * along + az * side * k, "spruce_fence");
				}
			}
		}
		// the walkway over the tracks: planks between the platforms, level with the rails
		int w = Settlement.WALKWAY_ALONG;
		for (int k = -inner + 1; k < inner; k++) {
			site.set(x0 + dx * w + ax * k, g, z0 + dz * w + az * k, "spruce_planks");
		}
	}

	private static void staff(Site site, Settlement s, int x0, int z0, int g, RandomSource random) {
		BlockPos center = new BlockPos(x0, g + 1, z0);
		site.person(x0 + 2, g + 1, z0 + 2, Warga.PPKA, Warga.SKIN_PPKA, new BlockPos(x0 + 2, g + 1, z0 + 2), center, 90);
		int vx = s.plazaX() - s.qx * 2;
		int vz = s.plazaZ() + s.qz * 2;
		site.person(vx, g + 1, vz, Warga.VENDOR, Warga.SKIN_WOMEN + random.nextInt(Warga.SKINS_PER_GROUP), new BlockPos(vx, g + 1, vz), center, 40);
	}

	// dirt roads following a graded land profile, with embankments and plank bridges over water
	private static void roads(Site site, Settlement s) {
		for (int[] road : s.roads()) {
			if (!site.overlaps(Math.min(road[0], road[2]) - 14, Math.min(road[1], road[3]) - 14, Math.max(road[0], road[2]) + 14, Math.max(road[1], road[3]) + 14)) {
				continue;
			}
			int[] profile = s.roadProfile(road);
			double dx = road[2] - road[0];
			double dz = road[3] - road[1];
			double length2 = Math.max(1, dx * dx + dz * dz);
			for (int x = site.box.minX(); x <= site.box.maxX(); x++) {
				for (int z = site.box.minZ(); z <= site.box.maxZ(); z++) {
					double distance = Settlement.distanceToSegment(x + 0.5, z + 0.5, road);
					if (distance > 14 || !site.has(x, z)) {
						continue;
					}
					// across the station yard the land is already flat and built on: the road is just a path over it
					// (grading it there dug the ground out from under the hall)
					if (yard(s, x, z) <= 0) {
						if (distance <= 1.8 && site.get(x, s.node.ground(), z).is(Blocks.GRASS_BLOCK)) {
							site.set(x, s.node.ground(), z, "dirt_path");
						}
						continue;
					}
					// the road's height at the nearest point along it
					double t = Mth.clamp(((x + 0.5 - road[0]) * dx + (z + 0.5 - road[1]) * dz) / length2, 0, 1);
					int target = profile[(int) Math.round(t * (profile.length - 1))];
					int natural = site.ground(x, z, false);
					boolean railAbove = railAt(site, x, target + 1, z);
					boolean railHere = railAt(site, x, target, z);
					if (railAt(site, x, target - 1, z) || railHere || railAbove) {
						// a level crossing: planks between the rails, the rails stay
						if (railAbove) {
							site.set(x, target, z, "spruce_planks");
						} else if (railHere) {
							site.set(x, target - 1, z, "spruce_planks");
						}
						continue;
					}
					if (distance <= 1.8) {
						roadColumn(site, x, target, z, true, natural);
					} else {
						// banks either side, sloping from the road back to the land
						double bankWidth = Mth.clamp(Math.abs(natural - target), 2, 12);
						double blend = Mth.clamp((distance - 1.8) / bankWidth, 0, 1);
						int bank = (int) Math.round(Mth.lerp(blend, target, natural));
						if (bank != natural) {
							roadColumn(site, x, bank, z, false, natural);
						}
					}
					if (distance <= 4.5) {
						clearCanopy(site, x, target, z);
					}
					// a lamp post every 24 blocks, so the way to the village shows from the station and at night
					double along = t * Math.sqrt(length2);
					boolean left = (x + 0.5 - road[0]) * dz - (z + 0.5 - road[1]) * dx > 0;
					if (left && distance > 2.3 && distance <= 3.3 && Math.floorMod((int) Math.round(along), 24) == 12 && site.get(x, target, z).isSolidRender()) {
						site.set(x, target + 1, z, "spruce_fence");
						site.set(x, target + 2, z, "spruce_fence");
						site.set(x, target + 3, z, "lantern");
					}
				}
			}
		}
	}

	// a corridor clear of trees over the road, so it shows from afar
	private static void clearCanopy(Site site, int x, int target, int z) {
		for (int y = target + 1; y <= target + 18; y++) {
			BlockState state = site.get(x, y, z);
			if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.VINE)) {
				site.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	// trees grown in a neighbouring chunk after the road was made can reach over it: take their branches back off
	public static void sweepRoads(Site site, Site around, Settlement s) {
		for (int[] road : s.roads()) {
			if (!around.overlaps(Math.min(road[0], road[2]) - 5, Math.min(road[1], road[3]) - 5, Math.max(road[0], road[2]) + 5, Math.max(road[1], road[3]) + 5)) {
				continue;
			}
			int[] profile = s.roadProfile(road);
			double dx = road[2] - road[0];
			double dz = road[3] - road[1];
			double length2 = Math.max(1, dx * dx + dz * dz);
			for (int x = around.box.minX(); x <= around.box.maxX(); x++) {
				for (int z = around.box.minZ(); z <= around.box.maxZ(); z++) {
					if (site.has(x, z) || !around.has(x, z) || Settlement.distanceToSegment(x + 0.5, z + 0.5, road) > 4.5) {
						continue;
					}
					double t = Mth.clamp(((x + 0.5 - road[0]) * dx + (z + 0.5 - road[1]) * dz) / length2, 0, 1);
					clearCanopy(around, x, profile[(int) Math.round(t * (profile.length - 1))], z);
				}
			}
		}
	}

	private static boolean railAt(Site site, int x, int y, int z) {
		return y >= site.level.getMinY() && y <= site.level.getMaxY() && site.get(x, y, z).is(BlossomBlocks.TRACK);
	}

	private static void roadColumn(Site site, int x, int y, int z, boolean path, int natural) {
		BlockState base = site.get(x, natural, z);
		if (!base.getFluidState().isEmpty()) {
			site.set(x, y, z, "spruce_planks");
			site.clearAbove(x, y, z);
			return;
		}
		if (!terrain(base)) {
			return;
		}
		BlockState current = site.get(x, y, z);
		if (!terrain(current) && !current.isAir() && !current.is(BlockTags.SUBSTRATE_OVERWORLD)) {
			return;
		}
		site.level(x, y, z, path ? "dirt_path" : "grass_block");
	}

	private static boolean terrain(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.WATER) || state.is(Blocks.LAVA);
	}

	// sawah and pastures in the ring around the kampungs
	private static void fields(Site site, NusantaraNetwork network, Settlement s) {
		if (!site.overlaps(s.centerX - s.reach, s.centerZ - s.reach, s.centerX + s.reach, s.centerZ + s.reach)) {
			return;
		}
		for (Plot plot : s.plots()) {
			if (!site.overlaps(plot.x0(), plot.z0(), plot.x0() + Settlement.PLOT_X - 1, plot.z0() + Settlement.PLOT_Z - 1)) {
				continue;
			}
			int px = Math.floorDiv(plot.x0(), Settlement.PLOT_X);
			int pz = Math.floorDiv(plot.z0(), Settlement.PLOT_Z);
			for (int lx = 0; lx < Settlement.PLOT_X; lx++) {
				for (int lz = 0; lz < Settlement.PLOT_Z; lz++) {
					int x = plot.x0() + lx;
					int z = plot.z0() + lz;
					if (!site.has(x, z)) {
						continue;
					}
					int level = plot.level();
					boolean bund = lx == 0 || lz == 0;
					if (plot.kind() != Plot.RICE) {
						site.level(x, level, z, "grass_block");
						boolean fence = plot.kind() == Plot.PEN && (lx == 1 || lx == Settlement.PLOT_X - 1 || lz == 1 || lz == Settlement.PLOT_Z - 1);
						if (fence) {
							boolean gate = lz == Settlement.PLOT_Z - 1 && lx == Settlement.PLOT_X / 2;
							site.set(x, level + 1, z, gate ? Site.facing("oak_fence_gate", Direction.SOUTH) : Site.block("oak_fence"));
						} else if (!bund && (lx * 3 + lz * 5) % 9 == 0) {
							site.set(x, level + 1, z, "short_grass");
						}
						continue;
					}
					site.level(x, level, z, bund ? "grass_block" : "farmland");
					if (bund) {
						continue;
					}
					int roll = Math.floorMod(network.hash(px, pz, 41), 10);
					boolean channel = lx == 4 || lx == 8;
					if (roll == 0 || channel) {
						site.set(x, level - 1, z, "mud");
						site.set(x, level, z, "water");
					} else if (roll == 1) {
						site.set(x, level, z, "coarse_dirt");
					} else {
						site.set(x, level, z, Site.block("farmland").setValue(BlockStateProperties.MOISTURE, 7));
						int age = roll < 5 ? CropBlock.MAX_AGE : roll == 9 ? 0 : 2 + Math.floorMod(network.hash(x, z, 42), 4);
						site.set(x, level + 1, z, BlossomBlocks.RICE_CROP.defaultBlockState().setValue(CropBlock.AGE, Math.min(age, CropBlock.MAX_AGE)));
					}
				}
			}
			// a gubuk or a scarecrow in some plots, animals in the pastures
			int mx = plot.x0() + Settlement.PLOT_X / 2;
			int mz = plot.z0() + Settlement.PLOT_Z / 2;
			if (!site.has(mx, mz)) {
				continue;
			}
			int extra = Math.floorMod(network.hash(px, pz, 43), 10);
			if (plot.kind() == Plot.RICE && extra == 0) {
				gubuk(site, mx, plot.level(), mz);
			} else if (plot.kind() == Plot.RICE && extra < 3) {
				site.set(mx, plot.level(), mz, "grass_block");
				for (int y = plot.level() + 1; y <= plot.level() + 2; y++) {
					site.set(mx, y, mz, "oak_fence");
				}
				site.set(mx, plot.level() + 3, mz, "carved_pumpkin");
			} else if (plot.kind() != Plot.RICE) {
				boolean cows = extra % 3 != 0;
				for (int i = 0; i < (cows ? 3 : 5); i++) {
					animal(site, cows ? EntityTypes.COW : EntityTypes.CHICKEN, mx - 3 + i * 2, plot.level() + 1, mz + (i % 2) * 2 - 1, plot.kind() == Plot.PEN);
				}
				if (plot.kind() == Plot.PEN) {
					site.set(plot.x0() + 2, plot.level() + 1, plot.z0() + 2, "hay_block");
					site.set(plot.x0() + 3, plot.level() + 1, plot.z0() + 2, Site.block("water_cauldron").setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				}
			}
		}
	}

	private static void gubuk(Site site, int x, int level, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				site.set(x + dx, level, z + dz, "grass_block");
				site.set(x + dx, level + 2, z + dz, "bamboo_planks");
				for (int y = level + 3; y <= level + 4; y++) {
					site.set(x + dx, y, z + dz, dx != 0 && dz != 0 ? "bamboo_fence" : "air");
				}
				if (dx != 0 && dz != 0) {
					site.set(x + dx, level + 1, z + dz, "bamboo_block");
				} else {
					site.set(x + dx, level + 1, z + dz, "air");
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				site.set(x + dx, level + 5, z + dz, Site.block("bamboo_mosaic_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		site.set(x, level + 1, z + 2, Site.facing("bamboo_stairs", Direction.NORTH));
	}

	private static void animal(Site site, EntityType<? extends Mob> type, int x, int y, int z, boolean keep) {
		if (!site.has(x, z)) {
			return;
		}
		Mob mob = type.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return;
		}
		mob.snapTo(x + 0.5, y, z + 0.5, site.level.getRandom().nextFloat() * 360, 0);
		if (keep) {
			mob.setPersistenceRequired();
		}
		site.level.addFreshEntity(mob);
	}
}
