# Квестовая система

## Обзор

Квесты определяются в JSON-файлах в папке `config/ponynpc/quests/`.  
Каждый квест состоит из **этапов (stages)** с разными типами завершения.  
Прогресс хранится server-side в PersistentState и **не теряется при смерти**.

---

## Формат квеста (JSON)

```json
{
  "id": "my_quest",
  "name": "My Quest",
  "start_stage": "intro",
  "stages": {
    "intro": {
      "type": "dialog",
      "description": "Talk to the NPC",
      "next_stage": "collect_item"
    },
    "collect_item": {
      "type": "collect_quest_item",
      "description": "Find the artifact",
      "item_id": "my_artifact",
      "next_stage": "finish"
    },
    "finish": {
      "type": "dialog",
      "description": "Return to NPC",
      "next_stage": null
    }
  }
}
```

---

## Типы этапов

| Тип | Описание | Завершение |
|-----|----------|------------|
| `dialog` | Диалоговый этап | Через action `quest:<id>:complete_stage` в диалоге |
| `collect_quest_item` | Получить квестовый предмет | Автоматически при `give_quest_item` с нужным `item_id` |
| `wait_days` | Подождать N игровых дней | Автоматически через 24000×N тиков |
| `multi_collect` | Собрать N уникальных целей | Через action `quest:<id>:collect:<stageId>:<targetId>` |
| `dungeon` | Пройти данж | Автоматически при завершении данжа (`dungeon:complete`) |

### Поля типа `wait_days`

```json
{
  "type": "wait_days",
  "days": 1,
  "next_stage": "next"
}
```

### Поля типа `multi_collect`

```json
{
  "type": "multi_collect",
  "targets": ["piece_1", "piece_2", "piece_3", "piece_4"],
  "required_count": 4,
  "next_stage": "next"
}
```

Если `required_count = 0`, требуются все targets.

### Поля типа `dungeon`

```json
{
  "type": "dungeon",
  "dungeon_id": "shadow_ruins",
  "next_stage": "return_to_trixie_1"
}
```

Этап завершается автоматически когда игрок завершает данж с указанным `dungeon_id` (action `dungeon:complete`).

---

## Proximity Trigger

Любой этап может иметь `proximity_trigger` — срабатывает когда игрок входит в зону, находясь на этом этапе.

Проверяется каждые 20 тиков. Триггер **не срабатывает повторно** пока spawned entity жив; сбрасывается когда игрок покидает радиус и entity удалён.

> **Авто-старт квеста.** Если `proximity_trigger` задан на `start_stage` квеста — квест запускается автоматически при входе игрока в зону. Вызывать `quest:<id>:start` вручную не нужно.

### Режимы триггера

#### 1. `ghost_npc` (по умолчанию)

Спаунит пони-NPC видимого только этому игроку. Опционально выполняет `actions` немедленно.

```json
"proximity_trigger": {
  "dimension": "minecraft:overworld",
  "x": 100, "y": 64, "z": -50,
  "radius": 15.0,
  "spawn_ghost_npc": {
    "dialog_id": "trixie_encounter_1",
    "skin": "trixie",
    "name": "Trixie",
    "auto_dialog": true,
    "despawn_after_dialog": true
  },
  "actions": []
}
```

#### 2. `spawn_marker`

Спаунит кликабельный маркер (вращающийся золотой куб с плашкой). Игрок нажимает ПКМ — выполняются `actions` маркера, маркер исчезает.

```json
"proximity_trigger": {
  "dimension": "minecraft:overworld",
  "x": 100, "y": 64, "z": 200,
  "radius": 10.0,
  "spawn_marker": {
    "name": "Забрать книгу",
    "actions": ["quest:shadow_book_ch1:complete_stage:enter_ruins"]
  }
}
```

#### 3. `auto_complete`

Немедленно выполняет `actions` при входе в зону, без спауна какой-либо сущности. Используется для автоматического перехода этапа зоной.

```json
"proximity_trigger": {
  "dimension": "minecraft:overworld",
  "x": 0, "y": 64, "z": 0,
  "radius": 5.0,
  "auto_complete": true,
  "actions": ["quest:my_quest:complete_stage"]
}
```

### Поля `proximity_trigger`

| Поле | Тип | Описание |
|------|-----|----------|
| `dimension` | string | ID измерения (например `"minecraft:overworld"`) |
| `x`, `y`, `z` | double | Центр зоны |
| `radius` | double | Радиус в блоках |
| `spawn_ghost_npc` | object | Конфиг пони-NPC (режим ghost_npc) |
| `spawn_marker` | object | Конфиг маркера: `name` + `actions` |
| `auto_complete` | bool | Если `true` — режим auto_complete |
| `actions` | array | Действия для ghost_npc и auto_complete режимов |

---

## Квестовый карман (Quest Pocket)

Квестовые предметы хранятся виртуально — не в инвентаре, не теряются при смерти.

Управление через команды:
```
/ponynpc pocket                   — посмотреть свой карман
/ponynpc pocket give <item_id>    — дать предмет себе (для отладки)
/ponynpc pocket take <item_id>    — забрать предмет
```

---

## Команды управления квестами

Требуют op level 2.

| Команда | Описание |
|---------|----------|
| `/ponynpc quest list` | Список загруженных квестов |
| `/ponynpc quest start <id>` | Начать квест для себя |
| `/ponynpc quest start <id> <player>` | Начать квест для игрока |
| `/ponynpc quest status <id>` | Текущий этап квеста |
| `/ponynpc quest advance <id>` | Принудительно завершить текущий этап |
| `/ponynpc reload` | Перезагрузить диалоги и квесты |

---

## Прогресс квестов

- Файл: `[world]/data/ponynpc_quest_progress.dat`
- Каждый игрок имеет независимый прогресс
- Quest Pocket: `[world]/data/ponynpc_quest_pocket.dat`
