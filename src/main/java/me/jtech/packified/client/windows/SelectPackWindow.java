package me.jtech.packified.client.windows;

import imgui.ImGui;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.packets.C2SInfoPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class SelectPackWindow {
    public static boolean open = false;
    private static boolean first = true;
    private static List<ResourcePackProfile> packs = new ArrayList<>();

    public static void render() {
        if (!open) {
            return;
        }
        if (first) {
            packs = PackUtils.refresh();
            first = false;
        }
        if (ImGui.begin("Select Pack")) {
            for (ResourcePackProfile pack : packs) {
                if (ImGui.menuItem(pack.getDisplayName().getString())) {
                    EditorWindow.openFiles.clear();
                    if (PackifiedClient.currentPack != null) {
                        PackUtils.unloadPack(PackifiedClient.currentPack);
                    }
                    PackifiedClient.currentPack = pack;
                    PackUtils.loadPack(pack);
                    PackUtils.checkPackType(pack);
                    open = false;
                    first = true;
                    if (PackifiedClient.currentPack != null) {
                        ClientPlayNetworking.send(new C2SInfoPacket(PackifiedClient.currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                    }
                }
            }
        }
        ImGui.end();
    }
}
