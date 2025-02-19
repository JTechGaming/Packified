package me.jtech.packified.client.windows;

import imgui.ImGui;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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
                }
            }
        }
        ImGui.end();
    }
}
