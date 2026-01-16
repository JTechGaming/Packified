package me.jtech.packified.client.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.windows.LogWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * This is a very modular and easy to use tutorial helper that I wrote specifically for this project, but
 * I think it has a lot of use cases outside this project too so feel free to use this in your projects if you want to
 *
 */

@Environment(EnvType.CLIENT)
public class TutorialHelper {
    public static TutorialStage currentStage;
    public static ImBoolean isOpen = new ImBoolean(true);
    private static final LinkedList<TutorialStage> stages = new LinkedList<>();

    private static final Gson GSON = new GsonBuilder().create();
    private static final Path localDirectoryPath = Path.of("C:\\");
    private static boolean loadedFromFile = false;


    public static TutorialStage WELCOME_STAGE = registerStage(new TutorialStage(
            "Start",
            "Welcome to the Packified mod!"
    ));
    public static TutorialStage HIERARCHY_STAGE = registerStage(new TutorialStage(
            "File Hierarchy",
            "Welcome to the Packified mod!"
    ));
    public static TutorialStage TEST_STAGE = registerStage(new TutorialStage(
            "File Hierarchy",
            "Welcome to the Packified mod!",
            () -> {
                ImGui.button("Test Button");
                ImGui.sameLine();
                ImGui.textWrapped("This is a test button that does nothing, but it is here to test actions in tutorial stages.");
            },
            0, 200 // 0 is default width, 200 is custom height
    ));
    public static TutorialStage END_STAGE = registerStage(new TutorialStage(
            "That's it!",
            "That is all you need to know before using the editor! Thanks for following this tutorial and good luck making resource packs!"
    ));


    private static TutorialStage registerStage(TutorialStage stage) {
        stages.add(stage);
        return stage;
    }

    public static void init() {
        if (!localDirectoryPath.resolve("packified").toFile().exists()) {
            localDirectoryPath.resolve("packified").toFile().mkdirs();
        }
        File configFile = localDirectoryPath.resolve("packified/tutorial.json").toFile();
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (Exception e) {
                LogWindow.addError("Failed to create tutorial config file: " + e.getMessage());
            }
        }

