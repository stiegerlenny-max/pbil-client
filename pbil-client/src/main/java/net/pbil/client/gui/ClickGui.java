package net.pbil.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.pbil.client.module.Category;
import net.pbil.client.module.Module;
import net.pbil.client.module.ModuleManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modul-Menue im Stil des Screenshots. Eingaben werden ueber GLFW abgefragt,
 * damit der Code nicht an den Eingabe-Signaturen einzelner Minecraft-Versionen haengt.
 *
 * Linksklick: Modul an/aus   |   Mittelklick: Taste zuweisen (Esc = loeschen)
 * Scrollrad ueber einem Modul: Wert aendern   |   Tippen: Module suchen
 */
public class ClickGui extends Screen {
    private static final int PANEL_W = 120;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 16;
    private static final int GAP = 10;

    private static final int COL_HEADER = 0xF0121218;
    private static final int COL_ROW = 0xD01B1B22;
    private static final int COL_ROW_HOVER = 0xD0272731;
    private static final int COL_ON = 0xFF2F6BFF;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_DIM = 0xFF9AA0AE;

    private final Map<Integer, Boolean> lastKeys = new HashMap<>();
    private boolean lastLeft, lastMiddle;
    private Module binding = null;
    private String query = "";
    private int mouseX, mouseY;

    public ClickGui() {
        super(Text.literal("Pbil Client"));
    }

    @Override
    protected void init() {
        // aktuellen Tastenstatus merken, damit das noch gehaltene P nichts ausloest
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            lastKeys.put(k, GLFW.glfwGetKey(w, k) == GLFW.GLFW_PRESS);
        }
        lastLeft = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        lastMiddle = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return binding == null;
    }

    private List<Module> modulesOf(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.category != c) continue;
            if (!query.isEmpty() && !m.name.toLowerCase().contains(query.toLowerCase())) continue;
            out.add(m);
        }
        return out;
    }

    private int panelX(int index) {
        int total = Category.values().length * PANEL_W + (Category.values().length - 1) * GAP;
        int start = Math.max(10, (this.width - total) / 2);
        return start + index * (PANEL_W + GAP);
    }

    /** Liefert das Modul unter dem Mauszeiger oder null. */
    private Module moduleAt(int mx, int my) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int x = panelX(i);
            int y = 40 + HEADER_H;
            for (Module m : modulesOf(cats[i])) {
                if (mx >= x && mx < x + PANEL_W && my >= y && my < y + ROW_H) return m;
                y += ROW_H;
            }
        }
        return null;
    }

    private boolean pressedNow(long w, int key) {
        boolean down = GLFW.glfwGetKey(w, key) == GLFW.GLFW_PRESS;
        boolean was = lastKeys.getOrDefault(key, false);
        lastKeys.put(key, down);
        return down && !was;
    }

    private void pollInput() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();

        // Tastenbelegung
        if (binding != null) {
            for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
                if (pressedNow(w, k)) {
                    binding.key = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
                    binding = null;
                    net.pbil.client.Config.save();
                    break;
                }
            }
        } else {
            // Suche
            for (int k = GLFW.GLFW_KEY_A; k <= GLFW.GLFW_KEY_Z; k++) {
                if (pressedNow(w, k)) query += (char) ('a' + (k - GLFW.GLFW_KEY_A));
            }
            for (int k = GLFW.GLFW_KEY_0; k <= GLFW.GLFW_KEY_9; k++) {
                if (pressedNow(w, k)) query += (char) ('0' + (k - GLFW.GLFW_KEY_0));
            }
            if (pressedNow(w, GLFW.GLFW_KEY_SPACE)) query += " ";
            if (pressedNow(w, GLFW.GLFW_KEY_BACKSPACE) && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            }
        }

        // Mausklicks
        boolean left = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean middle = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        Module hit = moduleAt(mouseX, mouseY);
        if (hit != null && binding == null) {
            if (left && !lastLeft) hit.toggle();
            if (middle && !lastMiddle) binding = hit;
        }
        lastLeft = left;
        lastMiddle = middle;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        Module m = moduleAt((int) mx, (int) my);
        if (m != null && m.hasValue) {
            m.adjustValue(vertical > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        this.mouseX = mx;
        this.mouseY = my;
        super.render(ctx, mx, my, delta);
        pollInput();

        Category[] cats = Category.values();
        Module hovered = moduleAt(mx, my);

        for (int i = 0; i < cats.length; i++) {
            int x = panelX(i);
            int y = 40;
            ctx.fill(x, y, x + PANEL_W, y + HEADER_H, COL_HEADER);
            ctx.drawText(this.textRenderer, cats[i].label, x + 8, y + 5, COL_TEXT, false);
            y += HEADER_H;

            for (Module m : modulesOf(cats[i])) {
                int bg = m.isEnabled() ? COL_ON : (m == hovered ? COL_ROW_HOVER : COL_ROW);
                ctx.fill(x, y, x + PANEL_W, y + ROW_H, bg);
                ctx.drawText(this.textRenderer, m.name, x + 6, y + 4, COL_TEXT, false);

                String right = "";
                if (binding == m) right = "...";
                else if (m.key > 0) right = keyName(m.key);
                else if (m.hasValue) right = trimValue(m.value);
                if (!right.isEmpty()) {
                    int tw = this.textRenderer.getWidth(right);
                    ctx.drawText(this.textRenderer, right, x + PANEL_W - tw - 6, y + 4, m.isEnabled() ? COL_TEXT : COL_DIM, false);
                }
                y += ROW_H;
            }
        }

        // Suchfeld
        int sx = this.width - PANEL_W - 10;
        ctx.fill(sx, 10, sx + PANEL_W, 10 + HEADER_H, COL_HEADER);
        String shown = query.isEmpty() ? "Module suchen..." : query + "_";
        ctx.drawText(this.textRenderer, shown, sx + 8, 15, query.isEmpty() ? COL_DIM : COL_TEXT, false);

        // Hinweiszeile
        String tip;
        if (binding != null) tip = "Taste fuer " + binding.name + " druecken (Esc = loeschen)";
        else if (hovered != null) tip = hovered.name + ": " + hovered.description
                + (hovered.hasValue ? "  [" + hovered.valueName + " " + trimValue(hovered.value) + "]" : "");
        else tip = "Linksklick = an/aus  |  Mittelklick = Taste zuweisen  |  Scrollrad = Wert";
        ctx.drawText(this.textRenderer, tip, 10, this.height - 14, COL_DIM, false);
    }

    private static String trimValue(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private static String keyName(int key) {
        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null) return n.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            default -> "K" + key;
        };
    }
}
