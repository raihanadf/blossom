package com.raihanadf.blossom.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// a table lamp: click to switch, it fades on and off softly. use any dye on it to recolour the shade
public class LampBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 4);
	public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 15, 12);
	private static final int FADE_TICKS = 2;

	public LampBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(GLOW, 0).setValue(COLOR, DyeColor.WHITE));
	}

	public static int lightLevel(BlockState state) {
		return state.getValue(GLOW) * 7 / 2;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		DyeColor dye = held.get(DataComponents.DYE);
		if (dye == null || dye == state.getValue(COLOR)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			level.setBlockAndUpdate(pos, state.setValue(COLOR, dye));
			held.consume(1, player);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			boolean lit = !state.getValue(LIT);
			level.setBlockAndUpdate(pos, state.setValue(LIT, lit));
			level.scheduleTick(pos, this, FADE_TICKS);
			level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, lit ? 1.4f : 1.1f);
		}
		return InteractionResult.SUCCESS;
	}

	// one glow step per tick until the lamp reaches fully on or fully off
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int glow = state.getValue(GLOW);
		int target = state.getValue(LIT) ? 4 : 0;
		if (glow != target) {
			level.setBlockAndUpdate(pos, state.setValue(GLOW, glow + Integer.signum(target - glow)));
			level.scheduleTick(pos, this, FADE_TICKS);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT, GLOW, COLOR);
	}
}
