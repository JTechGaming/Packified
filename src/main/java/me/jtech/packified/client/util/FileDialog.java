package me.jtech.packified.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Environment(EnvType.CLIENT)
public class FileDialog {
    private static final ExecutorService dialogThread = Executors.newSingleThreadExecutor();
    private static CompletableFuture<String> currentSaveOrOpenFileDialog = null;
    public static boolean initializedNfd = false;

    public static boolean hasDialog() {
        return currentSaveOrOpenFileDialog != null;
    }

    public static CompletableFuture<String> openFileDialog(String defaultPath, String filterDescription, String... filters) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);

        currentSaveOrOpenFileDialog = new CompletableFuture<>();
        CompletableFuture<String> future = currentSaveOrOpenFileDialog;

        boolean initializedNfd = FileDialog.initializedNfd;
        FileDialog.initializedNfd = true;

        Runnable runnable = () -> {
            if (!initializedNfd) {
                NativeFileDialog.NFD_Init();
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.callocPointer(1);

                StringBuilder filterBuilder = new StringBuilder();

                if (filters == null || filters.length == 0) {
                    // Use a catch-all filter when none provided
                    filterBuilder.append("*");
                } else {
                    for (String f : filters) {
                        if (filterBuilder.length() > 0) filterBuilder.append(",");
                        filterBuilder.append(filter(f));
                    }
                }

                String desc = (filterDescription == null || filterDescription.isEmpty()) ? "All Files" : filterDescription;

                NFDFilterItem.Buffer filtersBuffer = NFDFilterItem.malloc(1);
                filtersBuffer.get(0)
                        .name(stack.UTF8(filter(desc)))
                        .spec(stack.UTF8(filterBuilder.toString()));

                int result = NativeFileDialog.NFD_OpenDialog(out, filtersBuffer, filter(defaultPath));

                if (result != NativeFileDialog.NFD_OKAY) {
                    currentSaveOrOpenFileDialog.complete(null);
                    currentSaveOrOpenFileDialog = null;
                } else {
                    currentSaveOrOpenFileDialog.complete(out.getStringUTF8(0));
                    currentSaveOrOpenFileDialog = null;
                    NativeFileDialog.NFD_FreePath(out.get(0));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                if (currentSaveOrOpenFileDialog != null) {
                    currentSaveOrOpenFileDialog.complete(null);
                    currentSaveOrOpenFileDialog = null;
                }
            }
        };

        if (MinecraftClient.IS_SYSTEM_MAC) {
            // MacOS needs dialogs to be run from the main thread
            MinecraftClient.getInstance().submit(runnable);
        } else {
            dialogThread.submit(runnable);
        }

        return future;
    }

    public static CompletableFuture<String> saveFileDialog(String defaultPath, String defaultName, String filterDescription, String... filters) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);

        currentSaveOrOpenFileDialog = new CompletableFuture<>();
        CompletableFuture<String> future = currentSaveOrOpenFileDialog;

        boolean initializedNfd = FileDialog.initializedNfd;
        FileDialog.initializedNfd = true;

        Runnable runnable = () -> {
            if (!initializedNfd) {
                NativeFileDialog.NFD_Init();
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.callocPointer(1);

                StringBuilder filterBuilder = new StringBuilder();

                for (String filter : filters) {
                    if (!filterBuilder.isEmpty()) filterBuilder.append(",");
                    filterBuilder.append(filter(filter));
                }

                NFDFilterItem.Buffer filtersBuffer = NFDFilterItem.malloc(1);
                filtersBuffer.get(0)
                        .name(stack.UTF8(filter(filterDescription)))
                        .spec(stack.UTF8(filterBuilder.toString()));

                int result = NativeFileDialog.NFD_SaveDialog(out, filtersBuffer, filter(defaultPath), filter(defaultName));

                if (result != NativeFileDialog.NFD_OKAY) {
                    currentSaveOrOpenFileDialog.complete(null);
                    currentSaveOrOpenFileDialog = null;
                } else {
                    currentSaveOrOpenFileDialog.complete(out.getStringUTF8(0));
                    currentSaveOrOpenFileDialog = null;
                    NativeFileDialog.NFD_FreePath(out.get(0));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                if (currentSaveOrOpenFileDialog != null) {
                    currentSaveOrOpenFileDialog.complete(null);
                    currentSaveOrOpenFileDialog = null;
                }
            }
        };

        if (MinecraftClient.IS_SYSTEM_MAC) {
            // MacOS needs dialogs to be run from the main thread
            MinecraftClient.getInstance().submit(runnable);
        } else {
            dialogThread.submit(runnable);
        }

        return future;
    }

    public static String filter(CharSequence in) {
        return filterLT20(in.toString()
                .replace("'", "")
                .replace("\"", "")
                .replace("$", "")
                .replace("`", ""));
    }

    public static String filterLT20(CharSequence in) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c >= 32 || c == '\n') builder.append(c);
        }
        return builder.toString();
    }
}
