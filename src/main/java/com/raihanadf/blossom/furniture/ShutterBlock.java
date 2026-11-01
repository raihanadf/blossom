package com.raihanadf.blossom.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// a thin louvred panel mounted against the wall behind its facing direction
public class ShutterBlock extends Block {
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public ShutterBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape(state.getValue(FACING));
	}

	private static VoxelShape shape(Direction facing) {
		return switch (facing) {
			case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
			case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
			case EAST -> Block.box(0, 0, 0, 2, 16, 16);
			case WEST -> Block.box(14, 0, 0, 16, 16, 16);
			default -> Shapes.empty();
		};
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
