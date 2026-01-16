package me.jtech.packified.client.helpers;

import me.jtech.packified.client.windows.LogWindow;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ExternalEditorHelper {
    private static Optional<Path> jsonEditor = Optional.empty();

    public static Optional<Path> findJSONEditor() {
        if (jsonEditor.isPresent()) {
            return jsonEditor;
        }

        // Options sorted by priority
        String[] editors = {
                "blockbench", // Blockbench
                "code",       // Visual Studio Code (code)
                "subl",       // Sublime Text (subl)
                "sublime_text", // Sublime executable name
                "atom",       // Atom
                "notepad++",  // Notepad++
                "notepad"     // Windows Notepad
        };

        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA") == null ? "" : System.getenv("LOCALAPPDATA");
            List<String> possible = Arrays.asList(
                    // Blockbench often installed to LocalAppData\Programs\blockbench
                    localAppData.isEmpty() ? "" : localAppData + "\\Programs\\Blockbench\\Blockbench.exe",
                    // VS Code typical installers
                    System.getenv("ProgramFiles") == null ? "" : System.getenv("ProgramFiles") + "\\Microsoft VS Code\\Code.exe",
                    System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)") + "\\Microsoft VS Code\\Code.exe",
                    // Sublime
                    System.getenv("ProgramFiles") == null ? "" : System.getenv("ProgramFiles") + "\\Sublime Text 3\\sublime_text.exe",
                    System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)") + "\\Sublime Text 3\\sublime_text.exe",
                    // Notepad++
                    System.getenv("ProgramFiles") == null ? "" : System.getenv("ProgramFiles") + "\\Notepad++\\notepad++.exe",
                    System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)") + "\\Notepad++\\notepad++.exe",
                    // Windows notepad
                    System.getenv("SystemRoot") == null ? "" : System.getenv("SystemRoot") + "\\system32\\notepad.exe"
            );

            for (String p : possible) {
                if (p != null && !p.isEmpty()) {
                    Path path = Paths.get(p);
                    if (Files.exists(path)) {
                        System.out.println("Found JSON editor: " + path);
                        jsonEditor = Optional.of(path);
                        return Optional.of(path);
                    }
                }
            }
        }

        for (String editor : editors) {
            Optional<Path> found = findExecutableOnPath(editor);
            if (found.isPresent()) {
                System.out.println("Found JSON editor: " + found.get());
                jsonEditor = found;
                return found;
            }
        }

        System.out.println("No JSON editor found.");
        return Optional.empty();
    }

    private static Optional<Path> imageEditor = Optional.empty();

    public static Optional<Path> findImageEditor() {
        if (imageEditor.isPresent()) {
            return imageEditor;
        }

        // Options sorted by priority
        String[] editors = {
                "photoshop",  // Photoshop (may not be on PATH)
                "gimp",       // GIMP
                "paintdotnet",// Paint.NET (sometimes has this name)
                "paint.net",  // alternative
                "krita",      // Krita
                "mspaint"     // Microsoft Paint
        };

        if (isWindows()) {
            String pf = System.getenv("ProgramFiles") == null ? "" : System.getenv("ProgramFiles");
            String pfx86 = System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)");
            List<String> possible = Arrays.asList(
                    // Photoshop common locations (versions vary; include a couple of typical paths)
                    pf.isEmpty() ? "" : pf + "\\Adobe\\Adobe Photoshop 2024\\Photoshop.exe",
                    pfx86.isEmpty() ? "" : pfx86 + "\\Adobe\\Adobe Photoshop 2024\\Photoshop.exe",
                    pf.isEmpty() ? "" : pf + "\\Adobe\\Adobe Photoshop CC 2019\\Photoshop.exe",
                    // GIMP
                    pf.isEmpty() ? "" : pf + "\\GIMP 2\\bin\\gimp-2.10.exe",
                    pfx86.isEmpty() ? "" : pfx86 + "\\GIMP 2\\bin\\gimp-2.10.exe",
                    // Paint.NET
                    pf.isEmpty() ? "" : pf + "\\paint.net\\PaintDotNet.exe",
                    pfx86.isEmpty() ? "" : pfx86 + "\\paint.net\\PaintDotNet.exe",
                    // Krita
                    pf.isEmpty() ? "" : pf + "\\Krita (x64)\\bin\\krita.exe",
                    pfx86.isEmpty() ? "" : pfx86 + "\\Krita (x86)\\bin\\krita.exe",
                    // MS Paint
                    System.getenv("SystemRoot") == null ? "" : System.getenv("SystemRoot") + "\\system32\\mspaint.exe"
            );

            for (String p : possible) {
                if (p != null && !p.isEmpty()) {
                    Path path = Paths.get(p);
                    if (Files.exists(path)) {
                        System.out.println("Found image editor: " + path);
                        imageEditor = Optional.of(path);
                        return Optional.of(path);
                    }
                }
            }
        }

        for (String editor : editors) {
            Optional<Path> found = findExecutableOnPath(editor);
            if (found.isPresent()) {
                System.out.println("Found image editor: " + found.get());
                imageEditor = found;
                return found;
            }
        }

        System.out.println("No image editor found.");
        return Optional.empty();
    }

    private static Optional<Path> audioEditor = Optional.empty();

    public static Optional<Path> findAudioEditor() {
        if (audioEditor.isPresent()) {
            return audioEditor;
        }

        String[] editors = {
                "audacity"   // Audacity
        };

        // Windows fallback: check common Program Files locations for Audacity
        if (isWindows()) {
            List<String> possible = Arrays.asList(
                    System.getenv("ProgramFiles")  == null ? "" : System.getenv("ProgramFiles") + "\\Audacity\\audacity.exe",
                    System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)") + "\\Audacity\\audacity.exe",
                    System.getenv("ProgramW6432") == null ? "" : System.getenv("ProgramW6432") + "\\Audacity\\audacity.exe"
            );
            for (String p : possible) {
                if (p != null && !p.isEmpty()) {
                    Path path = Paths.get(p);
                    if (Files.exists(path)) {
                        System.out.println("Found audio editor: " + path);
                        audioEditor = Optional.of(path);
                        return Optional.of(path);
                    }
                }
            }
        }

        for (String editor : editors) {
            Optional<Path> found = findExecutableOnPath(editor);
            if (found.isPresent()) {
                System.out.println("Found audio editor: " + found.get());
                audioEditor = found;
                return found;
            }
        }

        System.out.println("No audio editor found.");
        return Optional.empty();
    }

    private static Optional<Path> findExecutableOnPath(String name) {
        String cmd = isWindows() ? "where" : "which";
        ProcessBuilder pb = new ProcessBuilder(cmd, name);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        Path path = Paths.get(line.trim());
                        if (Files.exists(path)) return Optional.of(path);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    public static void openFileWithEditor(Path editor, Path file) {
        ConfirmWindow.open("You are about to open a file in: " + editor.getFileName().toString().replace(".exe", ""), "Are you sure you want to open the file with the external editor?\n\nEditor: " + editor.toString() + "\nFile: " + file.toString(), () -> {
            try {
                LogWindow.addInfo("Opening file " + file + " with editor " + editor);
                if (isWindows()) {
                    // If editor is a wrapper in ...\bin\code, prefer the sibling Code.exe one level up
                    Path parent = editor.getParent();
                    if (parent != null && "bin".equalsIgnoreCase(parent.getFileName().toString())) {
                        Path codeExe = parent.getParent() != null ? parent.getParent().resolve("Code.exe") : null;
                        if (codeExe != null && Files.exists(codeExe)) {
                            new ProcessBuilder(codeExe.toString(), file.toString()).start();
                            return;
                        }
                    }
                    // If it's a real .exe or executable, run it directly
                    String name = editor.getFileName().toString();
                    if (name.toLowerCase().endsWith(".exe") || Files.isExecutable(editor)) {
                        new ProcessBuilder(editor.toString(), file.toString()).start();
                        return;
                    }
                    // Fallback: let cmd handle scripts/associations (uses start to launch)
                    String comSpec = System.getenv("ComSpec");
                    if (comSpec == null || comSpec.isEmpty()) comSpec = "cmd";
                    new ProcessBuilder(comSpec, "/c", "start", "\"\"", editor.toString(), file.toString()).start();
                } else {
                    new ProcessBuilder(editor.toString(), file.toString()).start();
                }
            } catch (IOException e) {
                LogWindow.addError("Failed to open file with editor: " + e.getMessage());
            }
        });
    }
}
