# Ghost NPC система

## Что такое Ghost NPC

Ghost NPC — это обычный `PonyNpcEntity` который видим **только конкретным игрокам**.  
Используется для:
- Квестовых сцен (персонаж появляется только для игрока в нужной стадии)
- Автоматически исчезает после диалога
- Не сохраняется в мире (не persistent)

---

## Как работает

### Фильтрация трекинга

Через Mixin на `ThreadedAnvilChunkStorage$EntityTracker.startTracking()`:  
если у NPC есть `visibleToPlayers` и игрок не в списке — spawn-пакет не отправляется.

### Жизненный цикл

```
Proximity trigger срабатывает
    ↓
GhostNpcManager.spawnGhostNpc(player, world, config)
    ↓
PonyNpcEntity создаётся с visibleToPlayers = {playerUUID}
    ↓
Если autoDialog=true → NetworkHandler.openDialogForPlayer()
    ↓
Игрок закрывает диалог (next=null или ESC)
    ↓
Если despawnAfterDialog=true → npc.discard()
```

---

## Конфигурация в квесте

```json
"proximity_trigger": {
  "dimension": "minecraft:overworld",
  "x": 100, "y": 64, "z": -50,
  "radius": 10.0,
  "spawn_ghost_npc": {
    "dialog_id": "trixie_encounter_1",
    "skin": "trixie",
    "name": "Trixie",
    "auto_dialog": true,
    "despawn_after_dialog": true
  }
}
```

| Поле | Описание |
|------|----------|
| `dialog_id` | ID диалога из config/ponynpc/dialogs/ |
| `skin` | Короткое имя скина или полный идентификатор |
| `name` | Отображаемое имя над NPC |
| `auto_dialog` | Открыть диалог сразу после спауна |
| `despawn_after_dialog` | Удалить NPC когда диалог закрывается |

---

## Spawn позиция

Ghost NPC появляется в 2 блоках перед игроком, лицом к игроку.  
Позиция и разворот вычисляются автоматически по yaw игрока.

---

## Очистка при logout

При отключении игрока все его ghost NPC автоматически удаляются через `GhostNpcManager.despawnAll()`.

---

## Proximity Trigger Manager

`ProximityTriggerManager.tick()` вызывается каждые **20 тиков** (1 раз в секунду).

Проверяет для каждого онлайн-игрока:
1. Все активные квесты игрока
2. Текущий этап каждого квеста
3. Если у этапа есть `proximity_trigger` — проверяет расстояние
4. Если игрок в зоне — спаунит ghost NPC или выполняет actions (только один раз за стадию)
