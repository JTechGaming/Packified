package me.jtech.packified.client.helpers;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class NotificationHelper {
    private static int NOTIFY_DEFAULT_TOAST_FLAGS = ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoFocusOnAppearing;
    private static List<Notification> notifications = new ArrayList<>();

    public static void render() {
        Iterator<Notification> iterator = notifications.iterator();

        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (!notification.hasProgressBar) {
                long elapsedTime = System.currentTimeMillis() - notification.startTime;
                if (elapsedTime >= notification.duration) {
                    iterator.remove();
                    continue;
                }
            }
            if (notification.getProgress() >= notification.getMaxProgress() && notification.hasProgressBar()) {
                iterator.remove();
                continue;
            }

            // Set position to center of viewport
            ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
            ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);

            ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

            ImGui.begin(notification.title, NOTIFY_DEFAULT_TOAST_FLAGS);
            ImGui.pushTextWrapPos();
            ImGui.text(notification.title);
            ImGui.text(notification.message);
            if (notification.hasProgressBar()) {
                ImGui.progressBar(notification.getProgress() / (float) notification.getMaxProgress(), 300, 100);
            }
            ImGui.popTextWrapPos();
            ImGui.end();
        }
    }

    public static Notification addNotification(String title, String message, long duration) {
        Notification notification = new Notification(title, message, duration);
        notifications.add(notification);
        return notification;
    }

    public static Notification addNotification(String title, String message, long duration, int progress, int maxProgress) {
        Notification notification = new Notification(title, message, duration);
        notification.setProgress(progress);
        notification.setMaxProgress(maxProgress);
        notifications.add(notification);
        return notification;
    }

    public static class Notification {
        private String title;
        private String message;
        private long duration;
        private long startTime;
        private boolean hasProgressBar;
        private int progress;
        private int maxProgress;

        public Notification(String title, String message, long duration) {
            this.title = title;
            this.message = message;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            this.hasProgressBar = false;
            this.progress = 0;
            this.maxProgress = 100; // Default max progress
        }

        public void setProgress(int progress) {
            this.hasProgressBar = true;
            this.progress = progress;
        }
        public void setMaxProgress(int maxProgress) {
            this.maxProgress = maxProgress;
        }
        public boolean isExpired() {
            return System.currentTimeMillis() - startTime >= duration;
        }
        public String getTitle() {
            return title;
        }
        public String getMessage() {
            return message;
        }
        public long getDuration() {
            return duration;
        }
        public boolean hasProgressBar() {
            return hasProgressBar;
        }
        public int getProgress() {
            return progress;
        }
        public int getMaxProgress() {
            return maxProgress;
        }
    }
}
