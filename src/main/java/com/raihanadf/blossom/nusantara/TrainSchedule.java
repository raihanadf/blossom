package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.nusantara.NusantaraNetwork.Chain;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// the timetable of a chain of stations, from the time alone. time runs in slots: in each slot every line of the chain
// has one train on it, which stands at a station until its lori has checked the line, then runs to the next one. trains go on through
// every station and only turn back at a real end of the line, where the loco runs round its coaches: it uncouples,
// pulls forward into the headshunt onto a turntable that slowly swings it round to face the other way, runs back,
// crosses over to the other track,
// past the coaches, crosses back behind them and couples on at the other end
public final class TrainSchedule {
	// time runs in slots of five and a half minutes. each line has one train run per slot (the trains of a line take turns
	// going each way) and one lori run before it: just after a train has come in over a line, the line's lori sets
	// off over it the other way, ahead of the next train, and radios the line clear from the siding at the far end.
	// that train leaves DEPART into its slot, or later if the lori isn't in yet, at a steady train's pace
	public static final int SLOT = 6600;
	public static final int DWELL = 900;
	private static final int DEPART = DWELL;
	private static final int RADIO = 100;
	private static final double TRAIN_TOP = 0.85;
	private static final double TRAIN_ACCEL = 0.003;
	// spare time at the end of a slot: every train is in by then
	private static final int SPARE = 150;
	// how soon after a train has come in the lori sets off behind it
	private static final int LORI_GAP = 200;
	private static final double LORI_ACCEL = 0.004;
	private static final double LORI_SLOWEST = 0.55;
	private static final double LORI_FASTEST = 0.85;
	private static final int LORI_BUDGET = 2700;
	// one cc201 and four coaches
	public static final int CARS = 5;
	public static final double LOCO = 11;
	public static final double COACH = 12;
	public static final double GAP = 0.5;
	// half the length of the four coaches together; the loco is coupled on beyond them at the front
	public static final double COACHES_HALF = (4 * COACH + 3 * GAP) / 2;
	private static final double LOCO_REACH = COACHES_HALF + GAP + LOCO / 2;
	// the run-round at a terminus, in ticks of the stop: uncouple, onto the turntable, turn round, back past the coaches
	// on the other track, then up to the coaches again
	public static final int UNCOUPLE = 40;
	private static final int OUT_END = 150;
	private static final int TURN_START = 160;
	private static final int TURN_END = 270;
	private static final int BACK_START = 280;
	private static final int BACK_END = 480;
	private static final int RETURN_START = 495;
	public static final int COUPLE = 580;
	// the headshunt beyond the hub at a terminus: the crossover between its two tracks, how far out the turntable
	// stands (the loco's middle when it is on it) and its radius, and how far the headshunt reaches
	public static final int CROSS_NEAR = 10;
	public static final int CROSS_FAR = 20;
	public static final double SHUNT_AT = 29;
	public static final double TABLE_RADIUS = 6.5;
	public static final int HEADSHUNT = 37;
	// out on the line, where the station tracks have merged back into one
	private static final double MERGED = 112;

	private TrainSchedule() {
	}

	public record Train(long chain, int id) {
	}

	// where a train is: the middle of its coaches as a distance along the chain, which way it heads (along = towards
	// the chain's end), whether it stands at a station and which one (station numbers go negative too, west and north
	// of the world's middle; while running it is the next stop), how long it has stood there, the line it is on, its
	// speed in blocks per tick, whether it left from (or stands turning at) an end of the chain, and how far into the
	// run it is
	public record State(double center, boolean along, boolean standing, int station, int waited, int line, double speed, boolean terminus,
			double progress) {
		public boolean turning() {
			return standing && terminus;
		}
	}

	public static long slot(Chain chain, double time) {
		return (long) Math.floor((time + (chain.row ? 0 : SLOT / 2)) / SLOT);
	}

	private static double phase(Chain chain, double time) {
		double t = time + (chain.row ? 0 : SLOT / 2);
		return t - Math.floor(t / SLOT) * SLOT;
	}

	// the train on line k in this slot: heading along when (slot - k) is even, so the trains on neighbouring lines
	// meet at the station between them (which has two tracks). a train heading along moves one line on every slot and
	// its id (slot - k, even) never changes; one heading back has the odd id slot + k. at a real end of the line the
	// train turns round there and carries on as a new train the other way
	public static Train trainOn(Chain chain, int k, long slot) {
		boolean along = Math.floorMod(slot - k, 2) == 0;
		return new Train(chain.key, (int) (along ? slot - k : slot + k));
	}

