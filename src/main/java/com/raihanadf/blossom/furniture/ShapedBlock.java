package com.raihanadf.blossom.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// furniture that just sits there with a custom outline, like the table
public class ShapedBlock extends Block {
	private final VoxelShape shape;

	public ShapedBlock(Properties properties, VoxelShape shape) {
		super(properties);
		this.shape = shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}
}
