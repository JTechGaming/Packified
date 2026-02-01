package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourceType;

import java.util.*;

@Environment(EnvType.CLIENT)
public class PackCreationWindow {
    public static ImBoolean isOpen = new ImBoolean(false);
    private static ImString packName = new ImString(64);
    private static ImInt packType = new ImInt(0); // 0 for Resource Pack, 1 for Data Pack, 2 for Iris shader pack
    private static ImString packDescription = new ImString(256);

    public static final Map<String, String> resourcePackVersions = assemblePackVersions();

    private static int packVersionIndex = computePackVersionStartIndex();
    private static int packVersionEndIndex = computePackVersionEndIndex();

    public static void render() {
        if (!isOpen.get()) return;

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        ImGui.begin("Pack Creation Wizard", isOpen);

        ImGui.setNextItemWidth(150);
        ImGui.inputText("Pack Name", packName);

        ImGui.combo("Pack Type", packType, new String[]{"Resource Pack", "Data Pack", "Iris Shader Pack"}, 3);

        String[] keys = resourcePackVersions.keySet().toArray(new String[0]);
        String rangeStartKey = keys[packVersionIndex];

        // Begin custom combo
        ImGui.setNextItemWidth(150);
        double currentVersion = parseDoubleSafe(getCurrentVersion());
        if (ImGui.beginCombo("Pack Version Range", rangeStartKey)) {
            for (int i = 0; i < keys.length; i++) {
                boolean isSelected = (packVersionIndex == i);
                boolean isCurrent = Math.abs(parseDoubleSafe(keys[i]) - currentVersion) < 1e-6;
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
                boolean isCurrent = Math.abs(parseDoubleSafe(keys[i]) - currentVersion) < 1e-6;
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

        renderGenerateFolders();

        if (ImGui.button("Create Pack")) {
            if (!packName.get().isEmpty()) {
                PackUtils.createPack(packName.get(), packDescription.get(), packVersionIndex, packVersionEndIndex);
                generateSelected(root, "");
                isOpen.set(false); // Close the window after creation
            } else {
                System.out.println("Pack name cannot be empty."); //todo replace with popup notification
            }
        }

        ImGui.end();
    }

    static class FolderNode { // todo move to global util class
        String name;
        ImBoolean enabled = new ImBoolean(false);
        List<FolderNode> children = new ArrayList<>();

        FolderNode(String name) {
            this.name = name;
        }
    }

    static FolderNode root = new FolderNode("All");

    static void buildTree() {
        String[] paths = {
                "textures/",
                "textures/item/",
                "textures/block/",
                "textures/models/",
                "textures/entity/",
                "textures/colormap/",
                "textures/misc/",
                "textures/effect/",
                "textures/entity/entity_type",
                "textures/environment/",
                "textures/gui/",
                "textures/gui/texture/",
                "textures/map/",
                "textures/mob_effect/",
                "textures/particle/",
                "textures/painting/",
                "textures/trims/",
                "textures/trims/color_palettes/",
                "textures/trims/entity/",
                "textures/trims/entity/humanoid/",
                "textures/trims/entity/humanoid_leggings/",
                "textures/trims/items/",
                "models/",
                "models/item/",
                "models/block/",
                "sounds/",
                "sounds/block",
                "sounds/records",
                "lang/",
                "shaders/",
                "shaders/programs/",
                "shaders/core/",
                "font/",
                "font/include",
                "atlases/",
                "texts/",
                "items/"
        };

        for (String path : paths) {
            insertPath(root, path);
        }
    }

    static {
        buildTree();
    }

    static void insertPath(FolderNode root, String path) {
        String[] parts = path.split("/");
        FolderNode current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            FolderNode finalCurrent = current;
            FolderNode child = current.children.stream()
                    .filter(n -> n.name.equals(part))
                    .findFirst()
                    .orElseGet(() -> {
                        FolderNode n = new FolderNode(part);
                        finalCurrent.children.add(n);
                        return n;
                    });

            current = child;
        }
    }

    static void setRecursive(FolderNode node, boolean value) {
        node.enabled.set(value);
        for (FolderNode child : node.children) {
            setRecursive(child, value);
        }
    }

    // todo move to global util class
    public static void renderFolderNode(FolderNode node) {
        ImGui.pushID(node.name);

        boolean changed = ImGui.checkbox("", node.enabled);
        if (changed) {
            setRecursive(node, node.enabled.get());
        }
        ImGui.sameLine();

        int flags = node.children.isEmpty() ? ImGuiTreeNodeFlags.Leaf : ImGuiTreeNodeFlags.OpenOnArrow;

        boolean open = ImGui.treeNodeEx(node.name, flags);

        if (open) {
            for (FolderNode child : node.children) {
                renderFolderNode(child);
            }
            ImGui.treePop();
        }

        ImGui.popID();
    }

    static void renderGenerateFolders() {
        renderFolderNode(root);
    }

    static void generateSelected(FolderNode node, String path) {
        if (node.enabled.get()) {
            FileUtils.generateFolderStructure(path);
        }
        for (FolderNode child : node.children) {
            generateSelected(child, path + child.name + "/");
        }
    }

    private static double parseDoubleSafe(String s) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    private static int computePackVersionStartIndex() {
        String[] keys = resourcePackVersions.keySet().toArray(new String[0]);
        double current = parseDoubleSafe(getCurrentVersion());
        int bestIndex = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < keys.length; i++) {
            double val = parseDoubleSafe(keys[i]);
            if (val <= current && val > bestVal) {
                bestVal = val;
                bestIndex = i;
            }
        }
        return bestIndex == -1 ? 0 : bestIndex;
    }

