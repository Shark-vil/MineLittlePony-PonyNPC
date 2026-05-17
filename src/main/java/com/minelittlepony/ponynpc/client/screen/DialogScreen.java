package com.minelittlepony.ponynpc.client.screen;

import com.minelittlepony.ponynpc.network.CloseDialogPayload;
import com.minelittlepony.ponynpc.network.DialogChoicePayload;
import com.minelittlepony.ponynpc.network.OpenDialogPayload;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Окно диалога в стиле Fallout 3.
 *
 * Верхняя часть (~60%): имя NPC и текст с эффектом постепенного появления.
 * Нижняя часть (~40%): варианты ответа игрока.
 *
 * Кликнуть во время печатания = мгновенно показать весь текст.
 * ESC = закрыть диалог (сервер получает уведомление).
 */
public class DialogScreen extends Screen {

    // Скорость печатания: символов за тик
    private static final int CHARS_PER_TICK = 1;

    // Цвета интерфейса
    private static final int COLOR_BACKGROUND      = 0xCC000000;
    private static final int COLOR_PANEL_TEXT      = 0x99111111;
    private static final int COLOR_PANEL_CHOICES   = 0x99181818;
    private static final int COLOR_SEPARATOR       = 0xFF555555;
    private static final int COLOR_NPC_NAME        = 0xFFD4A017;
    private static final int COLOR_TEXT            = 0xFFE8E8E8;
    private static final int COLOR_CHOICE_NORMAL    = 0xFFCCCCCC;
    private static final int COLOR_CHOICE_HOVER     = 0xFFFFFFFF;
    private static final int COLOR_CHOICE_DONE      = 0xFF888888;
    private static final int COLOR_CHOICE_DONE_MARK = 0xFF55FF55;
    private static final int COLOR_CHOICE_LOCKED    = 0xFF555555; // unavailable (condition failed)
    private static final int COLOR_CHOICE_BG_HOVER  = 0x44FFFFFF;
    private static final int COLOR_HINT             = 0xFF666666;

    private final UUID npcUuid;
    private final String npcName;
    private String dialogId;
    private String nodeId;
    private String fullText;
    private String sound;
    private int soundChars;
    private List<OpenDialogPayload.ChoiceData> choices;

    private int charsRevealed = 0;
    private int hoveredChoice = -1;
    /** true = закрытие инициировано сервером, пакет отправлять не нужно. */
    private boolean serverInitiatedClose = false;

    public DialogScreen(OpenDialogPayload payload) {
        super(Text.literal(payload.npcName()));
        this.npcUuid   = payload.npcUuid();
        this.npcName   = payload.npcName();
        this.dialogId  = payload.dialogId();
        this.nodeId    = payload.nodeId();
        this.fullText  = payload.text();
        this.sound     = payload.sound();
        this.soundChars = payload.soundChars();
        this.choices   = payload.choices();
    }

    // ---- Обновление узла (переход внутри диалога) ----

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public void updateNode(OpenDialogPayload payload) {
        this.dialogId   = payload.dialogId();
        this.nodeId     = payload.nodeId();
        this.fullText   = payload.text();
        this.sound      = payload.sound();
        this.soundChars = payload.soundChars();
        this.choices    = payload.choices();
        this.charsRevealed = 0;
        this.hoveredChoice = -1;
    }

    // ---- Тик (анимация текста) ----

    @Override
    public void tick() {
        if (charsRevealed >= fullText.length()) return;

        int prev = charsRevealed;
        charsRevealed = Math.min(charsRevealed + CHARS_PER_TICK, fullText.length());

        // Звук каждые soundChars символов
        if (!sound.isEmpty() && soundChars > 0) {
            if (charsRevealed / soundChars > prev / soundChars) {
                playTypingSound();
            }
        }
    }

