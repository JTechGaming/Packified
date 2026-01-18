package me.jtech.packified;

import me.jtech.packified.client.networking.packets.*;
import me.jtech.packified.client.util.SyncPacketData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Packified implements ModInitializer {
    public static final String MOD_ID = "packified";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String version = "1.2+d289";

    public static List<UUID> moddedPlayers = new ArrayList<>();

    public static boolean debugMode = false;

    public static final int MAX_PACKET_SIZE = 32767 / 4; // 32KB - 1KB for overhead

    public static Identifier identifier(String name) {
        return Identifier.of(MOD_ID, name);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Packified");
        PayloadTypeRegistry.playS2C().register(S2CSyncPackChanges.ID, S2CSyncPackChanges.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CRequestFullPack.ID, S2CRequestFullPack.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CSendFullPack.ID, S2CSendFullPack.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CPlayerHasMod.ID, S2CPlayerHasMod.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CInfoPacket.ID, S2CInfoPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(C2SRequestFullPack.ID, C2SRequestFullPack.CODEC);
        PayloadTypeRegistry.playC2S().register(C2SSendFullPack.ID, C2SSendFullPack.CODEC);
        PayloadTypeRegistry.playC2S().register(C2SSyncPackChanges.ID, C2SSyncPackChanges.CODEC);
        PayloadTypeRegistry.playC2S().register(C2SHasMod.ID, C2SHasMod.CODEC);
        PayloadTypeRegistry.playC2S().register(C2SInfoPacket.ID, C2SInfoPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(C2SSyncPackChanges.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (payload.markedPlayers().getFirst().getLeastSignificantBits() == 0 && payload.markedPlayers().getFirst().getMostSignificantBits() == 0) {
                    // Store the pack on the server
                    //TODO Store the pack on the server
                    return;
                }
                // Send the pack changes to all players
                List<ServerPlayerEntity> players = context.server().getPlayerManager().getPlayerList();
                for (ServerPlayerEntity player : players) {
                    if (payload.markedPlayers().contains(player.getUuid())) {
                        ServerPlayNetworking.send(context.player(), new S2CRequestFullPack(payload.packetData().packName(), player.getUuid()));
                        continue;
                    }
                    ServerPlayNetworking.send(player, new S2CSyncPackChanges(payload.packetData(), context.player().getUuid()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2SSendFullPack.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Send the full pack to all players
                ServerPlayerEntity player = context.server().getPlayerManager().getPlayer(payload.player());
                if (player == null) {
                    return;
                }
                LOGGER.info(payload.packetData().lastData() ? "Last data chunk received" : "Receiving full pack from player: {}", player.getUuid());
                ServerPlayNetworking.send(player, new S2CSendFullPack(payload.packetData()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2SRequestFullPack.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Request the full pack from the owning player and send it to the requesting player
                ServerPlayerEntity player = context.server().getPlayerManager().getPlayer(payload.player());
                if (player == null) {
                    return;
                }
                ServerPlayNetworking.send(player, new S2CRequestFullPack(payload.packName(), context.player().getUuid()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2SHasMod.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Returns if the player has the mod installed
                LOGGER.info("Player {} has the mod installed: {}", context.player().getUuid(), moddedPlayers.contains(context.player().getUuid()));
                moddedPlayers.add(context.player().getUuid());
                for (UUID player : moddedPlayers) {
                    ServerPlayerEntity serverPlayer = context.server().getPlayerManager().getPlayer(player);
                    if (serverPlayer == null) {
                        continue;
                    }
                    ServerPlayNetworking.send(serverPlayer, new S2CPlayerHasMod(moddedPlayers, context.player().getUuid()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2SInfoPacket.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Send the info packet to all players
                List<ServerPlayerEntity> players = context.server().getPlayerManager().getPlayerList();
                for (ServerPlayerEntity player : players) {
                    if (player.getUuid().equals(context.player().getUuid())) {
                        continue;
                    }
                    ServerPlayNetworking.send(player, new S2CInfoPacket(payload.info(), payload.player()));
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, listener) -> {
            // Remove the player from the list of modded players
            moddedPlayers.remove(handler.player.getUuid());
            for (UUID player : moddedPlayers) {
                ServerPlayerEntity serverPlayer = listener.getPlayerManager().getPlayer(player);
                if (serverPlayer == null) {
                    continue;
                }
                ServerPlayNetworking.send(serverPlayer, new S2CPlayerHasMod(moddedPlayers, handler.player.getUuid()));
            }
        });
    }
}
