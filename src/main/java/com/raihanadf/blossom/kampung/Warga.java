package com.raihanadf.blossom.kampung;

import com.raihanadf.blossom.cooking.CookingPotBlockEntity;
import com.raihanadf.blossom.cooking.KitchenRecipes;
import com.raihanadf.blossom.furniture.SeatBlock;
import com.raihanadf.blossom.furniture.SeatEntity;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomItems;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// a kampung resident: farms, cooks, chats, naps on the porch, sleeps at night. the satpam guards the gate instead
public class Warga extends PathfinderMob {
	public static final int RESIDENT = 0;
	public static final int FARMER = 1;
	public static final int COOK = 2;
	public static final int GUARD = 3;

	// skins 0-2 are men, 3-5 women, 6 is the satpam uniform
	public static final int SKINS = 7;
	private static final List<String> MEN = List.of("Budi", "Agus", "Joko", "Bambang", "Slamet", "Andi", "Rahmat", "Wahyu", "Hasan", "Ilham");
	private static final List<String> WOMEN = List.of("Siti", "Sri", "Dewi", "Wati", "Ani", "Rini", "Ayu", "Fitri", "Nur", "Tuti");

	private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(Warga.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Warga.class, EntityDataSerializers.INT);

	private BlockPos home;
	private BlockPos bed;
	private BlockPos work;
	// what the cook has ready to share, the count is the number of servings
	private ItemStack servings = ItemStack.EMPTY;
	private boolean playerWasNear;

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

	private boolean isGuard() {
		return getJob() == GUARD;
	}

	// called once by the village when it moves in
	public void settle(int job, int skin, BlockPos home, BlockPos bed, BlockPos work, BlockPos center, int radius) {
		entityData.set(JOB, job);
		entityData.set(SKIN, skin);
		this.home = home;
		this.bed = bed;
		this.work = work;
		// everyone stays inside the fence; the satpam's work goal keeps pulling him back to the post
		setHomeTo(center, radius);
		List<String> names = skin < 3 || skin == 6 ? MEN : WOMEN;
		setCustomName(Component.literal((skin < 3 || skin == 6 ? "Pak " : "Bu ") + names.get(getRandom().nextInt(names.size()))));
		Item tool = switch (job) {
			case FARMER -> Items.IRON_HOE;
			case COOK -> BlossomItems.KITCHEN_KNIFE;
			case GUARD -> BlossomItems.BATON;
			default -> Items.AIR;
		};
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
		setDropChance(EquipmentSlot.MAINHAND, 0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		// spawned on its own (egg, command): a resident of wherever it stands
		settle(RESIDENT, getRandom().nextInt(6), blockPosition(), null, null, blockPosition(), 16);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(JOB, RESIDENT);
		builder.define(SKIN, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
			@Override
			public boolean canUse() {
				return isGuard() && super.canUse();
			}
		});
		goalSelector.addGoal(1, new PanicGoal(this, 1.3) {
			@Override
			public boolean canUse() {
				return !isGuard() && super.canUse();
			}
		});
		goalSelector.addGoal(2, new SleepGoal());
		goalSelector.addGoal(3, new WorkGoal());
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
				return isGuard() && super.canUse();
			}
		});
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true) {
			@Override
			public boolean canUse() {
				return isGuard() && super.canUse();
			}
		});
	}

	private void wave() {
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() || tickCount % 10 != 0) {
			return;
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
		if (!(level() instanceof ServerLevel) || isSleeping()) {
			return isSleeping() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		getLookControl().setLookAt(player);
		wave();
		if (getJob() == COOK && !servings.isEmpty()) {
			ItemStack dish = servings.split(1);
			Component name = dish.getHoverName();
			if (!player.getInventory().add(dish)) {
				Block.popResource(level(), player.blockPosition(), dish);
			}
			say(player, Component.translatable("message.blossom.warga.cook_gift", name));
			((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.5, getZ(), 2, 0.2, 0.1, 0.2, 0);
		} else {
			String job = List.of("resident", "farmer", "cook", "guard").get(getJob());
			say(player, Component.translatable("message.blossom.warga." + job + "." + getRandom().nextInt(3)));
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
	}

	private boolean busyElsewhere() {
		return isSleeping() || isPassenger() || getTarget() != null;
	}

	// at night everyone but the satpam walks home and gets into bed
	private class SleepGoal extends Goal {
		SleepGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return !isGuard() && bed != null && level().isDarkOutside() && !isPassenger();
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
			if (distanceToSqr(Vec3.atBottomCenterOf(bed)) > 4) {
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

	// the day job: the farmer tends the field, the cook keeps the pot going, the satpam keeps watch at the gate
	private class WorkGoal extends Goal {
		private BlockPos target;
		private int cooldown;

		WorkGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return getJob() != RESIDENT && work != null && (isGuard() || !level().isDarkOutside()) && !busyElsewhere()
					&& (isGuard() || getRandom().nextInt(20) == 0);
		}

		@Override
		public boolean canContinueToUse() {
			return work != null && (isGuard() || !level().isDarkOutside()) && !busyElsewhere() && (isGuard() || getRandom().nextInt(1200) != 0);
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
			switch (getJob()) {
				case FARMER -> farm();
				case COOK -> cook();
				default -> watchGate();
			}
		}

		private boolean walkTo(BlockPos pos, double reach) {
			if (distanceToSqr(Vec3.atBottomCenterOf(pos)) > reach * reach) {
				if (getNavigation().isDone()) {
					getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8);
				}
				return false;
			}
			getNavigation().stop();
			getLookControl().setLookAt(Vec3.atCenterOf(pos));
			return true;
		}

		private void watchGate() {
			if (target == null || getRandom().nextInt(300) == 0) {
				// mostly stand at the post, sometimes stroll a few steps along the fence and back
				target = getRandom().nextInt(3) == 0 ? work.offset(getRandom().nextInt(7) - 3, 0, getRandom().nextInt(7) - 3) : work;
			}
			walkTo(target, 1.5);
		}

		private void farm() {
			if (target == null) {
				if (cooldown > 0) {
					walkTo(work, 3);
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
			if (!walkTo(target, 2)) {
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
			if (!walkTo(work, 2.2) || !(level().getBlockEntity(work) instanceof CookingPotBlockEntity pot)) {
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

	// takes a seat on a nearby chair for a while: on the porch by day, the satpam at the post by night
	private class SitGoal extends Goal {
		private BlockPos seat;
		private int ticks;

		SitGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			BlockPos around = isGuard() ? work : home;
			if (around == null || busyElsewhere() || getRandom().nextInt(isGuard() ? 400 : 250) != 0 || (!isGuard() && level().isDarkOutside())) {
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
			return ticks > 0 && getTarget() == null && hurtTime == 0 && (isGuard() || !level().isDarkOutside());
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
			if (isPassenger()) {
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
			if (isGuard() || busyElsewhere() || level().isDarkOutside() || getRandom().nextInt(300) != 0) {
				return false;
			}
			List<Warga> near = level().getEntitiesOfClass(Warga.class, getBoundingBox().inflate(12),
					other -> other != Warga.this && !other.isGuard() && !other.isSleeping());
			partner = near.isEmpty() ? null : near.get(getRandom().nextInt(near.size()));
			return partner != null;
		}

		@Override
		public void start() {
			ticks = 120 + getRandom().nextInt(160);
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && partner.isAlive() && !partner.isSleeping() && hurtTime == 0;
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
