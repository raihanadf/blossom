package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

// what fills a station's grounds, after lempuyangan and tugu: a covered concourse from the hall to the platform gates
// with the platform's satpam, and the forecourt out front with a paved drive, a parking lot with motors, kiosks,
// shade trees in planters with benches, the flag, and a satpam post by the gate in the wall
public final class StationGrounds {
	private StationGrounds() {
	}

	// the concourse between the hall and the gate line of the platforms out of `port`: paving under a roof on
	// concrete posts, benches facing the gates, lamps, and the satpam who watches the gates from the platform side
	public static void concourse(Site site, Settlement s, int port, int g) {
		int dx = NusantaraNetwork.DX[port];
		int dz = NusantaraNetwork.DZ[port];
		int side = s.gates(port % 2 == 0).side();
		int ax = -dz * side;
		int az = dx * side;
		int x0 = s.node.x();
		int z0 = s.node.z();
		int fence = Settlement.FENCE;
		int from = Settlement.ENTRY_ALONG - 4;
		int to = Settlement.EXIT_ALONG + 4;
		for (int along = from; along <= to; along++) {
			for (int k = fence + 1; k <= fence + 6; k++) {
				int x = x0 + dx * along + ax * k;
				int z = z0 + dz * along + az * k;
				site.set(x, g, z, (along + k) % 2 == 0 ? "polished_andesite" : "smooth_stone");
				site.clearAbove(x, g, z);
				boolean post = (k == fence + 1 || k == fence + 6) && (along == from || along == to || k == fence + 6 && Math.floorMod(along - from, 4) == 0);
				if (post) {
					for (int y = g + 1; y <= g + 4; y++) {
						site.set(x, y, z, "light_gray_concrete");
					}
				}
				site.set(x, g + 5, z, Site.block("smooth_stone_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
				// benches facing the gates, out of the way in and out
				boolean bench = k == fence + 5 && (along == from + 1 || along == from + 2 || along == to - 1 || along == to - 2);
				if (bench) {
					site.set(x, g + 1, z, BlossomBlocks.CHAIRS.get("spruce").defaultBlockState()
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.getNearest(-ax, 0, -az, null)));
				}
			}
		}
		for (int along : new int[] {from + 3, to - 3}) {
			site.set(x0 + dx * along + ax * (fence + 3), g + 4, z0 + dz * along + az * (fence + 3), Site.block("lantern").setValue(BlockStateProperties.HANGING, true));
		}
		// the platform's satpam, inside by the gates
		int sx = x0 + dx * (Settlement.ENTRY_ALONG + 1) + ax * (fence - 1);
		int sz = z0 + dz * (Settlement.ENTRY_ALONG + 1) + az * (fence - 1);
		site.person(sx, g + 2, sz, Warga.GUARD, Warga.SKIN_SATPAM, new BlockPos(sx, g + 2, sz), new BlockPos(x0, g + 1, z0), 80);
	}

	// the forecourt: the square between the hall, the plaza and the wall on the settlement's side
	public static void forecourt(Site site, Settlement s, int g, RandomSource random) {
		int x0 = s.node.x();
		int z0 = s.node.z();
		if (!site.overlaps(Math.min(x0, x0 + s.qx * 46), Math.min(z0, z0 + s.qz * 46), Math.max(x0, x0 + s.qx * 46), Math.max(z0, z0 + s.qz * 46))) {
			return;
		}
		int[] road = s.roads().getFirst();
		Ground ground = new Ground(s, road);
		// the drive: paving along the road from the plaza out to the gate in the wall
		for (int u = 15; u <= 45; u++) {
			for (int v = 15; v <= 45; v++) {
				int x = ground.x(u);
				int z = ground.z(v);
				if (!site.has(x, z) || ground.built(u, v)) {
					continue;
				}
				double drive = Settlement.distanceToSegment(x + 0.5, z + 0.5, road);
				if (drive <= 2.2) {
					site.set(x, g, z, drive <= 0.8 ? "polished_andesite" : "stone_bricks");
				} else if (drive <= 3.2) {
					// a kerb of low hedge along the drive
					site.set(x, g + 1, z, Math.floorMod(u + v, 5) == 0 ? Site.block("lantern") : Site.block("azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true));
				}
			}
		}
		// parking on whichever side of the hall the drive leaves alone, kiosks on the other
		boolean parkingAlongU = ground.roadIn(39, 16, 44, 30) <= ground.roadIn(16, 39, 30, 44);
		int[] parking = parkingAlongU ? new int[] {39, 16, 44, 30} : new int[] {16, 39, 30, 44};
		int[] kiosks = parkingAlongU ? new int[] {16, 39, 30, 44} : new int[] {39, 16, 44, 30};
		parking(site, s, ground, parking, g, random);
		int kioskCount = 0;
		for (int i = 0; i < 2; i++) {
			int u = parkingAlongU ? kiosks[0] + 2 + i * 8 : kiosks[0] + 2;
			int v = parkingAlongU ? kiosks[1] + 2 : kiosks[1] + 2 + i * 8;
			if (ground.free(u - 1, v - 1, u + 3, v + 2)) {
				kiosk(site, s, ground, u, v, g, random, kioskCount++);
			}
		}
		// the lawn is a little taman: shade trees in planters (each with a bench) on a grid wherever there is room,
		// and flower beds in the gaps between them
		for (int u = 18; u <= 42; u += 6) {
			for (int v = 18; v <= 42; v += 6) {
				if (ground.taken(u, v, parking) || ground.taken(u, v, kiosks)) {
					continue;
				}
				if (ground.free(u - 2, v - 2, u + 2, v + 2)) {
					tree(site, ground, u, v, g);
				}
			}
		}
		String[] flowers = {"red_tulip", "pink_tulip", "orange_tulip", "oxeye_daisy", "cornflower", "allium"};
		for (int u = 16; u <= 44; u++) {
			for (int v = 16; v <= 44; v++) {
				boolean bed = Math.floorMod(u, 6) == 3 && Math.floorMod(v, 3) != 2 || Math.floorMod(v, 6) == 3 && Math.floorMod(u, 3) != 2;
				if (!bed || !ground.free(u, v, u, v) || ground.taken(u, v, parking) || ground.taken(u, v, kiosks)) {
					continue;
				}
				int x = ground.x(u);
				int z = ground.z(v);
				if (site.has(x, z) && site.get(x, g, z).is(Blocks.GRASS_BLOCK) && site.get(x, g + 1, z).isAir()) {
					site.set(x, g + 1, z, flowers[Math.floorMod(u * 7 + v * 13, flowers.length)]);
				}
			}
		}
		// the flag at the corner of the plaza
		int fu = 38;
		int fv = 38;
		if (ground.free(fu, fv, fu, fv)) {
			int fx = ground.x(fu);
			int fz = ground.z(fv);
			site.set(fx, g, fz, "stone_bricks");
			for (int y = g + 1; y <= g + 9; y++) {
				site.set(fx, y, fz, y <= g + 1 ? "stone_brick_wall" : "iron_chain");
			}
			site.set(fx, g + 10, fz, "lantern");
			int fly = ground.x(fu + 1) - fx;
			for (int k = 1; k <= 3; k++) {
				site.set(fx + fly * k, g + 9, fz, "red_wool");
				site.set(fx + fly * k, g + 8, fz, "white_wool");
			}
		}
		guardPost(site, s, ground, road, g);
	}

	// rows of parking bays marked out in white, a few motors parked, an ojek driver waiting by them
	private static void parking(Site site, Settlement s, Ground ground, int[] area, int g, RandomSource random) {
		int motors = 0;
		boolean alongV = area[3] - area[1] > area[2] - area[0];
		for (int u = area[0]; u <= area[2]; u++) {
			for (int v = area[1]; v <= area[3]; v++) {
				if (!ground.free(u, v, u, v)) {
					continue;
				}
				int x = ground.x(u);
				int z = ground.z(v);
				int across = alongV ? u - area[0] : v - area[1];
				int bay = alongV ? v - area[1] : u - area[0];
				boolean line = across == 2 || (across != 2 && bay % 3 == 0);
				site.set(x, g, z, line ? "white_concrete" : "polished_andesite");
				site.clearAbove(x, g, z);
				if (!line && bay % 3 == 1 && (across == 0 || across == 4) && random.nextInt(3) != 0 && site.has(x, z)) {
					var motor = BlossomEntities.MOTOR.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
					motor.snapTo(x + 0.5, g + 1, z + 0.5, alongV ? (across == 0 ? 90 : -90) : (across == 0 ? 0 : 180), 0);
					motor.setColor(DyeColor.values()[random.nextInt(DyeColor.values().length)]);
					site.level.addFreshEntity(motor);
					motors++;
				}
			}
		}
		if (motors > 0) {
			int u = alongV ? area[0] + 2 : area[0] + 1;
			int v = alongV ? area[1] + 1 : area[1] + 2;
			int x = ground.x(u);
			int z = ground.z(v);
			site.person(x, g + 1, z, Warga.RESIDENT, Warga.SKIN_MEN + random.nextInt(Warga.SKINS_PER_GROUP), new BlockPos(x, g + 1, z),
					new BlockPos(s.plazaX(), g + 1, s.plazaZ()), 12);
		}
	}

	// a kiosk: a counter under a striped awning on four posts, goods on the shelf behind, the seller
	private static void kiosk(Site site, Settlement s, Ground ground, int u, int v, int g, RandomSource random, int index) {
		String[] goods = {"barrel", "melon", "hay_block", "pumpkin"};
		for (int du = 0; du <= 2; du++) {
			for (int dv = 0; dv <= 1; dv++) {
				int x = ground.x(u + du);
				int z = ground.z(v + dv);
				site.set(x, g, z, "spruce_planks");
				site.clearAbove(x, g, z);
				boolean post = (du == 0 || du == 2);
				if (post) {
					site.set(x, g + 1, z, "spruce_fence");
					site.set(x, g + 2, z, "spruce_fence");
				} else if (dv == 0) {
					site.set(x, g + 1, z, "spruce_planks");
				}
				site.set(x, g + 3, z, (du + index) % 2 == 0 ? "red_wool" : "white_wool");
			}
			site.set(ground.x(u + du), g + 1, ground.z(v + 2), du == 1 ? "air" : goods[random.nextInt(goods.length)]);
		}
		int x = ground.x(u + 1);
		int z = ground.z(v + 1);
		site.person(x, g + 1, z, Warga.VENDOR, Warga.SKIN_WOMEN + random.nextInt(Warga.SKINS_PER_GROUP), new BlockPos(x, g + 1, z),
				new BlockPos(s.plazaX(), g + 1, s.plazaZ()), 4);
	}

	// a shade tree in a raised planter, with a bench beside it and someone waiting on it
	private static void tree(Site site, Ground ground, int u, int v, int g) {
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				int x = ground.x(u + du);
				int z = ground.z(v + dv);
				site.set(x, g, z, "grass_block");
				if (du != 0 || dv != 0) {
					site.set(x, g + 1, z, Site.block("stone_brick_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
				}
			}
		}
		int x = ground.x(u);
		int z = ground.z(v);
		for (int y = g + 1; y <= g + 5; y++) {
			site.set(x, y, z, "jungle_wood");
		}
		for (int dy = 4; dy <= 8; dy++) {
			int r = dy == 8 ? 1 : dy == 4 ? 2 : 3;
			for (int ox = -r; ox <= r; ox++) {
				for (int oz = -r; oz <= r; oz++) {
					if (ox * ox + oz * oz > r * r + 1 || ox == 0 && oz == 0 && dy <= 5) {
						continue;
					}
					if (site.get(x + ox, g + dy, z + oz).isAir()) {
						site.set(x + ox, g + dy, z + oz, Site.block("jungle_leaves").setValue(BlockStateProperties.PERSISTENT, true));
					}
				}
			}
		}
		int bx = ground.x(u + 2);
		site.set(bx, g + 1, z, BlossomBlocks.CHAIRS.get("spruce").defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.getNearest(bx - x, 0, 0, null)));
	}

	// the satpam post by the gate in the wall: a little white hut with windows on the drive, a chair, a sign, and
	// the satpam standing at its door
	private static void guardPost(Site site, Settlement s, Ground ground, int[] road, int g) {
		double len = Math.hypot(road[2] - road[0], road[3] - road[1]);
		double ux = (road[2] - road[0]) / len;
		double uz = (road[3] - road[1]) / len;
		double gx = road[0];
		double gz = road[1];
		// out along the drive to the last spot inside the wall
		for (double t = 0; t < len; t += 0.5) {
			double x = road[0] + ux * t;
			double z = road[1] + uz * t;
			if (StationBuilder.yard(s, (int) Math.floor(x), (int) Math.floor(z)) > 0) {
				break;
			}
			gx = x;
			gz = z;
		}
		for (int sign : new int[] {1, -1}) {
			double px = -uz * sign;
			double pz = ux * sign;
			int cx = (int) Math.floor(gx - ux * 4 + px * 5);
			int cz = (int) Math.floor(gz - uz * 4 + pz * 5);
			if (!fits(s, cx, cz, road)) {
				continue;
			}
			Direction toDrive = Direction.getApproximateNearest(-px, 0, -pz);
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					int x = cx + ox;
					int z = cz + oz;
					site.set(x, g, z, "polished_andesite");
					boolean wall = Math.abs(ox) == 1 || Math.abs(oz) == 1;
					boolean front = ox == toDrive.getStepX() || oz == toDrive.getStepZ();
					boolean door = front && (toDrive.getStepX() != 0 ? oz == 0 : ox == 0);
					for (int y = g + 1; y <= g + 3; y++) {
						String block = !wall ? "air" : door && y <= g + 2 ? "air" : front && y == g + 2 ? "glass_pane" : y == g + 1 ? "light_gray_concrete" : "white_concrete";
						site.set(x, y, z, block);
					}
					site.set(x, g + 4, z, Site.block("smooth_stone_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
				}
			}
			site.set(cx - toDrive.getStepX(), g + 1, cz - toDrive.getStepZ(), BlossomBlocks.CHAIRS.get("dark_oak").defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, toDrive));
			site.sign(cx, g + 5, cz, toDrive, false, "", "POS SATPAM", "", "");
			int sx = cx + toDrive.getStepX() * 2;
			int sz = cz + toDrive.getStepZ() * 2;
			site.person(sx, g + 1, sz, Warga.GUARD, Warga.SKIN_SATPAM, new BlockPos(sx, g + 1, sz), new BlockPos(s.node.x(), g + 1, s.node.z()), 80);
			return;
		}
		// no room for the hut on either side: the satpam still stands his post beside the drive, just inside the gate
		int sx = (int) Math.floor(gx - ux * 2 - uz * 3);
		int sz = (int) Math.floor(gz - uz * 2 + ux * 3);
		site.person(sx, g + 1, sz, Warga.GUARD, Warga.SKIN_SATPAM, new BlockPos(sx, g + 1, sz), new BlockPos(s.node.x(), g + 1, s.node.z()), 80);
	}

	// whether a 3x3 hut (and the spot in front of it) fits at a centre: inside the grounds, off the drive, clear of
	// the hall and plaza
	private static boolean fits(Settlement s, int cx, int cz, int[] road) {
		for (int ox = -2; ox <= 2; ox++) {
			for (int oz = -2; oz <= 2; oz++) {
				int x = cx + ox;
				int z = cz + oz;
				if (StationBuilder.yard(s, x, z) > 0 || StationBuilder.yard(s, x + 1, z) > 0 || StationBuilder.yard(s, x - 1, z) > 0
						|| StationBuilder.yard(s, x, z + 1) > 0 || StationBuilder.yard(s, x, z - 1) > 0) {
					return false;
				}
				if (Settlement.distanceToSegment(x + 0.5, z + 0.5, road) < 2.6 && Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
					return false;
				}
			}
		}
		Ground ground = new Ground(s, road);
		int u = (cx - s.node.x()) * s.qx;
		int v = (cz - s.node.z()) * s.qz;
		return !ground.built(u, v);
	}

	// the forecourt in its own coordinates: u and v count from the station's middle towards the settlement, 15 to 45
	// is the forecourt; the hall and the plaza are already built on
	private record Ground(Settlement s, int[] road) {
		int x(int u) {
			return s.node.x() + s.qx * u;
		}

		int z(int v) {
			return s.node.z() + s.qz * v;
		}

		boolean built(int u, int v) {
			boolean hall = u >= 13 && u <= 27 && v >= 13 && v <= 27;
			boolean plaza = u >= 26 && u <= 38 && v >= 26 && v <= 38;
			return hall || plaza || u < 15 || v < 15 || u > 45 || v > 45;
		}

		// free for something to stand: on the forecourt, off the drive and its hedges
		boolean free(int u0, int v0, int u1, int v1) {
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (built(u, v) || u > 44 || v > 44 || Settlement.distanceToSegment(x(u) + 0.5, z(v) + 0.5, road) < 3.6) {
						return false;
					}
				}
			}
			return true;
		}

		// how much of an area the drive crosses
		int roadIn(int u0, int v0, int u1, int v1) {
			int count = 0;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (Settlement.distanceToSegment(x(u) + 0.5, z(v) + 0.5, road) < 3.6) {
						count++;
					}
				}
			}
			return count;
		}

		boolean taken(int u, int v, int[] area) {
			return u >= area[0] - 2 && u <= area[2] + 2 && v >= area[1] - 2 && v <= area[3] + 2;
		}
	}
}
