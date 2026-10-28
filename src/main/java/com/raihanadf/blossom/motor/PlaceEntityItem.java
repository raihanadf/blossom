package com.raihanadf.blossom.motor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

// puts its entity down where you click, facing the way you look. used for the motor and the football
public class PlaceEntityItem extends Item {
	private final EntityType<?> type;

	public PlaceEntityItem(EntityType<?> type, Properties properties) {
		super(properties);
		this.type = type;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Entity entity = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (entity == null || !level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
			return InteractionResult.FAIL;
		}
		float yaw = context.getPlayer() == null ? 0 : context.getPlayer().getYRot();
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
		entity.setYHeadRot(yaw);
		ItemStack stack = context.getItemInHand();
		DyeColor color = stack.get(DataComponents.BASE_COLOR);
		if (entity instanceof Motor motor && color != null) {
			motor.setColor(color);
		}
		level.addFreshEntity(entity);
		level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
		stack.consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}
}
