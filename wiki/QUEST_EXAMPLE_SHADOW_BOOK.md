# Пример: квест «Книга Теней» (shadow_book_ch1)

Полный разбор реального квеста, встроенного в мод.  
Используй его как шаблон при создании собственных квестов.

---

## Общая схема

```
[Прибытие в Понивилль]
        ↓  train_welcome → start quest
[Этап 1] talk_to_trixie        — proximity trigger спавнит Ghost Trixie
        ↓
[Этап 2] talk_to_librarian     — NPC Библиотекарь → идти за билетом
        ↓
[Этап 3] find_twilight         — NPC Твайлайт → получить library_card
        ↓
[Этап 4] return_to_librarian   — Библиотекарь, нужен library_card
        ↓
[Этап 5] find_book_location    — кликнуть полку в архиве (block_zone)
        ↓
[Этап 6] enter_ruins           — proximity trigger → маркер → данж shadow_ruins
        ↓  данж выдаёт quest_item: shadow_book
[Этап 7] return_to_trixie_1    — NPC Трикси, забрать shadow_book
        ↓
[Этап 8] wait_for_trixie       — ждать 1 игровой день
        ↓
[Этап 9] talk_to_twilight_2    — Твайлайт объясняет что книга зашифрована
        ↓
[Этап 10] go_to_trixie_2       — Трикси рассказывает об оригами
        ↓
[Этап 11] find_origami_pieces  — multi_collect: 4 информатора, по 1 фрагменту
        ↓
[Этап 12] return_to_twilight_final — финал, награда
```

---

## 1. Файл квеста

`config/ponynpc/quests/shadow_book_ch1.json`

```json
{
  "id": "shadow_book_ch1",
  "name": "Книга Теней",
  "start_stage": "talk_to_trixie",
  "stages": {

    "talk_to_trixie": {
      "type": "dialog",
      "description": "Поговорить с Трикси у выхода со станции",
      "next_stage": "talk_to_librarian",
      "proximity_trigger": {
        "dimension": "minecraft:overworld",
        "x": 0, "y": 64, "z": 0,
        "radius": 15.0,
        "spawn_ghost_npc": {
          "dialog_id": "trixie_encounter_1",
          "skin": "trixie",
          "name": "Великая и Могучая Трикси",
          "auto_dialog": true,
          "despawn_after_dialog": false
        }
      }
    },

    "talk_to_librarian": {
      "type": "dialog",
      "description": "Поговорить с Библиотекарем о Книге Теней",
      "next_stage": "find_twilight"
    },

    "find_twilight": {
      "type": "dialog",
      "description": "Получить читательский билет у Твайлайт Спаркл",
      "next_stage": "return_to_librarian"
    },

    "return_to_librarian": {
      "type": "dialog",
      "description": "Вернуться к Библиотекарю с читательским билетом",
      "next_stage": "find_book_location"
    },

    "find_book_location": {
      "type": "dialog",
      "description": "Найти в архиве книгу с координатами руин",
      "next_stage": "enter_ruins"
    },

    "enter_ruins": {
      "type": "dungeon",
      "description": "Найти и пройти Руины Теней, достать Книгу Теней",
      "dungeon_id": "shadow_ruins",
      "next_stage": "return_to_trixie_1",
      "proximity_trigger": {
        "dimension": "minecraft:overworld",
        "x": 0, "y": 64, "z": 0,
        "radius": 5.0,
        "spawn_marker": {
          "name": "Войти в Руины Теней",
          "actions": ["dungeon:enter:shadow_ruins"]
        }
      }
    },

    "return_to_trixie_1": {
      "type": "dialog",
      "description": "Отдать Книгу Теней Трикси",
      "next_stage": "wait_for_trixie"
    },

    "wait_for_trixie": {
      "type": "wait_days",
      "description": "Подождать один игровой день",
      "days": 1,
      "next_stage": "talk_to_twilight_2"
    },

    "talk_to_twilight_2": {
      "type": "dialog",
      "description": "Поговорить с Твайлайт — выяснить что книга была зашифрована",
      "next_stage": "go_to_trixie_2"
    },

    "go_to_trixie_2": {
      "type": "dialog",
      "description": "Выяснить у Трикси где оригами из книги",
      "next_stage": "find_origami_pieces"
    },

    "find_origami_pieces": {
      "type": "multi_collect",
      "description": "Собрать все 4 фрагмента оригами по городу",
      "targets": [
        "origami_piece_1",
        "origami_piece_2",
        "origami_piece_3",
        "origami_piece_4"
      ],
      "required_count": 4,
      "next_stage": "return_to_twilight_final"
    },

    "return_to_twilight_final": {
      "type": "dialog",
      "description": "Вернуться к Твайлайт со всеми фрагментами оригами",
      "next_stage": null
    }
  }
}
```

