package com.raihanadf.blossom.client;

import com.raihanadf.blossom.nusantara.TrainCar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

// a rolling rumble under every car, louder and higher as the train picks up speed, and the clack of the rail joints
public class TrainSound extends AbstractTickableSoundInstance {
	private final TrainCar car;
	private double sinceJoint;

	public TrainSound(TrainCar car) {
		super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.car = car;
		this.looping = true;
		this.delay = 0;
		this.volume = 0;
		this.x = car.getX();
		this.y = car.getY();
		this.z = car.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (car.isRemoved()) {
			stop();
			return;
		}
		x = car.getX();
		y = car.getY() + 1;
		z = car.getZ();
		float speed = (float) car.speed();
		volume = Mth.clamp(speed * 0.9f, 0, 0.7f);
		pitch = 0.4f + Mth.clamp(speed * 0.5f, 0, 0.5f);
		// every rail length the wheels knock over a joint: ta-tak
		sinceJoint += speed;
		if (sinceJoint > 9 && speed > 0.05f) {
			sinceJoint = 0;
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null) {
				minecraft.level.playLocalSound(x, car.getY(), z, SoundEvents.CHAIN_STEP, SoundSource.NEUTRAL, 0.5f, 0.6f, false);
				minecraft.level.playLocalSound(x, car.getY(), z, SoundEvents.STONE_STEP, SoundSource.NEUTRAL, 0.35f, 0.5f, false);
			}
		}
	}
}
