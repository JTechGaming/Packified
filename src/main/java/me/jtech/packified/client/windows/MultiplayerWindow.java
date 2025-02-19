package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import me.jtech.packified.Packified;
import me.jtech.packified.SyncPacketData;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackFile;
import me.jtech.packified.packets.C2SRequestFullPack;
import me.jtech.packified.packets.C2SSyncPackChanges;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;

public class MultiplayerWindow {
    public static void render() {
        // Render the multiplayer window
        if (ImGui.begin("Multiplayer")) {
            if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_sync.png"), 14, 14)) {
                // Sync the pack to all the server players
                ResourcePackProfile pack = PackifiedClient.currentPack;
                List<PackFile> changedFiles = EditorWindow.changedAssets;
                List<SyncPacketData.AssetData> assets = new ArrayList<>();
                for (PackFile file : changedFiles) {
                    assets.add(new SyncPacketData.AssetData(file.getIdentifier(), file.getExtension().getExtension(), file.getTextContent())); //TODO make this work for all file types
                }
                SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(pack));
                ClientPlayNetworking.send(new C2SSyncPackChanges(data));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Sync your pack to all the server players");
            }
            // TODO add option to sync the pack to the server resource pack
            ImGui.separator();

            MinecraftClient.getInstance().world.getPlayers().forEach(player -> {
                boolean greyedOut = !Packified.moddedPlayers.contains(player.getUuid());

                ImGui.pushStyleColor(ImGuiCol.Text, greyedOut ? ImGui.getColorU32(ImGuiCol.TextDisabled) : ImGui.getColorU32(ImGuiCol.Text));
                ImGui.menuItem(player.getDisplayName().getString());
                ImGui.popStyleColor();
                if (ImGui.isItemHovered()) {
                    if (greyedOut) {
                        ImGui.setTooltip("Player does not have the mod installed");
                    } else {
                        ImGui.setTooltip("Player has the mod installed");
                    }
                }

                if (ImGui.beginPopupContextItem(player.getDisplayName().getString())) {
                    if (ImGui.beginMenu("Request Pack")) {
                        if (ImGui.menuItem("Full Pack")) {
                            // Send a request for the full pack
                            ClientPlayNetworking.send(new C2SRequestFullPack("!!currentpack!!", player.getUuid()));
                        }
                        if (ImGui.menuItem("Changes (not implemented)")) {
                            // Send a request for the changes
                            //Packified.sendChangesRequest(player.getUuid());
                        }
                        ImGui.endMenu();
                    }
                    ImGui.endPopup();
                }
            });
        }
        ImGui.end();
    }
}