### Ключевые моменты

| Поле | Зачем |
|------|-------|
| `start_stage` | Первый этап, который активируется сразу при старте квеста |
| `type: "dialog"` | Этап завершается через `complete_stage` в диалоге |
| `type: "dungeon"` | Этап завершается когда игрок проходит данж |
| `type: "wait_days"` | Автоматически завершается через N игровых дней |
| `type: "multi_collect"` | Нужно выполнить `collect` для каждого target-а |
| `proximity_trigger` | При входе в радиус — спавнит Ghost NPC или маркер |

---

## 2. Запуск квеста через диалог

Квест стартует когда NPC у поезда говорит с прибывшим игроком.

`config/ponynpc/dialogs/train_welcome.json`
```json
{
  "id": "train_welcome",
  "nodes": {
    "start": {
      "text": "Добро пожаловать в Понивилль! Здесь много интересного — советую осмотреться.",
      "choices": [
        {
          "id": "thanks",
          "text": "Спасибо! Обязательно.",
          "next": null,
          "once": true,
          "actions": ["quest:shadow_book_ch1:start"]
        }
      ]
    }
  }
}
```

> `once: true` + `actions: ["quest:...:start"]` — квест запускается ровно один раз,  
> при повторном разговоре кнопка уже не появится.

---

## 3. Ghost NPC — Трикси (этапы 1, 7, 10)

Трикси появляется тремя способами:

### 3a. Первый контакт — Ghost NPC через proximity trigger

Когда игрок приближается к точке `x=0 y=64 z=0` на радиус 15 блоков,  
сервер спавнит **ghost NPC** виден только этому игроку, автоматически начинает диалог.

`config/ponynpc/dialogs/trixie_encounter_1.json`
```json
{
  "id": "trixie_encounter_1",
  "nodes": {
    "start": {
      "text": "Наконец-то ты пришёл! ... Трикси обязывает тебя принести «Книгу Теней»!",
      "choices": [
        {
          "id": "accept",
          "text": "...Хорошо. Найду эту книгу.",
          "next": null,
          "once": false,
          "actions": [
            "quest:shadow_book_ch1:complete_stage",
            "ghost_npc:despawn"
          ]
        },
        {
          "id": "what",
          "text": "Подожди — что за «Книга Теней»?",
          "next": "explain",
          "once": false
        }
      ]
    },
    "explain": { ... }
  }
}
```

> `ghost_npc:despawn` — NPC исчезает сразу после завершения диалога.  
> `complete_stage` — переходим на следующий этап.

### 3b. Постоянный NPC Трикси на карте — режим router

Трикси также стоит на карте как обычный NPC. Её диалог зависит от текущего этапа квеста.  
Файл для NPC на карте `config/ponynpc/dialogs/trixie_main.json` включает несколько квестовых веток,  
скрытых через `conditions + hidden_if_condition_fails: true`.

**Альтернативный вариант через router** (рекомендуется для сложных NPC):

`config/ponynpc/dialogs/trixie_router.json`
```json
{
  "id": "trixie_router",
  "mode": "router",
  "entries": [
    {
      "dialog": "trixie_return_book",
      "conditions": ["quest:shadow_book_ch1:stage:return_to_trixie_1"]
    },
    {
      "dialog": "trixie_about_origami",
      "conditions": ["quest:shadow_book_ch1:stage:go_to_trixie_2"]
    },
    {
      "dialog": "trixie_idle"
    }
  ]
}
```