	public static @Nullable State state(Chain chain, Train train, double time) {
		long slot = slot(chain, time);
		boolean along = Math.floorMod(train.id(), 2) == 0;
		long at = along ? slot - train.id() : train.id() - slot;
		if (at < chain.start || at >= chain.end) {
			return null;
		}
		int line = (int) at;
		int from = along ? line : line + 1;
		int to = along ? line + 1 : line;
		boolean terminus = along ? line == chain.start && chain.startReal : line + 1 == chain.end && chain.endReal;
		double a = chain.stop(from);
		double b = chain.stop(to);
		double p = phase(chain, time);
		Timing timing = timing(chain, line);
		// it leaves once the lori has radioed in. the last 41 ticks before it goes (the whistle, the doors) always
		// come just before it leaves
		int leaves = leaves(chain, line, slot, 1);
		if (p < leaves) {
			int waited = p < HOLD_AT ? (int) p : (int) Math.max(HOLD_AT, p - (leaves - DWELL));
			return new State(a, along, true, from, waited, line, 0, terminus, 0);
		}
		double distance = Math.abs(b - a);
		double t = p - leaves;
		if (t >= timing.run()) {
			// arrived: it stands at the next station until its next slot
			return new State(b, along, true, to, (int) Math.min(HOLD_AT, t - timing.run()), line, 0, false, 1);
		}
		double s = ramped(t, distance, timing.top(), TRAIN_ACCEL);
		double speed = ramped(t + 0.5, distance, timing.top(), TRAIN_ACCEL) - ramped(t - 0.5, distance, timing.top(), TRAIN_ACCEL);
		return new State(a + Math.signum(b - a) * s, along, false, to, 0, line, speed, terminus, t / timing.run());
	}

	// one line's runs: the lori's top speed and how long its run takes, the train's top speed and how long its run
	// takes. it all follows from the line's length (the same both ways)
	private record Timing(double loriTop, int loriRun, double top, int run) {
	}

	private static Timing timing(Chain chain, int k) {
		Edge edge = chain.edge(k);
		double lori = Math.abs(edge.parking(false) - edge.parking(true));
		double loriTop = Mth.clamp(lori / LORI_BUDGET, LORI_SLOWEST, LORI_FASTEST);
		int loriRun = (int) Math.ceil(duration(lori, loriTop, LORI_ACCEL));
		double distance = Math.abs(chain.stop(k + 1) - chain.stop(k));
		// the usual pace, a little quicker only on a line too long for it to fit
		double room = SLOT - SPARE - DEPART;
		double top = TRAIN_TOP;
		if (duration(distance, top, TRAIN_ACCEL) > room) {
			double root = TRAIN_ACCEL * TRAIN_ACCEL * room * room - 4 * TRAIN_ACCEL * distance;
			top = root > 0 ? (TRAIN_ACCEL * room - Math.sqrt(root)) / 2 : TRAIN_ACCEL * room / 2;
		}
		return new Timing(loriTop, loriRun, top, (int) Math.ceil(duration(distance, top, TRAIN_ACCEL)));
	}

	// when (into its slot) the train of line k leaves in `slot`: DEPART, or later when its lori isn't in by then
	// (it set off late behind a late train, or stopped to mend the line), but never so late it can't be in by the
	// end of the slot. `depth` is how many slots back a late train is still followed
	private static int leaves(Chain chain, int k, long slot, int depth) {
		Timing timing = timing(chain, k);
		double radioed = loriStart(chain, k, slot, depth) + timing.loriRun() + delays(chain, k, slot)[1] + RADIO;
		return (int) Mth.clamp(Math.ceil(radioed), DEPART, SLOT - SPARE - timing.run());
	}

	// when (into `slot`, below zero for the slot before) the lori of line k sets off on the run ahead of that slot's
	// train: just after the train before it came in over the line
	private static double loriStart(Chain chain, int k, long slot, int depth) {
		int before = depth > 0 ? leaves(chain, k, slot - 1, depth - 1) : DEPART;
		return before + timing(chain, k).run() + LORI_GAP - SLOT;
	}

	// how long a run of `distance` takes speeding up at `accel` to `top`, rolling, and slowing to a stop
	private static double duration(double distance, double top, double accel) {
		if (distance < top * top / accel) {
			return 2 * Math.sqrt(distance / accel);
		}
		return distance / top + top / accel;
	}

