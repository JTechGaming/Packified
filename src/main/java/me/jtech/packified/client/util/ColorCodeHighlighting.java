package me.jtech.packified.client.util;

import imgui.ImGui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorCodeHighlighting {
    // Regex for vec3(r, g, b) and vec4(r, g, b, a)
    private static final Pattern VECTOR_COLOR_REGEX = Pattern.compile(
            "\\bvec([34])\\(\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?)(?:\\s*,\\s*([0-9]*\\.[0-9]+f?|[0-9]+f?))?\\s*\\)"
    ); // bro wtf is this regex im going crazy

    // Regex for hex values like #FF5733
    private static final Pattern HEX_COLOR_REGEX = Pattern.compile(
            "#([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{3})\\b", Pattern.CASE_INSENSITIVE
    );

    public static void parseColorCodes(String text) {
        String[] lines = text.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            Matcher vectorMatcher = VECTOR_COLOR_REGEX.matcher(lines[i]);
            while (vectorMatcher.find()) {
                int lineNumber = i + 1;
                int type = Integer.parseInt(vectorMatcher.group(1)); // 3 or 4

                // Parse RGBA components (normalized 0.0 - 1.0)
                float r = parseFloat(vectorMatcher.group(2));
                float g = parseFloat(vectorMatcher.group(3));
                float b = parseFloat(vectorMatcher.group(4));
                float a = (type == 4 && vectorMatcher.group(5) != null) ? parseFloat(vectorMatcher.group(5)) : 1.0f;

                if (ImGui.beginChild("TextEditor")) {
                    float internalScrollY = ImGui.getScrollY();
                    drawColorIndicator(lineNumber, r, g, b, a, internalScrollY);
                }
                ImGui.endChild();
            }

            Matcher hexMatcher = HEX_COLOR_REGEX.matcher(lines[i]);
            while (hexMatcher.find()) {
                int lineNumber = i + 1;
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

                if (ImGui.beginChild("TextEditor")) {
                    float internalScrollY = ImGui.getScrollY();
                    drawColorIndicator(lineNumber, r, g, b, a, internalScrollY);
                }
                ImGui.endChild();
            }
        }
    }

    private static float parseFloat(String val) {
        return Float.parseFloat(val.replace("f", ""));
    }

    static void drawColorIndicator(int line, float r, float g, float b, float a, float scrollY) {
        float lineHeight = ImGui.getTextLineHeight();
        float padding = (ImGui.getTextLineHeightWithSpacing() - lineHeight);
        float startY = ImGui.getWindowPos().y + ImGui.getCursorPosY() + padding / 2;

        float yPos = startY + (line - 1) * lineHeight - scrollY;
        float xPos = ImGui.getWindowPos().x; // Left margin/sideline

        if (yPos <= startY - lineHeight/2 + padding) {
            return;
        }

        // Add to drawlist
        int color = intToColor((int)(r*255), (int)(g*255), (int)(b*255), (int)(a*255));
        ImGui.getWindowDrawList().addRectFilled(xPos, yPos, xPos + lineHeight - padding*2.5f, yPos + lineHeight - padding, color);
    }

    public static int intToColor(int r, int g, int b, int a) {
        return a << 24 | b << 16 | g << 8 | r;
    }
}
