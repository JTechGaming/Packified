package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import me.jtech.packified.client.imgui.ImGuiImplementation;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class LogWindow {
    public static ImBoolean isOpen = new ImBoolean(true);
    private static LinkedList<LogEntry> logEntries = new LinkedList<>();

    public static void render() {
        if (!isOpen.get()) {
            return; // If the window is not open, do not render
        }

        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        if (ImGui.begin("Logs", isOpen, ImGuiWindowFlags.MenuBar)) {
            // Draw Menubar
            if (ImGui.beginMenuBar()) {
                if (ImGui.beginMenu("Log")) {
                    if (ImGui.menuItem("Clear Logs")) {
                        logEntries.clear(); // Clear the log entries
                    }
                    ImGui.endMenu();
                }
                ImGui.endMenuBar();
            }

            // Display log entries
            for (LogEntry entry : logEntries) {
                Color color = entry.getColor();
                float r = color.getRed() / 255.0f;
                float g = color.getGreen() / 255.0f;
                float b = color.getBlue() / 255.0f;
                float alpha = 1.0f;
                ImGui.textColored(r, g, b, alpha, "[" + timestampToString(entry.getTimestamp()) + "] " + entry.getMessage());
            }
        }
        ImGui.end();
    }

    private static String timestampToString(long timestamp) {
        // Convert timestamp to a human-readable format
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }

    public static void addInfo(String message) {
        addLog(message, LogType.INFO.getColor());
    }

    public static void addWarning(String message) {
        addLog(message, LogType.WARNING.getColor());
    }

    public static void addError(String message) {
        addLog(message, LogType.ERROR.getColor());
    }

    public static void addLog(String message, Color color) {
        LogEntry logEntry = new LogEntry(message, color);
        logEntries.add(logEntry);
    }

    public enum LogType {
        INFO(Color.WHITE),
        WARNING(new Color(0xffcc00)),
        ERROR(new Color(0xff0033));

        private final Color color;

        LogType(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    private static class LogEntry {
        private String message;
        private long timestamp;
        private Color color;

        public LogEntry(String message, Color color) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.color = color;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Color getColor() {
            return color;
        }
    }
}
