package me.jtech.packified.client;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import me.jtech.packified.client.windows.LogWindow;
import net.minecraft.client.MinecraftClient;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class CornerNotificationsHelper {
    private static final List<ImGuiNotification> notifications = new LinkedList<>();

    public static void addNotification(String message, String description, Color color, float durationSeconds) {
        notifications.add(new ImGuiNotification(message, description, color, durationSeconds));

        // Forward the notification to the log
        LogWindow.addLog(message + ": " + description, color);
    }

    public static void render() {
        if (notifications.isEmpty()) {
            return; // Exit early if there are no notifications
        }

        int screenWidth = (int) ImGui.getMainViewport().getSizeX();
        int screenHeight = (int) ImGui.getMainViewport().getSizeY();

        float notificationWidth = 220f;
        float notificationHeight = 120f;
        float padding = 10f;

        int index = 0;
        Iterator<ImGuiNotification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            try {
                ImGuiNotification notification = iterator.next();

                if (index > 5) {
                    // If the notification would be off-screen, pause the timer and skip rendering
                    notification.paused = true;
                    continue;
                }

                if (notification.isExpired()) {
                    iterator.remove();
                    continue;
                }

                notification.paused = false;

                float alpha = notification.getAlpha();
                ImGui.setNextWindowBgAlpha(alpha * 0.8f);

                ImGui.setNextWindowPos(screenWidth - notificationWidth + padding * -1,
                        screenHeight - (notificationHeight + padding) * (index + 1),
                        ImGuiCond.Always);
                ImGui.setNextWindowSize(notificationWidth, notificationHeight);

                ImGui.begin("Notification_" + index, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize |
                        ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar |
                        ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoSavedSettings);

                // Render title (bold)
                Color color = notification.color;
                float r = color.getRed() / 255.0f;
                float g = color.getGreen() / 255.0f;
                float b = color.getBlue() / 255.0f;
                ImGui.textColored(r, g, b, alpha, notification.title); // Yellow title

                // Slight spacing between title and description
                ImGui.spacing();

                // Render description (regular text)
                ImGui.textWrapped(notification.description);

                ImGui.end();

                index++;
            } catch (NullPointerException exception) {
                LogWindow.addWarning("Error rendering notification: " + exception.getMessage());
            }
        }
    }

    public static class ImGuiNotification {
        public String title;
        public String description;
        public Color color; // Color of the notification
        public float duration; // In seconds
        public long startTime;
        public boolean paused = false;

        public ImGuiNotification(String title, String description, Color color, float duration) {
            this.title = title;
            this.description = description;
            this.color = color;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
        }

        public float getElapsedTime() {
            startTime = paused ? System.currentTimeMillis() : startTime;
            return (System.currentTimeMillis() - startTime) / 1000.0f;
        }

        public boolean isExpired() {
            return getElapsedTime() > duration;
        }

        public float getAlpha() {
            float elapsed = getElapsedTime();
            if (elapsed > duration - 0.5f) { // Fade out in the last 0.5s
                return Math.max(0, 1.0f - (elapsed - (duration - 0.5f)) / 0.5f);
            }
            return 1.0f;
        }
    }
}
