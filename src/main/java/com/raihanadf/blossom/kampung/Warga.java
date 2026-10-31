package com.raihanadf.blossom.kampung;

import com.raihanadf.blossom.cooking.CookingPotBlockEntity;
import com.raihanadf.blossom.cooking.KitchenRecipes;
import com.raihanadf.blossom.furniture.SeatBlock;
import com.raihanadf.blossom.furniture.SeatEntity;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomItems;
import com.raihanadf.blossom.sports.Football;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// kampung and station folk: residents chat, sit on porches and sleep at night, farmers tend the sawah, the cook keeps
// the pot going, kids play football, the satpam guards the gate, the station master sends trains off, passengers wait
// on the platforms and ride the trains, and the people who got off wander into the kampung before heading back
public class Warga extends PathfinderMob {
	public static final int RESIDENT = 0;
	public static final int FARMER = 1;
	public static final int COOK = 2;
	public static final int GUARD = 3;
	public static final int KID = 4;
	public static final int PPKA = 5;
	public static final int PASSENGER = 6;
	public static final int VENDOR = 7;
	public static final int TRAVELER = 8;
	// rides the inspection trolley (lori inspeksi) ahead of the trains
	public static final int INSPECTOR = 9;
	private static final List<String> JOBS = List.of("resident", "farmer", "cook", "guard", "kid", "ppka", "passenger", "vendor", "traveler", "inspector");

	// skin numbers: 12 men, 12 women, 4 boys, 4 girls, then the uniforms (textures/entity/warga/<skinName>.png)
	public static final int SKINS_PER_GROUP = 12;
	public static final int SKIN_MEN = 0;
	public static final int SKIN_WOMEN = 12;
	public static final int SKIN_BOYS = 24;
	public static final int SKIN_GIRLS = 28;
	public static final int SKIN_SATPAM = 32;
	public static final int SKIN_PPKA = 33;
	public static final int SKIN_INSPEKTUR = 34;

	private static final List<String> MEN = List.of("Budi", "Agus", "Joko", "Bambang", "Slamet", "Andi", "Rahmat", "Wahyu", "Hasan", "Ilham",
			"Darto", "Sugeng", "Yanto", "Herman", "Tono", "Karjo");
	private static final List<String> WOMEN = List.of("Siti", "Sri", "Dewi", "Wati", "Ani", "Rini", "Ayu", "Fitri", "Nur", "Tuti",
			"Sumi", "Lastri", "Yati", "Endang", "Ningsih", "Murni");
	private static final List<String> KIDS = List.of("Adi", "Dimas", "Bayu", "Eko", "Putri", "Indah", "Rina", "Lia", "Tole", "Ucok", "Nunung", "Dodi");
	private static final List<Item> SNACKS = List.of(BlossomItems.SOTO_AYAM, BlossomItems.MIE_GORENG, BlossomItems.NASI_GORENG, BlossomItems.BERRY_COCOA,
			BlossomItems.APPLE_SLICES);

	private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(Warga.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Warga.class, EntityDataSerializers.INT);

	private BlockPos home;
	private BlockPos bed;
	private BlockPos work;
	// what the cook has ready to share, the count is the number of servings
	private ItemStack servings = ItemStack.EMPTY;
	private boolean playerWasNear;
	// riding a train as a passenger the train brought along: such riders are never saved and leave with the train
	private boolean aboard;
	// how long a traveller still strolls about before going back to the platform
	private int travelTicks;
	// waypoints walked in order before the usual work: through the station gates, over the walkway between the tracks
	private final List<BlockPos> route = new ArrayList<>();
	// the way back to the platform, for when a traveller's time in the kampung is up
	private final List<BlockPos> returnRoute = new ArrayList<>();
	private int giftCooldown;

