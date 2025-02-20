package me.jtech.packified.client.windows;

import imgui.ImGui;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.packets.C2SInfoPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class SelectPackWindow {
    public static boolean open = false;

    public static void render() {
        if (!open) {
            return;
        }
        if (ImGui.begin("Select Pack")) {
            for (int i = 0; i < PackUtils.refresh().size(); i++) {
                if (ImGui.menuItem(PackUtils.refresh().get(i).getDisplayName().getString())) {
                    EditorWindow.openFiles.clear();
                    PackifiedClient.currentPack = PackUtils.refresh().get(i);
                    PackUtils.checkPackType(PackUtils.refresh().get(i));
                    open = false;
                    if (PackifiedClient.currentPack != null) {
                        ClientPlayNetworking.send(new C2SInfoPacket(PackifiedClient.currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                    }
                }
            }
        }
        ImGui.end();
    }
}
