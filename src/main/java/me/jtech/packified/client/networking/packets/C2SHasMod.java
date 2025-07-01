package me.jtech.packified.client.networking.packets;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record C2SHasMod(String clientVersion) implements CustomPayload {
    public static final Id<C2SHasMod> ID = new Id<>(NetworkingConstants.C2S_HAS_MOD);
    public static final PacketCodec<RegistryByteBuf, C2SHasMod> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, C2SHasMod::clientVersion,
            C2SHasMod::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
