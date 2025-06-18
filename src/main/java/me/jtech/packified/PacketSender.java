package me.jtech.packified;

import me.jtech.packified.packets.C2SSendFullPack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketSender {
    private static final ConcurrentLinkedQueue<C2SSendFullPack> packetQueue = new ConcurrentLinkedQueue<>();

    private static final int PPT = 1;

    public static void queuePacket(C2SSendFullPack packet) {
        packetQueue.add(packet);
    }

    public static void processQueue() {
        for (int i = 0; i < PPT && !packetQueue.isEmpty(); i++) {
            C2SSendFullPack packet = packetQueue.poll();
            if (packet != null) {
                sendPacket(packet);
            }
        }
    }

    private static void sendPacket(CustomPayload packet) {
        ClientPlayNetworking.send(packet);
    }
}
