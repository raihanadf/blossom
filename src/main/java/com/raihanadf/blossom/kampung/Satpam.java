package com.raihanadf.blossom.kampung;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.damagesource.DamageSource;
import com.raihanadf.blossom.registry.BlossomItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// the satpam keeps the wanderer's hit-and-run brain, but never leaves his kampung.
public class Satpam extends Warga {
	public static final float SLASH = 30.0f;
	public static final int SLASH_TICKS = 14, DODGE_TICKS = 10, DASH_TICKS = 5, RUSH_TICKS = 40,
			EVADE_TICKS = 16, ESCAPE_TICKS = 8, ULT_COOLDOWN = 340, ULT_STRIKES = 4, ULT_GAP = 4,
			HORDE = 10, STUCK_TICKS = 200, PRESS_TICKS = 200, STAND_HITS = 3, STAND_HITS_COVERED = 5,
			STAND_WINDOW = 200, STAND_TICKS = 600, STAND_RUSH = 12, SWIPE_COOLDOWN = 240,
			SWIPE_RECAST = 40, SWIPE_WINDOW = 100;
	private static final double SIGHT = 20, REACH = 2.2, ULT_RANGE = 5.5, VIEW = 0.4226182617,
			SWIPE_PAST = 2.5, SWIPE_RANGE = 7, SWIPE_FAN = 0.5;
	private static final EntityDataAccessor<Boolean> DRAWN = SynchedEntityData.defineId(Satpam.class, EntityDataSerializers.BOOLEAN);
	private final List<Mob> foes = new ArrayList<>();
	private final Set<Mob> swipeCut = new HashSet<>();
	private final int[] hitTimes = {-1000, -1000, -1000, -1000, -1000};
	private BlockPos post, villageCenter;
	private int villageRadius = 24;
	private int slashCooldown, dodgeCooldown, dashTicks, rushCooldown, evadeCooldown, escapeCooldown,
			ultCooldown, ultStrikes, ultTimer, swipeCooldown, swipeAt, swipeRecast, triangleAt,
			lastHurt = -1000, lastCut = -1000, chasedSince, pressEnd, chainUntil, standUntil,
			hitNext, lastAttackerAt = -1000, calmTicks, repath, hideQuiet, lowCalm;
	private int awareness, inReach;
	private LivingEntity lastAttacker, standTarget;
	private Mob nearest, second, bloater;
	private Vec3 away = Vec3.ZERO, pendingDash, triangleDir = Vec3.ZERO, lastRetreat = Vec3.ZERO;
	private boolean hiding, swipeLeap, swipeReady;
	private double leapLeft;
	private Vec3 leapDir = Vec3.ZERO;
	private final List<Mob> eyes = new ArrayList<>();

	public Satpam(EntityType<? extends Satpam> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	public void settle(int job, int skin, BlockPos home, BlockPos bed, BlockPos work, BlockPos center, int radius) {
		super.settle(GUARD, SKIN_SATPAM, home, null, work, center, radius);
		this.post = work;
		this.villageCenter = center.immutable();
		this.villageRadius = radius;
		sheath();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DRAWN, false);
	}

	public boolean drawn() {
		return entityData.get(DRAWN);
	}

	public BlockPos post() {
		return post;
	}

	public boolean hiddenFrom(Mob looker) {
		return looker.getTarget() != this && !inView(looker, position());
	}

