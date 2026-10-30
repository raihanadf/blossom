package com.raihanadf.blossom.nusantara;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import com.raihanadf.blossom.nusantara.TrainSchedule.Inspection;
import com.raihanadf.blossom.nusantara.TrainSchedule.State;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// the lori inspeksi: a little yellow trolley with two inspectors, one for every line. it waits in a siding beside the
// line just out of a station and, before each train, runs over the line ahead of it to the siding at the other end.
// on the way it puts the line back the way it was built: ballast, bridges and tunnel linings, the stone dike that
// holds water back, rails that went missing, and it clears out whatever stands in the trains' way (a block someone
// placed, a tree, part of a structure, the crater an explosion left). like the train cars it is never saved, the
// timetable brings it back (parked or on its way) whenever someone is near
public class Lori extends Entity implements GeoEntity {
	private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("animation.lori.roll");
	// the two inspectors sit side by side on the bench, (across, along) in blocks from the middle
	private static final List<double[]> SEATS = List.of(new double[] {-0.38, 0.05}, new double[] {0.38, 0.05});
	private static final double SEAT_HEIGHT = 0.8;
	// look this far ahead of where it is, so there is room to stop before whatever is wrong
	private static final int AHEAD = 18;
	// how it slows to a stop and pulls away again, in ticks
	private static final int EASE = 25;
	// how long an inspector works on one spot, and how long the crew may stay out before the train needs the line
	private static final int WORK = 36;
	private static final int SHIFT = 400;
	private static final int WALK = 120;
	private static final int RUNNING = 0;
	private static final int BRAKING = 1;
	private static final int WORKING = 2;
	private static final int RESUMING = 3;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private boolean row;
	private int fixed;
	private int line;
	private @Nullable Inspection run;
	// the last sample of the line already looked at
	private int checked = Integer.MIN_VALUE;
	private int mended;
	// stopping to mend puts it behind its timetable: `delay` ticks behind, rolling at `rate` of its timetable pace
	// (below 1 while slowing or pulling away, above 1 while it makes the time up again)
	private double delay;
	private double rate = 1;
	private int phase = RUNNING;
	private int phaseTicks;
	private final List<Warga> crew = new ArrayList<>();
	private final List<Job> jobs = new ArrayList<>();
	private final Map<Warga, Job> working = new HashMap<>();
	private final Map<Warga, Integer> effort = new HashMap<>();
	// the stretch of line the crew is out on
	private @Nullable Job stretch;
	// it has said on the radio that the line is clear, this run
	private boolean radioed = true;

	// one spot to mend: where to stand, the blocks it covers, and the stretch of line it belongs to
	private record Job(BlockPos at, BoundingBox box, Edge edge, int lo, int hi) {
	}

