package com.cucumber;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CucumberScreen extends Screen {
    private static final int W = 600, H = 300, SIDE = 110, ROW = 22;
    private static final int MIN_RADIUS = 100, MAX_RADIUS = 10000;
    private static final String[] TABS = {"Players", "Themes"};

    // persists between openings
    private static int themeIndex = 0;
    private static int tab = 0;
    private static int radius = 300;

    private boolean dragging = false;

    private int x, y;
    private int scroll = 0;
    private UUID selected = null;
    private List<PlayerEntity> players = new ArrayList<>();

    public CucumberScreen() {
        super(Text.literal("cucumber"));
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Theme theme() {
        return Theme.ALL[themeIndex];
    }

    // ---- geometry helpers ----
    private int cx() { return x + SIDE + 14; }
    private int listY() { return y + 50; }
    private int listW() { return 240; }
    private int sliderX() { return cx() + 170; }
    private int sliderW() { return x + W - 14 - sliderX(); }

    private void setRadiusFromMouse(double mx) {
        double f = Math.max(0.0, Math.min(1.0, (mx - sliderX()) / sliderW()));
        double v = MIN_RADIUS * Math.pow((double) MAX_RADIUS / MIN_RADIUS, f); // log scale
        int step = v < 1000 ? 10 : 100;
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, (int) (Math.round(v / step) * step)));
    }

    private double radiusFraction() {
        return Math.log((double) radius / MIN_RADIUS) / Math.log((double) MAX_RADIUS / MIN_RADIUS);
    }
    private int detailX() { return cx() + listW() + 12; }
    private int bottom() { return y + H - 14; }
    private int visibleRows() { return (bottom() - listY()) / ROW; }

    private void round(DrawContext c, int rx, int ry, int rw, int rh, int col) {
        c.fill(rx + 1, ry, rx + rw - 1, ry + rh, col);
        c.fill(rx, ry + 1, rx + rw, ry + rh - 1, col);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void refreshPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            players = new ArrayList<>();
            return;
        }
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && mc.player.distanceTo(p) <= radius) list.add(p);
        }
        list.sort(Comparator.comparingDouble(mc.player::distanceTo));
        players = list;
        int max = Math.max(0, players.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Theme t = theme();
        refreshPlayers();

        ctx.fill(0, 0, width, height, 0x90000000);
        round(ctx, x - 1, y - 1, W + 2, H + 2, (t.accent() & 0x00FFFFFF) | 0x55000000);
        round(ctx, x, y, W, H, t.bg());
        round(ctx, x, y, SIDE, H, t.sidebar());

        // logo
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        ctx.drawText(textRenderer, "cucumber", (int) ((x + 12) / 1.5f), (int) ((y + 14) / 1.5f), t.accent(), false);
        ctx.getMatrices().pop();
        ctx.fill(x + 12, y + 40, x + SIDE - 12, y + 41, t.panel());

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            int ty = y + 54 + i * 26;
            boolean active = tab == i;
            boolean hover = in(mx, my, x + 8, ty, SIDE - 16, 22);
            if (active || hover) round(ctx, x + 8, ty, SIDE - 16, 22, active ? t.panel() : (t.panel() & 0x00FFFFFF) | 0x80000000);
            if (active) ctx.fill(x + 8, ty + 4, x + 10, ty + 18, t.accent());
            ctx.drawText(textRenderer, TABS[i], x + 20, ty + 7, active ? t.text() : t.dim(), false);
        }
        ctx.drawText(textRenderer, "radius: " + radius + " blocks", x + 12, y + H - 16, t.dim(), false);

        // header
        ctx.drawText(textRenderer, TABS[tab], cx(), y + 16, t.text(), false);
        ctx.drawText(textRenderer, tab == 0 ? "players nearby" : "pick a theme", cx(), y + 28, t.dim(), false);

        if (tab == 0) renderSlider(ctx, t);
        if (tab == 0) renderPlayers(ctx, mx, my, t);
        else renderThemes(ctx, mx, my, t);
    }

    private void renderSlider(DrawContext ctx, Theme t) {
        int sx = sliderX(), sw = sliderW(), ty = y + 32;
        ctx.drawText(textRenderer, "Radius", sx, y + 16, t.dim(), false);
        String val = radius + " blocks";
        ctx.drawText(textRenderer, val, sx + sw - textRenderer.getWidth(val), y + 16, t.text(), false);
        round(ctx, sx, ty, sw, 4, t.panel());
        int fillW = (int) (sw * radiusFraction());
        round(ctx, sx, ty, Math.max(2, fillW), 4, t.accent());
        int kx = sx + fillW;
        round(ctx, kx - 3, ty - 3, 7, 10, dragging ? t.accent() : t.text());
    }

    private void renderPlayers(DrawContext ctx, int mx, int my, Theme t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int lx = cx(), ly = listY(), lw = listW();

        if (players.isEmpty()) {
            round(ctx, lx, ly, lw, bottom() - ly, t.panel());
            ctx.drawText(textRenderer, "Nobody within " + radius + " blocks", lx + 10, ly + 10, t.dim(), false);
        }

        int end = Math.min(players.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            PlayerEntity p = players.get(i);
            int ry = ly + (i - scroll) * ROW;
            boolean sel = p.getUuid().equals(selected);
            boolean hover = in(mx, my, lx, ry, lw, ROW - 2);
            int bg = sel ? ((t.accent() & 0x00FFFFFF) | 0x55000000) : (hover ? t.panel() + 0x00080808 : t.panel());
            round(ctx, lx, ry, lw, ROW - 2, bg);
            if (sel) ctx.fill(lx, ry + 3, lx + 2, ry + ROW - 5, t.accent());
            ctx.drawText(textRenderer, p.getName().getString(), lx + 8, ry + 6, t.text(), false);
            String d = (int) mc.player.distanceTo(p) + "m";
            ctx.drawText(textRenderer, d, lx + lw - 8 - textRenderer.getWidth(d), ry + 6, t.dim(), false);
        }

        // detail panel
        int dx = detailX(), dw = x + W - 14 - dx;
        round(ctx, dx, ly, dw, bottom() - ly, t.panel());

        PlayerEntity sp = getSelectedPlayer();
        if (sp == null) {
            ctx.drawText(textRenderer, "Select a player", dx + 10, ly + 10, t.dim(), false);
            return;
        }
        ctx.drawText(textRenderer, sp.getName().getString(), dx + 10, ly + 10, t.accent(), false);
        ctx.drawText(textRenderer, "Distance: " + (int) mc.player.distanceTo(sp) + " blocks", dx + 10, ly + 26, t.dim(), false);
        ctx.drawText(textRenderer, "Health: " + (int) sp.getHealth(), dx + 10, ly + 38, t.dim(), false);

        int bx = dx + 10, by = ly + 62, bw = dw - 20, bh = 24;
        boolean hover = in(mx, my, bx, by, bw, bh);
        round(ctx, bx, by, bw, bh, hover ? t.accent() : (t.accent() & 0x00FFFFFF) | 0xCC000000);
        String label = "BOMB";
        ctx.drawText(textRenderer, label, bx + (bw - textRenderer.getWidth(label)) / 2, by + 8, 0xFFFFFFFF, false);
    }

    private void renderThemes(DrawContext ctx, int mx, int my, Theme t) {
        int lx = cx(), ly = listY(), w = W - SIDE - 28;
        for (int i = 0; i < Theme.ALL.length; i++) {
            Theme th = Theme.ALL[i];
            int ry = ly + i * 30;
            boolean active = i == themeIndex;
            boolean hover = in(mx, my, lx, ry, w, 26);
            round(ctx, lx, ry, w, 26, hover ? t.panel() + 0x00080808 : t.panel());
            if (active) ctx.fill(lx, ry + 4, lx + 2, ry + 22, t.accent());
            ctx.drawText(textRenderer, th.name(), lx + 10, ry + 9, t.text(), false);
            int sx = lx + w - 90;
            ctx.fill(sx, ry + 6, sx + 14, ry + 20, th.bg());
            ctx.fill(sx + 18, ry + 6, sx + 32, ry + 20, th.panel());
            ctx.fill(sx + 36, ry + 6, sx + 50, ry + 20, th.accent());
            if (active) ctx.drawText(textRenderer, "on", sx + 58, ry + 9, t.accent(), false);
        }
    }

    private PlayerEntity getSelectedPlayer() {
        if (selected == null) return null;
        for (PlayerEntity p : players) if (p.getUuid().equals(selected)) return p;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, x + 8, y + 54 + i * 26, SIDE - 16, 22)) {
                tab = i;
                return true;
            }
        }

        if (tab == 0) {
            if (in(mx, my, sliderX() - 4, y + 26, sliderW() + 8, 18)) {
                dragging = true;
                setRadiusFromMouse(mx);
                return true;
            }
            int end = Math.min(players.size(), scroll + visibleRows());
            for (int i = scroll; i < end; i++) {
                int ry = listY() + (i - scroll) * ROW;
                if (in(mx, my, cx(), ry, listW(), ROW - 2)) {
                    selected = players.get(i).getUuid();
                    return true;
                }
            }
            if (getSelectedPlayer() != null) {
                int dx = detailX(), dw = x + W - 14 - dx;
                if (in(mx, my, dx + 10, listY() + 62, dw - 20, 24)) {
                    // BOMB: intentionally does nothing
                    return true;
                }
            }
        } else {
            int w = W - SIDE - 28;
            for (int i = 0; i < Theme.ALL.length; i++) {
                if (in(mx, my, cx(), listY() + i * 30, w, 26)) {
                    themeIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            setRadiusFromMouse(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (tab == 0) {
            int max = Math.max(0, players.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vAmount)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_O) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
