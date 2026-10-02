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
    private static final String[] TABS = {"Players", "Themes", "Settings", "About"};
    private static final String[] SUBTITLES = {"players nearby", "pick a theme", "tweak the menu", "info"};

    // slider ids
    private static final int S_RADIUS = 0, S_DIM = 1, S_CORNER = 2, S_DELAY = 3;

    private static int tab = 0;

    private int x, y;
    private int scroll = 0;
    private int dragging = -1;
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

    @Override
    public void removed() {
        Config.save();
    }

    private Theme theme() {
        return Theme.ALL[Config.theme];
    }

    // ---------- geometry ----------
    private int cx() { return x + SIDE + 14; }
    private int contentW() { return W - SIDE - 28; }
    private int listY() { return y + 50; }
    private int listW() { return 240; }
    private int detailX() { return cx() + listW() + 12; }
    private int bottom() { return y + H - 14; }
    private int visibleRows() { return (bottom() - listY()) / ROW; }
    private int r() { return Config.corner; }

    /** returns {trackX, trackY, trackW} */
    private int[] track(int id) {
        if (id == S_RADIUS) {
            int sx = cx() + 170;
            return new int[]{sx, y + 32, x + W - 14 - sx};
        }
        if (id == S_DELAY && tab == 0) {
            int dx = detailX();
            return new int[]{dx + 12, listY() + 128, x + W - 14 - dx - 24};
        }
        int row = id == S_DIM ? 3 : (id == S_CORNER ? 4 : 6);
        int ry = listY() + row * 32;
        return new int[]{cx() + contentW() - 170, ry + 12, 150};
    }

    private double fraction(int id) {
        switch (id) {
            case S_RADIUS: return Math.log((double) Config.radius / MIN_RADIUS) / Math.log((double) MAX_RADIUS / MIN_RADIUS);
            case S_DIM: return Config.dim / 90.0;
            case S_DELAY: return (Config.bombDelay - 1) / 119.0;
            default: return Config.corner / 10.0;
        }
    }

    private void setFromMouse(int id, double mx) {
        int[] tr = track(id);
        double f = Math.max(0.0, Math.min(1.0, (mx - tr[0]) / tr[2]));
        switch (id) {
            case S_RADIUS: {
                double v = MIN_RADIUS * Math.pow((double) MAX_RADIUS / MIN_RADIUS, f);
                int step = v < 1000 ? 10 : 100;
                Config.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, (int) (Math.round(v / step) * step)));
                break;
            }
            case S_DIM: Config.dim = (int) Math.round(f * 90); break;
            case S_DELAY: Config.bombDelay = 1 + (int) Math.round(f * 119); break;
            default: Config.corner = (int) Math.round(f * 10); break;
        }
    }

    // ---------- drawing helpers ----------
    private void roundR(DrawContext c, int rx, int ry, int rw, int rh, int rad, int col) {
        rad = Math.max(0, Math.min(rad, Math.min(rw, rh) / 2));
        if (rad == 0) {
            c.fill(rx, ry, rx + rw, ry + rh, col);
            return;
        }
        for (int i = 0; i < rad; i++) {
            double dy = rad - i - 0.5;
            int inset = rad - (int) Math.round(Math.sqrt(rad * rad - dy * dy));
            c.fill(rx + inset, ry + i, rx + rw - inset, ry + i + 1, col);
            c.fill(rx + inset, ry + rh - i - 1, rx + rw - inset, ry + rh - i, col);
        }
        c.fill(rx, ry + rad, rx + rw, ry + rh - rad, col);
    }

    private void round(DrawContext c, int rx, int ry, int rw, int rh, int col) {
        roundR(c, rx, ry, rw, rh, r(), col);
    }

    private static int alpha(int col, int a) {
        return (col & 0x00FFFFFF) | (a << 24);
    }

    private static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int rr = (int) (ar + (br - ar) * f), rg = (int) (ag + (bg - ag) * f), rb = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void text(DrawContext c, String s, int tx, int ty, int col) {
        c.drawText(textRenderer, s, tx, ty, col, false);
    }

    private void textRight(DrawContext c, String s, int rightX, int ty, int col) {
        c.drawText(textRenderer, s, rightX - textRenderer.getWidth(s), ty, col, false);
    }

    private void refreshPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            players = new ArrayList<>();
            return;
        }
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && mc.player.distanceTo(p) <= Config.radius) list.add(p);
        }
        if (Config.sortByName) {
            list.sort(Comparator.comparing((PlayerEntity p) -> p.getName().getString().toLowerCase()));
        } else {
            list.sort(Comparator.comparingDouble(mc.player::distanceTo));
        }
        players = list;
        int max = Math.max(0, players.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Theme t = theme();
        refreshPlayers();

        ctx.fill(0, 0, width, height, (Config.dim * 255 / 100) << 24);

        int wr = r() + 2;
        roundR(ctx, x - 1, y - 1, W + 2, H + 2, wr + 1, alpha(t.accent(), 0x55));
        roundR(ctx, x, y, W, H, wr, t.bg());
        roundR(ctx, x, y, SIDE, H, wr, t.sidebar());
        ctx.fill(x + SIDE - wr, y, x + SIDE, y + H, t.sidebar());

        // logo
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        ctx.drawText(textRenderer, "cucumber", (int) ((x + 12) / 1.5f), (int) ((y + 14) / 1.5f), t.accent(), false);
        ctx.getMatrices().pop();
        roundR(ctx, x + 12, y + 40, SIDE - 24, 1, 0, t.panel());

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            int ty = y + 54 + i * 26;
            boolean active = tab == i;
            boolean hover = in(mx, my, x + 8, ty, SIDE - 16, 22);
            if (active) round(ctx, x + 8, ty, SIDE - 16, 22, t.panel());
            else if (hover) round(ctx, x + 8, ty, SIDE - 16, 22, alpha(t.panel(), 0x90));
            roundR(ctx, x + 16, ty + 9, 4, 4, 2, active ? t.accent() : t.dim());
            text(ctx, TABS[i], x + 26, ty + 7, active ? t.text() : t.dim());
        }
        text(ctx, "radius: " + Config.radius, x + 12, y + H - 16, t.dim());

        // header
        text(ctx, TABS[tab], cx(), y + 16, t.text());
        text(ctx, SUBTITLES[tab], cx(), y + 28, t.dim());

        switch (tab) {
            case 0: renderSlider(ctx, S_RADIUS, t); renderPlayers(ctx, mx, my, t); break;
            case 1: renderThemes(ctx, mx, my, t); break;
            case 2: renderSettings(ctx, mx, my, t); break;
            default: renderAbout(ctx, t); break;
        }
    }

    private void renderSlider(DrawContext ctx, int id, Theme t) {
        int[] tr = track(id);
        int fillW = (int) (tr[2] * fraction(id));
        roundR(ctx, tr[0], tr[1], tr[2], 4, 2, t.panel());
        roundR(ctx, tr[0], tr[1], Math.max(4, fillW), 4, 2, t.accent());
        roundR(ctx, tr[0] + fillW - 4, tr[1] - 3, 8, 10, 4, dragging == id ? t.accent() : t.text());
    }

    private void renderPlayers(DrawContext ctx, int mx, int my, Theme t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int[] tr = track(S_RADIUS);
        text(ctx, "Radius", tr[0], y + 16, t.dim());
        textRight(ctx, Config.radius + " blocks", tr[0] + tr[2], y + 16, t.text());

        int lx = cx(), ly = listY(), lw = listW();

        if (players.isEmpty()) {
            round(ctx, lx, ly, lw, 28, t.panel());
            text(ctx, "Nobody within " + Config.radius + " blocks", lx + 10, ly + 10, t.dim());
        }

        int end = Math.min(players.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            PlayerEntity p = players.get(i);
            int ry = ly + (i - scroll) * ROW;
            boolean sel = p.getUuid().equals(selected);
            boolean hover = in(mx, my, lx, ry, lw, ROW - 2);
            int bg = sel ? mix(t.panel(), t.accent(), 0.30f) : (hover ? mix(t.panel(), t.accent(), 0.12f) : t.panel());
            round(ctx, lx, ry, lw, ROW - 2, bg);

            String name = p.getName().getString();
            roundR(ctx, lx + 5, ry + 3, 14, 14, 7, sel ? t.accent() : alpha(t.accent(), 0xAA));
            String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            text(ctx, ini, lx + 5 + (14 - textRenderer.getWidth(ini)) / 2, ry + 6, 0xFFFFFFFF);
            text(ctx, name, lx + 25, ry + 6, t.text());
            if (Config.showDistance) {
                textRight(ctx, (int) mc.player.distanceTo(p) + "m", lx + lw - 8, ry + 6, t.dim());
            }
        }

        // detail panel
        int dx = detailX(), dw = x + W - 14 - dx;
        round(ctx, dx, ly, dw, bottom() - ly, t.panel());

        // timer controls (always visible)
        text(ctx, "Timer", dx + 12, ly + 110, t.dim());
        textRight(ctx, Config.bombDelay + "s", dx + dw - 12, ly + 110, t.text());
        renderSlider(ctx, S_DELAY, t);
        button(ctx, dx + 12, ly + 144, 24, 18, "-", mx, my, t);
        button(ctx, dx + dw - 36, ly + 144, 24, 18, "+", mx, my, t);

        PlayerEntity sp = getSelectedPlayer();
        if (sp == null) {
            text(ctx, "Select a player", dx + 12, ly + 12, t.dim());
            return;
        }
        String name = sp.getName().getString();
        roundR(ctx, dx + 12, ly + 12, 30, 30, 15, t.accent());
        String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        text(ctx, ini, dx + 12 + (30 - textRenderer.getWidth(ini)) / 2, ly + 23, 0xFFFFFFFF);
        text(ctx, name, dx + 50, ly + 14, t.text());
        text(ctx, (int) mc.player.distanceTo(sp) + " blocks away", dx + 50, ly + 27, t.dim());
        if (Config.showHealth) {
            text(ctx, "Health: " + (int) sp.getHealth() + " / " + (int) sp.getMaxHealth(), dx + 12, ly + 52, t.dim());
        }

        int bx = dx + 12, by = ly + 74, bw = dw - 24, bh = 26;
        boolean hover = in(mx, my, bx, by, bw, bh);
        boolean armed = Bomb.armed();
        roundR(ctx, bx, by, bw, bh, r() + 1, armed ? alpha(t.accent(), 0x77) : (hover ? t.accent() : alpha(t.accent(), 0xCC)));
        String label = armed ? "BOMB  " + Bomb.secondsLeft() + "s" : "BOMB";
        text(ctx, label, bx + (bw - textRenderer.getWidth(label)) / 2, by + 9, 0xFFFFFFFF);
    }

    private void renderThemes(DrawContext ctx, int mx, int my, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        for (int i = 0; i < Theme.ALL.length; i++) {
            Theme th = Theme.ALL[i];
            int ry = ly + i * 32;
            boolean active = i == Config.theme;
            boolean hover = in(mx, my, lx, ry, w, 28);
            round(ctx, lx, ry, w, 28, hover ? mix(t.panel(), t.accent(), 0.12f) : t.panel());
            if (active) roundR(ctx, lx + 3, ry + 6, 3, 16, 1, t.accent());
            text(ctx, th.name(), lx + 14, ry + 10, t.text());
            int sx = lx + w - 100;
            roundR(ctx, sx, ry + 7, 14, 14, 7, th.bg());
            roundR(ctx, sx + 18, ry + 7, 14, 14, 7, th.panel());
            roundR(ctx, sx + 36, ry + 7, 14, 14, 7, th.accent());
            if (active) text(ctx, "active", sx + 58, ry + 10, t.accent());
        }
    }

    private void toggle(DrawContext ctx, int tx, int ty, boolean on, Theme t) {
        roundR(ctx, tx, ty, 32, 14, 7, on ? t.accent() : alpha(t.dim(), 0x70));
        roundR(ctx, tx + (on ? 20 : 2), ty + 2, 10, 10, 5, 0xFFFFFFFF);
    }

    private void renderSettings(DrawContext ctx, int mx, int my, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        String[] labels = {"Show distance in list", "Show health in details", "Sort players by name",
                "Background dim", "Corner roundness", "Bomb sound", "Bomb delay"};
        for (int i = 0; i < labels.length; i++) {
            int ry = ly + i * 32;
            boolean hover = in(mx, my, lx, ry, w, 28);
            round(ctx, lx, ry, w, 28, hover && i < 3 ? mix(t.panel(), t.accent(), 0.10f) : t.panel());
            text(ctx, labels[i], lx + 12, ry + 10, t.text());
        }
        toggle(ctx, lx + w - 44, ly + 7, Config.showDistance, t);
        toggle(ctx, lx + w - 44, ly + 32 + 7, Config.showHealth, t);
        toggle(ctx, lx + w - 44, ly + 64 + 7, Config.sortByName, t);

        renderSlider(ctx, S_DIM, t);
        renderSlider(ctx, S_CORNER, t);
        renderSlider(ctx, S_DELAY, t);

        int sy = ly + 5 * 32, px = lx + w - 196;
        button(ctx, px, sy + 5, 18, 18, "<", mx, my, t);
        String nm = Bomb.NAMES[Config.bombSound];
        text(ctx, nm, px + 22 + (90 - textRenderer.getWidth(nm)) / 2, sy + 10, t.accent());
        button(ctx, px + 116, sy + 5, 18, 18, ">", mx, my, t);
        button(ctx, lx + w - 56, sy + 5, 46, 18, "Test", mx, my, t);
        int[] dl = track(S_DELAY);
        textRight(ctx, Config.bombDelay + "s", dl[0] - 10, ly + 6 * 32 + 10, t.dim());

        int[] d = track(S_DIM), c = track(S_CORNER);
        textRight(ctx, Config.dim + "%", d[0] - 10, ly + 3 * 32 + 10, t.dim());
        textRight(ctx, Config.corner + "px", c[0] - 10, ly + 4 * 32 + 10, t.dim());
    }

    private void button(DrawContext ctx, int bx, int by, int bw, int bh, String label, int mx, int my, Theme t) {
        boolean hover = in(mx, my, bx, by, bw, bh);
        roundR(ctx, bx, by, bw, bh, r(), hover ? mix(t.bg(), t.accent(), 0.35f) : t.bg());
        text(ctx, label, bx + (bw - textRenderer.getWidth(label)) / 2, by + (bh - 8) / 2, t.text());
    }

    private void renderAbout(DrawContext ctx, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        round(ctx, lx, ly, w, bottom() - ly, t.panel());
        String[] lines = {
                "cucumber v1.0.0",
                "",
                "Press O to open or close this menu (rebind in Controls).",
                "Players: shows everyone within the radius you set.",
                "Click a name to select it and see details.",
                "BOMB plays a sound after a delay (pick it in Settings).",
                "Themes: pick a color style, it is saved automatically.",
                "Settings: toggles, background dim and corner roundness.",
                "",
                "Note: the game only knows about players your client has",
                "loaded, so servers with low view distance show fewer."
        };
        for (int i = 0; i < lines.length; i++) {
            text(ctx, lines[i], lx + 14, ly + 14 + i * 12, i == 0 ? t.accent() : t.dim());
        }
    }

    private PlayerEntity getSelectedPlayer() {
        if (selected == null) return null;
        for (PlayerEntity p : players) if (p.getUuid().equals(selected)) return p;
        return null;
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, x + 8, y + 54 + i * 26, SIDE - 16, 22)) {
                tab = i;
                dragging = -1;
                return true;
            }
        }

        if (tab == 0) {
            int[] tr = track(S_RADIUS);
            if (in(mx, my, tr[0] - 4, tr[1] - 8, tr[2] + 8, 20)) {
                dragging = S_RADIUS;
                setFromMouse(S_RADIUS, mx);
                return true;
            }
            int tdx = detailX(), tdw = x + W - 14 - tdx;
            int[] dtr = track(S_DELAY);
            if (in(mx, my, dtr[0] - 4, dtr[1] - 8, dtr[2] + 8, 20)) {
                dragging = S_DELAY;
                setFromMouse(S_DELAY, mx);
                return true;
            }
            if (in(mx, my, tdx + 12, listY() + 144, 24, 18)) {
                Config.bombDelay = Math.max(1, Config.bombDelay - 1);
                return true;
            }
            if (in(mx, my, tdx + tdw - 36, listY() + 144, 24, 18)) {
                Config.bombDelay = Math.min(120, Config.bombDelay + 1);
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
                if (in(mx, my, dx + 12, listY() + 74, dw - 24, 26)) {
                    Bomb.start();
                    return true;
                }
            }
        } else if (tab == 1) {
            for (int i = 0; i < Theme.ALL.length; i++) {
                if (in(mx, my, cx(), listY() + i * 32, contentW(), 28)) {
                    Config.theme = i;
                    return true;
                }
            }
        } else if (tab == 2) {
            for (int i = 0; i < 3; i++) {
                if (in(mx, my, cx(), listY() + i * 32, contentW(), 28)) {
                    if (i == 0) Config.showDistance = !Config.showDistance;
                    if (i == 1) Config.showHealth = !Config.showHealth;
                    if (i == 2) Config.sortByName = !Config.sortByName;
                    return true;
                }
            }
            int sy = listY() + 5 * 32, px = cx() + contentW() - 196;
            int n = Bomb.NAMES.length;
            if (in(mx, my, px, sy + 5, 18, 18)) {
                Config.bombSound = (Config.bombSound + n - 1) % n;
                Bomb.play(Config.bombSound);
                return true;
            }
            if (in(mx, my, px + 116, sy + 5, 18, 18)) {
                Config.bombSound = (Config.bombSound + 1) % n;
                Bomb.play(Config.bombSound);
                return true;
            }
            if (in(mx, my, cx() + contentW() - 56, sy + 5, 46, 18)) {
                Bomb.play(Config.bombSound);
                return true;
            }
            for (int id : new int[]{S_DIM, S_CORNER, S_DELAY}) {
                int[] tr = track(id);
                if (in(mx, my, tr[0] - 4, tr[1] - 8, tr[2] + 8, 20)) {
                    dragging = id;
                    setFromMouse(id, mx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            setFromMouse(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = -1;
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
