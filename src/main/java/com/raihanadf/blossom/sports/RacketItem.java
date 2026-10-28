package com.raihanadf.blossom.sports;

import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// badminton: right click the racket to swing at any shuttlecock in front of you (forgiving on aim),
// right click a shuttlecock to toss it up for a serve
public class RacketItem extends Item {
	private final boolean shuttle;

	public RacketItem(Properties properties, boolean shuttle) {
		super(properties);
		this.shuttle = shuttle;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		if (shuttle) {
			Shuttlecock serve = BlossomEntities.SHUTTLECOCK.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
			Vec3 at = eye.add(look.multiply(0.8, 0, 0.8)).add(0, -0.3, 0);
			serve.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
			serve.setDeltaMovement(look.x * 0.1, 0.5, look.z * 0.1);
			serverLevel.addFreshEntity(serve);
			player.getItemInHand(hand).consume(1, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.5f, 1.8f);
			return InteractionResult.SUCCESS;
		}
		List<Shuttlecock> inReach = serverLevel.getEntitiesOfClass(Shuttlecock.class, player.getBoundingBox().inflate(4.5), cock -> {
			Vec3 to = cock.position().subtract(eye);
			return !cock.onGround() && to.length() < 4.5 && to.normalize().dot(look) > 0.35;
		});
		inReach.stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresentOrElse(
				cock -> cock.hit(serverLevel, player, true),
				() -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4f, 1.6f));
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 6);
		return InteractionResult.SUCCESS;
	}
}
