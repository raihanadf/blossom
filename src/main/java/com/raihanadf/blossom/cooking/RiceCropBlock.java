package com.raihanadf.blossom.cooking;

import com.raihanadf.blossom.registry.BlossomBlocks;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

// padi: planted on farmland like wheat, the grains are both the seed and the harvest
public class RiceCropBlock extends CropBlock {
	public RiceCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return BlossomBlocks.RICE;
	}
}
