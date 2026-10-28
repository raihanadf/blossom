package com.raihanadf.blossom.cooking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// home cooked food: finishing a bowl makes you a little happy
public class DishItem extends Item {
	public DishItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getEyeY() + 0.4, entity.getZ(), 3, 0.3, 0.15, 0.3, 0);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