    private void playTypingSound() {
        Identifier soundId = Identifier.tryParse(sound);
        if (soundId == null) return;
        SoundEvent event = Registries.SOUND_EVENT.get(soundId);
        if (event == null) return;
        float pitch = 0.85F + (float)(Math.random() * 0.3F);
        client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, 0.4F));
    }

    // ---- Рендер ----

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Свой фон рисуем в render() — стандартный applyBlur() не нужен
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int w = width;
        int h = height;

        int splitY = h * 6 / 10;          // граница текст / выборы
        int margin = 20;
        int textPanelX1 = margin;
        int textPanelX2 = w - margin;
        int choicePanelX1 = margin;
        int choicePanelX2 = w - margin;

        // Фон
        ctx.fill(0, 0, w, h, COLOR_BACKGROUND);

        // ── Панель текста ──────────────────────────────────
        ctx.fill(textPanelX1, margin, textPanelX2, splitY, COLOR_PANEL_TEXT);

        // Имя NPC
        String nameDisplay = npcName.isEmpty() ? "???" : npcName;
        ctx.drawCenteredTextWithShadow(textRenderer, nameDisplay, w / 2, margin + 6, COLOR_NPC_NAME);

        // Разделитель под именем
        int sepY = margin + 18;
        ctx.fill(textPanelX1 + 4, sepY, textPanelX2 - 4, sepY + 1, COLOR_SEPARATOR);

        // Текст диалога (постепенное появление)
        String visible = fullText.substring(0, charsRevealed);
        int textX = textPanelX1 + 8;
        int textY = sepY + 6;
        int textMaxWidth = textPanelX2 - textPanelX1 - 16;

        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(visible), textMaxWidth);
        for (OrderedText line : lines) {
            ctx.drawText(textRenderer, line, textX, textY, COLOR_TEXT, true);
            textY += textRenderer.fontHeight + 2;
        }

        // Подсказка «кликни чтобы пропустить»
        if (charsRevealed < fullText.length()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "[ Click to skip ]", w / 2, splitY - 14, COLOR_HINT);
        }

        // ── Панель выборов ─────────────────────────────────
        ctx.fill(choicePanelX1, splitY + 2, choicePanelX2, h - margin, COLOR_PANEL_CHOICES);

        // Разделитель
        ctx.fill(choicePanelX1, splitY, choicePanelX2, splitY + 2, COLOR_SEPARATOR);

        // Определяем, нет ли ни одного выбора — покажем авто-закрытие
        boolean hasChoices = !choices.isEmpty();

        if (hasChoices) {
            int choiceX = choicePanelX1 + 8;
            int choiceY = splitY + 8;
            int choiceW  = choicePanelX2 - choicePanelX1 - 16;
            int lineH    = textRenderer.fontHeight + 6;

            hoveredChoice = -1;

            for (int i = 0; i < choices.size(); i++) {
                var choice = choices.get(i);
                boolean locked = !choice.available();
                boolean hovered = !locked
                    && mouseY >= choiceY - 2 && mouseY < choiceY + lineH - 2
                    && mouseX >= choiceX && mouseX < choiceX + choiceW;
                if (hovered) hoveredChoice = i;

                if (hovered) {
                    ctx.fill(choiceX - 2, choiceY - 2, choiceX + choiceW + 2, choiceY + lineH - 2, COLOR_CHOICE_BG_HOVER);
                }

                if (locked) {
                    // Заблокировано условием — показываем серым без стрелки
                    ctx.drawText(textRenderer, "  " + choice.text(), choiceX, choiceY, COLOR_CHOICE_LOCKED, false);
                } else if (choice.done()) {
                    ctx.drawText(textRenderer, "✓", choiceX, choiceY, COLOR_CHOICE_DONE_MARK, true);
                    ctx.drawText(textRenderer, "  " + choice.text(), choiceX, choiceY, COLOR_CHOICE_DONE, true);
                } else {
                    int color = hovered ? COLOR_CHOICE_HOVER : COLOR_CHOICE_NORMAL;
                    ctx.drawText(textRenderer, "> " + choice.text(), choiceX, choiceY, color, true);
                }

                choiceY += lineH;
            }
        } else {
            // Нет выборов (режим RANDOM или пустой узел)
            boolean textDone = charsRevealed >= fullText.length();
            if (textDone) {
                // Текст дочитан — показываем кликабельный «Close»
                boolean closeHovered = mouseY >= splitY + 12 && mouseY < splitY + 28
                    && mouseX >= w / 2 - 60 && mouseX < w / 2 + 60;
                if (closeHovered) {
                    ctx.fill(w / 2 - 62, splitY + 10, w / 2 + 62, splitY + 30, COLOR_CHOICE_BG_HOVER);
                }
                ctx.drawCenteredTextWithShadow(textRenderer,
                    "[ Click to close ]", w / 2, splitY + 16,
                    closeHovered ? COLOR_CHOICE_HOVER : COLOR_HINT);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer,
                    "[ Press ESC to close ]", w / 2, splitY + 16, COLOR_HINT);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ---- Ввод ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Кликнуть во время печатания = мгновенно показать весь текст
        if (charsRevealed < fullText.length()) {
            charsRevealed = fullText.length();
            return true;
        }

        // Нет выборов (RANDOM-режим) — любой клик закрывает диалог
        if (choices.isEmpty()) {
            close();
            return true;
        }

        // Кликнуть на выбор
        if (hoveredChoice >= 0 && hoveredChoice < choices.size()) {
            selectChoice(choices.get(hoveredChoice));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectChoice(OpenDialogPayload.ChoiceData choice) {
        if (!choice.available()) return;
        client.getSoundManager().play(PositionedSoundInstance.master(PonyNpcSounds.DIALOG_CLICK, 1.0f, 0.8f));
        ClientPlayNetworking.send(new DialogChoicePayload(npcUuid, nodeId, choice.id()));
    }

    /** Вызывается из клиента когда сервер инициирует закрытие. */
    public void closeByServer() {
        serverInitiatedClose = true;
        if (client != null) client.setScreen(null);
    }

    // ---- Закрытие ----

    @Override
    public void close() {
        // Уведомляем сервер только если закрытие инициировано игроком (ESC)
        if (!serverInitiatedClose && client != null && client.player != null) {
            try {
                ClientPlayNetworking.send(new DialogChoicePayload(npcUuid, nodeId, "__close__"));
            } catch (Exception ignored) {
                // Если нет подключения — молча закрываем
            }
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
