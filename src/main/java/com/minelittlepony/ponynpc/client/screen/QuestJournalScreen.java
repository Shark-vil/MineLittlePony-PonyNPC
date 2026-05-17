package com.minelittlepony.ponynpc.client.screen;

import com.minelittlepony.ponynpc.network.QuestJournalPayload;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Дневник квестов в стиле DialogScreen.
 *
 * Левая панель (~30%): список квестов с вкладками «В процессе» / «Завершено».
 * Правая панель (~70%): детали выбранного квеста со списком этапов.
 */
public class QuestJournalScreen extends Screen {

    // Цвета (та же палитра, что в DialogScreen)
    private static final int C_BG            = 0xCC000000;
    private static final int C_PANEL         = 0x99111111;
    private static final int C_SEP           = 0xFF555555;
    private static final int C_GOLD          = 0xFFD4A017;
    private static final int C_TEXT          = 0xFFE8E8E8;
    private static final int C_GRAY          = 0xFF888888;
    private static final int C_HINT          = 0xFF666666;
    private static final int C_GREEN         = 0xFF55FF55;
    private static final int C_WHITE         = 0xFFFFFFFF;
    private static final int C_AMBER         = 0xFFFFAA00;
    private static final int C_HOVER_BG      = 0x44FFFFFF;
    private static final int C_SEL_BG        = 0x66FFFFFF;
    private static final int C_CUR_STAGE_BG  = 0x22FFFFFF;
    private static final int C_PROG_BG       = 0xFF2A2A2A;
    private static final int C_PROG_FILL     = 0xFF55AA55;

    private static final int MARGIN = 14;
    private static final int ITEM_H = 20;

    private final List<QuestJournalPayload.QuestEntry> entries;

    private enum Tab { ACTIVE, COMPLETED }

    private Tab activeTab     = Tab.ACTIVE;
    private int selectedIndex = 0;
    private int listScroll    = 0;
    private int detailScroll  = 0;

    public QuestJournalScreen(QuestJournalPayload payload) {
        super(Text.literal("Дневник квестов"));
        this.entries = payload.entries();
    }

    // ── Вспомогательные методы ─────────────────────────────────────────

    private List<QuestJournalPayload.QuestEntry> filtered() {
        return entries.stream()
            .filter(e -> activeTab == Tab.COMPLETED ? e.complete() : !e.complete())
            .toList();
    }

    private int contentTop()  { return MARGIN + 26 + 22 + 3; }
    private int contentBot()  { return height - MARGIN - 16; }
    private int leftPanelW()  { return width * 28 / 100; }
    private int listLeft()    { return MARGIN; }
    private int listRight()   { return MARGIN + leftPanelW(); }
    private int detailLeft()  { return listRight() + 8; }
    private int detailRight() { return width - MARGIN; }
    private int tabY()        { return MARGIN + 27; }
    private int tabH()        { return 20; }

    // ── Рендер ────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        // Собственный фон — стандартный blur не нужен
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = width, h = height;
        int ct = contentTop(), cb = contentBot();

        // Фон
        ctx.fill(0, 0, w, h, C_BG);

        // ── Заголовок ──────────────────────────────────────────────
        int hT = MARGIN, hB = hT + 26;
        ctx.fill(MARGIN, hT, w - MARGIN, hB, C_PANEL);
        ctx.fill(MARGIN,         hT,     w - MARGIN, hT + 1, C_SEP);
        ctx.fill(MARGIN,         hB - 1, w - MARGIN, hB,     C_SEP);
        ctx.fill(MARGIN,         hT,     MARGIN + 1,  hB,     C_SEP);
        ctx.fill(w - MARGIN - 1, hT,     w - MARGIN,  hB,     C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "✦ ДНЕВНИК КВЕСТОВ ✦", w / 2, hT + 8, C_GOLD);

        // ── Вкладки ───────────────────────────────────────────────
        drawTabs(ctx, mx, my);

        // ── Фон панелей ───────────────────────────────────────────
        ctx.fill(listLeft(),   ct, listRight(),   cb, C_PANEL);
        ctx.fill(detailLeft(), ct, detailRight(), cb, C_PANEL);