Router проверяет условия сверху вниз и открывает первый совпавший диалог.  
Последняя запись без `conditions` — fallback (диалог всегда).

---

## 4. Библиотекарь — условные ветки в одном диалоге

Библиотекарь участвует в двух этапах квеста (`talk_to_librarian` и `return_to_librarian`).  
В текущей реализации это один файл с несколькими скрытыми выборами.

`config/ponynpc/dialogs/librarian_dialog.json`

```json
{
  "id": "librarian_dialog",
  "nodes": {
    "start": {
      "text": "Добро пожаловать в библиотеку. Чем могу помочь?",
      "choices": [

        {
          "id": "ask_shadow_book",
          "text": "Я ищу «Книгу Теней».",
          "next": "need_card",
          "once": false,
          "conditions": ["quest:shadow_book_ch1:stage:talk_to_librarian"],
          "hidden_if_condition_fails": true
        },

        {
          "id": "give_card",
          "text": "Вот мой читательский билет. Я хочу пройти в архив.",
          "next": "access_granted",
          "once": false,
          "conditions": [
            "quest:shadow_book_ch1:stage:return_to_librarian",
            "quest:shadow_book_ch1:has_item:library_card"
          ],
          "hidden_if_condition_fails": true,
          "actions": [
            "quest:shadow_book_ch1:take_quest_item:library_card",
            "quest:shadow_book_ch1:complete_stage"
          ]
        },

        {
          "id": "nothing",
          "text": "Просто смотрю. Спасибо.",
          "next": null,
          "once": false
        }
      ]
    },

    "need_card": {
      "text": "«Книга Теней»? Она в закрытом архиве. Без читательского билета не пущу. Обратись к Твайлайт.",
      "choices": [
        {
          "id": "ok",
          "text": "Понял, схожу к Твайлайт.",
          "next": null,
          "once": false,
          "conditions": ["quest:shadow_book_ch1:stage:talk_to_librarian"],
          "hidden_if_condition_fails": true,
          "actions": ["quest:shadow_book_ch1:complete_stage"]
        },
        { "id": "back", "text": "Может что-то другое?", "next": "start", "once": false }
      ]
    },

    "access_granted": {
      "text": "Отлично. Добро пожаловать в архив.",
      "choices": [
        { "id": "thanks", "text": "Благодарю!", "next": null, "once": false }
      ]
    }
  }
}
```

**Паттерн:** несколько веток в одном `start`-узле, каждая скрыта через условие.  
При возврате с картой оба условия (`stage:return_to_librarian` + `has_item:library_card`) должны выполняться.

---

## 5. Квестовый предмет — читательский билет

Твайлайт выдаёт `library_card` через `give_quest_item`, Библиотекарь забирает через `take_quest_item`.

`config/ponynpc/dialogs/twilight_main.json` — фрагмент узла `card_trust`:
```json
{
  "id": "thanks",
  "text": "Спасибо огромное, Твайлайт!",
  "next": null,
  "once": true,
  "conditions": ["quest:shadow_book_ch1:stage:find_twilight"],
  "hidden_if_condition_fails": false,
  "actions": [
    "quest:shadow_book_ch1:give_quest_item:library_card",
    "quest:shadow_book_ch1:complete_stage"
  ]
}
```

> `hidden_if_condition_fails: false` — кнопка остаётся **видна** даже если условие не выполнено,  
> но будет **неактивна** (серая). Используется когда хочешь показать недоступный вариант.
>
> `once: true` — кнопка исчезает после нажатия, предотвращая дублирование предмета.

---

## 6. Block Interaction Zone — архив библиотеки

Когда игрок на этапе `find_book_location`, определённые книжные полки становятся кликабельными.

`config/ponynpc/block_zones/library_archive.json`
```json
{
  "id": "library_archive",
  "condition": "quest:shadow_book_ch1:stage:find_book_location",
  "world": "minecraft:overworld",
  "min_x": -100, "min_y": 60, "min_z": -100,
  "max_x": -80,  "max_y": 75, "max_z": -80,
  "target_block": "minecraft:bookshelf",
  "default_dialog": "library_random_shelf",
  "default_name": "Книжная полка",
  "blocks": [
    {
      "x": -90, "y": 64, "z": -90,
      "dialog": "library_shadow_book_shelf",
      "name": "Полка с загадочной книгой"
    }
  ]
}
```