	private static boolean inView(Mob looker, Vec3 at) {
		Vec3 facing = Vec3.directionFromRotation(0, looker.getYHeadRot());
		Vec3 to = at.subtract(looker.position());
		double distance = to.horizontalDistance();
		return distance <= 0.01 || (to.x * facing.x + to.z * facing.z) / distance >= VIEW;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new LastStand());
		goalSelector.addGoal(2, new Hide());
		goalSelector.addGoal(3, new LieLow());
		goalSelector.addGoal(4, new Fight());
		goalSelector.addGoal(5, new Stalk());
		goalSelector.addGoal(6, new Avoid());
		goalSelector.addGoal(7, new Roam());
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		slashCooldown--; dodgeCooldown--; dashTicks--; rushCooldown--; evadeCooldown--; escapeCooldown--;
		ultCooldown--; swipeCooldown--;
		if (pendingDash != null) {
			dash(pendingDash, 1.3);
			pendingDash = null;
		}
		if (getTarget() == null || !getTarget().isAlive()) {
			if (++calmTicks > 60 && drawn() && ultStrikes == 0) sheath();
		} else {
			calmTicks = 0;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		scan();
		if (ultStrikes > 0) { snowfall(); return; }
		if (leapLeft > 0) { leap(); return; }
		if (dashTicks > 0 || pendingDash != null) return;
		if (swipeLeap) { leapOver(); return; }
		if (triangleAt > 0 && tickCount >= triangleAt) { triangleAt = 0; slashTriangle(); }
		if (swipeReady && tickCount - swipeAt > SWIPE_WINDOW) swipeReady = false;
		if (getHealth() < getMaxHealth() && tickCount - lastHurt > 80 && tickCount % 25 == 0 && (nearest == null || distanceTo(nearest) > 12)) heal(1);
		if (hazard(blockPosition()) && escapeCooldown <= 0) escape();
		else if (standTarget != null && cornered() && ultCooldown <= 0) ultimate();
		else if (creeperThreat()) { if (evadeCooldown <= 0 && !evade()) retreat(1.6); }
		else if (cornered() && ultCooldown <= 0) ultimate();
		else if (inReach >= 2 && evadeCooldown <= 0 && !evade() && ultCooldown <= 0) ultimate();
		else if (onGround() && !hiding && (awareness > 0 || standTarget != null) && !swipeReady && swipeCooldown <= 0) swipe();
	}

	private boolean villageThreat(Mob mob) {
		if (villageCenter == null) return true;
		if (mob.getTarget() == this) return true;
		// monsters inside the fence (or right outside it), not ones he can merely see from his post
		if (mob instanceof Monster && mob.distanceToSqr(Vec3.atCenterOf(villageCenter)) <= (villageRadius + 4) * (villageRadius + 4)) return true;
		for (Warga warga : level().getEntitiesOfClass(Warga.class, mob.getBoundingBox().inflate(1), w -> w != this && w.isAlive())) return true;
		for (Player player : level().players()) {
			if (player.isAlive() && player.distanceToSqr(Vec3.atCenterOf(villageCenter)) <= villageRadius * villageRadius && mob.getTarget() == player) return true;
		}
		return false;
	}

	private boolean foe(Mob mob) {
		return mob != this && mob.isAlive() && mob instanceof Monster && villageThreat(mob);
	}

	private boolean ranged(Mob mob) {
		return mob instanceof AbstractSkeleton || mob instanceof Pillager || mob instanceof Witch || mob instanceof Blaze;
	}

