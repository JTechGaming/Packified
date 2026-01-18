package me.jtech.packified.client.windows.popups;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.EditorWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class PackBrowserWindow {
    public static ImBoolean open = new ImBoolean(false);
    private static boolean first = true;
    private static List<ResourcePackProfile> packs = new ArrayList<>();

    public static void render() {
        if (!open.get()) {
            first = true;
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

                MinecraftClient.getInstance().resourceManager.findResources("pack/" + pack.getId(), path -> {
                    System.out.println(path);
                    return false;
                });

                if (ImGui.menuItem(pack.getDisplayName().getString())) {
                    PackHelper.updateCurrentPack(pack);
                    open.set(false);
                    first = true;
                }
            }
        }
        ImGui.end();
    }
}