        // Рамки левой панели
        ctx.fill(listLeft(),         ct,     listRight(),     ct + 1, C_SEP);
        ctx.fill(listLeft(),         cb - 1, listRight(),     cb,     C_SEP);
        ctx.fill(listLeft(),         ct,     listLeft() + 1,  cb,     C_SEP);
        ctx.fill(listRight() - 1,    ct,     listRight(),     cb,     C_SEP);

        // Рамки правой панели
        ctx.fill(detailLeft(),        ct,     detailRight(),     ct + 1, C_SEP);
        ctx.fill(detailLeft(),        cb - 1, detailRight(),     cb,     C_SEP);
        ctx.fill(detailLeft(),        ct,     detailLeft() + 1,  cb,     C_SEP);
        ctx.fill(detailRight() - 1,   ct,     detailRight(),     cb,     C_SEP);

        // Декоративный разделитель между панелями
        int sepX    = listRight() + 3;
        int sepMidY = (ct + cb) / 2;
        ctx.fill(sepX, ct, sepX + 1, cb, C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "◆", sepX, sepMidY - 4, C_GOLD);

        // ── Содержимое левой панели (со скроллом) ─────────────────
        ctx.enableScissor(listLeft() + 1, ct + 1, listRight() - 1, cb - 1);
        drawQuestList(ctx, mx, my);
        ctx.disableScissor();

        // ── Содержимое правой панели (со скроллом) ─────────────────
        ctx.enableScissor(detailLeft() + 1, ct + 1, detailRight() - 1, cb - 1);
        drawQuestDetail(ctx, mx, my);
        ctx.disableScissor();

        // Подсказка внизу
        ctx.drawCenteredTextWithShadow(textRenderer,
            "[ J / ESC — закрыть ]", w / 2, h - MARGIN - 6, C_HINT);

