package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomItems;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// the gates in the station fence. FACING points into the station. the ticket gate (pintu masuk) lets in whoever
// carries a karcis and takes it as they pass, and sells one for an iron ingot (right click it holding the iron); the
// exit gate (pintu keluar) only opens for someone coming out. kampung folk always have their ticket ready, so they go
// both ways. the arm swings up while someone allowed through stands in the gate and drops behind them
public class StationGate extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	// a barrier too high to jump over
	private static final VoxelShape CLOSED = Block.box(0, 0, 0, 16, 24, 16);
	private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 16, 16);
	private static final int CLOSE_AFTER = 10;
	// which side of the middle of the gate each player stood on last time they were in one (the server's previous
	// position of a player isn't reliable for this), so the karcis is taken once, as they step across
	private static final Map<Entity, Double> LAST = new WeakHashMap<>();
	private final boolean exit;

	public StationGate(BlockBehaviour.Properties properties, boolean exit) {
		super(properties);
		this.exit = exit;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null && passes(state, pos, entityContext.getEntity())) {
			return Shapes.empty();
		}
		return CLOSED;
	}

	private boolean passes(BlockState state, BlockPos pos, Entity entity) {
		if (entity instanceof Warga || entity instanceof Player player && player.isCreative()) {
			return true;
		}
		if (!(entity instanceof Player player)) {
			return false;
		}
		if (exit) {
			// coming out: standing on the station's side of the gate
			return inward(state, pos, entity.getX(), entity.getZ()) > -0.05;
		}
		// someone already through (or turning back) is never shut in
		return inward(state, pos, entity.getX(), entity.getZ()) >= 0 || player.getInventory().contains(new ItemStack(BlossomItems.TICKET));
	}

	// how far a spot lies into the station from the middle of the gate
	private static double inward(BlockState state, BlockPos pos, double x, double z) {
		Direction in = state.getValue(FACING);
		return (x - pos.getX() - 0.5) * in.getStepX() + (z - pos.getZ() - 0.5) * in.getStepZ();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (level.isClientSide() || !passes(state, pos, entity)) {
			return;
		}
		// the arm swings up for whoever is let through, and stays up while someone stands in the gate
		if (!state.getValue(OPEN)) {
			level.setBlock(pos, state.setValue(OPEN, true), 3);
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.3f);
		}
		level.scheduleTick(pos, this, CLOSE_AFTER);
		// the karcis is taken as a player steps through the ticket gate into the station
		if (exit || !(entity instanceof ServerPlayer player) || player.isCreative()) {
			return;
		}
		double now = inward(state, pos, player.getX(), player.getZ());
		Double last = LAST.put(player, now);
		if (last != null && last < 0 && now >= 0) {
			int slot = player.getInventory().findSlotMatchingItem(new ItemStack(BlossomItems.TICKET));
			if (slot >= 0) {
				player.getInventory().removeItem(slot, 1);
				level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.4f);
				player.sendOverlayMessage(Component.translatable("message.blossom.gate.welcome"));
			}
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPEN)) {
			return;
		}
		if (!level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0.1, 0, 0.1), entity -> passes(state, pos, entity)).isEmpty()) {
			level.scheduleTick(pos, this, CLOSE_AFTER);
			return;
		}
		level.setBlock(pos, state.setValue(OPEN, false), 3);
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.3f);
	}

	// the ticket gate is its own ticket machine: an iron ingot in, a karcis out
	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (exit) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!held.is(Items.IRON_INGOT)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable(player.getInventory().contains(new ItemStack(BlossomItems.TICKET))
						? "message.blossom.gate.go" : "message.blossom.gate.need"));
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			held.consume(1, player);
			ItemStack ticket = new ItemStack(BlossomItems.TICKET);
			if (!player.getInventory().add(ticket)) {
				Block.popResource(level, player.blockPosition(), ticket);
			}
			level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.2f);
			player.sendOverlayMessage(Component.translatable("message.blossom.gate.bought"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}
}
