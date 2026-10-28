package com.raihanadf.blossom.client;

import com.raihanadf.blossom.motor.Motor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

// the engine: a low rumble while someone sits on it, rising in pitch and volume with speed
public class MotorSound extends AbstractTickableSoundInstance {
	private final Motor motor;

	public MotorSound(Motor motor) {
		super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.motor = motor;
		this.looping = true;
		this.delay = 0;
		this.volume = 0;
		this.x = motor.getX();
		this.y = motor.getY();
		this.z = motor.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (motor.isRemoved()) {
			stop();
			return;
		}
		x = motor.getX();
		y = motor.getY();
		z = motor.getZ();
		float speed = (float) motor.getSpeed();
		boolean running = motor.isVehicle();
		volume = running ? 0.25f + Mth.clamp(speed * 1.5f, 0, 0.5f) : 0;
		pitch = 0.55f + Mth.clamp(speed * 2.2f, 0, 1.1f);
	}
}
