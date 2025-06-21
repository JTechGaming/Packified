package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import me.jtech.packified.client.util.PackUtils;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PackCreationWindow {
    public static boolean isOpen = false;
    private static ImString packName = new ImString(64);
    private static ImString packDescription = new ImString(256);

    public static final Map<String, String> resourcePackVersions = assemblePackVersions();

    private static int packVersionIndex = resourcePackVersions.keySet().stream()
            .mapToInt(Integer::parseInt)
            .filter(i -> i <= Integer.parseInt(getCurrentVersion()))
            .max()
            .orElse(0); // Default to the first version that is less than or equal to the current version
    private static int packVersionEndIndex = resourcePackVersions.keySet().stream()
            .mapToInt(Integer::parseInt)
            .filter(i -> i <= Integer.parseInt(getCurrentVersion()))
            .max()
            .orElse(resourcePackVersions.size()-1); // Default to the latest version that is less than or equal to the current version

    public static void render() {
        if (!isOpen) return;

        ImGui.begin("Pack Creation Wizard", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize);

        ImGui.setNextItemWidth(150);
        ImGui.inputText("Pack Name", packName);

        String[] keys = resourcePackVersions.keySet().toArray(new String[0]);
        String rangeStartKey = keys[packVersionIndex];

        // Begin custom combo
        ImGui.setNextItemWidth(150);
        if (ImGui.beginCombo("Pack Version Range", rangeStartKey)) {
            for (int i = 0; i < keys.length; i++) {
                boolean isSelected = (packVersionIndex == i);
                boolean isCurrent = keys[i].equalsIgnoreCase(getCurrentVersion());
                if (isCurrent) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.0f, 1.0f, 0.0f, 1.0f); // Highlight current version in green
                }
                if (ImGui.selectable(keys[i], isSelected)) {
                    packVersionIndex = i; // Update the selected index
                    packVersionEndIndex = i; // Sync end index with start index
                }
                if (isCurrent) {
                    ImGui.popStyleColor(); // Reset to default color
                }

                // Show tooltip when hovering
                if (ImGui.isItemHovered()) {
                    String tooltipText = resourcePackVersions.get(keys[i]);
                    ImGui.beginTooltip();
                    ImGui.text(tooltipText != null ? tooltipText : "No version info" +
                            (isCurrent ? " (Current Version)" : ""));
                    ImGui.endTooltip();
                }

                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        String rangeEndKey = keys[packVersionEndIndex];

        ImGui.setNextItemWidth(150);
        if (ImGui.beginCombo("##", rangeEndKey)) {
            for (int i = 0; i < keys.length; i++) {
                boolean isSelected = (packVersionEndIndex == i);
                boolean isCurrent = keys[i].equalsIgnoreCase(getCurrentVersion());
                if (isCurrent) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.0f, 1.0f, 0.0f, 1.0f); // Highlight current version in green
                }
                if (ImGui.selectable(keys[i], isSelected)) {
                    packVersionEndIndex = i; // Update the selected index
                }
                if (isCurrent) {
                    ImGui.popStyleColor(); // Reset to default color
                }

                // Show tooltip when hovering
                if (ImGui.isItemHovered()) {
                    String tooltipText = resourcePackVersions.get(keys[i]);
                    ImGui.beginTooltip();
                    ImGui.text(tooltipText != null ? tooltipText : "No version info" +
                            (isCurrent ? " (Current Version)" : ""));
                    ImGui.endTooltip();
                }

                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.button("Create Pack")) {
            if (!packName.get().isEmpty()) {
                PackUtils.createPack(packName.get(), packDescription.get(), packVersionIndex, packVersionEndIndex);
                isOpen = false; // Close the window after creation
            } else {
                System.out.println("Pack name cannot be empty."); //todo replace with popup notification
            }
        }

        ImGui.end();
    }

    private static String getCurrentVersion() {
        return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES) + "";
    }

    private static Map<String, String> assemblePackVersions() {
        Map<String, String> resourcePackVersions = new LinkedHashMap<>();

        resourcePackVersions.put("1", "1.6.1–1.8.9");
        resourcePackVersions.put("2", "1.9–1.10.2");
        resourcePackVersions.put("3", "1.11–1.12.2");
        resourcePackVersions.put("4", "1.13–1.14.4");
        resourcePackVersions.put("5", "1.15–1.16.1");
        resourcePackVersions.put("6", "1.16.2–1.16.5");
        resourcePackVersions.put("7", "1.17–1.17.1");
        resourcePackVersions.put("8", "1.18–1.18.2");
        resourcePackVersions.put("9", "1.19–1.19.2");
        resourcePackVersions.put("11", "22w42a–22w44a");
        resourcePackVersions.put("12", "1.19.3");
        resourcePackVersions.put("13", "1.19.4");
        resourcePackVersions.put("14", "23w14a–23w16a");
        resourcePackVersions.put("15", "1.20–1.20.1");
        resourcePackVersions.put("16", "23w31a");
        resourcePackVersions.put("17", "23w32a–1.20.2-pre1");
        resourcePackVersions.put("18", "1.20.2");
        resourcePackVersions.put("19", "23w42a");
        resourcePackVersions.put("20", "23w43a-23w44a");
        resourcePackVersions.put("21", "23w45a-23w46a");
        resourcePackVersions.put("22", "1.20.3–1.20.4");
        resourcePackVersions.put("24", "24w03a-24w04a");
        resourcePackVersions.put("25", "24w05a-24w05b");
        resourcePackVersions.put("26", "24w06a-24w07");
        resourcePackVersions.put("28", "24w09a-24w10a");
        resourcePackVersions.put("29", "24w11a");
        resourcePackVersions.put("30", "24w12a");
        resourcePackVersions.put("31", "24w13a-1.20.5-pre3");
        resourcePackVersions.put("32", "1.20.5-");
        resourcePackVersions.put("33", "24w18a - 24w20a");
        resourcePackVersions.put("34", "1.21 - 1.21.1");
        resourcePackVersions.put("35", "24w33a");
        resourcePackVersions.put("36", "24w34a - 24w35a");
        resourcePackVersions.put("37", "24w36a");
        resourcePackVersions.put("38", "24w37a");
        resourcePackVersions.put("39", "24w38a - 24w39a");
        resourcePackVersions.put("40", "24w40a");
        resourcePackVersions.put("41", "1.21.2-pre1 - 1.21.2-pre2");
        resourcePackVersions.put("42", "1.21.2 - 1.21.3");
        resourcePackVersions.put("43", "24w44a");
        resourcePackVersions.put("44", "24w45a");
        resourcePackVersions.put("45", "24w46a");
        resourcePackVersions.put("46", "1.21.4-pre1 - 1.21.4");
        resourcePackVersions.put("47", "25w02a");
        resourcePackVersions.put("48", "25w03a");
        resourcePackVersions.put("49", "25w04a");
        resourcePackVersions.put("50", "25w05a");
        resourcePackVersions.put("51", "25w06a");
        resourcePackVersions.put("52", "25w07a");
        resourcePackVersions.put("53", "25w08a - 25w09b");
        resourcePackVersions.put("54", "25w10a");
        resourcePackVersions.put("55", "1.21.5-pre1 - 1.21.5");
        resourcePackVersions.put("56", "25w15a");
        resourcePackVersions.put("57", "25w16a");
        resourcePackVersions.put("58", "25w17a");
        resourcePackVersions.put("59", "25w18a");
        resourcePackVersions.put("60", "25w19a");
        resourcePackVersions.put("61", "25w20a");
        resourcePackVersions.put("62", "25w21a");
        resourcePackVersions.put("63", "1.21.6-pre1 - 1.21.6");

        return resourcePackVersions;
    }
}
