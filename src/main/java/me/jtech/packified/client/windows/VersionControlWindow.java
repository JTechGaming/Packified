package me.jtech.packified.client.windows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.helpers.VersionControlHelper;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class VersionControlWindow { // todo implement version control system for packs
    private static final Gson GSON = new GsonBuilder().create();

    private static final ImBoolean commitAll = new ImBoolean(false);
    private static final ImString commitVersion = new ImString();
    private static final ImString commitMessage = new ImString();

    public static ImBoolean isOpen = new ImBoolean(true);

    public static void render() {
        if (!isOpen.get() || ModelEditorWindow.isModelWindowFocused()) return;

        if (commitVersion.isEmpty()) {
            String currentVersion = VersionControlHelper.getCurrentVersion();
            if (currentVersion != null) {
                commitVersion.set(currentVersion);
            }
        }

        if (ImGui.begin("Version Control")) {
            if (!VersionControlHelper.queryFileChanges().isEmpty()) {
                if (ImGui.checkbox("All", commitAll)) {
                    VersionControlHelper.toggleAll(commitAll.get());
                }
                ImGui.separator();
                for (VersionControlHelper.FileChange fileChange : VersionControlHelper.queryFileChanges()) {
                    ImGui.pushStyleColor(ImGuiCol.Text, fileChange.getChangeType().getColor());
                    ImGui.checkbox(fileChange.getFilePath().getFileName().toString(), fileChange.getInclude());
                    ImGui.popStyleColor();
                    ImGui.sameLine();
                    ImGui.textDisabled(FileUtils.getRelativePackPath(fileChange.getFilePath()));
                }
            }
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            ImGui.text("Commit Message");
            ImGui.inputTextMultiline("##Commit Message Field", commitMessage);
            ImGui.separator();
            ImGui.spacing();
            ImGui.text("New Version");
            ImGui.inputText("##New Version Field", commitVersion);
            if (commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion())) {
                ImGui.sameLine();
                ImGui.textColored(0xff0033, "That version already exists, change to a higher one");
            }
            boolean noChanges = VersionControlHelper.queryFileChanges().stream().filter(fileChange -> fileChange.getInclude().get()).toList().isEmpty();
            if (noChanges) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Commit Changes") && MinecraftClient.getInstance().player != null) {
                if (commitVersion.isNotEmpty() && !commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion())) {
                    if (commitMessage.isNotEmpty()) {
                        VersionControlHelper.commit(PackHelper.getCurrentPack().getDisplayName().getString(), commitVersion.get(), commitMessage.get(), MinecraftClient.getInstance().player.getUuidAsString());
                    } else {
                        ImGui.textColored(0xff0033, "You must provide a commit message");
                    }
                } else {
                    ImGui.textColored(0xff0033, "Provided version already exists, change to a higher one");
                }
            }
            if (noChanges) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("No file changes found to commit");
                }
                ImGui.beginDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button("Commit Changes & Push") && MinecraftClient.getInstance().player != null) {
                if (commitVersion.isNotEmpty() && !commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion())) {
                    if (commitMessage.isNotEmpty()) {
                        VersionControlHelper.commit(PackHelper.getCurrentPack().getDisplayName().getString(), commitVersion.get(), commitMessage.get(), MinecraftClient.getInstance().player.getUuidAsString());
                        VersionControlHelper.pushToRemote();
                    } else {
                        ImGui.textColored(0xff0033, "You must provide a commit message");
                    }
                } else {
                    ImGui.textColored(0xff0033, "Provided version already exists, change to a higher one");
                }
            }
            if (noChanges) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("No file changes found to commit");
                }
            }
        }
        ImGui.end();
    }
}
