package me.jtech.packified.client.windows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.helpers.VersionControlHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class VersionControlWindow {
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
            if (!VersionControlHelper.queryStages().isEmpty()) {
                if (ImGui.checkbox("All", commitAll)) {
                    VersionControlHelper.toggleAll(commitAll.get());
                }
                ImGui.separator();
                for (VersionControlHelper.FileStage fileStage : VersionControlHelper.queryStages()) {
                    ImGui.pushStyleColor(ImGuiCol.Text, fileStage.getStageType().getColor());
                    ImGui.checkbox(fileStage.getFilePath().getFileName().toString(), fileStage.getInclude());
                    ImGui.popStyleColor();
                    ImGui.sameLine();
                    ImGui.textDisabled(FileUtils.getRelativePackPath(fileStage.getFilePath()));
                }
            } else {
                ImGuiImplementation.whiteSpace(10);
                ImGuiImplementation.centeredText("You don't have any awaiting changes yet...");
                ImGuiImplementation.whiteSpace(10);
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
            boolean canCommit = !VersionControlHelper.queryStages().stream().filter(fileStage -> fileStage.getInclude().get()).toList().isEmpty()
                    && commitMessage.isNotEmpty()
                    && commitVersion.isNotEmpty()
                    && !commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion());

            if (!canCommit) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Commit Changes") && MinecraftClient.getInstance().player != null) {
                if (commitVersion.isNotEmpty() && !commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion())) {
                    if (commitMessage.isNotEmpty()) {
                        VersionControlHelper.commit(PackHelper.getCurrentPack().getDisplayName().getString(), commitVersion.get(), commitMessage.get(), MinecraftClient.getInstance().player.getUuidAsString());
                        commitMessage.clear();
                    } else {
                        ImGui.textColored(0xff0033, "You must provide a commit message");
                    }
                } else {
                    ImGui.textColored(0xff0033, "Provided version already exists, change to a higher one");
                }
            }
            if (!canCommit) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("No file changes found to commit");
                }
                ImGui.beginDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button("Commit Changes & Push") && MinecraftClient.getInstance().player != null) {
                VersionControlHelper.commit(PackHelper.getCurrentPack().getDisplayName().getString(), commitVersion.get(), commitMessage.get(), MinecraftClient.getInstance().player.getUuidAsString());
                VersionControlHelper.pushToRemote();
                commitMessage.clear();
            }
            if (!canCommit) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("No file changes found to commit");
                }
            }

            if (commitMessage.isEmpty()) {
                ImGui.textColored(0xff0033, "You must provide a commit message");
            }
            if (commitVersion.isEmpty() || commitVersion.get().equalsIgnoreCase(VersionControlHelper.getCurrentVersion())) {
                ImGui.textColored(0xff0033, "Provided version already exists, change to a higher one");
            }

            ImGui.separator();
            ImGui.text("Commit History");

            ImGui.beginChild("CommitList", 0, 200, true);

            for (VersionControlHelper.VersionControlEntry entry : VersionControlHelper.getCommitsDescending()) {
                boolean pushed = VersionControlHelper.isPushed(entry.getVersion());

                ImGui.pushID(entry.getVersion());

                if (!pushed) {
                    ImGui.textColored(0xFFAA33, "●");
                    ImGui.sameLine();
                }

                ImGui.text(entry.getVersion());
                ImGui.sameLine();
                ImGui.textDisabled(entry.getAuthor());

                ImGui.textWrapped(entry.getDescription());

                if (ImGui.button("Rollback")) {
                    VersionControlHelper.rollback(entry.getVersion());
                }

                ImGui.separator();
                ImGui.popID();
            }

            ImGui.endChild();
        }
        ImGui.end();
    }
}
