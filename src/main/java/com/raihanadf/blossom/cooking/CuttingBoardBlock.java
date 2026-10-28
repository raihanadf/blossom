package com.raihanadf.blossom.cooking;

import com.raihanadf.blossom.registry.BlossomItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// put an ingredient on it, tap it with a kitchen knife a few times to chop it
public class CuttingBoardBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(1, 0, 2, 15, 1, 14);

	public CuttingBoardBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean empty = board.getItem().isEmpty();
		boolean place = empty && KitchenRecipes.BOARD.containsKey(held.getItem());
		boolean chop = !empty && held.is(BlossomItems.KITCHEN_KNIFE);
		if (!place && !chop) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel serverLevel) {
			if (place) {
				board.place(player, held);
			} else {
				board.chop(serverLevel, player, hand);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board) || board.getItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			board.takeBack(player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CuttingBoardBlockEntity(pos, state);
	}
}
