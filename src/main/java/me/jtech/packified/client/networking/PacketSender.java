package me.jtech.packified.client.networking;

import me.jtech.packified.client.windows.LogWindow;
import me.jtech.packified.client.networking.packets.C2SSendFullPack;
import me.jtech.packified.client.networking.packets.C2SSyncPackChanges;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketSender {
    private static final ConcurrentLinkedQueue<CustomPayload> packetQueue = new ConcurrentLinkedQueue<>();

    private static final int PPT = 1;

    public static void queuePacket(CustomPayload packet) {
        packetQueue.add(packet);
    }

    public static void processQueue() {
        for (int i = 0; i < PPT && !packetQueue.isEmpty(); i++) {
            CustomPayload packet = packetQueue.poll();
            if (packet != null) {
                sendPacket(packet, i);
            }

            if (packetQueue.isEmpty()) {
                LogWindow.addLog("All packets sent successfully.", LogWindow.LogType.INFO.getColor());
            }
        }
    }

    private static void sendPacket(CustomPayload packet, int i) {
        ClientPlayNetworking.send(packet);
        LogWindow.addLog("Packet sent: " + packet.getId().toString(), LogWindow.LogType.INFO.getColor());
        if (packet instanceof C2SSendFullPack fullPackPacket) {
            LogWindow.addLog(i + " / " + fullPackPacket.packetData().packetAmount() + " packets sent.", LogWindow.LogType.INFO.getColor());
        }
        if (packet instanceof C2SSyncPackChanges packChangesPacket) {
            LogWindow.addLog(i + " / " + packChangesPacket.packetData().packetAmount() + " packets sent.", LogWindow.LogType.INFO.getColor());
        }
    }
}