	private void scan() {
		foes.clear(); awareness = inReach = 0; nearest = second = bloater = null;
		double near = Double.MAX_VALUE, next = Double.MAX_VALUE, blast = Double.MAX_VALUE, ax = 0, az = 0;
		// he keeps an eye on what's around him and on his whole kampung, even from the post at its gate
		AABB watch = getBoundingBox().inflate(SIGHT);
		if (villageCenter != null) watch = watch.minmax(new AABB(villageCenter).inflate(villageRadius + 4, 12, villageRadius + 4));
		for (Mob mob : level().getEntitiesOfClass(Mob.class, watch, this::foe)) {
			foes.add(mob);
			double d = distanceTo(mob);
			// the wanderer stalks and waits, a satpam doesn't: anything that got inside his kampung gets dealt with now
			if (mob.getTarget() == this || villageCenter != null && mob.distanceToSqr(Vec3.atCenterOf(villageCenter)) <= villageRadius * villageRadius) awareness++;
			if (d < 12 && d > 0.01) { double weight = (mob instanceof Creeper ? 3 : 1) / Math.max(1, d); ax += (getX() - mob.getX()) / d * weight; az += (getZ() - mob.getZ()) / d * weight; }
			if (mob instanceof Creeper) { if (d < blast) { blast = d; bloater = mob; } continue; }
			if (d < mob.getBbWidth() / 2 + 1.9) inReach++;
			if (d < near) { next = near; second = nearest; near = d; nearest = mob; }
			else if (d < next) { next = d; second = mob; }
		}
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && distanceTo(target) < SIGHT) awareness++;
		if (awareness == 0) chasedSince = tickCount;
		double length = Math.sqrt(ax * ax + az * az);
		away = length > 0.0001 ? new Vec3(ax / length, 0, az / length) : Vec3.ZERO;
	}

	private List<Mob> eyes() {
		eyes.clear();
		for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32, 15, 32), this::ranged)) {
			if (mob.isAlive() && (mob.getTarget() == this || distanceTo(mob) < 24 && inView(mob, position())) && mob.hasLineOfSight(this)) eyes.add(mob);
		}
		return eyes;
	}

	private int alert(double range) {
		int count = 0;
		for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(range, 15, range), this::ranged)) {
			if (mob.isAlive() && (mob.getTarget() == this || mob.getLastHurtByMob() == this)) count++;
		}
		return count;
	}

	private boolean hazard(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		return state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)
				|| state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE);
	}

	private boolean clear(Vec3 direction, double distance) {
		if (direction.lengthSqr() < 0.01) return false;
		for (double step = 1; step <= distance; step++) {
			Vec3 at = position().add(direction.scale(step));
			if (!level().noCollision(this, getBoundingBox().move(direction.scale(step))) || hazard(BlockPos.containing(at)) || hazard(BlockPos.containing(at).above())) return false;
		}
		BlockPos land = BlockPos.containing(position().add(direction.scale(distance)));
		return level().getBlockState(land.below()).isFaceSturdy(level(), land.below(), Direction.UP) && !hazard(land);
	}

	private boolean cornered() {
		int close = 0, sides = 0;
		for (Mob mob : foes) {
			if (mob instanceof Creeper || distanceTo(mob) > 3.5) continue;
			close++;
			double angle = Math.atan2(mob.getZ() - getZ(), mob.getX() - getX());
			sides |= 1 << Math.floorMod((int)Math.floor((angle + Math.PI) / (Math.PI / 2)), 4);
		}
		return close >= 4 || close >= 3 && Integer.bitCount(sides) >= 3;
	}

	private boolean creeperThreat() {
		if (bloater == null) return false;
		Creeper creeper = (Creeper) bloater;
		return (creeper.getSwellDir() > 0 || creeper.getSwelling(1) > 0.65f) && distanceTo(creeper) < 4.0;
	}

	private boolean inBite(Mob mob, double margin) {
		return mob.getBoundingBox().inflate(0.83 + margin, 0, 0.83 + margin).intersects(getBoundingBox());
	}

	private boolean cuttable(LivingEntity target) {
		return target != null && !(target instanceof Creeper) && distanceTo(target) <= REACH + target.getBbWidth() / 2 && slashCooldown <= 0 && hasLineOfSight(target);
	}

	private void dash(Vec3 direction, double power) {
		getNavigation().stop();
		if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 10, .25, .5, .25, .02);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_SLIDE, getSoundSource(), 1, 1.2f);
		setDeltaMovement(direction.x * power, .12, direction.z * power); fallDistance = 0; dashTicks = DASH_TICKS;
	}

	private boolean evade() {
		Vec3 direction = bestWay(away, 4);
		if (direction == null) return false;
		evadeCooldown = EVADE_TICKS; dash(direction, 1.3); return true;
	}

	private Vec3 bestWay(Vec3 prefer, double distance) {
		Vec3 best = null; double score = -Double.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16; Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			if (!clear(direction, distance)) continue;
			double room = 12;
			for (Mob mob : foes) room = Math.min(room, distanceTo(mob) - (mob instanceof Creeper ? 4 : 0));
			double scoreHere = room + 3 * direction.dot(prefer);
			if (scoreHere > score) { score = scoreHere; best = direction; }
		}
		return best;
	}

	private void retreat(double speed) {
		if (tickCount % 8 != 0 && !getNavigation().isDone()) { getNavigation().setSpeedModifier(speed); return; }
		Vec3 direction = bestWay(away.add(lastRetreat), 8);
		if (direction != null) { lastRetreat = direction; getNavigation().moveTo(getX() + direction.x * 8, getY(), getZ() + direction.z * 8, speed); }
		else { Vec3 random = DefaultRandomPos.getPosAway(this, 10, 7, position().subtract(away.scale(4))); if (random != null) getNavigation().moveTo(random.x, random.y, random.z, speed); }
	}

	private void escape() {
		escapeCooldown = ESCAPE_TICKS;
		Vec3 direction = bestWay(away, 3);
		if (direction != null) { leapDir = direction; leapLeft = 3; dashTicks = 5; getNavigation().stop(); level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_JUMP, getSoundSource(), 1, 1.3f); }
	}

	private void leap() {
		double step = Math.min(.9, leapLeft); Vec3 move = leapDir.scale(step); leapLeft -= step;
		if (!level().noCollision(this, getBoundingBox().move(move))) { leapLeft = 0; return; }
		setPos(getX() + move.x, getY(), getZ() + move.z); setDeltaMovement(Vec3.ZERO); fallDistance = 0;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (ultStrikes > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
		if (source.is(DamageTypeTags.IS_PROJECTILE) && dashTicks <= 0 && dodgeCooldown <= 0) {
			Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity(); Vec3 side = dodgeSide(from);
			if (side != null) { pendingDash = side; dodgeCooldown = DODGE_TICKS; attacked(source); return false; }
		}
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) amount *= .39f;
		boolean hit = super.hurtServer(level, source, amount);
		if (hit) {
			lastHurt = tickCount; hitTimes[hitNext++ % hitTimes.length] = tickCount; attacked(source);
		}
		return hit;
	}

	private Vec3 dodgeSide(@Nullable Entity from) {
		Vec3 offset = from == null ? Vec3.directionFromRotation(0, getYRot()) : position().subtract(from.position());
		Vec3 side = new Vec3(-offset.z, 0, offset.x);
		if (side.lengthSqr() < .5) side = new Vec3(1, 0, 0); else side = side.normalize();
		return clear(side, 3) ? side : clear(side.reverse(), 3) ? side.reverse() : null;
	}

	private void attacked(DamageSource source) {
		Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (from instanceof LivingEntity living && living != this && living.isAlive()) {
			lastAttacker = living; lastAttackerAt = tickCount; setLastHurtByMob(living);
			if (recentHits() >= STAND_HITS && standTarget == null) { standTarget = living; standUntil = tickCount + STAND_TICKS; draw(); }
		}
	}

	private int recentHits() { int count = 0; for (int hit : hitTimes) if (tickCount - hit < STAND_WINDOW) count++; return count; }

	private boolean standing() {
		if (standTarget != null && standTarget.isAlive() && tickCount <= standUntil && distanceTo(standTarget) < 64) return true;
		if (lastAttacker != null && lastAttacker != standTarget && lastAttacker.isAlive() && tickCount - lastAttackerAt < 60 && tickCount <= standUntil) { standTarget = lastAttacker; return true; }
		standTarget = null; return false;
	}

	private void draw() {
		calmTicks = 0; entityData.set(DRAWN, true);
		if (getMainHandItem().isEmpty()) {
			ItemStack baton = getOffhandItem(); setItemSlot(EquipmentSlot.MAINHAND, baton.isEmpty() ? new ItemStack(BlossomItems.BATON) : baton); setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
	}

	private void sheath() {
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		entityData.set(DRAWN, false);
	}

	private boolean hit(LivingEntity target, float damage) {
		return level() instanceof ServerLevel server && target.hurtServer(server, damageSources().mobAttack(this), damage);
	}

	private void swingBaton() {
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
	}

	private void slash(LivingEntity target) {
		slashCooldown = SLASH_TICKS; draw(); swingBaton(); level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), .8f, 1.25f);
		if (hit(target, SLASH)) {
			lastCut = tickCount; target.knockback(.4, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 0);
			if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(), 1, 0, 0, 0, 0);
		}
	}

	private void backstab(LivingEntity target) {
		slash(target); chainUntil = tickCount + 30; slashCooldown = 6;
	}

	private boolean fighting() { return !hiding && awareness > 0 || standTarget != null; }

	private void swipe() {
		LivingEntity target = getTarget(); if (target == null || !target.isAlive() || target instanceof Creeper || !hasLineOfSight(target)) return;
		Vec3 direction = target.position().subtract(position()).multiply(1, 0, 1); double distance = direction.length();
		if (distance < 2 || distance > 5.5) return; direction = direction.normalize(); Vec3 landing = target.position().add(direction.scale(SWIPE_PAST));
		if (!clear(direction, distance + SWIPE_PAST) || !level().noCollision(this, getBoundingBox().move(landing.subtract(position())))) return;
		swipeCooldown = SWIPE_COOLDOWN; swipeAt = tickCount; swipeLeap = true; swipeFlown = 0; swipeCut.clear(); draw(); getNavigation().stop();
		setDeltaMovement(direction.x * 1.1, .8, direction.z * 1.1); fallDistance = 0;
	}
	private int swipeFlown;

	private void leapOver() {
		swipeFlown++; dashTicks = Math.max(dashTicks, 2); fallDistance = 0;
		for (Mob mob : foes) if (!(mob instanceof Creeper) && mob.isAlive() && !swipeCut.contains(mob) && Math.hypot(mob.getX() - getX(), mob.getZ() - getZ()) < mob.getBbWidth() / 2 + 1.3 && getY() - mob.getY() < mob.getBbHeight() + 2) { slash(mob); swipeCut.add(mob); }
		if (swipeFlown > 3 && onGround() || swipeFlown > 30) { swipeLeap = false; swipeReady = true; swipeRecast = swipeCut.isEmpty() ? swipeAt + SWIPE_RECAST : tickCount; slashCooldown = 0; }
	}

	private void triangle() {
		Vec3 direction = triangleAim(); if (direction == null) return; swipeReady = false; triangleDir = direction;
		if (clear(direction.reverse(), 3)) { dash(direction.reverse(), 1.3); triangleAt = tickCount + DASH_TICKS; } else slashTriangle();
	}
	private Vec3 triangleAim() {
		Vec3 best = null; int most = 0;
		for (Mob mob : foes) { Vec3 direction = mob.position().subtract(position()).multiply(1, 0, 1); if (mob instanceof Creeper || !mob.isAlive() || distanceTo(mob) > 4 || direction.lengthSqr() < .001) continue; direction = direction.normalize(); Vec3 from = position().subtract(direction.scale(3)); int count = 0; for (Mob other : foes) if (inFan(other, from, direction)) count++; if (count > most) { most = count; best = direction; } }
		return best;
	}
	private boolean inFan(Mob mob, Vec3 origin, Vec3 direction) {
		Vec3 to = mob.position().subtract(origin).multiply(1, 0, 1); double distance = to.length(); return !(mob instanceof Creeper) && mob.isAlive() && distance < SWIPE_RANGE + mob.getBbWidth() / 2 && Math.abs(mob.getY() - getY()) < 2.5 && (distance < .6 || to.dot(direction) / distance >= SWIPE_FAN) && hasLineOfSight(mob);
	}
	private void slashTriangle() { draw(); swingBaton(); for (Mob mob : new ArrayList<>(foes)) if (inFan(mob, position(), triangleDir)) slash(mob); triangleAt = 0; }

	private void ultimate() { ultCooldown = ULT_COOLDOWN; ultStrikes = ULT_STRIKES; ultTimer = 0; draw(); getNavigation().stop(); level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), getSoundSource(), 1, 1.4f); }
	private void snowfall() {
		if (ultTimer-- > 0) return; ultTimer = ULT_GAP; strike(); if (--ultStrikes == 0) { evadeCooldown = 0; evade(); }
	}
	private void strike() {
		Mob aim = null; int most = -1; for (Mob mob : foes) if (!(mob instanceof Creeper) && mob.isAlive() && distanceTo(mob) < ULT_RANGE) { int count = 0; for (Mob other : foes) if (other != mob && other.distanceTo(mob) < 3) count++; if (count > most) { most = count; aim = mob; } }
		Vec3 direction = aim == null ? Vec3.directionFromRotation(0, getYRot()) : aim.position().subtract(position()).multiply(1, 0, 1).normalize(); setYRot((float)(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90)); swingBaton(); int hits = 0;
		for (Mob mob : new ArrayList<>(foes)) if (inFan(mob, position(), direction) && distanceTo(mob) <= ULT_RANGE + mob.getBbWidth() / 2 && hit(mob, SLASH)) { hits++; mob.knockback(.9, getX() - mob.getX(), getZ() - mob.getZ(), damageSources().mobAttack(this), 0); }
		heal(hits); if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.SNOWFLAKE, getX() + direction.x * 2.5, getY() + 1.2, getZ() + direction.z * 2.5, 40, 1.6, .6, 1.6, .05);
	}

	private int nearbyCount(double range) { int count = 0; for (Mob mob : foes) if (!(mob instanceof Creeper) && distanceTo(mob) < range) count++; return count; }
	private boolean closeMelee() { for (Mob mob : foes) if (!(mob instanceof Creeper) && distanceTo(mob) < (mob.getTarget() == this && !ranged(mob) ? 6 : 3)) return true; return false; }
	private boolean pressing() { if (pressEnd > tickCount && getHealth() < 8) pressEnd = 0; else if (pressEnd <= tickCount && awareness > 0 && getHealth() >= 8 && tickCount - Math.max(lastCut, chasedSince) > STUCK_TICKS) pressEnd = tickCount + PRESS_TICKS; return pressEnd > tickCount; }

	private abstract class CombatGoal extends Goal {
		CombatGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
		@Override public boolean requiresUpdateEveryTick() { return true; }
	}
	private final class LastStand extends CombatGoal {
		@Override public boolean canUse() { return standing(); }
		@Override public boolean canContinueToUse() { return standing(); }
		@Override public void start() { draw(); }
		@Override public void tick() { if (standTarget == null || ultStrikes > 0 || dashTicks > 0) return; setTarget(standTarget); getLookControl().setLookAt(standTarget, 30, 30); double d = distanceTo(standTarget); if (d <= REACH + standTarget.getBbWidth() / 2 && slashCooldown <= 0) slash(standTarget); else if (d > 4 && rushCooldown <= 0) { Vec3 in = standTarget.position().subtract(position()).multiply(1, 0, 1).normalize(); if (clear(in, Math.min(4, d - 2))) { rushCooldown = STAND_RUSH; dash(in, 1.6); } } else getNavigation().moveTo(standTarget, 1.5); }
	}
	private final class Fight extends CombatGoal {
		@Override public boolean canUse() { scan(); return tickCount >= chainUntil && (awareness > 0 || bloater != null && distanceTo(bloater) < 16); }
		@Override public boolean canContinueToUse() { return canUse(); }
		@Override public void start() { draw(); }
		@Override public void tick() { scan(); if (ultStrikes > 0 || dashTicks > 0 || creeperThreat()) return; LivingEntity target = nearest; if (getTarget() instanceof LivingEntity attacked && attacked.isAlive() && (target == null || distanceTo(attacked) < distanceTo(target))) target = attacked; if (target == null) { if (bloater != null) retreat(1.4); return; } setTarget(target); getLookControl().setLookAt(target, 30, 30); double d = distanceTo(target); boolean horde = awareness >= HORDE || getHealth() < 8; if (horde) { if (cuttable(target) && (second == null || distanceTo(second) > 2.6)) slash(target); retreat(d < 3.4 ? 1.5 : 1.1); return; } if (cuttable(target)) { slash(target); retreat(1.3); return; } if (d > 5 && d < 14 && rushCooldown <= 0) { Vec3 in = target.position().subtract(position()).multiply(1, 0, 1).normalize(); if (clear(in, Math.min(4, d - 2))) { rushCooldown = RUSH_TICKS; dash(in, 1.6); return; } } if (d > REACH - .3) getNavigation().moveTo(target, 1.4); else if (d < 1.9) retreat(1.3); }
	}
	private final class Stalk extends CombatGoal {
		private Mob prey;
		@Override public boolean canUse() { scan(); if (awareness > 0 || getHealth() < 6 || nearbyCount(16) >= HORDE || !eyes().isEmpty()) return false; prey = null; double best = 64; for (Mob mob : foes) if (!(mob instanceof Creeper) && mob.getTarget() != Satpam.this && distanceTo(mob) < best) { best = distanceTo(mob); prey = mob; } return prey != null; }
		@Override public boolean canContinueToUse() { return prey != null && prey.isAlive() && prey.getTarget() != Satpam.this && distanceTo(prey) < 72; }
		@Override public void start() { hiding = true; }
		@Override public void stop() { hiding = false; getNavigation().stop(); }
		@Override public void tick() { Vec3 facing = Vec3.directionFromRotation(0, prey.getYHeadRot()); Vec3 to = position().subtract(prey.position()); double d = to.horizontalDistance(); boolean behind = d > .01 && (to.x * facing.x + to.z * facing.z) / d < -.34; getLookControl().setLookAt(prey, 30, 30); if (behind && d <= 2.6 && slashCooldown <= 0) { backstab(prey); return; } if (behind && d > 2.6 && d < 6 && slashCooldown <= 0) { Vec3 in = prey.position().subtract(position()).multiply(1, 0, 1).normalize(); if (clear(in, d - 1.5)) { rushCooldown = RUSH_TICKS; dash(in, 1.6); return; } } if (--repath <= 0) { repath = 5; Vec3 side = new Vec3(-facing.z, 0, facing.x); if (side.dot(to) < 0) side = side.reverse(); Vec3 goal = d < 9 ? prey.position().add(side.scale(5)).subtract(facing.scale(1.5)) : prey.position().subtract(facing.scale(1.8)); getNavigation().moveTo(goal.x, prey.getY(), goal.z, 1.15); } }
	}
	private final class Hide extends CombatGoal {
		@Override public boolean canUse() { scan(); return !closeMelee() && !eyes().isEmpty() && tickCount >= chainUntil; }
		@Override public boolean canContinueToUse() { return !closeMelee() && hideQuiet < 10; }
		@Override public void start() { hiding = true; hideQuiet = 0; }
		@Override public void stop() { hiding = false; getNavigation().stop(); }
		@Override public void tick() { List<Mob> lookers = eyes(); if (lookers.isEmpty()) hideQuiet++; else hideQuiet = 0; if (lookers.isEmpty()) { getNavigation().stop(); return; } Mob threat = lookers.getFirst(); getLookControl().setLookAt(threat); Vec3 away = position().subtract(threat.position()).multiply(1, 0, 1).normalize(); Vec3 cover = position().add(away.scale(8)); if (clear(away, 3)) getNavigation().moveTo(cover.x, getY(), cover.z, 1.2); }
	}
	private final class LieLow extends CombatGoal {
		@Override public boolean canUse() { scan(); return eyes().isEmpty() && !closeMelee() && alert(32) >= 2; }
		@Override public boolean canContinueToUse() { lowCalm = alert(48) >= 2 ? 0 : lowCalm + 1; return eyes().isEmpty() && !closeMelee() && lowCalm < 40; }
		@Override public void start() { lowCalm = 0; hiding = true; }
		@Override public void stop() { hiding = false; getNavigation().stop(); }
		@Override public void tick() { double x = 0, z = 0; int count = 0; for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), Satpam.this::ranged)) if (mob.getTarget() == Satpam.this) { x += mob.getX(); z += mob.getZ(); count++; } if (count > 0) { Vec3 center = new Vec3(x / count, getY(), z / count); Vec3 away = position().subtract(center).multiply(1, 0, 1).normalize(); if (position().distanceTo(center) < 40) getNavigation().moveTo(getX() + away.x * 8, getY(), getZ() + away.z * 8, 1.1); else getNavigation().stop(); } }
	}
	private final class Avoid extends CombatGoal {
		@Override public boolean canUse() { scan(); return awareness == 0 && nearbyCount(16) >= HORDE; }
		@Override public void tick() { retreat(1.0); }
		@Override public void stop() { getNavigation().stop(); }
	}
	private final class Roam extends Goal {
		Roam() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
		@Override public boolean canUse() { scan(); return awareness == 0 && getTarget() == null && getRandom().nextInt(40) == 0; }
		@Override public boolean canContinueToUse() { return !getNavigation().isDone() && awareness == 0; }
		@Override public void start() { repath = 0; }
		@Override public void tick() { if (post != null && level().isDarkOutside()) { if (distanceToSqr(Vec3.atBottomCenterOf(post)) > 4) getNavigation().moveTo(post.getX() + .5, post.getY(), post.getZ() + .5, 1); else getNavigation().stop(); return; } if (--repath <= 0 || getNavigation().isDone()) { repath = 80; Vec3 at = villageCenter == null ? DefaultRandomPos.getPos(Satpam.this, 16, 7) : DefaultRandomPos.getPosTowards(Satpam.this, villageRadius, 7, Vec3.atCenterOf(villageCenter), 1); if (at != null && villageCenter != null && at.distanceTo(Vec3.atCenterOf(villageCenter)) <= villageRadius + 8) getNavigation().moveTo(at.x, at.y, at.z, 1); } }
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output); output.storeNullable("SatpamPost", BlockPos.CODEC, post); output.storeNullable("SatpamCenter", BlockPos.CODEC, villageCenter); output.putInt("SatpamRadius", villageRadius);
	}
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input); post = input.read("SatpamPost", BlockPos.CODEC).orElse(null); villageCenter = input.read("SatpamCenter", BlockPos.CODEC).orElse(null); villageRadius = input.getIntOr("SatpamRadius", 24);
	}

	@Override public boolean removeWhenFarAway(double distance) { return false; }
}
