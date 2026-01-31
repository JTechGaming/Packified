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
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
            List<String> enabledPaths = collectFolders(root, "resourcepacks\\" + packName + "\\");
            for (int i=0; i<enabledPaths.size();i++) {
                String path = enabledPaths.get(i);
                enabledPaths.set(i, path.substring(0, path.lastIndexOf("\\"))); // Get rid of last backslash
            }
            PackUtils.exportPackWith(enabledPaths);
            isOpen.set(false); // Close the window after exporting
        }

        ImGui.end();
    }

    private static void insertPath(Path path) {
        String[] parts = path.toString().replace(FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks").resolve(PackHelper.getCurrentPack().getDisplayName().getString()).toString(), "").split("\\\\"); // backslash bc of filesystem path
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
        List<String> enabledPaths = new ArrayList<>();
        if (node.enabled.get()) {
            enabledPaths.add(path);
        }
        for (PackCreationWindow.FolderNode child : node.children) {
            enabledPaths.addAll(collectFolders(child, path + child.name + "\\"));
        }
        return enabledPaths;
    }

    private static void buildTree(Path rootPath) {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> sortedPaths = paths
                    .filter(Files::exists)
                    //.filter(Files::isDirectory) // kinda want to give control over individual files
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
