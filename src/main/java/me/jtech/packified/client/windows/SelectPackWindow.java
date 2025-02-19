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
        for (int i = 0; i < PackUtils.refresh().size(); i++) {
            if (ImGui.menuItem(PackUtils.refresh().get(i).getDisplayName().getString())) {
                PackifiedClient.currentPack = PackUtils.refresh().get(i);
                open = false;
            }
        }
    }
}