        super.render(ctx, mx, my, delta);
    }

    private void drawTabs(DrawContext ctx, int mx, int my) {
        int ty = tabY(), th = tabH();
        int totalW = width - 2 * MARGIN;
        int tw1    = totalW / 2;
        int tx1    = MARGIN;
        int tx2    = MARGIN + tw1;
        int tw2    = totalW - tw1;

        // Вкладка «В процессе»
        boolean h1 = mx >= tx1 && mx < tx2 && my >= ty && my < ty + th;
        boolean a1 = activeTab == Tab.ACTIVE;
        ctx.fill(tx1, ty, tx2, ty + th, a1 ? 0xAA1E1E1E : (h1 ? 0x33FFFFFF : 0x88111111));
        ctx.fill(tx1, ty, tx2, ty + 1,  C_SEP);
        ctx.fill(tx1, ty, tx1 + 1, ty + th, C_SEP);
        if (a1) ctx.fill(tx1, ty + th - 2, tx2, ty + th, C_GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, "В процессе",
            tx1 + tw1 / 2, ty + 5, a1 ? C_GOLD : (h1 ? C_TEXT : C_GRAY));

        long activeCount = entries.stream().filter(e -> !e.complete()).count();
        if (activeCount > 0) {
            String badge = String.valueOf(activeCount);
            ctx.drawText(textRenderer, badge, tx2 - textRenderer.getWidth(badge) - 5, ty + 5,
                a1 ? C_AMBER : C_HINT, true);
        }

        // Вкладка «Завершено»
        boolean h2 = mx >= tx2 && mx < tx2 + tw2 && my >= ty && my < ty + th;
        boolean a2 = activeTab == Tab.COMPLETED;
        ctx.fill(tx2, ty, tx2 + tw2, ty + th, a2 ? 0xAA1E1E1E : (h2 ? 0x33FFFFFF : 0x88111111));
        ctx.fill(tx2, ty, tx2 + tw2, ty + 1,        C_SEP);
        ctx.fill(tx2 + tw2 - 1, ty, tx2 + tw2, ty + th, C_SEP);
        if (a2) ctx.fill(tx2, ty + th - 2, tx2 + tw2, ty + th, C_GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, "Завершено",
            tx2 + tw2 / 2, ty + 5, a2 ? C_GOLD : (h2 ? C_TEXT : C_GRAY));

        long doneCount = entries.stream().filter(QuestJournalPayload.QuestEntry::complete).count();
        if (doneCount > 0) {
            String badge = String.valueOf(doneCount);
            ctx.drawText(textRenderer, badge, tx2 + tw2 - textRenderer.getWidth(badge) - 5, ty + 5,
                a2 ? C_GREEN : C_HINT, true);
        }
    }

    private void drawQuestList(DrawContext ctx, int mx, int my) {
        int ct    = contentTop(), cb = contentBot();
        int lx    = listLeft() + 6;
        int rw    = leftPanelW() - 12;
        int visH  = cb - ct;
        int maxVis = visH / ITEM_H;

        List<QuestJournalPayload.QuestEntry> list = filtered();

        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "— нет квестов —",
                listLeft() + leftPanelW() / 2, ct + visH / 2 - textRenderer.fontHeight / 2, C_HINT);
            return;
        }

        listScroll    = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - maxVis)));
        selectedIndex = Math.max(0, Math.min(selectedIndex, list.size() - 1));

        for (int i = listScroll; i < list.size(); i++) {
            int iy = ct + (i - listScroll) * ITEM_H + 2;
            if (iy + ITEM_H > cb) break;

            var entry   = list.get(i);
            boolean sel = (i == selectedIndex);
            boolean hov = !sel
                && mx >= listLeft() + 1 && mx < listRight() - 1
                && my >= iy && my < iy + ITEM_H;

            if (sel) {
                ctx.fill(listLeft() + 1, iy, listRight() - 1, iy + ITEM_H - 1, C_SEL_BG);
                // Золотая полоска слева у выбранного
                ctx.fill(listLeft() + 1, iy, listLeft() + 3, iy + ITEM_H - 1, C_GOLD);
            } else if (hov) {
                ctx.fill(listLeft() + 1, iy, listRight() - 1, iy + ITEM_H - 1, C_HOVER_BG);
            }

            // Иконка статуса
            String icon   = entry.complete() ? "✓" : "!";
            int iconColor = entry.complete() ? C_GREEN : C_AMBER;
            ctx.drawText(textRenderer, icon, lx, iy + 5, iconColor, true);

            // Название квеста (усекаем если длинное)
            String name   = entry.questName();
            int maxNameW  = rw - 16;
            if (textRenderer.getWidth(name) > maxNameW) {
                while (!name.isEmpty() && textRenderer.getWidth(name + "…") > maxNameW) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "…";
            }
            int nColor = sel ? C_WHITE : (hov ? C_TEXT : C_GRAY);
            ctx.drawText(textRenderer, name, lx + 12, iy + 5, nColor, true);
        }

        // Полоса прокрутки
        if (list.size() > maxVis) {
            int sbH = visH - 4;
            int tH  = Math.max(12, sbH * maxVis / list.size());
            int tY  = ct + 2 + listScroll * (sbH - tH) / Math.max(1, list.size() - maxVis);
            ctx.fill(listRight() - 4, ct + 2, listRight() - 2, cb - 2, 0xFF222222);
            ctx.fill(listRight() - 4, tY,     listRight() - 2, tY + tH, C_GRAY);
        }
    }

    private void drawQuestDetail(DrawContext ctx, int mx, int my) {
        int ct = contentTop(), cb = contentBot();
        int dl = detailLeft() + 10;
        int dw = detailRight() - detailLeft() - 20;

        List<QuestJournalPayload.QuestEntry> list = filtered();
        if (list.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(selectedIndex, list.size() - 1));
        var quest = list.get(selectedIndex);

        int y = ct + 8 - detailScroll;

        // Заголовок квеста
        if (inBounds(y, y + textRenderer.fontHeight, ct, cb)) {
            ctx.drawCenteredTextWithShadow(textRenderer, quest.questName(),
                detailLeft() + (detailRight() - detailLeft()) / 2, y, C_GOLD);
        }
        y += textRenderer.fontHeight + 4;

        // Разделитель
        if (inBounds(y, y + 2, ct, cb)) ctx.fill(dl, y, dl + dw, y + 1, C_SEP);
        y += 6;

        // Бейдж статуса
        String badge      = quest.complete() ? "✦ Выполнено" : "⚡ В процессе";
        int    badgeColor = quest.complete() ? C_GREEN : C_AMBER;
        if (inBounds(y, y + textRenderer.fontHeight, ct, cb)) {
            ctx.drawText(textRenderer, badge, dl, y, badgeColor, true);
        }
        y += textRenderer.fontHeight + 10;

        // Список этапов
        for (var stage : quest.stages()) {
            String desc  = stage.description().isEmpty() ? "(нет описания)" : stage.description();
            boolean multi = stage.required() > 0;
            if (multi) desc += "  (" + stage.collected() + "/" + stage.required() + ")";

            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(desc), dw - 18);
            int linesH    = lines.size() * (textRenderer.fontHeight + 2);
            int barH      = (multi && stage.status() == 1) ? 10 : 0;
            int stageH    = linesH + barH + 4;

            // Подсветка текущего этапа
            if (stage.status() == 1 && inBounds(y - 2, y + stageH, ct, cb)) {
                ctx.fill(dl - 4, y - 2, dl + dw + 4, y + stageH, C_CUR_STAGE_BG);
                ctx.fill(dl - 4, y - 2, dl - 2,       y + stageH, C_GOLD);
            }

            // Иконка этапа
            String icon;  int iconColor, textColor;
            if (stage.status() == 2)      { icon = "✓"; iconColor = C_GREEN; textColor = C_GRAY;  }
            else if (stage.status() == 1) { icon = "▶"; iconColor = C_GOLD;  textColor = C_WHITE; }
            else                          { icon = "○"; iconColor = C_HINT;  textColor = C_HINT;  }

            if (inBounds(y, y + textRenderer.fontHeight, ct, cb)) {
                ctx.drawText(textRenderer, icon, dl, y, iconColor, true);
            }

            // Строки описания
            for (OrderedText line : lines) {
                if (inBounds(y, y + textRenderer.fontHeight, ct, cb)) {
                    ctx.drawText(textRenderer, line, dl + 14, y, textColor, true);
                }
                y += textRenderer.fontHeight + 2;
            }

            // Полоса прогресса (только текущий MULTI_COLLECT этап)
            if (multi && stage.status() == 1) {
                int barX = dl + 14;
                int barW = dw - 18;
                if (inBounds(y, y + 6, ct, cb)) {
                    ctx.fill(barX, y, barX + barW, y + 5, C_PROG_BG);
                    int fillW = stage.required() > 0
                        ? Math.min(barW, (int)((float) stage.collected() / stage.required() * barW))
                        : 0;
                    if (fillW > 0) ctx.fill(barX, y, barX + fillW, y + 5, C_PROG_FILL);
                    // Метки делений
                    for (int tick = 1; tick < stage.required(); tick++) {
                        int tickX = barX + tick * barW / stage.required();
                        ctx.fill(tickX, y, tickX + 1, y + 5, 0xFF1A1A1A);
                    }
                }
                y += 10;
            }

            y += 5;
        }
    }

    private boolean inBounds(int top, int bot, int clipTop, int clipBot) {
        return bot >= clipTop && top <= clipBot;
    }

    // ── Ввод ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;
        int ct  = contentTop(), cb = contentBot();

        // Клик по вкладкам
        int ty = tabY(), th = tabH();
        if (imy >= ty && imy < ty + th) {
            int tw1 = (width - 2 * MARGIN) / 2;
            if (imx >= MARGIN && imx < MARGIN + tw1 && activeTab != Tab.ACTIVE) {
                setTab(Tab.ACTIVE); return true;
            }
            if (imx >= MARGIN + tw1 && imx < width - MARGIN && activeTab != Tab.COMPLETED) {
                setTab(Tab.COMPLETED); return true;
            }
        }

        // Клик по списку квестов
        if (imx >= listLeft() && imx < listRight() && imy >= ct && imy < cb) {
            int clicked = listScroll + (imy - ct) / ITEM_H;
            List<QuestJournalPayload.QuestEntry> list = filtered();
            if (clicked >= 0 && clicked < list.size()) {
                selectedIndex = clicked;
                detailScroll  = 0;
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    private void setTab(Tab tab) {
        activeTab     = tab;
        selectedIndex = 0;
        listScroll    = 0;
        detailScroll  = 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        int imx = (int) mx, imy = (int) my;
        int ct  = contentTop(), cb = contentBot();
        if (imy < ct || imy > cb) return false;

        int maxVis = (cb - ct) / ITEM_H;
        List<QuestJournalPayload.QuestEntry> list = filtered();

        if (imx < listRight()) {
            listScroll = Math.max(0, Math.min(
                listScroll - (int) vScroll,
                Math.max(0, list.size() - maxVis)));
        } else if (imx > detailLeft()) {
            detailScroll = Math.max(0, detailScroll - (int) (vScroll * 12));
        }
        return true;
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