    private static int computePackVersionEndIndex() {
        String[] keys = resourcePackVersions.keySet().toArray(new String[0]);
        double current = parseDoubleSafe(getCurrentVersion());
        int bestIndex = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < keys.length; i++) {
            double val = parseDoubleSafe(keys[i]);
            if (val <= current && val > bestVal) {
                bestVal = val;
                bestIndex = i;
            }
        }
        return bestIndex == -1 ? Math.max(0, keys.length - 1) : bestIndex;
    }

    private static String getCurrentVersion() {
        return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES) + "";
    }

    private static Map<String, String> assemblePackVersions() {
        Map<String, String> resourcePackVersions = new LinkedHashMap<>();

        resourcePackVersions.put("1", "1.6.1-1.8.9");
        resourcePackVersions.put("2", "1.9-1.10.2");
        resourcePackVersions.put("3", "1.11-1.12.2");
        resourcePackVersions.put("4", "1.13-1.14.4");
        resourcePackVersions.put("5", "1.15-1.16.1");
        resourcePackVersions.put("6", "1.16.2-1.16.5");
        resourcePackVersions.put("7", "1.17-1.17.1");
        resourcePackVersions.put("8", "1.18-1.18.2");
        resourcePackVersions.put("9", "1.19-1.19.2");
        resourcePackVersions.put("11", "22w42a-22w44a");
        resourcePackVersions.put("12", "1.19.3");
        resourcePackVersions.put("13", "1.19.4");
        resourcePackVersions.put("14", "23w14a-23w16a");
        resourcePackVersions.put("15", "1.20-1.20.1");
        resourcePackVersions.put("16", "23w31a");
        resourcePackVersions.put("17", "23w32a-1.20.2-pre1");
        resourcePackVersions.put("18", "1.20.2");
        resourcePackVersions.put("19", "23w42a");
        resourcePackVersions.put("20", "23w43a-23w44a");
        resourcePackVersions.put("21", "23w45a-23w46a");
        resourcePackVersions.put("22", "1.20.3-1.20.4");
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
        resourcePackVersions.put("63", "1.21.6-pre1 - 1.21.7-rc1");
        resourcePackVersions.put("64", "1.21.7-rc2 - 1.21.8");
        resourcePackVersions.put("65.0", "25w31a");
        resourcePackVersions.put("65.1", "25w32a");
        resourcePackVersions.put("65.2", "25w33a");
        resourcePackVersions.put("66.0", "25w34a - 25w34b");
        resourcePackVersions.put("67.0", "25w35a");
        resourcePackVersions.put("68.0", "25w36a - 25w36b");
        resourcePackVersions.put("69.0", "25w37a - 1.21.10");
        resourcePackVersions.put("70.0", "25w41a");
        resourcePackVersions.put("70.1", "25w42a");
        resourcePackVersions.put("71.0", "25w43a");
        resourcePackVersions.put("72.0", "25w44a");
        resourcePackVersions.put("73.0", "25w45a");
        resourcePackVersions.put("74.0", "25w46a");
        resourcePackVersions.put("75.0", "1.21.11");
        resourcePackVersions.put("76.0", "26.1 Snapshot 1");
        resourcePackVersions.put("77.0", "26.1 Snapshot 2");
        resourcePackVersions.put("78.0", "26.1 Snapshot 3");
        resourcePackVersions.put("78.1", "26.1 Snapshot 4");
        resourcePackVersions.put("79.0", "26.1 Snapshot 5");

        return resourcePackVersions;
    }
}