Блок на позиции `-90, 64, -90` открывает диалог `library_shadow_book_shelf` (нужная книга).  
Все остальные полки в зоне — `library_random_shelf` (случайные реплики).

Диалог книги `config/ponynpc/dialogs/archive_book.json`:
```json
{
  "id": "archive_book",
  "nodes": {
    "start": {
      "text": "Старая потрёпанная книга. Раскрыть?",
      "choices": [
        {
          "id": "read",
          "text": "Открыть книгу.",
          "next": "book_content",
          "once": false,
          "conditions": ["quest:shadow_book_ch1:stage:find_book_location"],
          "hidden_if_condition_fails": true
        },
        { "id": "ignore", "text": "Не сейчас.", "next": null, "once": false }
      ]
    },
    "book_content": {
      "text": "«...Руины именуемые Тенями Предков обнаружены к северу от Понивилля...»",
      "choices": [
        {
          "id": "take_note",
          "text": "Запомнить координаты и закрыть книгу.",
          "next": null,
          "once": true,
          "conditions": ["quest:shadow_book_ch1:stage:find_book_location"],
          "hidden_if_condition_fails": true,
          "actions": [
            "message:[Архив] Координаты руин: X=0 Z=0",
            "quest:shadow_book_ch1:complete_stage"
          ]
        }
      ]
    }
  }
}
```

> `actions: ["message:..."]` — отправляет сообщение в чат игрока.  
> Используется чтобы показать координаты или подсказку без UI.

### Как создать зону командами

```
/zone wand                         — взять палочку
(ЛКМ по углу 1, ПКМ по углу 2)
/zone add_default library_random_shelf
/zone mark library_shadow_book_shelf "Полка с загадочной книгой"
/zone save library_archive minecraft:bookshelf "quest:shadow_book_ch1:stage:find_book_location"
```

---

## 7. Данж — Руины Теней

`config/ponynpc/dungeons/shadow_ruins.json`
```json
{
  "id": "shadow_ruins",
  "name": "Руины Теней",
  "build_origin": null,
  "size": {"width": 50, "height": 20, "length": 70},
  "spawn_offset": {"x": 25, "y": 1, "z": 5},
  "finish_marker": {
    "name": "Алтарь Теней",
    "offset": {"x": 25, "y": 1, "z": 65},
    "actions": [
      "dungeon:complete",
      "quest:shadow_book_ch1:complete_stage:enter_ruins"
    ]
  },
  "fixed_time": 18000,
  "destructible": false,
  "rewards": [
    {"type": "currency",    "count": 100},
    {"type": "item",        "id": "minecraft:iron_chestplate", "count": 1},
    {"type": "item",        "id": "minecraft:diamond_helmet",  "count": 1},
    {"type": "quest_item",  "id": "shadow_book",               "count": 1}
  ]
}
```

Данж запускается через `dungeon:enter:shadow_ruins` в действии маркера или диалога.  
По завершении `dungeon:complete` выдаёт `rewards` и вызывает `complete_stage:enter_ruins`.  
`quest_item: shadow_book` — квестовый предмет, который Трикси заберёт позже.

---

## 8. Multi-collect — сбор оригами

Этап `find_origami_pieces` типа `multi_collect` требует выполнить 4 `collect`-действия.  
Каждый информатор в своём диалоге выдаёт один фрагмент:

| Файл | NPC | Действие |
|------|-----|---------|
| `informant_bon_bon.json` | Бон-Бон | `quest:shadow_book_ch1:collect:find_origami_pieces:origami_piece_1` |
| `informant_lyra.json` | Лира | `quest:shadow_book_ch1:collect:find_origami_pieces:origami_piece_2` |
| `informant_berry_punch.json` | Бэрри Пунч | `quest:shadow_book_ch1:collect:find_origami_pieces:origami_piece_3` |
| `informant_vinyl.json` | Виниловая | `quest:shadow_book_ch1:collect:find_origami_pieces:origami_piece_4` |