	// distance covered `t` ticks into such a run
	private static double ramped(double t, double distance, double top, double accel) {
		t = Math.max(0, t);
		double ramp = top / accel;
		double rampDistance = 0.5 * accel * ramp * ramp;
		if (2 * rampDistance > distance) {
			double half = Math.sqrt(distance / accel);
			if (t <= half) {
				return 0.5 * accel * t * t;
			}
			double left = Math.max(0, 2 * half - t);
			return distance - 0.5 * accel * left * left;
		}
		double cruise = (distance - 2 * rampDistance) / top;
		if (t <= ramp) {
			return 0.5 * accel * t * t;
		}
		if (t <= ramp + cruise) {
			return rampDistance + top * (t - ramp);
		}
		double left = Math.max(0, 2 * ramp + cruise - t);
		return distance - 0.5 * accel * left * left;
	}

	// how far behind its timetable the lori of a line got on the run ahead of the train of a slot (stopping to mend
	// the line): now, and the most it has been. the train leaves only once the lori is in, so the most it was ever
	// behind holds the train up (never less once it was more, so a train that has left is never put back). kept
	// only while the server runs: after a restart every train and lori is back on its timetable
	private static final int HOLD_AT = DWELL - 41;
	private static final Map<Long, double[]> DELAYS = new HashMap<>();

	private static long delayKey(Chain chain, int line, long slot) {
		return (((chain.row ? 1L : 0L) * 31 + chain.fixed) * 1_000_003L + line) * 1_000_033L + slot;
	}

	private static double[] delays(Chain chain, int line, long slot) {
		synchronized (DELAYS) {
			return DELAYS.getOrDefault(delayKey(chain, line, slot), new double[2]);
		}
	}

	// how far behind the lori of a line is now, on the run it is on at `time` by its timetable
	public static double loriDelay(Chain chain, int line, double time) {
		if (line < chain.start || line >= chain.end) {
			return 0;
		}
		return delays(chain, line, run(chain, line, time))[0];
	}

	// the lori of a line is `delay` ticks behind on its run ahead of the train of `slot`
	public static void delayLori(Chain chain, int line, long slot, double delay) {
		synchronized (DELAYS) {
			long key = delayKey(chain, line, slot);
			double[] known = DELAYS.getOrDefault(key, new double[2]);
			if (DELAYS.size() > 4096) {
				DELAYS.clear();
			}
			DELAYS.put(key, new double[] {delay, Math.max(known[1], delay)});
		}
	}

	// which run the lori of line k is on at `time`: the one ahead of the train of this slot, or (once this slot's
	// train is in) already the one ahead of the next slot's
	private static long run(Chain chain, int k, double time) {
		long slot = slot(chain, time);
		return phase(chain, time) >= loriStart(chain, k, slot + 1, 1) + SLOT ? slot + 1 : slot;
	}

	// whether the lori of line k is in for the train of `slot`, going by `loriTime` (the real time less how far
	// behind the lori is): it has finished that run, or already set off on the next one
	public static boolean loriIn(Chain chain, int k, long slot, double loriTime) {
		long run = run(chain, k, loriTime);
		if (run != slot) {
			return run > slot;
		}
		double t = phase(chain, loriTime) + (slot(chain, loriTime) - slot) * (double) SLOT - loriStart(chain, k, slot, 1);
		return t >= timing(chain, k).loriRun();
	}

	// whether the line of k has been radioed clear for this slot's train
	public static boolean loriDone(Chain chain, int k, double time) {
		return loriIn(chain, k, slot(chain, time), time - loriDelay(chain, k, time));
	}

	// the station where the train of line k waits for the lori in `slot`
	public static Settlement waitingAt(Chain chain, int k, long slot) {
		boolean along = Math.floorMod(slot - k, 2) == 0;
		return chain.station(along ? k : k + 1);
	}

	// where the lori is (a distance along the chain, on the siding lane), how fast it goes and which way
	// (direction 0 while parked), and the slot of the train it runs ahead of
	public record Inspection(double along, double speed, double direction, long run) {
		public boolean moving() {
			return direction != 0;
		}
	}

	// the inspection trolley (lori inspeksi) of a line: every line has one, parked in the siding at one of its ends.
	// it shuttles, each run ahead of the next train over the line, and never turns: heading back it runs the other
	// way round, like a real two-way trolley
	public static @Nullable Inspection inspection(Chain chain, int k, double time) {
		if (k < chain.start || k >= chain.end) {
			return null;
		}
		long run = run(chain, k, time);
		boolean along = Math.floorMod(run - k, 2) == 0;
		Edge edge = chain.edge(k);
		double base = chain.offset(k);
		double from = base + edge.parking(along);
		double to = base + edge.parking(!along);
		double direction = Math.signum(to - from);
		double distance = Math.abs(to - from);
		double top = timing(chain, k).loriTop();
		double t = phase(chain, time) + (slot(chain, time) - run) * (double) SLOT - loriStart(chain, k, run, 1);
		if (t <= 0) {
			return new Inspection(from, 0, 0, run);
		}
		double s = ramped(t, distance, top, LORI_ACCEL);
		if (s >= distance) {
			return new Inspection(to, 0, 0, run);
		}
		double speed = ramped(t + 0.5, distance, top, LORI_ACCEL) - ramped(t - 0.5, distance, top, LORI_ACCEL);
		return new Inspection(from + direction * s, speed, direction, run);
	}