        if (configFile.length() > 0) {
            try {
                String json = Files.readString(configFile.toPath());
                TutorialStage savedStage = GSON.fromJson(json, TutorialStage.class);
                if (savedStage != null) {
                    if (hasStage(savedStage)) {
                        if (savedStage.name.equals(stages.getLast().name)) {
                            // User already completed tutorial, so exit
                            isOpen.set(false);
                            return;
                        }
                        currentStage = getStageByName(savedStage.name);
                        loadedFromFile = true;
                    } else {
                        // If the saved stage is not in the list of stages, it is likely that there was an update to the tutorial system, so reset to the welcome stage
                        currentStage = WELCOME_STAGE;
                    }
                }
            } catch (Exception e) {
                LogWindow.addError("Failed to load tutorial config: " + e.getMessage());
                // If there is an error, reset to the welcome stage
                currentStage = WELCOME_STAGE;
            }
        } else {
            currentStage = WELCOME_STAGE;
        }
    }

    private static boolean hasStage(TutorialStage stage) {
        for (TutorialStage s : stages) {
            if (s.name.equals(stage.name)) {
                return true;
            }
        }
        return false;
    }

    private static TutorialStage getStageByName(String name) {
        for (TutorialStage stage : stages) {
            if (stage.name.equals(name)) {
                return stage;
            }
        }
        return null;
    }

    public static void updateTutorialConfig() {
        if (!localDirectoryPath.resolve("packified").toFile().exists()) {
            localDirectoryPath.resolve("packified").toFile().mkdirs();
        }
        File configFile = localDirectoryPath.resolve("packified/tutorial.json").toFile();
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (Exception e) {
                LogWindow.addError("Failed to create tutorial config file: " + e.getMessage());
            }
        }

        try {
            Files.writeString(configFile.toPath(), GSON.toJson(currentStage));
        } catch (Exception e) {
            LogWindow.addError("Failed to update tutorial config: " + e.getMessage());
        }
    }

    private static boolean startedTutorialFocus = false;

    public static boolean beginTutorialFocus(TutorialStage stage, String subStageDescription) {
        stage.subStages.add(subStageDescription);

        if (stage != currentStage) {
            return false;
        }

        if (stage.currentSubStage == stage.subStages.size() - 1) {
            startedTutorialFocus = true;
            return true;
        }
        return false;
    }

    public static boolean endTutorialFocus() {
        if (startedTutorialFocus) {
            startedTutorialFocus = false;
            return true;
        }
        return false;
    }

    public static void gatherSubStageAmounts() {
        if (currentStage.subStages.isEmpty() ? currentStage.currentSubStage > 0 : currentStage.currentSubStage > currentStage.subStages.size()-1) {
            if (stages.indexOf(currentStage) > stages.size()-1) {
                endTutorial();
            } else {
                currentStage.currentSubStage = currentStage.subStages.size()-1;
                currentStage = stages.get(stages.indexOf(currentStage) + 1);
                updateTutorialConfig();
            }
        }
        if (currentStage.currentSubStage < 0) {
            if (stages.indexOf(currentStage) > 0) {
                currentStage.currentSubStage = 0;
                currentStage = stages.get(stages.indexOf(currentStage) - 1);
                currentStage.currentSubStage = currentStage.subStages.size()-1;
                updateTutorialConfig();
            }
        }
    }

    public static void endFrame() {
        for (TutorialStage stage : stages) {
            stage.subStages.clear();
        }
    }

    public static void render() {
        if (!isOpen.get()) {
            return;
        }

        if (stages.isEmpty()) {
            return;
        }
        if (currentStage == null) {
            currentStage = stages.getFirst();
        }

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getLastWindowCenterPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.setNextWindowSize(400, 130);
        if (currentStage.customSize != null) {
            float customWidth = currentStage.customSize.x;
            float customHeight = currentStage.customSize.y;
            if (customWidth < 400) customWidth = 400; // Ensure minimum width
            if (customHeight < 130) customHeight = 130; // Ensure minimum height
            ImGui.setNextWindowSize(customWidth, customHeight);
        }

        if (loadedFromFile) {
            if (ImGui.begin("Tutorial", isOpen, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings)) {
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10, 10);
                ImGuiImplementation.centeredText("It seems like you did not finish the tutorial yet, would you like to continue?");
                ImGui.popStyleVar();
                if (ImGui.button("Yes, Continue Tutorial")) {
                    loadedFromFile = false;
                }
                ImGui.sameLine();
                if (ImGui.button("Yes, Reset Tutorial")) {
                    loadedFromFile = false;
                    resetTutorial();
                }
                ImGui.sameLine();
                if (ImGui.button("No")) {
                    loadedFromFile = false;
                    isOpen.set(false);
                    endTutorial();
                }
            }
            ImGui.end();
            return;
        }

        gatherSubStageAmounts();

        if (ImGui.begin(currentStage.name + " (" + (stages.indexOf(currentStage) + 1) + "/" + stages.size() + ")" + "##1", isOpen, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove)) {
            if (!currentStage.subStages.isEmpty() && currentStage.subStages.size() > currentStage.currentSubStage && currentStage.currentSubStage >= 0) {
                ImGuiImplementation.centeredText(currentStage.subStages.get(currentStage.currentSubStage));
            } else {
                ImGuiImplementation.centeredText(currentStage.description);
            }

            if (currentStage.action != null) {
                currentStage.action.execute();
            }

            if (stages.indexOf(currentStage) > 0 || currentStage.currentSubStage > 0) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing() - 10);
                if (ImGui.button("< Previous" + (!currentStage.subStages.isEmpty() && currentStage.currentSubStage > 0 ? " (" + (currentStage.currentSubStage) + "/" + currentStage.subStages.size() + ")" : ""))) {
                    currentStage.currentSubStage--;
                }
                ImGui.sameLine();
            }
            if (stages.indexOf(currentStage) == stages.size()-1 && currentStage.currentSubStage >= currentStage.subStages.size()-1) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - ImGui.calcTextSize("End Tutorial  ").x);
                ImGui.setCursorPosY(ImGui.getCursorPosY() + ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing() - 10);
                if (ImGui.button("End Tutorial")) {
                    endTutorial();
                }
            } else {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() -
                        ImGui.calcTextSize(!currentStage.subStages.isEmpty() && currentStage.currentSubStage < currentStage.subStages.size()-1 ? "Next > (0/0)  " : "Next >  ").x
                );
                ImGui.setCursorPosY(ImGui.getCursorPosY() + ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing() - 10);
                if (ImGui.button("Next >" + (!currentStage.subStages.isEmpty() && currentStage.currentSubStage < currentStage.subStages.size()-1 ? " (" + (currentStage.currentSubStage + 2) + "/" + currentStage.subStages.size() + ")" : ""))) {
                    currentStage.currentSubStage++;
                }
            }
        }

        gatherSubStageAmounts();
        ImGui.end();

        endFrame();
    }

    public static void resetTutorial() {
        currentStage = WELCOME_STAGE; // Reset to the welcome stage
        currentStage.currentSubStage = 0; // Reset to the first substage
        try {
            Files.writeString(localDirectoryPath.resolve("packified/tutorial.json"), GSON.toJson(currentStage));
        } catch (Exception e) {
            LogWindow.addError("Failed to reset tutorial: " + e.getMessage());
        }
    }

    public static void endTutorial() {
        isOpen.set(false);
        updateTutorialConfig();
    }

    public static class TutorialStage {
        String name;
        String description;
        public List<String> subStages = new ArrayList<>();
        public int currentSubStage = 0;
        public TutorialAction action = null;
        public ImVec2 customSize = null;

        public TutorialStage(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public TutorialStage(String name, String description, TutorialAction action) {
            this.name = name;
            this.description = description;
            this.action = action;
        }

        public TutorialStage(String name, String description, TutorialAction action, int customWidth, int customHeight) {
            this.name = name;
            this.description = description;
            this.action = action;
            this.customSize = new ImVec2(customWidth, customHeight);
        }

        @FunctionalInterface
        public interface TutorialAction {
            void execute();
        }
    }
}
