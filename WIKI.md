# PonyNPC — WIKI

Подмод для **Mine Little Pony** (Minecraft **1.21**, Fabric).  
Добавляет NPC-пони с системой диалогов, квестами, данжами и пати.

---

## Содержание

**Базовая документация**
1. [Архитектура](#архитектура)
2. [Установка и сборка](#установка-и-сборка)
3. [Режимы диалога (DialogMode)](#режимы-диалога-dialogmode)
4. [Создание диалогов (JSON)](#создание-диалогов-json)
5. [Команды](#команды)
6. [Звуки](#звуки)
7. [Скины NPC](#скины-npc)
8. [Защита NPC от кражи](#защита-npc-от-кражи)
9. [Как работает система диалогов](#как-работает-система-диалогов)
10. [Дневник квестов](#дневник-квестов)
11. [Quest Marker (интерактивный маркер)](#quest-marker)
12. [Block Interaction Zones (интерактивные блоки)](#block-interaction-zones)
13. [Система данжей](#система-данжей)
14. [Система пати](#система-пати)
15. [Структура пакетов](#структура-пакетов)
16. [Прогресс игрока](#прогресс-игрока)
17. [Структура файлов проекта](#структура-файлов-проекта)

**Расширенная документация (wiki/)**
- [Квестовая система](wiki/QUEST_SYSTEM.md) — квесты, этапы, proximity trigger, квестовый карман
- [Условия и действия диалогов](wiki/DIALOG_ACTIONS.md) — conditions/actions в DialogChoice
- [Ghost NPC](wiki/GHOST_NPC.md) — NPC видимые только конкретным игрокам

---

## Архитектура

```
PonyNpcMod (server/common)              PonyNpcClient (client-only)
    │                                       │
    ├─ PonyNpcEntities                      ├─ PonyNpcRenderer (пони-модель)
    │    ├─ EntityType<PonyNpcEntity>       ├─ QuestMarkerRenderer (куб/предмет)
    │    └─ EntityType<QuestMarkerEntity>   └─ DialogScreen / QuestJournalScreen
    ├─ PonyNpcSounds
    ├─ DialogManager (JSON → DialogData)
    ├─ NetworkHandler
    │    ├─ OpenDialogPayload (S2C)
    │    ├─ CloseDialogPayload (S2C)
    │    ├─ DialogChoicePayload (C2S)
    │    └─ QuestJournalPayload (S2C)
    ├─ QuestManager + QuestStateStore
    ├─ ProximityTriggerManager (каждые 20 тиков)
    ├─ GhostNpcManager
    ├─ DungeonRegistry + DungeonInstanceManager
    ├─ PartyManager
    └─ PonyNpcCommand / DungeonBuilderCommand / PartyCommand
```

---

## Установка и сборка

### Требования

- Java 21+
- Gradle (через `gradlew.bat` / `./gradlew`)
- MLP jar в папке `libs/`

### Сборка

```bat
./gradlew build
```

JAR появится в `build/libs/ponynpc-dev-1.1.0+1.21.jar`.

### Зависимости (runtime)

| Мод | Обязательно |
|-----|-------------|
| Mine Little Pony | ✅ |
| Fabric API 0.102.0+ | ✅ |

---

## Режимы диалога (DialogMode)

| Режим | Описание |
|-------|----------|
| `"quest"` | Ветвистый диалог с узлами, выборами и прогрессом (по умолчанию) |
| `"random"` | Случайная реплика из списка `phrases`, без выборов |

### QUEST (по умолчанию)
- Загружает узел `"start"`, показывает варианты ответа.
- Прогресс `once`-выборов сохраняется per-player, per-NPC.
- Диалог закрывается когда выбран вариант с `"next": null`.

### RANDOM
- Выбирает одну строку из массива `phrases` случайным образом.
- Нет выборов → игрок читает и нажимает ESC.

---

## Создание диалогов (JSON)

Файлы хранятся в:
```
[папка сервера]/config/ponynpc/dialogs/<id>.json
```

### Пример: QUEST

```json
{
  "id": "my_dialog",
  "nodes": {
    "start": {
      "text": "Привет, путник!",
      "sound": "ponynpc:talk",
      "sound_chars": 2,
      "choices": [
        { "id": "ask", "text": "Расскажи о городе.", "next": "about_town", "once": false },
        { "id": "bye",  "text": "До свидания!",       "next": null,         "once": false }
      ]
    },
    "about_town": {
      "text": "Это прекрасный город!",
      "choices": [{ "id": "back", "text": "Спасибо!", "next": "start", "once": false }]
    }
  }
}
```

### Пример: RANDOM

```json
{
  "id": "market_chatter",
  "mode": "random",
  "sound": "ponynpc:talk",
  "sound_chars": 2,
  "phrases": [
    "Fresh apples, only 5 bits!",
    "Lovely weather today, isn't it?"
  ]
}
```

### Поля

#### Корневой объект

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | ID диалога. Если пустой — из имени файла. |
| `mode` | string | `"quest"` (по умолчанию) или `"random"`. |
| `nodes` | object | Словарь узлов (QUEST). **Обязателен узел `"start"`**. |
| `phrases` | array | Список реплик (RANDOM). |
| `sound` | string | Sound event ID (RANDOM). По умолчанию: `"ponynpc:talk"`. |
| `sound_chars` | int | Интервал звука в символах. По умолчанию: `2`. |

#### DialogChoice

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | Уникальный ID варианта. |
| `text` | string | Текст кнопки. |
| `next` | string\|null | Следующий узел. `null` = закрыть диалог. |
| `once` | bool | Помечается выполненным после первого нажатия. |
| `conditions` | array | Условия видимости/доступности. |
| `hidden_if_condition_fails` | bool | Скрыть если условие не выполнено (по умолчанию `true`). |
| `actions` | array | Действия при выборе. |

---

## Команды

### `/ponynpc` — управление NPC и квестами (op level 2)

| Команда | Описание |
|---------|----------|
| `/ponynpc spawn <dialog_id>` | Спавнит пони-NPC перед игроком |
| `/ponynpc spawn <dialog_id> <skin>` | С указанным скином |
| `/ponynpc spawn <dialog_id> <skin> stationary [name]` | Неподвижный с именем |
| `/ponynpc spawn_marker <dialog_id> <item_id>` | Постоянный маркер с предметом |
| `/ponynpc spawn_marker <dialog_id> <item_id> <name>` | С именем |
| `/ponynpc remove` | Удаляет NPC/маркер, на который смотришь |
| `/ponynpc reload` | Перезагружает диалоги, квесты, данжи, block_zones |
| `/ponynpc list` | Загруженные диалоги |
| `/ponynpc quest list` | Загруженные квесты |
| `/ponynpc quest start <id> [player]` | Начать квест |
| `/ponynpc quest status <id> [player]` | Текущий этап |
| `/ponynpc quest advance <id> [player]` | Принудительно завершить этап |
| `/ponynpc quest reset <id> [player]` | Сбросить квест полностью (для теста) |
| `/ponynpc trigger set <quest_id> <stage_id> [radius]` | Записать текущую позицию как центр proximity trigger |
| `/ponynpc pocket [player]` | Содержимое квестового кармана |
| `/ponynpc pocket give <item_id> [player]` | Дать квестовый предмет |
| `/ponynpc pocket take <item_id> [player]` | Забрать квестовый предмет |
| `/ponynpc dungeon list` | Загруженные данжи |
| `/ponynpc dungeon enter <dungeon_id> [player]` | Войти в данж |
| `/ponynpc dungeon exit [player]` | Выйти из данжа (без наград) |

### `/dungeon` — создание данжей (op level 2)

| Команда | Описание |
|---------|----------|
| `/dungeon build <id>` | Телепорт в dungeon-измерение к зоне строительства (5×5 платформа) |
| `/dungeon exit` | Выйти из режима строительства обратно в обычный мир |
| `/dungeon pos1` | Запомнить минимальный угол области (нужно быть в dungeon-мире) |
| `/dungeon pos2` | Запомнить максимальный угол области |
| `/dungeon spawn` | Запомнить точку спауна игроков |
| `/dungeon finish` | Запомнить позицию маркера завершения |
| `/dungeon save <id>` | Сохранить данж в конфиг |
| `/dungeon info <id>` | Показать текущий конфиг данжа |
| `/dungeon test <id>` | Запустить тестовый инстанс для себя |

### `/zone` — создание block_zones (op level 2)

| Команда | Описание |
|---------|----------|
| `/zone wand` | Выдать Zone Wand (ЛКМ=pos1, ПКМ=pos2) |
| `/zone pos1` / `/zone pos2` | Установить угол по смотримому блоку (альтернатива жезлу) |
| `/zone mark <dialog> [name]` | Фиксированный особый блок (по прицелу) |
| `/zone unmark` | Снять fixed-отметку с блока |
| `/zone random_pick <dialog> [count] [name]` | Добавить группу рандомных блоков |
| `/zone add_default <dialog>` | Добавить диалог в пул по умолчанию |
| `/zone save <id> <block> [condition]` | Сохранить и перезагрузить |
| `/zone info` | Текущая сессия |
| `/zone list` | Все загруженные зоны |
| `/zone clear` | Сбросить сессию |

### `/party` — пати (доступна всем игрокам)

| Команда | Описание |
|---------|----------|
| `/party create` | Создать пати (ты — лидер) |
| `/party invite <player>` | Пригласить игрока |
| `/party join <leader>` | Вступить в пати лидера |
| `/party leave` | Покинуть пати |
| `/party kick <player>` | Кикнуть участника (только лидер) |
| `/party list` | Состав текущей пати |

---

## Звуки

### Встроенные события

| ID | Файл | Когда воспроизводится |
|----|------|-----------------------|
| `ponynpc:talk` | `sounds/dialog/talk.ogg` | Набор текста (обычный NPC) |
| `ponynpc:talk_magic` | `sounds/dialog/talk_magic.ogg` | Набор текста (магический NPC) |
| `ponynpc:dialog_click` | `sounds/dialog/dialog_click.ogg` | Нажатие варианта диалога |
| `ponynpc:quest_accept` | `sounds/quest/quest_accept.ogg` | Принятие квеста |
| `ponynpc:quest_stage` | `sounds/quest/quest_stage.ogg` | Переход на новый этап квеста |
| `ponynpc:quest_complete` | `sounds/quest/quest_complete.ogg` | Завершение квеста |
| `ponynpc:item_receive` | `sounds/quest/item_receive.ogg` | Получение квестового предмета |
| `ponynpc:dungeon_enter` | `sounds/dungeon/dungeon_enter.ogg` | Вход в данж |
| `ponynpc:dungeon_exit` | `sounds/dungeon/dungeon_exit.ogg` | Выход из данжа |
| `ponynpc:dungeon_complete` | `sounds/dungeon/dungeon_complete.ogg` | Завершение данжа |

### Добавление своего звука

1. Подготовь короткий OGG Vorbis файл.
2. Положи в `assets/ponynpc/sounds/dialog/myvoice.ogg`.
3. Зарегистрируй в `assets/ponynpc/sounds.json`:
   ```json
   "myvoice": { "sounds": [{ "name": "ponynpc:dialog/myvoice" }] }
   ```
4. В диалоге: `"sound": "ponynpc:myvoice"`.

---

## Скины NPC

**Короткое имя** (Tab-completion):
```
/ponynpc spawn my_dialog applejack Applejack
```

**Полный идентификатор**:
```
/ponynpc spawn my_dialog ponynpc:textures/skins/applejack.png Applejack
```

Если короткое имя не содержит `:`, мод подставляет `ponynpc:textures/skins/<имя>.png`.

### Добавление своего скина

Положи PNG в `src/main/resources/assets/ponynpc/textures/skins/my_pony.png`.

### Race и модель

Раса определяется автоматически из trigger-пикселей скина — той же системой что и в MLP.

---

## Защита NPC от кражи

Флаг `stationary` (через `/ponynpc spawn ... stationary`):

- Каждый тик сервер проверяет позицию и телепортирует NPC обратно.
- Блокирует velocity и knockback.
- Нет физической коллизии.
- Сохраняется в NBT.

---

## Как работает система диалогов

```
Игрок ПКМ по NPC/маркеру
    ↓
Server: canHit() → interact() → NetworkHandler.openDialogForPlayer()
    ↓
Server → Client: OpenDialogPayload
    ↓
Client: DialogScreen открывается
    ↓
Игрок кликает вариант
    ↓
Client → Server: DialogChoicePayload
    ↓
Server: валидация условий → executeAll(actions) → навигация
    ↓
  next == null?            next == "node_id"?
      ↓                         ↓
  CloseDialogPayload        OpenDialogPayload (следующий узел)
```

### Экран диалога

```
┌─────────────────────────────────────┐
│  TWILIGHT SPARKLE                   │  ← Имя NPC (золото)
│  ─────────────────────────────────  │
│  Привет, путник! Это Понивилль...   │  ← Текст (постепенное появление)
├─────────────────────────────────────┤
│  > Расскажи о городе.               │  ← Активный выбор
│  ✓ Знаешь ли ты секрет?            │  ← Выполненный (once=true)
│  > До свидания!                     │
└─────────────────────────────────────┘
```

---

## Дневник квестов

Открывается клавишей **`J`**. Показывает все начатые и завершённые квесты с прогрессом.

```
┌──────────────────┬──────────────────────────────────────┐
│  ! Книга Теней   │  Книга Теней                         │
│                  │  ✓ Поговорить с Трикси               │
│                  │  ▶ Собрать фрагменты оригами  (2/4)  │
│                  │    [████████░░░░░░░░░░]              │
│                  │  ○ Вернуться к Твайлайт              │
└──────────────────┴──────────────────────────────────────┘
```

| Элемент | Значение |
|---------|----------|
| `!` (оранжевый) | В процессе |
| `✓` (зелёный) | Завершён |
| `▶` (золотой) | Текущий активный этап |
| `○` (серый) | Будущий этап |
| `(2/4)` + полоса | Прогресс `multi_collect` |

---

## Quest Marker

`QuestMarkerEntity` — интерактивная сущность без модели пони. Используется для:
- Входа в данж (proximity trigger `spawn_marker`)
- Завершения данжа (dungeon `finish_marker`)
- Постоянных интерактивных объектов в мире (книги, алтари, порталы)

### Внешний вид

- **С предметом** → вращающийся предмет на высоте 0.9 блока
- **Без предмета** → вращающийся золотой каркасный куб

### Постоянный маркер (команда)

```
/ponynpc spawn_marker archive_book minecraft:book Книга Теней
/ponynpc spawn_marker ruins_entrance minecraft:nether_star Вход в Руины
```

Маркер сохраняется в мире между перезапусками (NBT). Видим всем игрокам.  
Удалить: смотри на маркер → `/ponynpc remove`.

### Временный маркер (proximity trigger)

```json
"spawn_marker": {
  "name": "Войти в Руины Теней",
  "actions": ["dungeon:enter:shadow_ruins"]
}
```

Появляется когда игрок входит в радиус, исчезает после нажатия. Виден только этому игроку.

### Маркер завершения данжа (finish_marker)

```json
"finish_marker": {
  "name": "Алтарь Теней",
  "offset": { "x": 4, "y": 4, "z": 9 },
  "actions": ["dungeon:complete", "quest:shadow_book_ch1:complete_stage:enter_ruins"]
}
```

---

## Block Interaction Zones

Позволяет сделать обычные блоки мира (книжные полки, алтари, сундуки и т.п.) кликабельными в определённой зоне, только во время нужного этапа квеста. При ПКМ открывается диалог — без спауна сущностей.

**Примеры применения:**
- Библиотека: одна полка особая (рандомно выбирается для каждого игрока), остальные — рандомный текст из пула.
- Алтарь: один из камней в руинах активирует событие.
- Доска объявлений: несколько записок с разными текстами.

### Три механизма назначения диалогов

Их можно комбинировать в любом сочетании:

| Механизм | Команда | Описание |
|----------|---------|----------|
| **Fixed** | `/zone mark` | Конкретный блок по координатам → фиксированный диалог |
| **Random pick** | `/zone random_pick` | Сервер рандомно выбирает N блоков как «особые» для каждого игрока |
| **Default pool** | `/zone add_default` | Все остальные блоки → случайный диалог из пула |

### Workflow

**1. Взять жезл и выделить зону**
```
/zone wand
  ЛКМ по угловому блоку  → pos1
  ПКМ по другому углу    → pos2
```

**2. Настроить диалоги**

*Вариант А — нужная полка всегда фиксирована (ты сам знаешь какая):*
```
(наводишь прицел на полку)
/zone mark library_key_shelf Полка с загадочной книгой
```

*Вариант Б — нужная полка рандомная (разная для каждого игрока):*
```
/zone random_pick library_key_shelf 1 Полка с загадочной книгой
```
Сервер сам найдёт все книжные полки в зоне и при первом взаимодействии игрока тайно назначит одну из них "особой".

*Вариант В — комбинация: одна фиксированная запись куратора + рандомная особая полка + пул случайных текстов:*
```
/zone mark library_curator_note Записка куратора
/zone random_pick library_key_shelf 1 Полка с загадочной книгой
/zone add_default library_shelf_boring
/zone add_default library_shelf_funny
/zone add_default library_shelf_lore
```

**3. Сохранить**
```
/zone save <id> <тип_блока> [условие]
```

| Параметр | Описание |
|----------|----------|
| `id` | Уникальное имя зоны (например `library_archive`) |
| `тип_блока` | Какой блок реагирует (например `minecraft:bookshelf`) |
| `условие` | *(опционально)* Когда зона активна (`quest:shadow_book_ch1:stage:find_book`) |

### Полный пример: библиотека с рандомной особой полкой

```
/zone wand
  ЛКМ по углу архива  → pos1
  ПКМ по другому углу → pos2

/zone random_pick library_key_shelf 1 Полка с загадочной книгой
/zone add_default library_shelf_boring
/zone add_default library_shelf_funny
/zone add_default library_shelf_lore

/zone save library_archive minecraft:bookshelf quest:shadow_book_ch1:stage:find_book
```

Результат для каждого игрока отдельно:
- При первом клике по любой полке сервер сканирует зону, перемешивает полки и назначает 1 из них как "особую" для этого игрока
- Особая полка → `library_key_shelf`
- Все остальные → рандомно один из: `library_shelf_boring`, `library_shelf_funny`, `library_shelf_lore`
- Работает только на этапе `find_book`
- Вне зоны — никакой реакции

### Все команды `/zone`

| Команда | Описание |
|---------|----------|
| `/zone wand` | Выдать Zone Wand (ЛКМ=pos1, ПКМ=pos2) |
| `/zone pos1` / `/zone pos2` | Установить угол по смотримому блоку (альтернатива жезлу) |
| `/zone mark <dialog> [name]` | Фиксированный особый блок (по прицелу) |
| `/zone unmark` | Снять fixed-отметку с блока |
| `/zone random_pick <dialog> [count] [name]` | Добавить группу рандомных блоков |
| `/zone add_default <dialog>` | Добавить диалог в пул по умолчанию |
| `/zone save <id> <block> [condition]` | Сохранить и перезагрузить |
| `/zone info` | Текущая сессия |
| `/zone list` | Все загруженные зоны |
| `/zone clear` | Сбросить сессию |

### JSON формат

Файлы хранятся в `config/ponynpc/block_zones/<id>.json`.  
Создаются командами автоматически, но можно редактировать вручную.

```json
{
  "id": "library_archive",
  "condition": "quest:shadow_book_ch1:stage:find_book",
  "world": "minecraft:overworld",
  "min_x": 100, "min_y": 60, "min_z": 200,
  "max_x": 120, "max_y": 75, "max_z": 220,
  "target_block": "minecraft:bookshelf",

  "random_picks": [
    { "count": 1, "dialog": "library_key_shelf", "name": "Полка с загадочной книгой" }
  ],

  "default_dialogs": [
    "library_shelf_boring",
    "library_shelf_funny",
    "library_shelf_lore"
  ],
  "default_name": "Книжная полка",

  "blocks": [
    { "x": 110, "y": 64, "z": 208, "dialog": "library_curator_note", "name": "Записка куратора" }
  ]
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `condition` | string | Условие активации. Пустая строка = зона всегда активна. |
| `world` | string | Измерение. Пустая строка = любое. |
| `target_block` | string | Тип блока, реагирующего на ПКМ. |
| `random_picks` | array | Группы рандомно назначаемых блоков. Per-player, вычисляется при первом касании зоны. |
| `random_picks[].count` | int | Сколько блоков назначить этой группе. |
| `random_picks[].dialog` | string | Диалог для блоков группы. |
| `default_dialogs` | array | Пул диалогов для всех остальных блоков. При каждом клике выбирается случайный. |
| `default_dialog` | string | Shorthand для одного диалога по умолчанию (совместимость). |
| `default_name` | string | Имя в шапке диалогового экрана для дефолтных блоков. |
| `blocks` | array | Фиксированные позиции с явным `dialog`/`name`. Приоритет выше random_picks. |

---

## Система данжей

### Концепция

Данж — изолированная копия построенной вручную области в измерении `ponynpc:dungeon`.  
Каждый игрок/пати получает отдельный **слот** (смещение по X). При выходе слот очищается.

### Workflow создания данжа

```
1. /dungeon build <id>    → телепорт в dungeon-мир, появляется 5×5 каменная платформа
2. Строишь данж вручную
3. /dungeon pos1          → нижний угол области
4. /dungeon pos2          → верхний угол области
5. /dungeon spawn         → точка спауна игроков (где стоишь)
6. /dungeon finish        → позиция маркера завершения
7. /dungeon save <id>     → сохранить конфиг в config/ponynpc/dungeons/<id>.json
8. /dungeon exit          → вернуться в обычный мир
```

### Формат JSON данжа

```json
{
  "id": "shadow_ruins",
  "name": "Руины Теней",
  "build_origin": { "x": -25004, "y": 60, "z": -4 },
  "size": { "width": 8, "height": 11, "length": 14 },
  "spawn_offset": { "x": 4, "y": 4, "z": 3 },
  "finish_marker": {
    "name": "Алтарь Теней",
    "offset": { "x": 4, "y": 4, "z": 9 },
    "actions": ["dungeon:complete", "quest:shadow_book_ch1:complete_stage:enter_ruins"]
  },
  "fixed_time": 18000,
  "destructible": false,
  "rewards": [
    { "type": "currency", "count": 100 },
    { "type": "item", "id": "minecraft:diamond_helmet", "count": 1 },
    { "type": "quest_item", "id": "shadow_book", "count": 1 }
  ]
}
```

### Поля

| Поле | Описание |
|------|----------|
| `build_origin` | Координаты начала build-зоны в dungeon-мире |
| `size` | Размер области (width/height/length) |
| `spawn_offset` | Смещение точки спауна относительно origin |
| `finish_marker` | Маркер завершения (ПКМ → выполнить actions) |
| `finish_npc` | Альтернатива finish_marker — ghost NPC с диалогом |
| `fixed_time` | Фиксированное время суток (0–24000), `null` = не менять |
| `destructible` | Игроки могут ломать блоки данжа (по умолчанию `false`) |
| `rewards` | Список наград при завершении |

### Типы наград

| `type` | Поля | Описание |
|--------|------|----------|
| `"currency"` | `count` | N золотых самородков |
| `"item"` | `id`, `count` | Обычный MC предмет |
| `"quest_item"` | `id`, `count` | Предмет в квестовый карман |

### Защита блоков

Если `destructible: false` — игроки **не могут** ломать блоки данжа.  
Исключение: блоки, которые сам игрок поставил в данже — их ломать можно.  
Ставить блоки можно всегда.

### Данжи и смерть

При смерти в данже и возрождении в обычном мире — инстанс автоматически очищается.  
Игрок может снова войти в данж.

---

## Система пати

Позволяет группе игроков входить в данж вместе. Лидер пати запускает `/ponynpc dungeon enter` — вся онлайн-пати телепортируется.

```
/party create          — создать пати
/party invite <player> — пригласить
/party join <leader>   — принять приглашение
/party leave           — покинуть
/party list            — состав
```

---

## Структура пакетов

### OpenDialogPayload (S2C)

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC/маркера |
| `npcName` | String | Имя |
| `dialogId` | String | ID диалога |
| `nodeId` | String | ID текущего узла |
| `text` | String | Текст узла |
| `sound` | String | Sound event ID |
| `soundChars` | int | Интервал звука |
| `choices` | List | Список вариантов |

### DialogChoicePayload (C2S)

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC/маркера |
| `nodeId` | String | Текущий узел |
| `choiceId` | String | ID выбора (`"__close__"` = ESC) |

---

## Прогресс игрока

| Файл | Содержимое |
|------|------------|
| `[world]/data/ponynpc_dialog_progress.dat` | Прогресс once-выборов диалогов |
| `[world]/data/ponynpc_quest_progress.dat` | Этапы квестов |
| `[world]/data/ponynpc_quest_pocket.dat` | Квестовый карман |

Ключ для прогресса диалога: `dialogId + ":" + npcUuid` — у двух NPC с одним диалогом независимый прогресс.

---

## Структура файлов проекта

```
submod-ponynpc/
├── build.gradle / gradle.properties
├── WIKI.md / wiki/
│
├── src/main/resources/
│   ├── fabric.mod.json
│   ├── ponynpc.mixins.json
│   ├── assets/ponynpc/
│   │   ├── sounds.json
│   │   ├── sounds/
│   │   │   ├── dialog/   talk.ogg  talk_magic.ogg  dialog_click.ogg
│   │   │   ├── quest/    quest_accept.ogg  quest_stage.ogg  quest_complete.ogg  item_receive.ogg
│   │   │   └── dungeon/  dungeon_enter.ogg  dungeon_exit.ogg  dungeon_complete.ogg
│   │   ├── textures/skins/
│   │   └── lang/en_us.json
│   └── data/ponynpc/
│       ├── dialogs/      ← Встроенные примеры
│       ├── quests/       ← shadow_book_ch1.json
│       └── block_zones/  ← Примеры зон интерактивных блоков
│
└── src/main/java/com/minelittlepony/ponynpc/
    ├── PonyNpcMod.java               Server entrypoint
    ├── PonyNpcClient.java            Client entrypoint
    ├── entity/
    │   ├── PonyNpcEntity.java        Пони NPC (stationary, ghost NPC)
    │   ├── QuestMarkerEntity.java    Интерактивный маркер (куб/предмет)
    │   ├── IDialogEntity.java        Интерфейс для диалоговых сущностей
    │   ├── VirtualDialogContext.java Виртуальный источник диалога (block_zones)
    │   └── PlayerVisibleEntity.java  Фильтрация видимости per-player
    ├── registry/
    │   ├── PonyNpcEntities.java
    │   └── PonyNpcSounds.java
    ├── dialog/
    │   ├── DialogMode/Data/Node/Choice/Manager.java
    │   └── ...
    ├── network/
    │   ├── OpenDialogPayload.java
    │   ├── CloseDialogPayload.java
    │   ├── DialogChoicePayload.java
    │   ├── QuestJournalPayload.java
    │   ├── RequestQuestJournalPayload.java
    │   └── NetworkHandler.java
    ├── quest/
    │   ├── QuestStageType/Stage/Definition/Manager.java
    │   ├── QuestConditionChecker.java
    │   ├── QuestActionExecutor.java
    │   ├── ProximityTriggerConfig.java
    │   ├── GhostNpcSpawnConfig.java
    │   ├── BlockZoneDefinition.java    Конфиг зоны интерактивных блоков
    │   ├── BlockZoneRegistry.java      Загрузчик block_zones/*.json
    │   └── BlockInteractionManager.java ПКМ-обработчик зон
    ├── state/
    │   ├── PlayerDialogState.java
    │   ├── QuestStateStore.java
    │   └── QuestPocketState.java
    ├── ghost/
    │   └── GhostNpcManager.java
    ├── trigger/
    │   └── ProximityTriggerManager.java
    ├── dungeon/
    │   ├── DungeonDefinition.java
    │   ├── DungeonRegistry.java
    │   ├── DungeonInstance.java
    │   ├── DungeonInstanceManager.java
    │   ├── DungeonCopier.java
    │   └── DungeonBuilderSession.java
    ├── party/
    │   ├── Party.java
    │   └── PartyManager.java
    ├── mixin/
    │   └── EntityTrackerMixin.java   Ghost NPC visibility filter
    ├── command/
    │   ├── PonyNpcCommand.java
    │   ├── DungeonBuilderCommand.java
    │   ├── PartyCommand.java
    │   ├── ZoneBuilderCommand.java     /zone — создание block_zones
    │   └── ZoneBuilderSession.java     Временная сессия разметки
    └── client/
        ├── render/
        │   ├── PonyNpcRenderer.java
        │   └── QuestMarkerRenderer.java
        └── screen/
            ├── DialogScreen.java
            └── QuestJournalScreen.java
```

---

*PonyNPC 1.1.0 / Minecraft 1.21 / Fabric*