	// the middle of a car as a distance along the chain: the loco at the front, the coaches always in the same order
	// along the chain (so they never swap round when the train turns)
	public static double carAlong(State state, int car) {
		if (car == 0) {
			return state.center() + (state.along() ? LOCO_REACH : -LOCO_REACH);
		}
		return state.center() + COACHES_HALF - COACH / 2 - (car - 1) * (COACH + GAP);
	}

	// trains heading along run on the first track, trains heading back on the second
	public static int lane(boolean along) {
		return along ? 0 : 1;
	}

	// the track a car is on: the one it came in on while turning at a terminus and for the first half of the run out
	// of it (out there the station tracks have long merged into the line's own), its own one otherwise
	public static int laneOf(State state) {
		boolean arrived = state.terminus() && (state.standing() || state.progress() < 0.5);
		return lane(arrived != state.along());
	}

	// the loco running round its coaches at a terminus: distance along the chain, how far across it is from the
	// middle of the line, and how far round it has turned (0 facing the way it came in, 1 facing back out)
	public static double[] locoTurning(Chain chain, State state) {
		int tau = state.waited();
		// it came in heading the other way, the arm of the line lies behind it
		int in = state.along() ? -1 : 1;
		double node = chain.nodeAt(state.station());
		double coaches = (node - state.center()) * in;
		double start = coaches - LOCO_REACH;
		double coupled = coaches + LOCO_REACH;
		double v;
		boolean crossed = false;
		if (tau < UNCOUPLE) {
			v = start;
		} else if (tau < OUT_END) {
			v = ease(start, -SHUNT_AT, tau, UNCOUPLE, OUT_END);
		} else if (tau < BACK_START) {
			v = -SHUNT_AT;
		} else if (tau < BACK_END) {
			v = ease(-SHUNT_AT, MERGED, tau, BACK_START, BACK_END);
			crossed = true;
		} else if (tau < RETURN_START) {
			v = MERGED;
		} else {
			v = ease(MERGED, coupled, tau, RETURN_START, COUPLE);
		}
		double d = node - in * v;
		int arrivalLane = lane(!state.along());
		int otherLane = lane(state.along());
		double offset = chain.point(d, arrivalLane)[5];
		if (crossed && v > -CROSS_FAR) {
			double other = chain.point(d, otherLane)[5];
			offset = v >= -CROSS_NEAR ? other : Mth.lerp((-CROSS_NEAR - v) / (CROSS_FAR - CROSS_NEAR), other, offset);
		}
		return new double[] {d, offset, ease(0, 1, tau, TURN_START, TURN_END)};
	}

	// the turntable at the end of a chain: where its middle lies (x, z, height of the bed there) and the line's
	// direction (x, z). it stands on the track trains come in on, as far out as the loco pulls
	public static double[] turntable(Chain chain, boolean atStart) {
		double d = atStart ? chain.nodeAt(chain.start) - SHUNT_AT : chain.nodeAt(chain.end) + SHUNT_AT;
		// trains come in to the start heading back, to the end heading along
		double[] p = chain.point(d, lane(!atStart));
		return new double[] {p[0] - p[3] * p[5], p[1] + p[2] * p[5], p[4], p[2], p[3]};
	}

	private static double ease(double from, double to, int tau, int start, int end) {
		double t = Mth.clamp((tau - start) / (double) (end - start), 0, 1);
		return Mth.lerp(t * t * (3 - 2 * t), from, to);
	}

	private static final String[] NAMES = {"Senja Utama", "Bima", "Mutiara Selatan", "Fajar Utama", "Gaya Baru Malam", "Matarmaja", "Kertajaya",
			"Tawang Jaya", "Sawunggalih", "Logawa", "Sri Tanjung", "Kahuripan", "Gumarang", "Purbaya", "Cirebon Ekspres", "Mataram"};

	public static String name(Train train) {
		return NAMES[(int) Math.floorMod(train.chain() * 13 + train.id() * 7L, (long) NAMES.length)];
	}
}
