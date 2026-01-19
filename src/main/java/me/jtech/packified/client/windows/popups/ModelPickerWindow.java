package me.jtech.packified.client.windows.popups;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ModelPickerWindow {
    private static final ImBoolean open = new ImBoolean(false);

    private static final ImBoolean onlyCustom = new ImBoolean(true);
    private static final ImString namespaceFilter = new ImString("", 64);
    private static final ImString pathFilter = new ImString("", 64);

    private static final List<Identifier> itemModels = new ArrayList<>();
    private static int selectedIndex = -1;

    public static void open() {
        populateItemModels();
        selectedIndex = -1;
        namespaceFilter.set("");
        pathFilter.set("");
        onlyCustom.set(true);
        open.set(true);
    }

    private static void populateItemModels() {
        itemModels.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.resourceManager.findResources("models/item", s -> {
            String path = s.getPath();
            if (path.endsWith(".json")) {
                String modelPath = path.substring("models/".length(), path.length() - ".json".length());
                Identifier modelId = Identifier.of(s.getNamespace(), modelPath.replace("item/", ""));
                itemModels.add(modelId);
                itemModels.sort(Comparator.comparing(Identifier::toString));
            }
            return false;
        });
    }

    public static void render() {
        if (!open.get()) return;

        ImGuiImplementation.pushWindowCenterPos();

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getLastWindowCenterPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("Model Picker", open)) {

            // Filters
            ImGui.checkbox("Only custom (non-minecraft)", onlyCustom);
            ImGui.inputText("Namespace filter", namespaceFilter);
            ImGui.inputText("Path filter", pathFilter);

            // build filtered list
            String nsFilter = namespaceFilter.get().trim();
            String pFilter = pathFilter.get().trim();
            List<Identifier> filtered = itemModels.stream()
                    .filter(id -> !onlyCustom.get() || !id.getNamespace().equals("minecraft"))
                    .filter(id -> nsFilter.isEmpty() || id.getNamespace().contains(nsFilter))
                    .filter(id -> pFilter.isEmpty() || id.getPath().contains(pFilter))
                    .collect(Collectors.toList());

            // List area
            ImGui.beginChild("##model_list", 0, 300, true);
            for (int i = 0; i < filtered.size(); i++) {
                Identifier id = filtered.get(i);
                String label = id.getNamespace() + ":" + id.getPath();
                boolean isSelected = (selectedIndex >= 0 && selectedIndex < filtered.size() && filtered.get(selectedIndex).equals(id));
                if (ImGui.selectable(label, isSelected)) {
                    selectedIndex = i;
                }
            }
            ImGui.endChild();

            // Buttons
            if (ImGui.button("Cancel")) {
                open.set(false);
            }
            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - ImGui.calcTextSize("Confirm  ").x);
            if (ImGui.button("Confirm")) {
                applySelectedModel(filtered);
                open.set(false);
            }
            ImGui.end();
        }
    }

    private static void applySelectedModel(List<Identifier> filtered) {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return;
        Identifier selected = filtered.get(selectedIndex);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) return;

        stack.set(DataComponentTypes.ITEM_MODEL, selected); // This already updates client-sided
        mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + mc.player.getInventory().getSelectedSlot(), stack)); // Update the item server-sided, 36 is the base slot for the hotbar
    }
}
