package me.jtech.packified;

import me.jtech.packified.client.windows.LogWindow;
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
                sendPacket(packet, i);
            }

            if (packetQueue.isEmpty()) {
                LogWindow.addLog("All packets sent successfully.", LogWindow.LogType.INFO.getColor());
            }
        }
    }

    private static void sendPacket(C2SSendFullPack packet, int i) {
        ClientPlayNetworking.send(packet);
        LogWindow.addLog("Packet sent: " + packet.getId().toString(), LogWindow.LogType.INFO.getColor());
        LogWindow.addLog(i + " / " + packet.packetData().packetAmount() + " packets sent.", LogWindow.LogType.INFO.getColor());
    }
}
