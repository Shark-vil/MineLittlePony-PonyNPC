# PonyNPC — WIKI

Подмод для **Mine Little Pony** (Minecraft 1.21.1, Fabric).  
Добавляет NPC-пони с системой диалогов в стиле Fallout 3.

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
10. [Структура пакетов](#структура-пакетов)
11. [Прогресс игрока](#прогресс-игрока)
12. [Структура файлов проекта](#структура-файлов-проекта)

**Расширенная документация (wiki/)**
- [Квестовая система](wiki/QUEST_SYSTEM.md) — квесты, этапы, квестовый карман
- [Условия и действия диалогов](wiki/DIALOG_ACTIONS.md) — conditions/actions в DialogChoice
- [Ghost NPC](wiki/GHOST_NPC.md) — NPC видимые только конкретным игрокам

---

## Архитектура

```
PonyNpcMod (server/common)         PonyNpcClient (client-only)
    │                                   │
    ├─ PonyNpcEntities                  ├─ PonyNpcRenderer
    │    └─ EntityType<PonyNpcEntity>   │    └─ Использует MLP ModelType
    ├─ PonyNpcSounds                    └─ DialogScreen
    ├─ DialogManager                          └─ Fallout 3 стиль UI
    │    └─ JSON → DialogData
    ├─ NetworkHandler
    │    ├─ OpenDialogPayload (S2C)
    │    ├─ CloseDialogPayload (S2C)
    │    └─ DialogChoicePayload (C2S)
    ├─ PlayerDialogState (PersistentState)
    └─ PonyNpcCommand
```

---

## Установка и сборка

### Требования

- Java 21+
- Gradle (через `gradlew.bat` / `./gradlew`)
- MLP jar в папке `libs/` (копируется автоматически из loom-кэша скриптом)

### Сборка

```bat
build-ponynpc.bat
```

JAR появится в `build/libs/ponynpc-1.0.0.jar`.

### Зависимости (runtime)

| Мод | Обязательно |
|-----|-------------|
| Mine Little Pony | ✅ |
| Fabric API 0.102.0+ | ✅ |
| MSON 1.10.0+ | ✅ (через MLP) |
| Kirin 1.20.1+ | ✅ (через MLP) |

---

## Режимы диалога (DialogMode)

Поле `"mode"` в корне JSON-файла диалога.

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
- Нет выборов → игрок читает и кликает / нажимает ESC.
- Прогресс не сохраняется.

---

## Создание диалогов (JSON)

Файлы диалогов хранятся в:
```
[папка сервера]/config/ponynpc/dialogs/<id>.json
```

### Пример: QUEST

```json
{
  "id": "my_dialog",
  "nodes": {
    "start": {
      "text": "Привет, путник! Что тебя привело в Понивилль?",
      "sound": "ponynpc:talk",
      "sound_chars": 2,
      "choices": [
        { "id": "ask", "text": "Расскажи о городе.", "next": "about_town", "once": false },
        { "id": "secret", "text": "Знаешь ли ты секрет?", "next": "secret_node", "once": true },
        { "id": "bye", "text": "До свидания!", "next": null, "once": false }
      ]
    },
    "about_town": {
      "text": "Это прекрасный город!",
      "choices": [{ "id": "back", "text": "Спасибо!", "next": "start", "once": false }]
    },
    "secret_node": {
      "text": "К северу от Понивилля есть скрытые руины...",
      "sound": "ponynpc:talk_magic",
      "choices": [{ "id": "noted", "text": "Запомню.", "next": "start", "once": false }]
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
    "Did you hear about the dragon near Canterlot?",
    "Lovely weather today, isn't it?"
  ]
}
```

### Поля

#### Корневой объект

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | ID диалога. Если пустой — берётся из имени файла. |
| `mode` | string | `"quest"` (по умолчанию) или `"random"`. |
| `nodes` | object | Словарь узлов (QUEST). **Обязателен узел `"start"`**. |
| `phrases` | array | Список реплик (RANDOM). |
| `sound` | string | Sound event ID (RANDOM). По умолчанию: `"ponynpc:talk"`. |
| `sound_chars` | int | Интервал звука в символах (RANDOM). По умолчанию: `2`. |

#### DialogNode

| Поле | Тип | Описание |
|------|-----|----------|
| `text` | string | Текст NPC. |
| `sound` | string | Sound event для печатания. |
| `sound_chars` | int | Звук каждые N символов. |
| `choices` | array | Варианты ответа. |

#### DialogChoice

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | Уникальный ID варианта. |
| `text` | string | Текст кнопки. |
| `next` | string\|null | Следующий узел. `null` = закрыть диалог. |
| `once` | bool | Помечается выполненным после первого нажатия. |

---

## Команды

Все команды требуют уровня оператора (`op level 2`).

| Команда | Описание |
|---------|----------|
| `/ponynpc spawn <dialog_id>` | Спавнит NPC перед игроком |
| `/ponynpc spawn <dialog_id> <skin>` | С указанным скином |
| `/ponynpc spawn <dialog_id> <skin> <name>` | С именем |
| `/ponynpc spawn <dialog_id> <skin> stationary` | Неподвижный NPC |
| `/ponynpc spawn <dialog_id> <skin> stationary <name>` | Неподвижный с именем |
| `/ponynpc remove` | Удаляет NPC, на которого смотришь |
| `/ponynpc reload` | Перезагружает все JSON-диалоги |
| `/ponynpc list` | Показывает загруженные диалоги |

### Примеры

```
/ponynpc spawn example
/ponynpc spawn example applejack Applejack
/ponynpc spawn example twilight_sparkle stationary Twilight Sparkle
/ponynpc spawn example ponynpc:textures/skins/rarity.png stationary Rarity
```

### Параметры

**`<dialog_id>`** — Tab-completion показывает загруженные диалоги.

**`<skin>`** — принимает:
- Короткое имя встроенного скина: `applejack`, `twilight_sparkle`, `rainbow_dash` — Tab-completion работает.
- Полный идентификатор: `ponynpc:textures/skins/applejack.png`
- Скин из другого мода: `minelittlepony:textures/entity/pony/twilight.png`
- Если не указан — назначается случайный фоновый пони из MLP.

**`stationary`** — ключевое слово. NPC будет заморожен на месте:
- Не может быть сдвинут физически.
- Возвращается на исходную позицию если перемещён модами (Carry On, Unicopia и т.д.).
- Не имеет физической коллизии — игроки проходят сквозь него.
- Взаимодействие (диалог по ПКМ) работает как обычно.

---

## Звуки

### Встроенные события

| ID | Файл | Описание |
|----|------|----------|
| `ponynpc:talk` | `sounds/dialog/talk.ogg` | Обычное общение |
| `ponynpc:talk_magic` | `sounds/dialog/talk_magic.ogg` | Магическое общение |

### Добавление звука

1. Подготовь короткий OGG Vorbis файл (0.05–0.15 сек).
2. Положи в `assets/ponynpc/sounds/dialog/myvoice.ogg`.
3. Зарегистрируй в `assets/ponynpc/sounds.json`:
   ```json
   "myvoice": { "sounds": [{ "name": "ponynpc:dialog/myvoice", "stream": false }] }
   ```
4. В диалоге: `"sound": "ponynpc:myvoice"`.

---

## Скины NPC

### Как задать скин

**Короткое имя** (рекомендуется, Tab-completion):
```
/ponynpc spawn my_dialog applejack Applejack
```

**Полный идентификатор**:
```
/ponynpc spawn my_dialog ponynpc:textures/skins/applejack.png Applejack
```

Если короткое имя не содержит `:`, мод автоматически подставляет путь `ponynpc:textures/skins/<имя>.png`.

### Добавление своего скина

1. Положи PNG в `src/main/resources/assets/ponynpc/textures/skins/my_pony.png`
2. После сборки: `/ponynpc spawn dialog my_pony My Pony`

### Форматы

| Формат | Пример |
|--------|--------|
| Короткое имя (встроенный) | `applejack` |
| Из ресурсов мода | `ponynpc:textures/skins/my_pony.png` |
| Из ресурсов MLP | `minelittlepony:textures/entity/pony/twilight.png` |
| Не задан | Случайный фоновый пони из MLP |

### Race и модель

Раса определяется автоматически из trigger-пикселей скина — той же системой что и в MLP.

---

## Защита NPC от кражи

### Флаг `stationary`

NPC, заспавненный с ключевым словом `stationary`:

- **Фиксирован на месте.** Каждый тик сервер проверяет позицию и телепортирует NPC обратно если он сдвинулся.
- **Блокирует velocity и knockback** — попытки сдвинуть через взрыв, заклинание или мод не дадут результата.
- **Нет физической коллизии** — игроки и сущности проходят сквозь NPC (взаимодействие по ПКМ работает).
- **Защищает от Carry On, Unicopia Telekinesis** и других модов, перемещающих сущности.
- **Сохраняется в NBT** — после перезагрузки мира NPC остаётся заморожен на той же позиции.

### Дистанция активации диалога

Диалог активируется только если игрок находится не дальше **4 блоков** от NPC. Это предотвращает случайную активацию издалека.

### Удаление NPC

Даже stationary NPC можно удалить командой:
```
/ponynpc remove
```
(посмотри на NPC и выполни команду)

---

## Как работает система диалогов

```
Игрок ПКМ по NPC (≤ 4 блока)
    ↓
Server: interactMob() → проверка дистанции
    ↓
Server: DialogManager.getDialog(npc.dialogId)
    ↓
Server → Client: OpenDialogPayload
    ↓
Client: DialogScreen открывается
    ↓
Игрок кликает на вариант
    ↓
Client → Server: DialogChoicePayload
    ↓
Server: валидация → markChoiceDone() если once=true
    ↓
  next == null?         next == "node_id"?
      ↓                     ↓
  CloseDialogPayload    OpenDialogPayload (следующий узел)
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

## Структура пакетов

### OpenDialogPayload (S2C)

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |
| `npcName` | String | Имя NPC |
| `dialogId` | String | ID диалога |
| `nodeId` | String | ID текущего узла |
| `text` | String | Текст узла |
| `sound` | String | Sound event ID |
| `soundChars` | int | Интервал звука |
| `choices` | List\<ChoiceData\> | Список вариантов |

### DialogChoicePayload (C2S)

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |
| `nodeId` | String | Текущий узел |
| `choiceId` | String | ID выбора (`"__close__"` = ESC) |

### CloseDialogPayload (S2C)

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |

---

## Прогресс игрока

Хранится в `PlayerDialogState` (extends `PersistentState`).

- **Файл:** `[world]/data/ponynpc_dialog_progress.dat`
- **Ключ:** `dialogId + ":" + npcUuid` — уникален для каждого NPC
- У двух NPC с одним диалогом — независимый прогресс.

---

## Квестовая система (Phase 1)

Квесты хранятся в `config/ponynpc/quests/*.json`.  
Подробная документация: [wiki/QUEST_SYSTEM.md](wiki/QUEST_SYSTEM.md)

Быстрый старт:
```
/ponynpc quest list                         — список квестов
/ponynpc quest start shadow_book_ch1        — начать квест
/ponynpc quest status shadow_book_ch1       — текущий этап
/ponynpc pocket                             — содержимое квестового кармана
/ponynpc reload                             — перезагрузить всё
```

---

## Расширение в будущем

### Новые режимы диалога

```java
// 1. DialogMode.java
@SerializedName("gpt") GPT,

// 2. DialogManager.validate()
case GPT -> { /* валидация */ yield true; }

// 3. NetworkHandler.openDialogForPlayer()
case GPT -> openGptDialog(player, npc, dialog);
```

### Квестовая система

Добавь `actions` в `DialogChoice`:
```json
"actions": ["give_item:diamond:1", "set_flag:quest_started"]
```

### Ходьба и патрулирование

`PonyNpcEntity` extends `PathAwareEntity` — готова к AI:
```java
this.goalSelector.add(0, new WanderAroundFarGoal(this, 1.0));
```

---

## Структура файлов проекта

```
submod-ponynpc/
├── build.gradle
├── gradle.properties
├── WIKI.md
├── wiki/                              ← Расширенная документация
│   ├── QUEST_SYSTEM.md
│   ├── DIALOG_ACTIONS.md
│   └── GHOST_NPC.md
│
├── src/main/resources/
│   ├── fabric.mod.json
│   ├── ponynpc.mixins.json            ← Mixin конфигурация
│   ├── assets/ponynpc/
│   │   ├── icon.png
│   │   ├── sounds.json
│   │   ├── sounds/dialog/
│   │   │   ├── talk.ogg
│   │   │   └── talk_magic.ogg
│   │   ├── textures/skins/           ← PNG скины
│   │   └── lang/en_us.json
│   └── data/ponynpc/
│       ├── dialogs/                  ← Встроенные примеры диалогов
│       │   ├── example.json
│       │   ├── example_random.json
│       │   ├── trixie_encounter_1.json
│       │   ├── librarian_dialog.json
│       │   └── twilight_library_card.json
│       └── quests/                   ← Встроенные примеры квестов
│           └── shadow_book_ch1.json
│
└── src/main/java/com/minelittlepony/ponynpc/
    ├── PonyNpcMod.java               Server entrypoint
    ├── PonyNpcClient.java            Client entrypoint
    ├── entity/
    │   └── PonyNpcEntity.java        NPC (stationary, ghost NPC support)
    ├── registry/
    │   ├── PonyNpcEntities.java
    │   └── PonyNpcSounds.java
    ├── dialog/
    │   ├── DialogMode.java
    │   ├── DialogData.java
    │   ├── DialogNode.java
    │   ├── DialogChoice.java         + conditions, actions, hiddenIfConditionFails
    │   └── DialogManager.java
    ├── network/
    │   ├── OpenDialogPayload.java    + ChoiceData.available
    │   ├── CloseDialogPayload.java
    │   ├── DialogChoicePayload.java
    │   └── NetworkHandler.java       + условия/действия, ghost despawn
    ├── quest/                        ← Новая квестовая система
    │   ├── QuestStageType.java
    │   ├── QuestStage.java
    │   ├── QuestDefinition.java
    │   ├── ProximityTriggerConfig.java
    │   ├── GhostNpcSpawnConfig.java
    │   ├── QuestManager.java
    │   ├── QuestConditionChecker.java
    │   └── QuestActionExecutor.java
    ├── state/
    │   ├── PlayerDialogState.java
    │   ├── PlayerQuestState.java      ← Новый
    │   ├── QuestStateStore.java       ← Новый
    │   └── QuestPocketState.java      ← Новый
    ├── ghost/                         ← Ghost NPC система
    │   └── GhostNpcManager.java
    ├── trigger/                       ← Proximity trigger система
    │   └── ProximityTriggerManager.java
    ├── mixin/                         ← Mixins
    │   └── EntityTrackerMixin.java    Ghost NPC visibility filter
    ├── command/
    │   └── PonyNpcCommand.java       + quest/pocket команды
    └── client/
        ├── render/
        │   └── PonyNpcRenderer.java
        └── screen/
            └── DialogScreen.java     + locked choice support
```

---

*Документация актуальна для PonyNPC 1.0.0 / Minecraft 1.21.1 / Fabric*
