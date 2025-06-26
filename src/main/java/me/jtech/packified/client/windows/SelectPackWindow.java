package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
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
    public static ImBoolean open = new ImBoolean(false);
    private static boolean first = true;
    private static List<ResourcePackProfile> packs = new ArrayList<>();

    public static void render() {
        if (!open.get()) {
            return;
        }
        if (first) {
            packs = PackUtils.refresh();
            first = false;
        }

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getLastWindowCenterPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("Select Pack", open)) {
            for (ResourcePackProfile pack : packs) {
                if (ImGui.menuItem(pack.getDisplayName().getString())) {
                    EditorWindow.openFiles.clear();
                    if (PackifiedClient.currentPack != null) {
                        PackUtils.unloadPack(PackifiedClient.currentPack);
                    }
                    PackifiedClient.currentPack = pack;
                    PackUtils.loadPack(pack);
                    PackUtils.checkPackType(pack);
                    open.set(false);
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