Паттерн диалога информатора:
```json
{
  "id": "informant_bon_bon",
  "nodes": {
    "start": {
      "text": "Привет! Добро пожаловать в мою конфетную лавку.",
      "choices": [
        {
          "id": "ask_trixie",
          "text": "Бон-Бон, ты вчера видела Трикси?",
          "next": "bon_bon_story",
          "once": true,
          "conditions": ["quest:shadow_book_ch1:stage:find_origami_pieces"],
          "hidden_if_condition_fails": true
        },
        { "id": "nothing", "text": "Просто смотрю.", "next": null, "once": false }
      ]
    },
    "bon_bon_story": {
      "text": "Трикси? Да, заходила вчера. Вот, держи — фигурку оставила.",
      "choices": [
        {
          "id": "take_piece",
          "text": "Спасибо! Это именно то что я искал.",
          "next": null,
          "once": false,
          "actions": ["quest:shadow_book_ch1:collect:find_origami_pieces:origami_piece_1"]
        }
      ]
    }
  }
}
```

> `once: true` на кнопке `ask_trixie` — чтобы нельзя было бесконечно получать фрагмент.  
> Но само действие `collect` — idempotent: повторное выполнение не увеличивает счётчик сверх 1.

---

## 9. Финал и награда

`config/ponynpc/dialogs/twilight_main.json` — узел `twilight_receives`:
```json
{
  "id": "glad_to_help",
  "text": "Рад что помог.",
  "next": null,
  "once": true,
  "conditions": ["quest:shadow_book_ch1:stage:return_to_twilight_final"],
  "hidden_if_condition_fails": true,
  "actions": [
    "quest:shadow_book_ch1:give_currency:300",
    "quest:shadow_book_ch1:give_item:minecraft:totem_of_undying:1",
    "quest:shadow_book_ch1:give_item:minecraft:golden_apple:3",
    "quest:shadow_book_ch1:give_item:minecraft:diamond_hoe:1",
    "quest:shadow_book_ch1:complete_stage"
  ]
}
```

`complete_stage` на последнем этапе (`next_stage: null`) завершает квест целиком.

---

## 10. Полная карта файлов

```
config/ponynpc/
├── quests/
│   └── shadow_book_ch1.json          ← определение квеста
│
├── dialogs/
│   ├── train_welcome.json            ← старт квеста (NPC у поезда)
│   ├── trixie_encounter_1.json       ← Ghost NPC (этап 1)
│   ├── trixie_main.json              ← постоянная Трикси (этапы 7, 10)
│   ├── librarian_dialog.json         ← Библиотекарь (этапы 2, 4)
│   ├── twilight_main.json            ← Твайлайт (этапы 3, 9, 12)
│   ├── archive_book.json             ← блок в архиве (этап 5)
│   ├── ruins_entrance.json           ← маркер у входа в руины (этап 6)
│   ├── informant_bon_bon.json        ┐
│   ├── informant_lyra.json           │ информаторы
│   ├── informant_berry_punch.json    │ (этап 11)
│   └── informant_vinyl.json          ┘
│
├── dungeons/
│   └── shadow_ruins.json             ← данж (этап 6)
│
└── block_zones/
    └── library_archive.json          ← интерактивные полки (этап 5)
```

---

## Частые ошибки

| Проблема | Причина | Решение |
|----------|---------|---------|
| Кнопка диалога видна не на том этапе | Неправильное условие в `conditions` | Проверь точный ID этапа: `quest:<id>:stage:<stage_id>` |
| Квестовый предмет выдаётся повторно | Нет `once: true` на кнопке | Добавь `"once": true` к выбору с `give_quest_item` |
| `multi_collect` не завершается | Неверный ID target-а в `collect` | ID в `targets[]` квеста должен совпадать с `collect:...:TARGET_ID` |
| Данж не завершает этап | Нет `complete_stage` в `finish_marker.actions` | Добавь `"quest:<id>:complete_stage:<stage_id>"` |
| Block zone не активируется | Неправильная область или `condition` | Проверь координаты командой `/zone info` и состояние квеста |
