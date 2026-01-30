package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class PackExporterWindow {
    public static ImBoolean isOpen = new ImBoolean(false);

    private static PackCreationWindow.FolderNode root;

    public static void render() {
        if (!isOpen.get() || PackHelper.isInvalid()) {
            root = null;
            return;
        }

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        ImGui.begin("Pack Exporter", isOpen);

        String packName = PackHelper.getCurrentPack().getDisplayName().getString();
        ImGui.text("Pack Name: " + packName);
        ImGui.text("Pack Type: Resource Pack"); // todo make this actually work
        if (root == null) {
            root = new PackCreationWindow.FolderNode(packName);
            Path rootPath = FileUtils.getPackFolderPath();
            buildTree(rootPath);
        }
        PackCreationWindow.renderFolderNode(root);

        if (ImGui.button("Export Pack")) {
            PackUtils.exportPackWith();
            isOpen.set(false); // Close the window after exporting
        }

        ImGui.end();
    }

    private static void insertPath(Path path) {
        String[] parts = path.toString().split("/");
        PackCreationWindow.FolderNode current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            PackCreationWindow.FolderNode finalCurrent = current;
            PackCreationWindow.FolderNode child = current.children.stream()
                    .filter(n -> n.name.equals(part))
                    .findFirst()
                    .orElseGet(() -> {
                        PackCreationWindow.FolderNode n = new PackCreationWindow.FolderNode(part);
                        n.enabled.set(!path.toString().contains(".packified")); // export all except version control folder by default
                        finalCurrent.children.add(n);
                        return n;
                    });

            current = child;
        }
    }

    private static List<String> collectFolders(PackCreationWindow.FolderNode node, String path) {
        if (node.enabled.get()) {

        }
        for (PackCreationWindow.FolderNode child : node.children) {
            collectFolders(child, path + child.name + "/");
        }
    }

    private static void buildTree(Path rootPath) {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> sortedPaths = paths
                    .filter(Files::exists)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path path : sortedPaths) {
                insertPath(path);
            }
        } catch (IOException e) {
            LogWindow.addError(e.getMessage());
        }
    }
}
