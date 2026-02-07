package me.jtech.packified.client.util;

import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorCodeHighlighting {
    // Regex for vec3(r, g, b) and vec4(r, g, b, a)
    private static final Pattern VECTOR_COLOR_REGEX = Pattern.compile(
            "\\bvec([34])\\(\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)(?:\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?))?\\s*\\)"
    ); // bro wtf is this regex im going crazy

    private static final Pattern RGB_COLOR_REGEX = Pattern.compile(
            "\\brgb\\(\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)(?:\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?))?\\s*\\)"
    );

    // Regex for hex values like #FF5733
    private static final Pattern HEX_COLOR_REGEX = Pattern.compile(
            "#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{3})\\b", Pattern.CASE_INSENSITIVE
    );

    public static void parseColorCodes(String text) {
        String[] lines = text.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            Matcher vectorMatcher = VECTOR_COLOR_REGEX.matcher(lines[i]);
            List<float[]> vectorColors = new ArrayList<>();
            while (vectorMatcher.find()) {
                int type = Integer.parseInt(vectorMatcher.group(1)); // 3 or 4
                float r = parseFloat(vectorMatcher.group(2));
                float g = parseFloat(vectorMatcher.group(3));
                float b = parseFloat(vectorMatcher.group(4));
                float a = (type == 4 && vectorMatcher.group(5) != null) ? parseFloat(vectorMatcher.group(5)) : 1.0f;
                vectorColors.add(new float[]{r, g, b, a});
            }
            for (int idx = 0; idx < vectorColors.size(); idx++) {
                float[] c = vectorColors.get(idx);
                if (ImGui.beginChild("TextEditor")) {
                    float internalScrollY = ImGui.getScrollY();
                    drawColorIndicator(i + 1, idx, vectorColors.size(), c[0], c[1], c[2], c[3], internalScrollY);
                }
                ImGui.endChild();
            }

            Matcher rgbMatcher = RGB_COLOR_REGEX.matcher(lines[i]);
            List<float[]> rgbColors = new ArrayList<>();
            while (rgbMatcher.find()) {
                float r = parseFloat(rgbMatcher.group(1));
                float g = parseFloat(rgbMatcher.group(2));
                float b = parseFloat(rgbMatcher.group(3));
                rgbColors.add(new float[]{r, g, b, 1.0f});
            }
            for (int idx = 0; idx < rgbColors.size(); idx++) {
                float[] c = rgbColors.get(idx);
                if (ImGui.beginChild("TextEditor")) {
                    float internalScrollY = ImGui.getScrollY();
                    drawColorIndicator(i + 1, idx, rgbColors.size(), c[0], c[1], c[2], c[3], internalScrollY);
                }
                ImGui.endChild();
            }

            Matcher hexMatcher = HEX_COLOR_REGEX.matcher(lines[i]);
            List<float[]> hexColors = new ArrayList<>();
            while (hexMatcher.find()) {
                String hex = hexMatcher.group(1);
                float r = 0, g = 0, b = 0, a = 1.0f;
                if (hex.length() == 3 || hex.length() == 4) {
                    // Shorthand, like #RGB or #RGBA such as #F05 -> #FF0055
                    r = Integer.parseInt(hex.substring(0, 1), 16) / 15.0f;
                    g = Integer.parseInt(hex.substring(1, 2), 16) / 15.0f;
                    b = Integer.parseInt(hex.substring(2, 3), 16) / 15.0f;
                    if (hex.length() == 4) {
                        a = Integer.parseInt(hex.substring(3, 4), 16) / 15.0f;
                    }
                } else if (hex.length() == 6 || hex.length() == 8) {
                    // #RRGGBB or #RRGGBBAA
                    r = Integer.parseInt(hex.substring(0, 2), 16) / 255.0f;
                    g = Integer.parseInt(hex.substring(2, 4), 16) / 255.0f;
                    b = Integer.parseInt(hex.substring(4, 6), 16) / 255.0f;
                    if (hex.length() == 8) {
                        a = Integer.parseInt(hex.substring(6, 8), 16) / 255.0f;
                    }
                }
                hexColors.add(new float[]{r, g, b, a});
            }
            for (int idx = 0; idx < hexColors.size(); idx++) {
                float[] c = hexColors.get(idx);
                if (ImGui.beginChild("TextEditor")) {
                    float internalScrollY = ImGui.getScrollY();
                    drawColorIndicator(i + 1, idx, hexColors.size(), c[0], c[1], c[2], c[3], internalScrollY);
                }
                ImGui.endChild();
            }
        }
    }

    private static float parseFloat(String val) {
        return Float.parseFloat(val.replace("f", ""));
    }

    static void drawColorIndicator(int line, int index, int total, float r, float g, float b, float a, float scrollY) {
        if (total <= 0) return;

        float lineHeight = ImGui.getTextLineHeight();
        float spacing = ImGui.getTextLineHeightWithSpacing() - lineHeight;

        float startY = ImGui.getWindowPos().y + ImGui.getCursorPosY() + spacing * 0.5f;
        float yPos = startY + (line - 1) * ImGui.getTextLineHeight() - scrollY;

        if (yPos + lineHeight < startY) return;

        float windowPosX = ImGui.getWindowPos().x;

        float segmentWidth = lineHeight / total;
        float x0 = windowPosX + segmentWidth * index;
        float x1 = (index == total - 1)
                ? windowPosX + lineHeight   // absorb remainder on last segment
                : x0 + segmentWidth;

        int color = intToColor(
                (int)(r * 255),
                (int)(g * 255),
                (int)(b * 255),
                (int)(a * 255)
        );

        ImGui.getWindowDrawList().addRectFilled(
                x0,
                yPos,
                x1,
                yPos + lineHeight,
                color
        );
    }


    public static int intToColor(int r, int g, int b, int a) {
        return a << 24 | b << 16 | g << 8 | r;
    }
}
