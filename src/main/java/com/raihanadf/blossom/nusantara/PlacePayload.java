package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.Blossom;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// where a player is in the nusantara world: a lang key ("" when out in the country) and the place's name, for the hud
public record PlacePayload(String key, String name) implements CustomPacketPayload {
	public static final Type<PlacePayload> TYPE = new Type<>(Blossom.id("place"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlacePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PlacePayload::key, ByteBufCodecs.STRING_UTF8, PlacePayload::name, PlacePayload::new);

	@Override
	public Type<PlacePayload> type() {
		return TYPE;
	}
}