	public Lori(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static Lori create(ServerLevel level, Chain chain, int line) {
		Lori lori = new Lori(BlossomEntities.LORI, level);
		lori.row = chain.row;
		lori.fixed = chain.fixed;
		lori.line = line;
		lori.delay = TrainSchedule.loriDelay(chain, line, level.getGameTime());
		lori.follow(chain, level.getGameTime() - lori.delay);
		lori.setOldPosAndRot();
		for (int i = 0; i < SEATS.size(); i++) {
			Warga inspector = BlossomEntities.WARGA.create(level, EntitySpawnReason.EVENT);
			if (inspector == null) {
				continue;
			}
			inspector.snapTo(lori.getX(), lori.getY(), lori.getZ(), lori.getYRot(), 0);
			inspector.settle(Warga.INSPECTOR, Warga.SKIN_INSPEKTUR, null, null, null, lori.blockPosition(), 32);
			level.addFreshEntity(inspector);
			inspector.board(lori);
			lori.crew.add(inspector);
		}
		return lori;
	}

	// where the timetable puts the trolley right now: x, y, z, yaw and pitch (null when the line is not on this
	// chain). it always faces along the line, heading back it runs the other way round
	public static double @Nullable [] place(Chain chain, int line, double time) {
		Inspection run = TrainSchedule.inspection(chain, line, time);
		if (run == null) {
			return null;
		}
		double[] p = chain.point(run.along(), NusantaraNetwork.SIDING_LANE);
		double x = p[0] - p[3] * p[5];
		double z = p[1] + p[2] * p[5];
		float yaw = (float) (Mth.atan2(-p[2], p[3]) * Mth.RAD_TO_DEG);
		double rise = chain.point(run.along() + 2, NusantaraNetwork.SIDING_LANE)[4] - chain.point(run.along() - 2, NusantaraNetwork.SIDING_LANE)[4];
		float pitch = (float) (-Math.atan2(rise, 4) * Mth.RAD_TO_DEG);
		return new double[] {x, p[4] + 1, z, yaw, pitch};
	}

	private boolean follow(Chain chain, double time) {
		run = TrainSchedule.inspection(chain, line, time);
		double[] at = place(chain, line, time);
		if (run == null || at == null) {
			return false;
		}
		setPos(at[0], at[1], at[2]);
		setYRot((float) at[3]);
		setXRot((float) at[4]);
		return true;
	}

	public @Nullable Chain chain() {
		NusantaraNetwork network = TrainCar.network(level());
		return network == null ? null : network.chainAt(row, fixed, line);
	}

	public double speed() {
		if (level().isClientSide()) {
			return Math.hypot(getX() - xo, getZ() - zo);
		}
		return run == null ? 0 : run.speed() * rate;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level) {
			Chain chain = chain();
			long time = level.getGameTime();
			if (chain == null || !follow(chain, time - delay)) {
				crew.forEach(Entity::discard);
				discard();
				return;
			}
			// parked where the timetable would have it parked anyway: nothing left to make up
			Inspection due = TrainSchedule.inspection(chain, line, time);
			if (phase == RUNNING && !run.moving() && due != null && !due.moving() && Math.abs(due.along() - run.along()) < 0.5) {
				delay = 0;
			}
			phaseTicks++;
			// how far behind it is, for the train of this run (which waits until the lori is in) and for whoever
			// brings the lori back after it was out of sight
			TrainSchedule.delayLori(chain, line, run.run(), delay);
			boolean arrived = phase == RUNNING && !run.moving() && TrainSchedule.loriIn(chain, line, run.run(), time - delay);
			if (arrived && !radioed) {
				radio(level, chain);
			}
			radioed = arrived;
			switch (phase) {
				case RUNNING -> {
					// making the lost time up, a little faster than the timetable but never racing
					rate = delay > 0 && run.moving() ? Math.min(1.5, 0.75 / Math.max(0.05, run.speed())) : 1;
					rate = Math.max(1, rate);
					delay = Math.max(0, delay - (rate - 1));
					if (!run.moving()) {
						checked = Integer.MIN_VALUE;
					} else if (gapAhead(level, chain)) {
						// the rails just in front are gone (blown up, dug out): stop short and see to it
						if (inspect(level, chain, true)) {
							phaseTicks = EASE - 4;
						}
					} else if (tickCount % 4 == 0) {
						inspect(level, chain, false);
					}
				}
				case BRAKING -> {
					rate = Math.max(0, 1 - phaseTicks / (double) EASE);
					delay += 1 - rate;
					if (rate == 0) {
						stepDown(level);
					}
				}
				case WORKING -> {
					rate = 0;
					delay += 1;
					work(level, chain, time);
				}
				default -> {
					rate = Math.min(1, phaseTicks / (double) EASE);
					delay += 1 - rate;
					if (rate == 1) {
						phase(RUNNING);
					}
				}
			}
			if (run.moving() && rate > 0.3 && tickCount % 60 == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.25f, 1.6f);
			}
		}
		setBoundingBox(makeBoundingBox(position()));
	}

	private void phase(int next) {
		phase = next;
		phaseTicks = 0;
	}

	// whether the track is missing right in front of the trolley
	private boolean gapAhead(ServerLevel level, Chain chain) {
		for (int k = 1; k <= 4; k++) {
			double along = run.along() + run.direction() * k;
			int line = chain.lineAt(along);
			Edge edge = chain.edge(line);
			double sample = along - chain.offset(line);
			if (edge == null || sample < 0 || sample >= edge.length()) {
				continue;
			}
			double[] p = chain.point(along, NusantaraNetwork.SIDING_LANE);
			BlockPos rail = BlockPos.containing(p[0] - p[3] * p[5], p[4] + 1, p[1] + p[2] * p[5]);
			if (!level.isLoaded(rail)) {
				continue;
			}
			boolean track = false;
			for (BlockPos near : BlockPos.betweenClosed(rail.offset(-1, -1, -1), rail.offset(1, 1, 1))) {
				track |= level.getBlockState(near).is(BlossomBlocks.TRACK);
			}
			if (!track) {
				return true;
			}
		}
		return false;
	}

	// look over the line a little ahead of the trolley (from right in front of it when `close`): anything not the
	// way it was built, and the trolley pulls up so the crew can get down and see to it. true when it found work
	private boolean inspect(ServerLevel level, Chain chain, boolean close) {
		double ahead = run.along() + run.direction() * AHEAD;
		int k = chain.lineAt(ahead);
		Edge edge = chain.edge(k);
		if (edge == null) {
			return false;
		}
		int target = Mth.clamp(Mth.floor(ahead - chain.offset(k)), 0, edge.length() - 1);
		// samples count along the line from its first station; heading back they come down. it keeps looking over
		// the stretch from 4 blocks in front (so whatever happens there after a first look is still seen), but not
		// again over what the crew has just put right
		boolean up = run.direction() > 0;
		int from = target - (up ? 1 : -1) * (close ? AHEAD : AHEAD - 4);
		if (checked != Integer.MIN_VALUE && !close) {
			from = up ? Math.max(from, checked + 1) : Math.min(from, checked - 1);
		}
		int lo = Math.max(0, Math.min(from, target));
		int hi = Math.min(edge.length() - 1, Math.max(from, target));
		if (lo > hi || Mth.floor(ahead - chain.offset(k)) < 0 || Mth.floor(ahead - chain.offset(k)) >= edge.length() && !close) {
			// inside a station: the station keeps its own yard
			return false;
		}
		BoundingBox area = area(edge, lo, hi);
		// only where the world is loaded and running: the rest has not changed since it was built
		if (!level.isPositionEntityTicking(new BlockPos(area.minX(), 0, area.minZ())) || !level.isPositionEntityTicking(new BlockPos(area.maxX(), 0, area.maxZ()))) {
			return false;
		}
		Site survey = Site.survey(level, area);
		TrackBuilder.repair(survey, edge, lo, hi);
		List<BlockPos> wrong = survey.changes();
		if (wrong.isEmpty()) {
			return false;
		}
		// something is wrong: take in the rest of it too (a crater runs on past what was looked at), so it is all
		// seen to at one stop
		if (up) {
			hi = Math.min(edge.length() - 1, hi + AHEAD);
		} else {
			lo = Math.max(0, lo - AHEAD);
		}
		target = up ? hi : lo;
		area = area(edge, lo, hi);
		survey = Site.survey(level, area);
		TrackBuilder.repair(survey, edge, lo, hi);
		wrong = survey.changes();
		// nearest first, close spots worked together
		Vec3 heading = Vec3.directionFromRotation(0, getYRot()).scale(run.direction());
		wrong.sort((p, q) -> Double.compare(Vec3.atCenterOf(p).subtract(position()).dot(heading), Vec3.atCenterOf(q).subtract(position()).dot(heading)));
		jobs.clear();
		// whatever the crew leaves, this stretch counts as looked at
		checked = target;
		List<BlockPos> group = new ArrayList<>();
		for (BlockPos pos : wrong) {
			if (!group.isEmpty() && (Math.abs(pos.getX() - group.getFirst().getX()) > 3 || Math.abs(pos.getZ() - group.getFirst().getZ()) > 3)) {
				jobs.add(job(level, group, edge, lo, hi));
				group.clear();
			}
			group.add(pos);
		}
		jobs.add(job(level, group, edge, lo, hi));
		stretch = new Job(jobs.getFirst().at(), area, edge, lo, hi);
		phase(BRAKING);
		return true;
	}

	private static Job job(ServerLevel level, List<BlockPos> group, Edge edge, int lo, int hi) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : group) {
			minX = Math.min(minX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		BoundingBox box = new BoundingBox(minX - 1, level.getMinY(), minZ - 1, maxX + 1, level.getMaxY(), maxZ + 1);
		return new Job(group.getFirst(), box, edge, lo, hi);
	}

	// the stretch of line between two samples, wide enough for its ballast and slopes
	private static BoundingBox area(Edge edge, int lo, int hi) {
		int reach = edge.ballast(lo) + 6;
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = lo; i <= hi; i++) {
			minX = Math.min(minX, Mth.floor(edge.xs[i]) - reach);
			maxX = Math.max(maxX, Mth.floor(edge.xs[i]) + reach);
			minZ = Math.min(minZ, Mth.floor(edge.zs[i]) - reach);
			maxZ = Math.max(maxZ, Mth.floor(edge.zs[i]) + reach);
		}
		return new BoundingBox(minX, -64, minZ, maxX, 320, maxZ);
	}

	// pulled up: the crew climbs down, a word about what they saw, a ring of the bell
	private void stepDown(ServerLevel level) {
		phase(WORKING);
		working.clear();
		effort.clear();
		for (Warga inspector : crew) {
			if (inspector.isAlive()) {
				inspector.stepDown();
				Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90).scale(inspector == crew.getFirst() ? 1.2 : -1.2);
				inspector.snapTo(getX() + side.x, getY(), getZ() + side.z, getYRot(), 0);
			}
		}
		level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 1.2f);
		say(level, crew.getFirst(), "stop");
	}

	// each inspector takes the next spot, walks over, works at it (swinging, chips flying) and mends it, then the next;
	// when all is done they walk back and climb on. if the train is getting close or it is taking too long, they
	// finish up in a hurry
	private void work(ServerLevel level, Chain chain, long time) {
		boolean hurry = phaseTicks > SHIFT;
		if (hurry && !jobs.isEmpty() || hurry && !working.isEmpty()) {
			List<Job> left = new ArrayList<>(jobs);
			left.addAll(working.values());
			for (Job job : left) {
				mend(level, job);
			}
			jobs.clear();
			working.clear();
			say(level, crew.getFirst(), "hurry");
		}
		boolean done = true;
		for (Warga inspector : crew) {
			if (!inspector.isAlive() || inspector.getVehicle() == this) {
				continue;
			}
			done = false;
			Job job = working.get(inspector);
			if (job == null && !jobs.isEmpty()) {
				job = jobs.removeFirst();
				working.put(inspector, job);
				effort.put(inspector, 0);
			}
			if (job == null && !working.isEmpty() && !hurry) {
				// nothing of its own left: lends a hand where the other one is working
				Job other = working.values().iterator().next();
				Vec3 near = Vec3.atBottomCenterOf(other.at());
				inspector.getLookControl().setLookAt(near.x, near.y + 0.5, near.z);
				if (inspector.distanceToSqr(near) > 2.4 * 2.4) {
					if (tickCount % 10 == 0) {
						inspector.getNavigation().moveTo(near.x + 1, near.y, near.z + 1, 0.8);
					}
				} else if (tickCount % 7 == 0) {
					inspector.getNavigation().stop();
					inspector.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
				}
				continue;
			}
			if (job == null) {
				// back to the lori and on
				if (inspector.distanceToSqr(this) < 2.5 * 2.5 || hurry && phaseTicks % 80 == 79) {
					inspector.climbBack(this);
				} else if (tickCount % 10 == 0) {
					inspector.getNavigation().moveTo(getX(), getY(), getZ(), hurry ? 1.3 : 0.8);
				}
				continue;
			}
			Vec3 spot = Vec3.atBottomCenterOf(job.at());
			inspector.getLookControl().setLookAt(spot.x, spot.y + 0.5, spot.z);
			// effort below zero is walking there (giving up on the walk after eight seconds), from zero on it is work
			int at = effort.merge(inspector, 1, Integer::sum) - WALK;
			boolean there = Math.hypot(inspector.getX() - spot.x, inspector.getZ() - spot.z) < 2.6 && Math.abs(inspector.getY() - spot.y) < 3;
			if (at < 0 && !there) {
				if (tickCount % 10 == 0) {
					inspector.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.8);
				}
				continue;
			}
			if (at < 0) {
				effort.put(inspector, WALK);
				at = 0;
			}
			inspector.getNavigation().stop();
			if (at % 6 == 0) {
				inspector.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
				BlockState state = level.getBlockState(job.at());
				BlockState shown = state.isAir() || !state.getFluidState().isEmpty() ? Blocks.GRAVEL.defaultBlockState() : state;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), spot.x, spot.y + 0.6, spot.z, 8, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, job.at(), shown.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.2f);
			}
			if (at >= WORK) {
				mend(level, job);
				working.remove(inspector);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 1, spot.z, 5, 0.4, 0.3, 0.4, 0);
			}
		}
		if (done && phaseTicks > 1) {
			// a last look over the whole stretch before they go: whatever a spot's mending left loose nearby
			if (stretch != null) {
				mended += TrackBuilder.repair(new Site(level, area(stretch.edge(), stretch.lo(), stretch.hi())), stretch.edge(), stretch.lo(), stretch.hi());
				stretch = null;
			}
			say(level, crew.getFirst(), "done");
			level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 1.5f);
			phase(RESUMING);
		}
	}

	// put one spot right: everything the builders would change inside its blocks
	private void mend(ServerLevel level, Job job) {
		mended += TrackBuilder.repair(new Site(level, job.box()), job.edge(), job.lo(), job.hi());
		Vec3 spot = Vec3.atBottomCenterOf(job.at());
		level.playSound(null, job.at(), SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 0.9f, 1f);
		level.sendParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 4, 0.3, 0.2, 0.3, 0.02);
	}

	// in the far siding: the crew radios the station the train waits at that the line is clear
	private void radio(ServerLevel level, Chain chain) {
		Warga who = crew.isEmpty() ? null : crew.getFirst();
		if (who == null) {
			return;
		}
		Settlement waiting = TrainSchedule.waitingAt(chain, line, run.run());
		Component words = Component.translatable("message.blossom.lori.radio", who.getName(), waiting.name);
		for (ServerPlayer player : level.players()) {
			boolean near = player.distanceToSqr(this) < 64 * 64
					|| Math.hypot(player.getX() - waiting.node.x(), player.getZ() - waiting.node.z()) < 120;
			if (near) {
				player.sendSystemMessage(words);
			}
		}
		level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.8f, 1.8f);
	}

	// a word from the crew to whoever is near
	private void say(ServerLevel level, Warga who, String what) {
		Component words = Component.translatable("message.blossom.warga.says", who.getName(), Component.translatable("message.blossom.lori." + what));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < 40 * 40) {
				player.sendSystemMessage(words);
			}
		}
	}

	// how many columns of line this run has mended so far
	public int mended() {
		return mended;
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		double yaw = getYRot() * Mth.DEG_TO_RAD;
		double hx = Math.abs(Math.sin(yaw)) * 1.4 + Math.abs(Math.cos(yaw)) * 0.95;
		double hz = Math.abs(Math.cos(yaw)) * 1.4 + Math.abs(Math.sin(yaw)) * 0.95;
		return new AABB(position.x - hx, position.y, position.z - hz, position.x + hx, position.y + 2.1, position.z + hz);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < SEATS.size();
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		int index = getPassengers().indexOf(passenger);
		if (index < 0) {
			return;
		}
		double[] seat = SEATS.get(index);
		Vec3 at = TrainCar.seat(this, seat[0], -seat[1], SEAT_HEIGHT);
		moveFunction.accept(passenger, at.x, at.y - passenger.getVehicleAttachmentPoint(this).y, at.z);
		if (passenger instanceof Warga warga) {
			// they look the way it goes (back over their shoulders when it runs back), now and then across at the line
			boolean back = run != null && run.direction() < 0;
			float yaw = getYRot() + (back ? (index == 0 ? 150 : -150) : 0) + (tickCount / 80 % 3 == 1 ? (index == 0 ? 50 : -50) : 0);
			warga.setYRot(getYRot());
			warga.setYBodyRot(getYRot());
			warga.setYHeadRot(yaw);
		}
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160 * 160;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Lori>("wheels", 0, test -> {
			double speed = speed();
			if (speed < 0.01) {
				return PlayState.STOP;
			}
			test.setControllerSpeed((float) Mth.clamp(speed * 3, 0, 5));
			return test.setAndContinue(ROLL);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
