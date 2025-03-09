package me.jtech.packified.packets;

import me.jtech.packified.CustomCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

public record S2CPlayerHasMod(List<UUID> moddedPlayers, UUID specificPlayer) implements CustomPayload {
    public static final Id<S2CPlayerHasMod> ID = new Id<>(NetworkingConstants.S2C_PLAYER_HAS_MOD);
    public static final PacketCodec<RegistryByteBuf, S2CPlayerHasMod> CODEC = PacketCodec.tuple(
            CustomCodecs.UuidCodecs.PACKET_CODEC, S2CPlayerHasMod::moddedPlayers,
            Uuids.PACKET_CODEC, S2CPlayerHasMod::specificPlayer,
            S2CPlayerHasMod::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
