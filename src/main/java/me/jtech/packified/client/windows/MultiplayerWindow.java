package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.networking.packets.C2SRequestFullPack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class MultiplayerWindow {
    public static ImBoolean isOpen = new ImBoolean(true);

    public static void render() {
        if (!isOpen.get()) {
            return; // If the window is not open, do not render
        }
        // Render the multiplayer window
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        if (ImGui.begin("Multiplayer", isOpen)) {
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_sync.png"), 14, 14);
            if (ImGui.isItemClicked()) {
                // Sync the pack to all the server players
                ResourcePackProfile pack = PackifiedClient.currentPack;
                PackUtils.sendPackChangesToPlayers(pack);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Sync your pack to all the server players");
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_upload.png"), 14, 14);
            if (ImGui.isItemClicked()) {
                // Upload the pack to the server
                ResourcePackProfile pack = PackifiedClient.currentPack;
                PackUtils.sendPackAsServerPack(pack);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Upload your pack to the server (This currently has no use)");
            }
            ImGui.separator();

            if (ImGui.beginTable("##multiplayer_table", 2, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.NoBordersInBody
                    | ImGuiTableFlags.BordersOuter | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                ImGui.tableSetupColumn("Player");
                ImGui.tableSetupColumn("Pack");
                ImGui.tableHeadersRow();
                ImGui.tableNextRow();
                MinecraftClient.getInstance().getNetworkHandler().getPlayerList().forEach(p -> {
                    UUID uuid = p.getProfile().getId();
                    String displayName = p.getProfile().getName();
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    boolean greyedOut = !Packified.moddedPlayers.contains(uuid);

                    ImGui.pushStyleColor(ImGuiCol.Text, greyedOut ? ImGui.getColorU32(ImGuiCol.TextDisabled) : ImGui.getColorU32(ImGuiCol.Text));
                    ImGui.menuItem(displayName);
                    ImGui.popStyleColor();
                    if (ImGui.isItemHovered()) {
                        if (!uuid.toString().equalsIgnoreCase(MinecraftClient.getInstance().player.getUuid().toString())) {
                            if (greyedOut) {
                                ImGui.setTooltip("Player does not have the mod installed");
                            } else {
                                ImGui.setTooltip("Player has the mod installed");
                            }
                        } else {
                            ImGui.setTooltip("You");
                        }
                    }

                    ImGui.tableSetColumnIndex(1);
                    if (uuid.equals(MinecraftClient.getInstance().player.getUuid())) {
                        if (PackifiedClient.currentPack == null) {
                            ImGui.text("Vanilla");
                        } else {
                            ImGui.text(PackifiedClient.currentPack.getDisplayName().getString());
                        }
                    } else {
                        ImGui.text(PackifiedClient.playerPacks.getOrDefault(uuid, "Vanilla"));
                    }
                    ImGui.tableSetColumnIndex(0);

                    if (PackifiedClient.playerPacks.containsKey(uuid) && uuid != MinecraftClient.getInstance().player.getUuid()) {
                        if (ImGui.beginPopupContextItem(displayName)) {
                            if (ImGui.beginMenu("Request Pack")) {
                                if (ImGui.menuItem("Full Pack")) {
                                    // Send a request for the full pack
                                    ClientPlayNetworking.send(new C2SRequestFullPack("!!currentpack!!", uuid));
                                }
                                if (ImGui.menuItem("Changes (not implemented)")) {
                                    // Send a request for the changes
                                    //Packified.sendChangesRequest(player.getUuid());
                                }
                                ImGui.endMenu();
                            }
                            ImGui.endPopup();
                        }
                    }
                });
                ImGui.endTable();
            }
        }
        ImGui.end();
    }
}
