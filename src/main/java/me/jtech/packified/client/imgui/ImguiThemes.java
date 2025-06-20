package me.jtech.packified.client.imgui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import me.jtech.packified.client.util.ModConfig;

import java.util.Map;

/**
 * The themes in this class were derived from the BESS project made by Shivang Sharma.
 * You can find the class with the themes here:
 * https://github.com/shivang51/bess/blob/main/Bess/src/settings/themes.cpp
 * I should note that i did port the themes to Java, so the code looks a bit different.
 * I also went ahead and slightly modified some of them, so if you want to use the original themes,
 * you can find them in the original C++ code, and you can then port them to Java yourself.
 * Thanks to Shivang for making these themes, they are really nice!
 */

public class ImguiThemes {
    private static int oldTheme = 0;
    private static ImInt currentTheme = new ImInt((int) Math.round((double) ModConfig.getSettings().getOrDefault("theme", 0))); // Default to Modern Dark

    public static void setModernDarkColors() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = style.getColors();

        // Base color scheme
        colors[ImGuiCol.Text] = new float[]{0.92f, 0.92f, 0.92f, 1.00f};
        colors[ImGuiCol.TextDisabled] = new float[]{0.50f, 0.50f, 0.50f, 1.00f};
        colors[ImGuiCol.WindowBg] = new float[]{0.13f, 0.14f, 0.15f, 1.00f};
        colors[ImGuiCol.ChildBg] = new float[]{0.13f, 0.14f, 0.15f, 1.00f};
        colors[ImGuiCol.PopupBg] = new float[]{0.10f, 0.10f, 0.11f, 0.94f};
        colors[ImGuiCol.Border] = new float[]{0.43f, 0.43f, 0.50f, 0.50f};
        colors[ImGuiCol.BorderShadow] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};
        colors[ImGuiCol.FrameBg] = new float[]{0.20f, 0.21f, 0.22f, 1.00f};
        colors[ImGuiCol.FrameBgHovered] = new float[]{0.25f, 0.26f, 0.27f, 1.00f};
        colors[ImGuiCol.FrameBgActive] = new float[]{0.18f, 0.19f, 0.20f, 1.00f};
        colors[ImGuiCol.TitleBg] = new float[]{0.15f, 0.15f, 0.16f, 1.00f};
        colors[ImGuiCol.TitleBgActive] = new float[]{0.15f, 0.15f, 0.16f, 1.00f};
        colors[ImGuiCol.TitleBgCollapsed] = new float[]{0.15f, 0.15f, 0.16f, 1.00f};
        colors[ImGuiCol.MenuBarBg] = new float[]{0.20f, 0.20f, 0.21f, 1.00f};
        colors[ImGuiCol.ScrollbarBg] = new float[]{0.20f, 0.21f, 0.22f, 1.00f};
        colors[ImGuiCol.ScrollbarGrab] = new float[]{0.28f, 0.28f, 0.29f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabHovered] = new float[]{0.33f, 0.34f, 0.35f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabActive] = new float[]{0.40f, 0.40f, 0.41f, 1.00f};
        colors[ImGuiCol.CheckMark] = new float[]{0.76f, 0.76f, 0.76f, 1.00f};
        colors[ImGuiCol.SliderGrab] = new float[]{0.28f, 0.56f, 1.00f, 1.00f};
        colors[ImGuiCol.SliderGrabActive] = new float[]{0.37f, 0.61f, 1.00f, 1.00f};
        colors[ImGuiCol.Button] = new float[]{0.20f, 0.25f, 0.30f, 1.00f};
        colors[ImGuiCol.ButtonHovered] = new float[]{0.30f, 0.35f, 0.40f, 1.00f};
        colors[ImGuiCol.ButtonActive] = new float[]{0.25f, 0.30f, 0.35f, 1.00f};
        colors[ImGuiCol.Header] = new float[]{0.25f, 0.25f, 0.25f, 0.80f};
        colors[ImGuiCol.HeaderHovered] = new float[]{0.30f, 0.30f, 0.30f, 0.80f};
        colors[ImGuiCol.HeaderActive] = new float[]{0.35f, 0.35f, 0.35f, 0.80f};
        colors[ImGuiCol.Separator] = new float[]{0.43f, 0.43f, 0.50f, 0.50f};
        colors[ImGuiCol.SeparatorHovered] = new float[]{0.33f, 0.67f, 1.00f, 1.00f};
        colors[ImGuiCol.SeparatorActive] = new float[]{0.33f, 0.67f, 1.00f, 1.00f};
        colors[ImGuiCol.ResizeGrip] = new float[]{0.28f, 0.56f, 1.00f, 1.00f};
        colors[ImGuiCol.ResizeGripHovered] = new float[]{0.37f, 0.61f, 1.00f, 1.00f};
        colors[ImGuiCol.ResizeGripActive] = new float[]{0.37f, 0.61f, 1.00f, 1.00f};
        colors[ImGuiCol.Tab] = new float[]{0.15f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.TabHovered] = new float[]{0.38f, 0.48f, 0.69f, 1.00f};
        colors[ImGuiCol.TabActive] = new float[]{0.28f, 0.38f, 0.59f, 1.00f};
        colors[ImGuiCol.TabUnfocused] = new float[]{0.15f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.TabUnfocusedActive] = new float[]{0.15f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.DockingPreview] = new float[]{0.28f, 0.56f, 1.00f, 1.00f};
        colors[ImGuiCol.DockingEmptyBg] = new float[]{0.13f, 0.14f, 0.15f, 1.00f};
        colors[ImGuiCol.PlotLines] = new float[]{0.61f, 0.61f, 0.61f, 1.00f};
        colors[ImGuiCol.PlotLinesHovered] = new float[]{1.00f, 0.43f, 0.35f, 1.00f};
        colors[ImGuiCol.PlotHistogram] = new float[]{0.90f, 0.70f, 0.00f, 1.00f};
        colors[ImGuiCol.PlotHistogramHovered] = new float[]{1.00f, 0.60f, 0.00f, 1.00f};
        colors[ImGuiCol.TableHeaderBg] = new float[]{0.19f, 0.19f, 0.20f, 1.00f};
        colors[ImGuiCol.TableBorderStrong] = new float[]{0.31f, 0.31f, 0.35f, 1.00f};
        colors[ImGuiCol.TableBorderLight] = new float[]{0.23f, 0.23f, 0.25f, 1.00f};
        colors[ImGuiCol.TableRowBg] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};
        colors[ImGuiCol.TableRowBgAlt] = new float[]{1.00f, 1.00f, 1.00f, 0.06f};
        colors[ImGuiCol.TextSelectedBg] = new float[]{0.28f, 0.56f, 1.00f, 0.35f};
        colors[ImGuiCol.DragDropTarget] = new float[]{0.28f, 0.56f, 1.00f, 0.90f};
        colors[ImGuiCol.NavHighlight] = new float[]{0.28f, 0.56f, 1.00f, 1.00f};
        colors[ImGuiCol.NavWindowingHighlight] = new float[]{1.00f, 1.00f, 1.00f, 0.70f};
        colors[ImGuiCol.NavWindowingDimBg] = new float[]{0.80f, 0.80f, 0.80f, 0.20f};
        colors[ImGuiCol.ModalWindowDimBg] = new float[]{0.80f, 0.80f, 0.80f, 0.35f};

        style.setColors(colors);

        // Style adjustments
        style.setWindowRounding(5.3f);
        style.setFrameRounding(2.3f);
        style.setScrollbarRounding(0);

        style.setWindowTitleAlign(0.50f, 0.50f);
        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(5.0f, 5.0f);
        style.setItemSpacing(6.0f, 6.0f);
        style.setItemInnerSpacing(6.0f, 6.0f);
        style.setIndentSpacing(25.0f);
    }

    public static void setFluentUIColors() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = style.getColors();

        // General window settings
        style.setWindowRounding(5.0f);
        style.setFrameRounding(5.0f);
        style.setScrollbarRounding(5.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);
        style.setPopupRounding(5.0f);

        // Setting the colors
        colors[ImGuiCol.Text] = new float[]{0.95f, 0.95f, 0.95f, 1.00f};
        colors[ImGuiCol.TextDisabled] = new float[]{0.60f, 0.60f, 0.60f, 1.00f};
        colors[ImGuiCol.WindowBg] = new float[]{0.13f, 0.13f, 0.13f, 1.00f};
        colors[ImGuiCol.ChildBg] = new float[]{0.10f, 0.10f, 0.10f, 1.00f};
        colors[ImGuiCol.PopupBg] = new float[]{0.18f, 0.18f, 0.18f, 1.f};
        colors[ImGuiCol.Border] = new float[]{0.30f, 0.30f, 0.30f, 1.00f};
        colors[ImGuiCol.BorderShadow] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};
        colors[ImGuiCol.FrameBg] = new float[]{0.20f, 0.20f, 0.20f, 1.00f};
        colors[ImGuiCol.FrameBgHovered] = new float[]{0.25f, 0.25f, 0.25f, 1.00f};
        colors[ImGuiCol.FrameBgActive] = new float[]{0.30f, 0.30f, 0.30f, 1.00f};
        colors[ImGuiCol.TitleBg] = new float[]{0.10f, 0.10f, 0.10f, 1.00f};
        colors[ImGuiCol.TitleBgActive] = new float[]{0.20f, 0.20f, 0.20f, 1.00f};
        colors[ImGuiCol.TitleBgCollapsed] = new float[]{0.10f, 0.10f, 0.10f, 1.00f};
        colors[ImGuiCol.MenuBarBg] = new float[]{0.15f, 0.15f, 0.15f, 1.00f};
        colors[ImGuiCol.ScrollbarBg] = new float[]{0.10f, 0.10f, 0.10f, 1.00f};
        colors[ImGuiCol.ScrollbarGrab] = new float[]{0.20f, 0.20f, 0.20f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabHovered] = new float[]{0.25f, 0.25f, 0.25f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabActive] = new float[]{0.30f, 0.30f, 0.30f, 1.00f};

        // Accent colors changed to darker olive-green/grey shades
        colors[ImGuiCol.CheckMark] = new float[]{0.45f, 0.45f, 0.45f, 1.00f};        // Dark gray for check marks
        colors[ImGuiCol.SliderGrab] = new float[]{0.45f, 0.45f, 0.45f, 1.00f};       // Dark gray for sliders
        colors[ImGuiCol.SliderGrabActive] = new float[]{0.50f, 0.50f, 0.50f, 1.00f}; // Slightly lighter gray when active
        colors[ImGuiCol.Button] = new float[]{0.25f, 0.25f, 0.25f, 1.00f};           // Button background (dark gray)
        colors[ImGuiCol.ButtonHovered] = new float[]{0.30f, 0.30f, 0.30f, 1.00f};    // Button hover state
        colors[ImGuiCol.ButtonActive] = new float[]{0.35f, 0.35f, 0.35f, 1.00f};     // Button active state
        colors[ImGuiCol.Header] = new float[]{0.40f, 0.40f, 0.40f, 1.00f};           // Dark gray for menu headers
        colors[ImGuiCol.HeaderHovered] = new float[]{0.45f, 0.45f, 0.45f, 1.00f};    // Slightly lighter on hover
        colors[ImGuiCol.HeaderActive] = new float[]{0.50f, 0.50f, 0.50f, 1.00f};     // Lighter gray when active
        colors[ImGuiCol.Separator] = new float[]{0.30f, 0.30f, 0.30f, 1.00f};        // Separators in dark gray
        colors[ImGuiCol.SeparatorHovered] = new float[]{0.35f, 0.35f, 0.35f, 1.00f};
        colors[ImGuiCol.SeparatorActive] = new float[]{0.40f, 0.40f, 0.40f, 1.00f};
        colors[ImGuiCol.ResizeGrip] = new float[]{0.45f, 0.45f, 0.45f, 1.00f}; // Resize grips in dark gray
        colors[ImGuiCol.ResizeGripHovered] = new float[]{0.50f, 0.50f, 0.50f, 1.00f};
        colors[ImGuiCol.ResizeGripActive] = new float[]{0.55f, 0.55f, 0.55f, 1.00f};
        colors[ImGuiCol.Tab] = new float[]{0.18f, 0.18f, 0.18f, 1.00f};        // Tabs background
        colors[ImGuiCol.TabHovered] = new float[]{0.40f, 0.40f, 0.40f, 1.00f}; // Darker gray on hover
        colors[ImGuiCol.TabActive] = new float[]{0.40f, 0.40f, 0.40f, 1.00f};
        colors[ImGuiCol.TabUnfocused] = new float[]{0.18f, 0.18f, 0.18f, 1.00f};
        colors[ImGuiCol.TabUnfocusedActive] = new float[]{0.40f, 0.40f, 0.40f, 1.00f};
        colors[ImGuiCol.DockingPreview] = new float[]{0.45f, 0.45f, 0.45f, 1.00f}; // Docking preview in gray
        colors[ImGuiCol.DockingEmptyBg] = new float[]{0.18f, 0.18f, 0.18f, 1.00f}; // Empty dock background

        style.setColors(colors);

        // Additional styles
        style.setFramePadding(8.0f, 4.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setIndentSpacing(20.0f);
        style.setScrollbarSize(16.0f);
    }

    public static void setFluentUILightTheme() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = style.getColors();

        // General window settings
        style.setWindowRounding(5.0f);
        style.setFrameRounding(5.0f);
        style.setScrollbarRounding(5.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);
        style.setPopupRounding(5.0f);

        // Setting the colors (Light version)
        colors[ImGuiCol.Text] = new float[]{0.10f, 0.10f, 0.10f, 1.00f};
        colors[ImGuiCol.TextDisabled] = new float[]{0.60f, 0.60f, 0.60f, 1.00f};
        colors[ImGuiCol.WindowBg] = new float[]{0.95f, 0.95f, 0.95f, 1.00f}; // Light background
        colors[ImGuiCol.ChildBg] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};
        colors[ImGuiCol.PopupBg] = new float[]{0.98f, 0.98f, 0.98f, 1.00f};
        colors[ImGuiCol.Border] = new float[]{0.70f, 0.70f, 0.70f, 1.00f};
        colors[ImGuiCol.BorderShadow] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};
        colors[ImGuiCol.FrameBg] = new float[]{0.85f, 0.85f, 0.85f, 1.00f}; // Light frame background
        colors[ImGuiCol.FrameBgHovered] = new float[]{0.80f, 0.80f, 0.80f, 1.00f};
        colors[ImGuiCol.FrameBgActive] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.TitleBg] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};
        colors[ImGuiCol.TitleBgActive] = new float[]{0.85f, 0.85f, 0.85f, 1.00f};
        colors[ImGuiCol.TitleBgCollapsed] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};
        colors[ImGuiCol.MenuBarBg] = new float[]{0.95f, 0.95f, 0.95f, 1.00f};
        colors[ImGuiCol.ScrollbarBg] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};
        colors[ImGuiCol.ScrollbarGrab] = new float[]{0.80f, 0.80f, 0.80f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabHovered] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabActive] = new float[]{0.70f, 0.70f, 0.70f, 1.00f};

        // Accent colors with a soft pastel gray-green
        colors[ImGuiCol.CheckMark] = new float[]{0.55f, 0.65f, 0.55f, 1.00f}; // Soft gray-green for check marks
        colors[ImGuiCol.SliderGrab] = new float[]{0.55f, 0.65f, 0.55f, 1.00f};
        colors[ImGuiCol.SliderGrabActive] = new float[]{0.60f, 0.70f, 0.60f, 1.00f};
        colors[ImGuiCol.Button] = new float[]{0.85f, 0.85f, 0.85f, 1.00f}; // Light button background
        colors[ImGuiCol.ButtonHovered] = new float[]{0.80f, 0.80f, 0.80f, 1.00f};
        colors[ImGuiCol.ButtonActive] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.Header] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.HeaderHovered] = new float[]{0.70f, 0.70f, 0.70f, 1.00f};
        colors[ImGuiCol.HeaderActive] = new float[]{0.65f, 0.65f, 0.65f, 1.00f};
        colors[ImGuiCol.Separator] = new float[]{0.60f, 0.60f, 0.60f, 1.00f};
        colors[ImGuiCol.SeparatorHovered] = new float[]{0.65f, 0.65f, 0.65f, 1.00f};
        colors[ImGuiCol.SeparatorActive] = new float[]{0.70f, 0.70f, 0.70f, 1.00f};
        colors[ImGuiCol.ResizeGrip] = new float[]{0.55f, 0.65f, 0.55f, 1.00f}; // Accent color for resize grips
        colors[ImGuiCol.ResizeGripHovered] = new float[]{0.60f, 0.70f, 0.60f, 1.00f};
        colors[ImGuiCol.ResizeGripActive] = new float[]{0.65f, 0.75f, 0.65f, 1.00f};
        colors[ImGuiCol.Tab] = new float[]{0.85f, 0.85f, 0.85f, 1.00f}; // Tabs background
        colors[ImGuiCol.TabHovered] = new float[]{0.80f, 0.80f, 0.80f, 1.00f};
        colors[ImGuiCol.TabActive] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.TabUnfocused] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};
        colors[ImGuiCol.TabUnfocusedActive] = new float[]{0.75f, 0.75f, 0.75f, 1.00f};
        colors[ImGuiCol.DockingPreview] = new float[]{0.55f, 0.65f, 0.55f, 1.00f}; // Docking preview in gray-green
        colors[ImGuiCol.DockingEmptyBg] = new float[]{0.90f, 0.90f, 0.90f, 1.00f};

        // Additional styles
        style.setFramePadding(8.0f, 4.0f);
        style.setItemSpacing(8.0f, 4.0f);
        style.setIndentSpacing(20.0f);
        style.setScrollbarSize(16.0f);
    }

    public static void setDeepDarkTheme() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = style.getColors();

        // Primary background
        colors[ImGuiCol.WindowBg] = new float[]{0.07f, 0.07f, 0.09f, 1.00f};  // #131318
        colors[ImGuiCol.MenuBarBg] = new float[]{0.12f, 0.12f, 0.15f, 1.00f}; // #131318
        colors[ImGuiCol.ChildBg] = new float[]{0.10f, 0.10f, 0.12f, 0.00f}; // #1A1A1F
        colors[ImGuiCol.PopupBg] = new float[]{0.18f, 0.18f, 0.22f, 1.00f};

        // Headers
        colors[ImGuiCol.Header] = new float[]{0.18f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.HeaderHovered] = new float[]{0.30f, 0.30f, 0.40f, 1.00f};
        colors[ImGuiCol.HeaderActive] = new float[]{0.25f, 0.25f, 0.35f, 1.00f};

        // Buttons
        colors[ImGuiCol.Button] = new float[]{0.20f, 0.22f, 0.27f, 1.00f};
        colors[ImGuiCol.ButtonHovered] = new float[]{0.30f, 0.32f, 0.40f, 1.00f};
        colors[ImGuiCol.ButtonActive] = new float[]{0.35f, 0.38f, 0.50f, 1.00f};

        // Frame BG
        colors[ImGuiCol.FrameBg] = new float[]{0.15f, 0.15f, 0.18f, 1.00f};
        colors[ImGuiCol.FrameBgHovered] = new float[]{0.22f, 0.22f, 0.27f, 1.00f};
        colors[ImGuiCol.FrameBgActive] = new float[]{0.25f, 0.25f, 0.30f, 1.00f};

        // Tabs
        colors[ImGuiCol.Tab] = new float[]{0.18f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.TabHovered] = new float[]{0.35f, 0.35f, 0.50f, 1.00f};
        colors[ImGuiCol.TabActive] = new float[]{0.25f, 0.25f, 0.38f, 1.00f};
        colors[ImGuiCol.TabUnfocused] = new float[]{0.13f, 0.13f, 0.17f, 1.00f};
        colors[ImGuiCol.TabUnfocusedActive] = new float[]{0.20f, 0.20f, 0.25f, 1.00f};

        // Title
        colors[ImGuiCol.TitleBg] = new float[]{0.12f, 0.12f, 0.15f, 1.00f};
        colors[ImGuiCol.TitleBgActive] = new float[]{0.15f, 0.15f, 0.20f, 1.00f};
        colors[ImGuiCol.TitleBgCollapsed] = new float[]{0.10f, 0.10f, 0.12f, 1.00f};

        // Borders
        colors[ImGuiCol.Border] = new float[]{0.20f, 0.20f, 0.25f, 0.50f};
        colors[ImGuiCol.BorderShadow] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};

        // Text
        colors[ImGuiCol.Text] = new float[]{0.90f, 0.90f, 0.95f, 1.00f};
        colors[ImGuiCol.TextDisabled] = new float[]{0.50f, 0.50f, 0.55f, 1.00f};

        // Highlights
        colors[ImGuiCol.CheckMark] = new float[]{0.50f, 0.70f, 1.00f, 1.00f};
        colors[ImGuiCol.SliderGrab] = new float[]{0.50f, 0.70f, 1.00f, 1.00f};
        colors[ImGuiCol.SliderGrabActive] = new float[]{0.60f, 0.80f, 1.00f, 1.00f};
        colors[ImGuiCol.ResizeGrip] = new float[]{0.50f, 0.70f, 1.00f, 0.50f};
        colors[ImGuiCol.ResizeGripHovered] = new float[]{0.60f, 0.80f, 1.00f, 0.75f};
        colors[ImGuiCol.ResizeGripActive] = new float[]{0.70f, 0.90f, 1.00f, 1.00f};

        // Scrollbar
        colors[ImGuiCol.ScrollbarBg] = new float[]{0.10f, 0.10f, 0.12f, 1.00f};
        colors[ImGuiCol.ScrollbarGrab] = new float[]{0.30f, 0.30f, 0.35f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabHovered] = new float[]{0.40f, 0.40f, 0.50f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabActive] = new float[]{0.45f, 0.45f, 0.55f, 1.00f};

        style.setColors(colors);

        // Style tweaks
        style.setWindowRounding(5.0f);
        style.setFrameRounding(5.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setPopupRounding(5.0f);
        style.setScrollbarRounding(5.0f);
        style.setWindowPadding(10, 10);
        style.setFramePadding(6, 4);
        style.setItemSpacing(8, 6);
        style.setPopupBorderSize(0.f);
    }

    public static void setDeepDarkBlueAccentTheme() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = style.getColors();

        // Primary background
        colors[ImGuiCol.WindowBg] = new float[]{0.07f, 0.07f, 0.09f, 1.00f};  // #131318
        colors[ImGuiCol.MenuBarBg] = new float[]{0.12f, 0.12f, 0.15f, 1.00f}; // #131318
        colors[ImGuiCol.ChildBg] = new float[]{0.10f, 0.10f, 0.12f, 0.00f}; // #1A1A1F
        colors[ImGuiCol.PopupBg] = new float[]{0.18f, 0.18f, 0.22f, 1.00f};

        // Headers
        colors[ImGuiCol.Header] = new float[]{0.18f, 0.18f, 0.22f, 1.00f};
        colors[ImGuiCol.HeaderHovered] = new float[]{0.30f, 0.30f, 0.40f, 1.00f};
        colors[ImGuiCol.HeaderActive] = new float[]{0.25f, 0.25f, 0.35f, 1.00f};

        // Buttons
        colors[ImGuiCol.Button] = new float[]{0.15f, 0.19f, 0.40f, 1.00f};
        colors[ImGuiCol.ButtonHovered] = new float[]{0.30f, 0.32f, 0.53f, 1.00f};
        colors[ImGuiCol.ButtonActive] = new float[]{0.35f, 0.38f, 0.63f, 1.00f};

        // Frame BG
        colors[ImGuiCol.FrameBg] = new float[]{0.15f, 0.15f, 0.18f, 1.00f};
        colors[ImGuiCol.FrameBgHovered] = new float[]{0.22f, 0.22f, 0.27f, 1.00f};
        colors[ImGuiCol.FrameBgActive] = new float[]{0.25f, 0.25f, 0.30f, 1.00f};

        // Tabs
        colors[ImGuiCol.Tab] = new float[]{0.12f, 0.16f, 0.37f, 1.00f};
        colors[ImGuiCol.TabHovered] = new float[]{0.13f, 0.17f, 0.38f, 1.00f};
        colors[ImGuiCol.TabActive] = new float[]{0.15f, 0.19f, 0.40f, 1.00f};
        colors[ImGuiCol.TabUnfocused] = new float[]{0.09f, 0.13f, 0.34f, 1.00f};
        colors[ImGuiCol.TabUnfocusedActive] = new float[]{0.11f, 0.15f, 0.36f, 1.00f};

        // Title
        colors[ImGuiCol.TitleBg] = new float[]{0.12f, 0.12f, 0.15f, 1.00f};
        colors[ImGuiCol.TitleBgActive] = new float[]{0.15f, 0.15f, 0.20f, 1.00f};
        colors[ImGuiCol.TitleBgCollapsed] = new float[]{0.10f, 0.10f, 0.12f, 1.00f};

        // Borders
        colors[ImGuiCol.Border] = new float[]{0.20f, 0.20f, 0.25f, 0.50f};
        colors[ImGuiCol.BorderShadow] = new float[]{0.00f, 0.00f, 0.00f, 0.00f};

        // Text
        colors[ImGuiCol.Text] = new float[]{0.90f, 0.90f, 0.95f, 1.00f};
        colors[ImGuiCol.TextDisabled] = new float[]{0.50f, 0.50f, 0.55f, 1.00f};

        // Highlights
        colors[ImGuiCol.CheckMark] = new float[]{0.50f, 0.70f, 1.00f, 1.00f};
        colors[ImGuiCol.SliderGrab] = new float[]{0.50f, 0.70f, 1.00f, 1.00f};
        colors[ImGuiCol.SliderGrabActive] = new float[]{0.60f, 0.80f, 1.00f, 1.00f};
        colors[ImGuiCol.ResizeGrip] = new float[]{0.50f, 0.70f, 1.00f, 0.50f};
        colors[ImGuiCol.ResizeGripHovered] = new float[]{0.60f, 0.80f, 1.00f, 0.75f};
        colors[ImGuiCol.ResizeGripActive] = new float[]{0.70f, 0.90f, 1.00f, 1.00f};

        // Scrollbar
        colors[ImGuiCol.ScrollbarBg] = new float[]{0.10f, 0.10f, 0.12f, 1.00f};
        colors[ImGuiCol.ScrollbarGrab] = new float[]{0.30f, 0.30f, 0.35f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabHovered] = new float[]{0.40f, 0.40f, 0.50f, 1.00f};
        colors[ImGuiCol.ScrollbarGrabActive] = new float[]{0.45f, 0.45f, 0.55f, 1.00f};

        style.setColors(colors);

        // Style tweaks
        style.setWindowRounding(5.0f);
        style.setFrameRounding(5.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setPopupRounding(5.0f);
        style.setScrollbarRounding(5.0f);
        style.setWindowPadding(10, 10);
        style.setFramePadding(6, 4);
        style.setItemSpacing(8, 6);
        style.setPopupBorderSize(0.f);
    }

    public static ImInt getCurrentTheme() {
        return currentTheme;
    }

    public static String[] getAvailableThemes() {
        return new String[]{
                "Deep Dark",
                "Deep Dark - Blue Accent",
                "Fluent UI",
                "Modern Dark",
                "Fluent UI - Light"
        };
    }

    public static void setTheme(ImInt currentTheme) {
        if (currentTheme.get() != oldTheme) {
            oldTheme = currentTheme.get();
            switch (currentTheme.get()) {
                case 0:
                    setDeepDarkTheme();
                    break;
                case 1:
                    setDeepDarkBlueAccentTheme();
                    break;
                case 2:
                    setFluentUIColors();
                    break;
                case 3:
                    setModernDarkColors();
                    break;
                case 4:
                    setFluentUILightTheme();
                    break;
                default:
                    setDeepDarkTheme(); // Fallback to Modern Dark
            }

            ModConfig.updateSettings(Map.of("theme", currentTheme.get()));
        }
    }
}
