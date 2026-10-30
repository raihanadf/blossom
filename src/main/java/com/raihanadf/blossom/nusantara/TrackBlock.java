package com.raihanadf.blossom.nusantara;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrackBlock extends BaseEntityBlock {
	private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 2, 16);
	public TrackBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrackBlockEntity(pos, state);
	}

	@Override
	public boolean isPossibleToRespawnInThis(BlockState state) {
		return true;
	}
}
