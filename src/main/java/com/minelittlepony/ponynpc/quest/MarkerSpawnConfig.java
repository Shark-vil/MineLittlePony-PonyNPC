package com.minelittlepony.ponynpc.quest;

import java.util.ArrayList;
import java.util.List;

/** Конфигурация маркера-объекта для proximity-триггера (не пони-NPC). */
public class MarkerSpawnConfig {
    /** Название, показывается как плашка над маркером. */
    public String name = "???";
    /** Действия, выполняемые когда игрок кликает ПКМ по маркеру. */
    public List<String> actions = new ArrayList<>();
}
