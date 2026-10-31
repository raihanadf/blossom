package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Mode;
import com.raihanadf.blossom.registry.BlossomBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// lays one line through one chunk. each column takes its place across the line from the nearest point of the line:
// ballast and rails in the middle, a grassy shoulder, the road on one side, then slopes back to the land. how it is
// built depends on how the line gets through the land there: a cutting or embankment, a lined tunnel, a brick
// viaduct on piers or a steel truss bridge over water
public final class TrackBuilder {
	private static final int SLOPE = 30;

	private TrackBuilder() {
	}

	private record Column(int sample, double across, double along) {
	}

	public static void build(Site site, Edge edge) {
		int x0 = site.box.minX();
		int z0 = site.box.minZ();
		int reach = edge.stationBallast() + SLOPE + 10;
		if (edge.maxX + reach < x0 || edge.minX - reach > x0 + 15 || edge.maxZ + reach < z0 || edge.minZ - reach > z0 + 15) {
			return;
		}
		// the stretch of line near this chunk
		int first = -1;
		int last = -1;
		for (int i = 0; i < edge.length(); i++) {
			if (edge.xs[i] >= x0 - reach && edge.xs[i] <= x0 + 15 + reach && edge.zs[i] >= z0 - reach && edge.zs[i] <= z0 + 15 + reach) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first < 0) {
			return;
		}
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				Column column = nearest(edge, x0 + lx + 0.5, z0 + lz + 0.5, first, last);
				if (column != null) {
					column(site, edge, x0 + lx, z0 + lz, column);
				}
			}
		}
		for (int lane = 0; lane < edge.lanes().length; lane++) {
			rails(site, edge, lane, Math.max(0, first - 8), Math.min(edge.length() - 1, last + 8));
		}
	}

	// trees growing in a neighbouring chunk after the line was laid can reach over it: take their branches back off
	public static void sweep(Site site, Site around, Edge edge) {
		int x0 = site.box.minX();
		int z0 = site.box.minZ();
		int first = -1;
		int last = -1;
		for (int i = 0; i < edge.length(); i++) {
			if (edge.xs[i] >= x0 - 30 && edge.xs[i] <= x0 + 45 && edge.zs[i] >= z0 - 30 && edge.zs[i] <= z0 + 45) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first < 0) {
			return;
		}
		for (int x = x0 - 6; x <= x0 + 21; x++) {
			for (int z = z0 - 6; z <= z0 + 21; z++) {
				if (site.has(x, z)) {
					continue;
				}
				Column column = nearest(edge, x + 0.5, z + 0.5, first, last);
				if (column == null || Math.abs(column.across()) > edge.ballast(column.sample()) + 9 || edge.mode(column.sample()) == Mode.TUNNEL) {
					continue;
				}
				int y = edge.bed(column.sample());
				for (int k = y + 1; k <= y + 12; k++) {
					BlockState state = around.get(x, k, z);
					if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.VINE) || state.is(Blocks.BAMBOO)) {
						around.set(x, k, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	// the inspection trolley's work over samples `from` to `to` of a line, inside `site`: every column of the bed is
	// built again as it should be (ballast, the tunnel's bore and lining, a viaduct's deck and piers, the stone dike
	// against water), whatever stands in the way of the trains is cleared, and missing rails are laid again. returns
	// how many blocks it changed
	public static int repair(Site site, Edge edge, int from, int to) {
		int first = Math.max(0, from - 3);
		int last = Math.min(edge.length() - 1, to + 3);
		int changed = 0;
		Set<Long> done = new HashSet<>();
		// the bed is rebuilt over the same stretch the rails are laid again on, so no track is ever laid where the deck
		// under it is still missing
		for (int i = Math.max(0, from - 1); i <= Math.min(edge.length() - 1, to + 1); i++) {
			int reach = edge.ballast(i) + 4;
			for (double a = -reach; a <= reach; a += 0.5) {
				int x = Mth.floor(edge.xs[i] - edge.tzs[i] * a);
				int z = Mth.floor(edge.zs[i] + edge.txs[i] * a);
				if (!site.has(x, z) || !done.add(BlockPos.asLong(x, 0, z))) {
					continue;
				}
				Column c = nearest(edge, x + 0.5, z + 0.5, first, last);
				if (c == null) {
					continue;
				}
				int bh = edge.ballast(c.sample());
				int edgeAt = edge.mode(c.sample()) == Mode.TUNNEL ? bh + 3 : bh + 1;
				if (Math.abs(c.across()) > edgeAt + 0.5) {
					continue;
				}
				int y = edge.bed(c.sample());
				int before = fingerprint(site, x, y, z);
				column(site, edge, x, z, c);
				// the space the trains run through: nothing but rails and air within reach of a track (the parapet
				// of a bridge or the wall of a tunnel at the edge of the bed stays). it starts over the ballast as laid:
				// on a climb the gravel and the ballast layer sit on the even grade, above the stepped bed, and clearing
				// from the bed planned air over them, so the crew took our own ballast for damage
				int top = y;
				while (site.get(x, top + 1, z).is(Blocks.GRAVEL) || site.get(x, top + 1, z).is(BlossomBlocks.BALLAST)) {
					top++;
				}
				for (int lane = 0; lane < edge.lanes().length && Math.abs(c.across()) < edgeAt - 0.5; lane++) {
					if (!edge.laid(lane, c.sample()) || Math.abs(c.across() - edge.laneOffset(lane, c.sample())) > 1.6) {
						continue;
					}
					for (int k = top + 1; k <= top + 4; k++) {
						BlockState state = site.get(x, k, z);
						if (!state.isAir() && !state.is(BlossomBlocks.TRACK) && !state.is(BlossomBlocks.BALLAST)) {
							site.set(x, k, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
				if (fingerprint(site, x, y, z) != before) {
					changed++;
				}
			}
		}
		for (int lane = 0; lane < edge.lanes().length; lane++) {
			rails(site, edge, lane, Math.max(0, from - 1), Math.min(edge.length() - 1, to + 1));
		}
		return changed;
	}

	// a cheap summary of a column around the bed, to tell whether a repair changed anything: which blocks stand there
	// (not their states, a wall or fence joining up to its neighbours again is no repair)
	private static int fingerprint(Site site, int x, int y, int z) {
		int hash = 1;
		for (int k = y - 2; k <= y + 5; k++) {
			hash = hash * 31 + site.get(x, k, z).getBlock().hashCode();
		}
		return hash;
	}

	private static Column nearest(Edge edge, double x, double z, int first, int last) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = first; i <= last; i++) {
			double dx = x - edge.xs[i];
			double dz = z - edge.zs[i];
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		double dx = x - edge.xs[best];
		double dz = z - edge.zs[best];
		double along = best + dx * edge.txs[best] + dz * edge.tzs[best];
		// nothing past the two ends of the line (the stations take over there)
		if (along < -0.5 || along > edge.length() - 0.5) {
			return null;
		}
		double across = dx * -edge.tzs[best] + dz * edge.txs[best];
		return new Column(best, across, along);
	}

	private static void column(Site site, Edge edge, int x, int z, Column c) {
		int bh = edge.ballast(c.sample());
		double ad = Math.abs(c.across());
		int y = edge.bed(c.sample());
		Mode mode = edge.mode(c.sample());
		// the ballast follows the line's even grade: whole gravel blocks up to it, then a layer of ballast for the
		// eighths of a block left over, so it climbs with the track instead of in steps
		int layers = 0;
		if (ad <= bh + 0.5) {
			double even = edge.bedAt(c.along());
			y = Mth.floor(even);
			layers = (int) Math.round((even - y) * 8);
			if (layers == 8) {
				y++;
				layers = 0;
			}
		}
		// the ballast, then one block of shoulder: the railing of a bridge stands there. tunnels are bored wider, the
		// cars swing out on curves and riders lean out of the windows
		int edgeAt = mode == Mode.TUNNEL ? bh + 3 : bh + 1;
		// cuttings and embankments slope back to the land at one across for one up, as far out as that takes
		if (ad > edgeAt + SLOPE + 0.5) {
			return;
		}
		int along = (int) Math.floor(c.along());
		boolean wall = ad > edgeAt - 0.5 && ad <= edgeAt + 0.5;
		boolean inside = ad <= edgeAt + 0.5;
		String top = ad <= bh + 0.5 ? "gravel" : "grass_block";
		// the first two blocks of a tunnel are its portal: a stone face up to the hillside, no loose land hanging over
		boolean portal = mode == Mode.TUNNEL && (edge.mode(Math.max(0, c.sample() - 2)) != Mode.TUNNEL || edge.mode(Math.min(edge.length() - 1, c.sample() + 2)) != Mode.TUNNEL);
		if (portal && !inside && ad <= edgeAt + 3.5) {
			for (int k = y; k <= Math.max(y + 9, site.ground(x, z, false)); k++) {
				site.set(x, k, z, edge.lining);
			}
			return;
		}
		switch (mode) {
			case OPEN -> {
				// where the line runs past (or through) water lying higher than its bed, its shoulder is a stone dike
				// that holds the water back, rather than a slope the water would hang over
				int water = wall ? waterTop(site, x, y, z) : Integer.MIN_VALUE;
				if (water > Integer.MIN_VALUE) {
					for (int k = y; k <= water + 1; k++) {
						site.set(x, k, z, edge.lining);
					}
					return;
				}
				if (inside) {
					site.level(x, y, z, top);
					if (layers > 0) {
						site.set(x, y + 1, z, ballastLayer(layers));
					}
				} else {
					// telegraph poles on one side, where the land beside the line is about level with it
					if (c.across() < 0 && Math.abs(ad - (edgeAt + 2)) < 0.5 && Math.floorMod(along, 24) == 0 && Math.abs(site.ground(x, z, false) - y) <= 1) {
						site.level(x, y, z, "grass_block");
						for (int k = y + 1; k <= y + 6; k++) {
							site.set(x, k, z, "spruce_fence");
						}
						return;
					}
					// cutting or embankment slope back to the land; water stays (the dike holds it), and a slope never
					// runs out into a lake or a river: built up from the bottom it stood out of the water as a pillar of
					// dirt (worst when a crew rebuilt a few blocks of it after an explosion)
					if (!site.get(x, y + 1, z).getFluidState().isEmpty()) {
						return;
					}
					int k = (int) Math.round(ad - edgeAt);
					int ground = site.ground(x, z, false);
					if (!site.get(x, ground, z).getFluidState().isEmpty()) {
						// along the water's edge a stone revetment instead, from the bottom up to the land beside it,
						// so the bank facing the water is a neat face rather than a sheer cut of dirt
						// (only right by the line: a lake shore further out is the land's own)
						int rim = Integer.MIN_VALUE;
						if (k <= 3) {
							for (Direction d : Direction.Plane.HORIZONTAL) {
								int near = site.ground(x + d.getStepX(), z + d.getStepZ(), false);
								if (site.get(x + d.getStepX(), near, z + d.getStepZ()).getFluidState().isEmpty()) {
									rim = Math.max(rim, Math.min(near, y));
								}
							}
						}
						for (int r = site.ground(x, z, true) + 1; r <= rim; r++) {
							site.set(x, r, z, edge.lining);
						}
						return;
					}
					if (ground > y + k) {
						site.level(x, y + k, z, "grass_block");
					} else if (ground < y - k && y - ground <= SLOPE) {
						site.level(x, y - k, z, "grass_block");
					}
				}
			}
			case TUNNEL -> {
				if (!inside) {
					return;
				}
				String lining = edge.lining;
				for (int k = y - 3; k < y; k++) {
					BlockState below = site.get(x, k, z);
					if (below.isAir() || !below.getFluidState().isEmpty()) {
						site.set(x, k, z, "stone");
					}
				}
				// along the bottom of a lake or the sea the tube is glass wherever the water is, so the riders can look out
				if (wall) {
					for (int k = y; k <= (portal ? Math.max(y + 9, site.ground(x, z, false)) : y + 7); k++) {
						site.set(x, k, z, tubeWall(site, x, k, z, lining));
					}
					return;
				}
				site.set(x, y, z, top.equals("grass_block") ? "polished_andesite" : top);
				for (int k = y + 1; k <= y + 6; k++) {
					site.set(x, k, z, "air");
				}
				site.set(x, y + 7, z, tubeWall(site, x, y + 7, z, lining));
				if (portal) {
					for (int k = y + 8; k <= Math.max(y + 9, site.ground(x, z, false)); k++) {
						site.set(x, k, z, lining);
					}
				}
				if (layers > 0) {
					site.set(x, y + 1, z, ballastLayer(layers));
				}
				if (Math.abs(ad - bh) < 0.5 && c.across() > 0 && Math.floorMod(along, 12) == 0) {
					site.set(x, y + 6, z, Site.block("lantern").setValue(BlockStateProperties.HANGING, true));
				}
			}
			case VIADUCT, STEEL -> {
				if (!inside) {
					return;
				}
				boolean steel = mode == Mode.STEEL;
				String deck = steel ? "smooth_stone" : edge.deck;
				site.set(x, y, z, top.equals("gravel") ? Site.block("gravel") : Site.block(deck));
				if (top.equals("gravel")) {
					site.set(x, y - 1, z, deck);
				}
				site.clearAbove(x, y, z);
				if (layers > 0) {
					site.set(x, y + 1, z, ballastLayer(layers));
				}
				int pierEvery = steel ? 24 : 12;
				int fromPier = Math.min(Math.floorMod(along, pierEvery), pierEvery - Math.floorMod(along, pierEvery));
				if (fromPier <= 1) {
					// a pier down to the land or the river bed
					for (int k = y - 1; k > site.level.getMinY(); k--) {
						BlockState below = site.get(x, k, z);
						if (!below.isAir() && below.getFluidState().isEmpty() && !Site.isCover(below)) {
							break;
						}
						site.set(x, k, z, steel ? "stone_bricks" : deck);
					}
				} else if (!steel && fromPier <= 3) {
					// the arch springing from the pier
					site.set(x, y - 1, z, deck);
					if (fromPier == 2) {
						site.set(x, y - 2, z, deck);
					}
				} else if (steel) {
					site.set(x, y - 1, z, "polished_deepslate");
				}
				if (wall) {
					if (steel) {
						// a low dark girder along the edge, a lamp on it now and then (tall bars only zigzag on a curve)
						site.set(x, y + 1, z, "polished_deepslate_wall");
						if (Math.floorMod(along, 16) == 0) {
							site.set(x, y + 2, z, "lantern");
						}
					} else {
						site.set(x, y + 1, z, deck.equals("bricks") ? "brick_wall" : deck.equals("mud_bricks") ? "mud_brick_wall" : "stone_brick_wall");
						if (Math.floorMod(along, 16) == 0) {
							site.set(x, y + 2, z, "lantern");
						}
					}
				}
			}
		}
	}

	// the highest water within three blocks of a column, between a line's bed and ten blocks over it (or none)
	private static int waterTop(Site site, int x, int y, int z) {
		int top = Integer.MIN_VALUE;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int k = y; k <= y + 10; k++) {
					if (!site.get(x + dx, k, z + dz).getFluidState().isEmpty()) {
						top = Math.max(top, k);
					}
				}
			}
		}
		return top;
	}

	private static BlockState ballastLayer(int layers) {
		return BlossomBlocks.BALLAST.defaultBlockState().setValue(BallastBlock.LAYERS, layers);
	}

	private static String tubeWall(Site site, int x, int y, int z, String lining) {
		// a wall already glazed stays glass (the water it keeps out is no longer in its place to see)
		boolean wet = !site.get(x, y, z).getFluidState().isEmpty() || !site.get(x, y + 1, z).getFluidState().isEmpty();
		return wet || site.get(x, y, z).is(Blocks.GLASS) ? "glass" : lining;
	}

	// the track of one lane: points along its middle a quarter block apart, on top of the ballast
	private static void rails(Site site, Edge edge, int lane, int from, int to) {
		List<double[]> points = new ArrayList<>();
		for (double i = from; i <= to; i += 0.25) {
			// station loops and the siding are only laid where they have split off the line's own track
			if (!edge.laid(lane, i)) {
				lay(site, points);
				points.clear();
				continue;
			}
			int k = Math.min(edge.length() - 2, Mth.floor(i));
			double f = i - k;
			double track = edge.laneOffset(lane, i);
			double tx = Mth.lerp(f, edge.txs[k], edge.txs[k + 1]);
			double tz = Mth.lerp(f, edge.tzs[k], edge.tzs[k + 1]);
			points.add(new double[] {Mth.lerp(f, edge.xs[k], edge.xs[k + 1]) - tz * track, edge.bedAt(i) + 1, Mth.lerp(f, edge.zs[k], edge.zs[k + 1]) + tx * track});
		}
		lay(site, points);
	}

	// smooth track along a line of points (x, height of the ballast top, z) about a quarter block apart: every block
	// the line passes through gets one piece, from where it enters the block to where it leaves, at its own angle and
	// grade, so the pieces join into one track on straights, curves, diagonals and climbs alike
	static void lay(Site site, List<double[]> points) {
		int n = points.size();
		int i = 0;
		while (i < n) {
			int cx = Mth.floor(points.get(i)[0]);
			int cz = Mth.floor(points.get(i)[2]);
			int j = i;
			double sx = 0;
			double sy = 0;
			double sz = 0;
			while (j < n && Mth.floor(points.get(j)[0]) == cx && Mth.floor(points.get(j)[2]) == cz) {
				sx += points.get(j)[0];
				sy += points.get(j)[1];
				sz += points.get(j)[2];
				j++;
			}
			int count = j - i;
			double[] before = points.get(Math.max(0, i - 1));
			double[] after = points.get(Math.min(n - 1, j));
			double dx = after[0] - before[0];
			double dy = after[1] - before[1];
			double dz = after[2] - before[2];
			double flat = Math.sqrt(dx * dx + dz * dz);
			// the distance between two points, so the piece covers its share of the line
			double spacing = n > 1 ? Math.sqrt(Math.pow(points.get(1)[0] - points.get(0)[0], 2) + Math.pow(points.get(1)[2] - points.get(0)[2], 2)) : 0.25;
			if (site.has(cx, cz) && flat > 1.0e-4) {
				double mx = sx / count;
				double my = sy / count;
				double mz = sz / count;
				int by = Mth.floor(my + 0.001);
				// the track block stands on the ballast (or a slab of it), never in place of it
				BlockState here = site.get(cx, by, cz);
				if (!Site.isCover(here) && !here.is(BlossomBlocks.TRACK)) {
					by++;
				}
				BlockPos pos = new BlockPos(cx, by, cz);
				if (!site.get(cx, by, cz).is(BlossomBlocks.TRACK)) {
					site.set(cx, by, cz, BlossomBlocks.TRACK.defaultBlockState());
				}
				// a track never hangs in the air over a small gap (a dip in the yard, a hollow under a tree): it gets ballast
				// and a little soil under it, down to the ground. a deeper drop is a bridge or an embankment, built by the
				// bed itself; filling it here stood a tall column of dirt out of the water under a broken bridge
				int support = by - 1;
				while (support > by - 6 && support > site.level.getMinY() && Site.isCover(site.get(cx, support, cz)) && site.get(cx, support, cz).getFluidState().isEmpty()) {
					support--;
				}
				if (support > by - 6 && site.get(cx, support, cz).getFluidState().isEmpty()) {
					for (int k = by - 1; k > support; k--) {
						site.set(cx, k, cz, k == by - 1 ? "gravel" : "dirt");
					}
				}
				// rails reach a little past their own block, so land or a slope over them and right beside them goes:
				// nothing natural is ever left lying on the track
				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						if (!site.has(cx + ox, cz + oz)) {
							continue;
						}
						for (int k = by + (ox == 0 && oz == 0 ? 1 : 0); k <= by + 3; k++) {
							BlockState above = site.get(cx + ox, k, cz + oz);
							// the next block's ballast (a step up) and water (a tube under a lake) stay
							if (Site.isNatural(above) && !above.is(Blocks.GRAVEL) && !above.is(BlossomBlocks.BALLAST) && above.getFluidState().isEmpty()) {
								site.set(cx + ox, k, cz + oz, Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
				if (!site.dry() && site.level.getBlockEntity(pos) instanceof TrackBlockEntity track) {
					float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
					float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
					track.addPiece((float) (mx - cx), (float) (0.5 + my - by), (float) (mz - cz), yaw, pitch, (float) (count * spacing), true);
				}
			}
			i = j;
		}
	}
}
