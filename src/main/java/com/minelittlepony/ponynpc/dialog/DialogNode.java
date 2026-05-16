package com.minelittlepony.ponynpc.dialog;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class DialogNode {
    /** Текст, произносимый NPC. Поддерживает \n для переноса строки. */
    public String text = "";

    /**
     * Sound event ID для звука печатания (Undertale-стиль).
     * Например: "ponynpc:talk". Пусто = без звука.
     */
    public String sound = "ponynpc:talk";

    /**
     * Через сколько раскрытых символов проигрывать звук.
     * По умолчанию 2 (каждые 2 символа).
     */
    @SerializedName("sound_chars")
    public int soundChars = 2;

    /** Варианты ответа игрока. */
    public List<DialogChoice> choices = new ArrayList<>();
}