	public Warga(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	public int getJob() {
		return entityData.get(JOB);
	}

	public int getSkin() {
		return entityData.get(SKIN);
	}

	public static boolean isKidSkin(int skin) {
		return skin >= SKIN_BOYS && skin < SKIN_SATPAM;
	}

	public static String skinName(int skin) {
		if (skin < SKIN_WOMEN) {
			return "man_" + skin;
		} else if (skin < SKIN_BOYS) {
			return "woman_" + (skin - SKIN_WOMEN);
		} else if (skin < SKIN_SATPAM) {
			return "kid_" + (skin - SKIN_BOYS);
		}
		return switch (skin) {
			case SKIN_SATPAM -> "satpam";
			case SKIN_PPKA -> "ppka";
			default -> "inspektur";
		};
	}

	public static int randomSkin(RandomSource random) {
		return random.nextInt(SKINS_PER_GROUP * 2);
	}

	private static boolean isMale(int skin) {
		return skin < SKIN_WOMEN || (skin >= SKIN_BOYS && skin < SKIN_GIRLS) || skin >= SKIN_SATPAM;
	}

	private boolean isGuard() {
		return getJob() == GUARD;
	}

	public boolean isAboard() {
		return aboard;
	}

	// called once by the village or station when it moves in
	public void settle(int job, int skin, BlockPos home, BlockPos bed, BlockPos work, BlockPos center, int radius) {
		entityData.set(JOB, job);
		entityData.set(SKIN, skin);
		this.home = home;
		this.bed = bed;
		this.work = work;
		setHomeTo(center, radius);
		setCustomName(Component.literal(nameFor(skin)));
		holdTool();
	}

	private String nameFor(int skin) {
		if (isKidSkin(skin)) {
			return KIDS.get(getRandom().nextInt(KIDS.size()));
		}
		List<String> names = isMale(skin) ? MEN : WOMEN;
		return (isMale(skin) ? "Pak " : "Bu ") + names.get(getRandom().nextInt(names.size()));
	}

	// what each of them carries: tools for work, food for the seller, and for most people nothing at all
	private void holdTool() {
		RandomSource random = getRandom();
		Item tool = switch (getJob()) {
			case FARMER -> Items.IRON_HOE;
			case COOK -> BlossomItems.KITCHEN_KNIFE;
			case GUARD -> BlossomItems.BATON;
			case INSPECTOR -> Items.IRON_PICKAXE;
			case VENDOR -> SNACKS.get(random.nextInt(SNACKS.size()));
			// a ticket in hand
			case PASSENGER, TRAVELER -> random.nextInt(3) == 0 ? Items.PAPER : Items.AIR;
			case KID -> random.nextInt(4) == 0 ? Items.APPLE : Items.AIR;
			case RESIDENT -> random.nextInt(4) != 0 ? Items.AIR : List.of(Items.BREAD, Items.POPPY, Items.PAPER).get(random.nextInt(3));
			default -> Items.AIR;
		};
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
		setDropChance(EquipmentSlot.MAINHAND, 0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		// spawned on its own (egg, command): a resident of wherever it stands
		settle(RESIDENT, randomSkin(getRandom()), blockPosition(), null, null, blockPosition(), 16);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(JOB, RESIDENT);
		builder.define(SKIN, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (SKIN.equals(accessor)) {
			refreshDimensions();
		}
	}

	// kids are half size
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		EntityDimensions dimensions = super.getDefaultDimensions(pose);
		return isKidSkin(getSkin()) ? dimensions.scale(0.55f) : dimensions;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.3) {
			@Override
			public boolean canUse() {
				return !isGuard() && super.canUse();
			}
		});
		goalSelector.addGoal(2, new SleepGoal());
		goalSelector.addGoal(3, new WorkGoal());
		goalSelector.addGoal(3, new PlayGoal());
		goalSelector.addGoal(4, new SitGoal());
		goalSelector.addGoal(5, new ChatGoal());
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Warga.class, 6.0f));
		goalSelector.addGoal(10, new RandomLookAroundGoal(this));

		targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return !isGuard() && super.canUse();
			}
		});
	}

	public void wave() {
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	// the station master raises his paddle and blows the whistle: the train may go
	public void whistle() {
		wave();
		level().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.6f, 2f);
		level().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.6f, 1.9f);
	}

	// kampung folk keep off the railway: a track can only be crossed where planks are laid between the rails (the
	// walkway between the platforms, level crossings)
	@Override
	protected PathNavigation createNavigation(Level level) {
		return new GroundPathNavigation(this, level) {
			@Override
			protected PathFinder createPathFinder(int maxVisitedNodes) {
				nodeEvaluator = new WalkNodeEvaluator() {
					@Override
					public PathType getPathType(PathfindingContext context, int x, int y, int z) {
						// inspectors are the one lot that walk the track
						if (getJob() != INSPECTOR && onTrack(context.level(), new BlockPos(x, y, z))) {
							return PathType.BLOCKED;
						}
						return super.getPathType(context, x, y, z);
					}
				};
				nodeEvaluator.setCanPassDoors(true);
				return new PathFinder(nodeEvaluator, maxVisitedNodes);
			}
		};
	}

	// a spot on the railway: on a track, or on the ballast right beside one (planks, a walkway or a level crossing,
	// are the way over)
	private static boolean onTrack(BlockGetter level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		// on a climb the ballast is a layer the walker stands in, or on
		boolean ballast = below.is(Blocks.GRAVEL) || below.is(BlossomBlocks.BALLAST) || level.getBlockState(pos).is(BlossomBlocks.BALLAST);
		if (below.is(BlockTags.PLANKS) || !(ballast || level.getBlockState(pos).is(BlossomBlocks.TRACK))) {
			return false;
		}
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 0, 2))) {
			if (level.getBlockState(near).is(BlossomBlocks.TRACK)) {
				return true;
			}
		}
		return false;
	}

	// a passenger the train brought along, or one boarding from the platform
	public void board(Entity car) {
		aboard = true;
		getNavigation().stop();
		startRiding(car, true, false);
	}

	// an inspector climbs down from the lori to mend the line: its own goals stand aside, the lori walks it about
	public void stepDown() {
		stopRiding();
		aboard = false;
		goalSelector.disableControlFlag(Goal.Flag.MOVE);
		goalSelector.disableControlFlag(Goal.Flag.LOOK);
		goalSelector.disableControlFlag(Goal.Flag.JUMP);
	}

	// and climbs back on when the work is done
	public void climbBack(Entity lori) {
		goalSelector.enableControlFlag(Goal.Flag.MOVE);
		goalSelector.enableControlFlag(Goal.Flag.LOOK);
		goalSelector.enableControlFlag(Goal.Flag.JUMP);
		board(lori);
	}

	// gets off at a station and heads for the kampung for a while (out through the exit gate, `out`), then comes back
	// to wait for another train (in through the ticket gate, `back`)
	public void alight(Vec3 at, BlockPos platform, BlockPos destination, BlockPos center, int radius, List<BlockPos> out, List<BlockPos> back) {
		stopRiding();
		aboard = false;
		snapTo(at.x, at.y, at.z, getYRot(), 0);
		entityData.set(JOB, TRAVELER);
		home = platform;
		work = destination;
		route.clear();
		route.addAll(out);
		returnRoute.clear();
		returnRoute.addAll(back);
		travelTicks = 1800 + getRandom().nextInt(2400);
		setHomeTo(center, radius);
		setPersistenceRequired();
	}

	// a waiting passenger steps up to the door of the train that just came in
	public void waitAt(BlockPos spot) {
		work = spot;
	}

	@Override
	public boolean shouldBeSaved() {
		// inspectors belong to their lori, which is never saved either
		return !aboard && getJob() != INSPECTOR && super.shouldBeSaved();
	}

	@Override
	public void tick() {
		super.tick();
		// the train that carried this rider is gone
		if (aboard && !isPassenger() && !level().isClientSide()) {
			discard();
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() || tickCount % 10 != 0 || aboard) {
			return;
		}
		if (giftCooldown > 0) {
			giftCooldown -= 10;
		}
		if (getJob() == TRAVELER && (travelTicks -= 10) <= 0) {
			// time to go home: back to the platform to wait for the next train
			entityData.set(JOB, PASSENGER);
			work = home;
			route.clear();
			route.addAll(returnRoute);
		}
		// a little wave when someone walks up
		Player player = level().getNearestPlayer(this, 5);
		boolean near = player != null && !isSleeping();
		if (near && !playerWasNear) {
			getLookControl().setLookAt(player);
			wave();
		}
		playerWasNear = near;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(level() instanceof ServerLevel level) || isSleeping()) {
			return isSleeping() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		getLookControl().setLookAt(player);
		wave();
		ItemStack gift = ItemStack.EMPTY;
		// the station master sells tickets: one iron ingot a karcis
		if (getJob() == PPKA) {
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.IRON_INGOT)) {
				say(player, Component.translatable("message.blossom.ticket.price"));
				return InteractionResult.SUCCESS;
			}
			held.consume(1, player);
			ItemStack ticket = new ItemStack(BlossomItems.TICKET);
			if (!player.getInventory().add(ticket)) {
				Block.popResource(level, player.blockPosition(), ticket);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.2f);
			say(player, Component.translatable("message.blossom.ticket.sold"));
			return InteractionResult.SUCCESS;
		}
		if (getJob() == COOK && !servings.isEmpty()) {
			gift = servings.split(1);
		} else if (getJob() == VENDOR && giftCooldown <= 0) {
			gift = new ItemStack(getMainHandItem().getItem());
			giftCooldown = 1200;
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SNACKS.get(getRandom().nextInt(SNACKS.size()))));
		}
		if (!gift.isEmpty()) {
			Component name = gift.getHoverName();
			if (!player.getInventory().add(gift)) {
				Block.popResource(level, player.blockPosition(), gift);
			}
			say(player, Component.translatable("message.blossom.warga." + JOBS.get(getJob()) + "_gift", name));
			level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.5, getZ(), 2, 0.2, 0.1, 0.2, 0);
		} else {
			say(player, Component.translatable("message.blossom.warga." + JOBS.get(getJob()) + "." + getRandom().nextInt(3)));
		}
		return InteractionResult.SUCCESS;
	}

	private void say(Player player, Component line) {
		player.sendOverlayMessage(Component.translatable("message.blossom.warga.says", getName(), line));
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Job", getJob());
		output.putInt("Skin", getSkin());
		output.storeNullable("HomeSpot", BlockPos.CODEC, home);
		output.storeNullable("Bed", BlockPos.CODEC, bed);
		output.storeNullable("Work", BlockPos.CODEC, work);
		output.store("Servings", ItemStack.OPTIONAL_CODEC, servings);
		output.putInt("TravelTicks", travelTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(JOB, input.getIntOr("Job", RESIDENT));
		entityData.set(SKIN, input.getIntOr("Skin", 0));
		home = input.read("HomeSpot", BlockPos.CODEC).orElse(null);
		bed = input.read("Bed", BlockPos.CODEC).orElse(null);
		work = input.read("Work", BlockPos.CODEC).orElse(null);
		servings = input.read("Servings", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		travelTicks = input.getIntOr("TravelTicks", 0);
	}

	private boolean busyElsewhere() {
		return isSleeping() || isPassenger() || getTarget() != null;
	}

	private boolean walkTo(BlockPos pos, double reach, double speed) {
		if (distanceToSqr(Vec3.atBottomCenterOf(pos)) > reach * reach) {
			if (getNavigation().isDone()) {
				getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
			}
			return false;
		}
		getNavigation().stop();
		getLookControl().setLookAt(Vec3.atCenterOf(pos));
		return true;
	}

	// at night everyone (but those on duty) walks home and gets into bed, kids without a bed just stay indoors
	private class SleepGoal extends Goal {
		SleepGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			int job = getJob();
			boolean onDuty = job == GUARD || job == PPKA || job == PASSENGER || job == TRAVELER || job == VENDOR || job == INSPECTOR;
			return !onDuty && (bed != null || home != null) && level().isDarkOutside() && !isPassenger();
		}

		@Override
		public boolean canContinueToUse() {
			return level().isDarkOutside() && hurtTime == 0;
		}

		@Override
		public void tick() {
			if (isSleeping()) {
				return;
			}
			if (bed == null) {
				walkTo(home, 1.5, 0.8);
			} else if (distanceToSqr(Vec3.atBottomCenterOf(bed)) > 4) {
				getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.8);
			} else if (level().getBlockState(bed).getBlock() instanceof BedBlock && !level().getBlockState(bed).getValue(BedBlock.OCCUPIED)) {
				startSleeping(bed);
			}
		}

		@Override
		public void stop() {
			if (isSleeping()) {
				stopSleeping();
			}
		}
	}

	// the day job. the satpam, station master and seller keep to their post, passengers pace the platform, the farmer
	// tends the field, the cook keeps the pot going, travellers stroll over to the kampung
	private class WorkGoal extends Goal {
		private BlockPos target;
		private int cooldown;

		WorkGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private boolean always() {
			int job = getJob();
			return job == GUARD || job == PPKA || job == PASSENGER || job == VENDOR || job == TRAVELER || job == INSPECTOR;
		}

		@Override
		public boolean canUse() {
			int job = getJob();
			return job != RESIDENT && job != KID && work != null && (always() || !level().isDarkOutside()) && !busyElsewhere()
					&& (always() || getRandom().nextInt(20) == 0);
		}

		@Override
		public boolean canContinueToUse() {
			return work != null && (always() || !level().isDarkOutside()) && !busyElsewhere() && (always() || getRandom().nextInt(1200) != 0);
		}

		@Override
		public void stop() {
			target = null;
			getNavigation().stop();
		}

		@Override
		public void tick() {
			if (cooldown > 0) {
				cooldown--;
			}
			if (!route.isEmpty()) {
				if (walkTo(route.getFirst(), 1.2, 0.7)) {
					route.removeFirst();
				}
				return;
			}
			switch (getJob()) {
				case FARMER -> farm();
				case COOK -> cook();
				case PASSENGER -> pace(5, 400, 0.5);
				case TRAVELER -> pace(10, 200, 0.6);
				default -> pace(3, 300, 0.8);
			}
		}

		// mostly stand at the spot, now and then wander a few steps around it and back
		private void pace(int spread, int every, double speed) {
			if (target == null || getRandom().nextInt(every) == 0) {
				target = getRandom().nextInt(3) == 0 ? work.offset(getRandom().nextInt(spread * 2 + 1) - spread, 0, getRandom().nextInt(spread * 2 + 1) - spread) : work;
				// never a stroll down onto the railway
				if (onTrack(level(), target) || onTrack(level(), target.below())) {
					target = work;
				}
			}
			walkTo(target, 1.5, speed);
		}

		private void farm() {
			if (target == null) {
				if (cooldown > 0) {
					walkTo(work, 3, 0.8);
					return;
				}
				List<BlockPos> chores = new ArrayList<>();
				for (BlockPos pos : BlockPos.betweenClosed(work.offset(-6, -1, -6), work.offset(6, 1, 6))) {
					BlockState state = level().getBlockState(pos);
					boolean ripe = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
					boolean bare = state.getBlock() instanceof FarmlandBlock && level().getBlockState(pos.above()).isAir();
					if (ripe || bare) {
						chores.add(pos.immutable());
					}
				}
				if (chores.isEmpty()) {
					cooldown = 200;
					return;
				}
				target = chores.get(getRandom().nextInt(chores.size()));
			}
			if (!walkTo(target, 2, 0.8)) {
				return;
			}
			ServerLevel level = (ServerLevel) level();
			BlockState state = level.getBlockState(target);
			wave();
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				// harvest and replant the same crop
				level.destroyBlock(target, false, Warga.this);
				level.setBlockAndUpdate(target, crop.getStateForAge(0));
			} else if (state.getBlock() instanceof FarmlandBlock && level.getBlockState(target.above()).isAir()) {
				Block seed = getRandom().nextInt(3) == 0 ? Blocks.CARROTS : BlossomBlocks.RICE_CROP;
				level.setBlockAndUpdate(target.above(), seed.defaultBlockState());
				level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 1f, 1f);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 0.8, target.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
			target = null;
			cooldown = 60 + getRandom().nextInt(100);
		}

		private void cook() {
			if (!walkTo(work, 2.2, 0.8) || !(level().getBlockEntity(work) instanceof CookingPotBlockEntity pot)) {
				return;
			}
			if (!pot.getResult().isEmpty()) {
				ItemStack cooked = pot.takeResult();
				if (servings.isEmpty() || !ItemStack.isSameItem(servings, cooked)) {
					servings = cooked;
				} else {
					servings.grow(cooked.getCount());
				}
				servings.setCount(Math.min(servings.getCount(), 6));
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(servings.getItem()));
				wave();
				cooldown = 400;
			} else if (!pot.hasIngredients() && cooldown == 0 && servings.getCount() < 6) {
				List<KitchenRecipes.PotRecipe> menu = KitchenRecipes.POT;
				pot.fill(menu.get(getRandom().nextInt(menu.size())).ingredients());
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BlossomItems.KITCHEN_KNIFE));
				wave();
			} else if (pot.hasIngredients() && getRandom().nextInt(40) == 0) {
				// stirring
				wave();
			}
		}
	}

	// kids play: football when there's a ball around, otherwise tag with the other kids
	private class PlayGoal extends Goal {
		private Entity chasing;
		private int ticks;

		PlayGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (getJob() != KID || busyElsewhere() || level().isDarkOutside() || getRandom().nextInt(40) != 0) {
				return false;
			}
			List<Football> balls = level().getEntitiesOfClass(Football.class, getBoundingBox().inflate(20));
			if (!balls.isEmpty()) {
				chasing = balls.getFirst();
			} else {
				List<Warga> kids = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(12), other -> other != Warga.this && other.getJob() == KID);
				chasing = kids.isEmpty() ? null : kids.get(getRandom().nextInt(kids.size()));
			}
			return chasing != null;
		}

		@Override
		public void start() {
			ticks = 200 + getRandom().nextInt(300);
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && chasing.isAlive() && hurtTime == 0 && !level().isDarkOutside() && distanceToSqr(chasing) < 30 * 30;
		}

		@Override
		public void tick() {
			ticks--;
			getLookControl().setLookAt(chasing);
			if (tickCount % 5 == 0) {
				getNavigation().moveTo(chasing, 1.1);
			}
			if (chasing instanceof Football ball && distanceToSqr(ball) < 1.6) {
				// kick it towards another kid, or back into the village if nobody else is playing
				List<Warga> kids = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(14), other -> other != Warga.this && other.getJob() == KID);
				Vec3 aim = kids.isEmpty() ? Vec3.atCenterOf(home) : kids.get(getRandom().nextInt(kids.size())).position();
				ball.kick(Warga.this, aim.subtract(ball.position()), 0.45 + getRandom().nextDouble() * 0.3);
				wave();
			} else if (chasing instanceof Warga other && distanceToSqr(other) < 2) {
				// tag, you're it
				wave();
				chasing = null;
				ticks = 0;
			}
		}

		@Override
		public void stop() {
			chasing = null;
			getNavigation().stop();
		}
	}

	// takes a seat on a nearby chair for a while: on the porch by day, at the post or on a platform bench when on duty
	private class SitGoal extends Goal {
		private BlockPos seat;
		private int ticks;

		SitGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		private boolean onDuty() {
			return isGuard() || getJob() == PASSENGER;
		}

		@Override
		public boolean canUse() {
			BlockPos around = onDuty() ? work : home;
			if (around == null || getJob() == KID || busyElsewhere() || getRandom().nextInt(onDuty() ? 300 : 250) != 0
					|| (!onDuty() && level().isDarkOutside())) {
				return false;
			}
			for (BlockPos pos : BlockPos.betweenClosed(around.offset(-6, -2, -6), around.offset(6, 2, 6))) {
				if (level().getBlockState(pos).getBlock() instanceof SeatBlock && !SeatEntity.isTaken(level(), pos)) {
					seat = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			ticks = 300 + getRandom().nextInt(600);
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && getTarget() == null && hurtTime == 0 && (onDuty() || !level().isDarkOutside()) && !aboard;
		}

		@Override
		public void tick() {
			ticks--;
			if (isPassenger()) {
				return;
			}
			if (distanceToSqr(Vec3.atBottomCenterOf(seat)) > 2.5) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, 0.7);
				}
			} else {
				BlockState state = level().getBlockState(seat);
				if (!(state.getBlock() instanceof SeatBlock chair)
						|| !SeatEntity.sit((ServerLevel) level(), seat, chair.getSeatHeight(), state.getValue(SeatBlock.FACING).toYRot(), Warga.this)) {
					ticks = 0;
				}
			}
		}

		@Override
		public void stop() {
			if (isPassenger() && !aboard) {
				stopRiding();
			}
		}
	}

	// walks up to a neighbour for a chat: both face each other and talk with their hands
	private class ChatGoal extends Goal {
		private Warga partner;
		private int ticks;

		ChatGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (isGuard() || getJob() == KID || busyElsewhere() || level().isDarkOutside() || getRandom().nextInt(300) != 0) {
				return false;
			}
			List<Warga> near = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(12),
					other -> other != Warga.this && !other.isGuard() && !other.isSleeping() && !other.isPassenger() && other.getJob() != KID);
			partner = near.isEmpty() ? null : near.get(getRandom().nextInt(near.size()));
			return partner != null;
		}

		@Override
		public void start() {
			ticks = 120 + getRandom().nextInt(160);
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && partner.isAlive() && !partner.isSleeping() && hurtTime == 0 && !isPassenger();
		}

		@Override
		public void tick() {
			ticks--;
			if (distanceToSqr(partner) > 6) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(partner, 0.7);
				}
				return;
			}
			getNavigation().stop();
			getLookControl().setLookAt(partner);
			partner.getLookControl().setLookAt(Warga.this);
			if (ticks % 40 == 0) {
				wave();
			} else if (ticks % 40 == 20) {
				partner.wave();
			}
		}

		@Override
		public void stop() {
			partner = null;
		}
	}
}
