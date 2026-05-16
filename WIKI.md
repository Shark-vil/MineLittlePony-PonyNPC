# PonyNPC — WIKI

Подмод для **Mine Little Pony** (Minecraft 1.21.1, Fabric).  
Добавляет NPC-пони с системой диалогов в стиле Fallout 3.

---

## Содержание

1. [Архитектура](#архитектура)
2. [Установка и сборка](#установка-и-сборка)
3. [Режимы диалога (DialogMode)](#режимы-диалога-dialogmode)
4. [Создание диалогов (JSON)](#создание-диалогов-json)
5. [Команды](#команды)
6. [Звуки](#звуки)
7. [Скины NPC](#скины-npc)
8. [Как работает система диалогов](#как-работает-система-диалогов)
9. [Структура пакетов](#структура-пакетов)
10. [Прогресс игрока](#прогресс-игрока)
11. [Расширение в будущем](#расширение-в-будущем)
12. [Структура файлов проекта](#структура-файлов-проекта)

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

**Потоки данных:**

```
[Игрок ПКМ по NPC]
   → interactMob() на сервере
   → NetworkHandler.openDialogForPlayer()
   → ServerPlayNetworking.send(OpenDialogPayload)
   → Клиент: DialogScreen открывается

[Игрок выбирает ответ]
   → ClientPlayNetworking.send(DialogChoicePayload)
   → Сервер: handleChoice()
      → markChoiceDone() (если once=true)
      → sendNode() следующего узла
         ИЛИ send(CloseDialogPayload)
   → Клиент: переход или закрытие
```

---

## Режимы диалога (DialogMode)

Поле `"mode"` в корне JSON-файла диалога. Регистр не важен.

| Режим | Значение | Описание |
|-------|----------|----------|
| `"quest"` | `DialogMode.QUEST` | Ветвистый диалог с узлами, выборами и прогрессом (по умолчанию) |
| `"random"` | `DialogMode.RANDOM` | Случайная реплика из списка `phrases`, без выборов |
| *(зарезервировано)* | `SCRIPTED` | Будущий режим: скриптованная последовательность |
| *(зарезервировано)* | `GPT` | Будущий режим: реплика генерируется ИИ |
| *(зарезервировано)* | `SHOP` | Будущий режим: диалог-магазин |

### Поведение каждого режима

#### QUEST (по умолчанию)
- Загружает узел `"start"`, показывает варианты ответа.
- Прогресс `once`-выборов сохраняется per-player, per-NPC.
- Диалог закрывается когда выбран вариант с `"next": null`.

#### RANDOM
- Выбирает одну строку из массива `phrases` случайным образом.
- Нет выборов → игрок читает и кликает / нажимает ESC.
- Прогресс **не сохраняется** (каждый раз новая реплика).
- Звук и интервал задаются на уровне диалога (`sound`, `sound_chars`).

### Как добавить новый режим (для разработчика)

1. Добавить значение в `DialogMode.java` с `@SerializedName`.
2. Добавить валидацию в `DialogManager.validate()` (switch-case).
3. Добавить обработчик в `NetworkHandler.openDialogForPlayer()` (switch-case).
4. Если нужен прогресс — расширить `PlayerDialogState`.

```java
// NetworkHandler.java — достаточно добавить одну ветку:
case SCRIPTED -> openScriptedDialog(player, npc, dialog);
```

---

## Установка и сборка

### Требования

- Java 21+
- Gradle (через `gradlew.bat` / `./gradlew`)
- Родительский проект MineLittlePony (оба должны быть в одной папке)

### Сборка (двухшаговая)

Из корня MineLittlePony:

```bat
build-ponynpc.bat
```

Или вручную:

```bat
:: Шаг 1 — собрать MineLittlePony.jar (без сабмода, чтобы не было конфликта версий)
gradlew.bat :remapJar -PskipSubmod

:: Шаг 2 — собрать сабмод, подхватив jar из шага 1
gradlew.bat :submod-ponynpc:build
```

JAR появится в `submod-ponynpc/build/libs/ponynpc-1.0.0.jar`.

> **Почему два шага?** Плагин `reckon` генерирует timestamp-версию при каждом запуске Gradle. Если собирать в один шаг — сабмод ищет jar с новым timestamp, которого ещё нет. `-PskipSubmod` отключает включение сабмода в первый шаг.

### Зависимости (runtime)

| Мод | Обязательно |
|-----|-------------|
| Mine Little Pony | ✅ |
| Fabric API 0.102.0+ | ✅ |
| MSON 1.10.0+ | ✅ (через MLP) |
| Kirin 1.20.1+ | ✅ (через MLP) |

---

## Создание диалогов (JSON)

Файлы диалогов хранятся в:
```
[папка сервера]/config/ponynpc/dialogs/<id>.json
```

При первом запуске создаётся пример: `example.json`.

### Структура файла

```json
{
  "id": "my_dialog",
  "nodes": {
    "start": {
      "text": "Привет, путник! Что тебя привело в Понивилль?",
      "sound": "ponynpc:talk",
      "sound_chars": 2,
      "choices": [
        {
          "id": "ask_about",
          "text": "Расскажи о городе.",
          "next": "about_town",
          "once": false
        },
        {
          "id": "secret",
          "text": "Знаешь ли ты секрет? (только раз)",
          "next": "secret_node",
          "once": true
        },
        {
          "id": "bye",
          "text": "До свидания!",
          "next": null,
          "once": false
        }
      ]
    },
    "about_town": {
      "text": "Это прекрасный город! Основан земными пони много лет назад.",
      "sound": "ponynpc:talk",
      "sound_chars": 2,
      "choices": [
        {
          "id": "back",
          "text": "Спасибо! (вернуться)",
          "next": "start",
          "once": false
        }
      ]
    },
    "secret_node": {
      "text": "Говорят, в Эквестрии есть скрытые руины к северу от Понивилля...",
      "sound": "ponynpc:talk_magic",
      "sound_chars": 3,
      "choices": [
        {
          "id": "noted",
          "text": "Запомню. Спасибо.",
          "next": "start",
          "once": false
        }
      ]
    }
  }
}
```

### Пример: режим RANDOM

```json
{
  "id": "market_chatter",
  "mode": "random",
  "sound": "ponynpc:talk",
  "sound_chars": 2,
  "phrases": [
    "Fresh apples, only 5 bits!",
    "Did you hear about the dragon near Canterlot?",
    "Lovely weather today, isn't it?",
    "The Wonderbolts are performing next week!"
  ]
}
```

Каждый раз при взаимодействии NPC случайно выбирает одну фразу. Игрок кликает для закрытия.

---

### Поля

#### Корневой объект

| Поле | Тип | Режим | Описание |
|------|-----|-------|----------|
| `id` | string | Все | ID диалога. Если пустой — берётся из имени файла. |
| `mode` | string | Все | `"quest"` (по умолчанию) или `"random"`. |
| `nodes` | object | QUEST | Словарь узлов. **Обязателен узел `"start"`**. |
| `phrases` | array | RANDOM | Список строк-реплик для случайного выбора. |
| `sound` | string | RANDOM | Sound event ID. По умолчанию: `"ponynpc:talk"`. |
| `sound_chars` | int | RANDOM | Интервал звука (символов). По умолчанию: `2`. |

#### DialogNode

| Поле | Тип | По умолчанию | Описание |
|------|-----|--------------|----------|
| `text` | string | `""` | Текст NPC. Поддерживает `\n` |
| `sound` | string | `"ponynpc:talk"` | ID звукового события для печатания |
| `sound_chars` | int | `2` | Звук играет каждые N символов |
| `choices` | array | `[]` | Варианты ответа игрока |

#### DialogChoice

| Поле | Тип | По умолчанию | Описание |
|------|-----|--------------|----------|
| `id` | string | `""` | Уникальный ID варианта внутри узла |
| `text` | string | `""` | Текст кнопки |
| `next` | string\|null | `null` | ID следующего узла. `null` = закрыть диалог |
| `once` | bool | `false` | Если `true` — помечается выполненным после первого нажатия |

### Правила

- **Всегда нужен узел `"start"`** — диалог начинается с него.
- Если у узла **нет выборов** — появляется кнопка «Нажми ESC».
- Если `next: null` — диалог закрывается.
- Варианты с `once: true` отображаются серым после первого выбора, но остаются кликабельными.
- Прогресс `once` уникален для каждого **игрока** и каждого **экземпляра NPC** (по UUID).

---

## Команды

Все команды требуют уровня оператора (`op level 2`).

| Команда | Описание |
|---------|----------|
| `/ponynpc spawn <dialog_id>` | Спавнит NPC перед игроком |
| `/ponynpc spawn <dialog_id> <skin>` | Спавнит NPC с указанным скином |
| `/ponynpc spawn <dialog_id> <skin> <name>` | Спавнит NPC с именем |
| `/ponynpc remove` | Удаляет NPC, на которого смотришь |
| `/ponynpc reload` | Перезагружает все JSON-диалоги |
| `/ponynpc list` | Показывает загруженные диалоги |

### Примеры

```
/ponynpc spawn example
/ponynpc spawn example "ponynpc:textures/skins/twilight.png" "Twilight Sparkle"
/ponynpc reload
```

**`<dialog_id>`** — Tab-completion показывает загруженные диалоги.  
**`<skin>`** — идентификатор текстуры вида `namespace:textures/path/file.png`. Файл должен лежать в `assets/<namespace>/<path>`.  
  Если не указан — назначается случайный фоновый пони из MLP.

---

## ModMenu

Мод отображается в ModMenu с описанием, автором и иконкой. Всё задаётся в `fabric.mod.json`.

### Иконка

Положи PNG-файл (128×128 пикселей) сюда:
```
submod-ponynpc/src/main/resources/assets/ponynpc/icon.png
```

Путь уже прописан в `fabric.mod.json`: `"icon": "assets/ponynpc/icon.png"`.

### Изменить описание / автора

В `fabric.mod.json`:
```json
"name": "PonyNPC",
"description": "Текст описания в ModMenu...",
"authors": [
  { "name": "Pipbuck Studio" }
],
"contact": {
  "sources": "https://ссылка-на-репозиторий"
}
```

---

## Звуки

### Встроенные события

| ID | Файл | Описание |
|----|------|----------|
| `ponynpc:talk` | `sounds/dialog/talk.ogg` | Обычное общение |
| `ponynpc:talk_magic` | `sounds/dialog/talk_magic.ogg` | Магическое общение (для единорогов/аликорнов) |

Звуковые файлы уже включены в мод (`assets/ponynpc/sounds/dialog/`).

### Добавление собственного звука

1. Подготовь короткий OGG Vorbis файл (0.05–0.15 сек, Undertale-стиль).
   - Если есть WAV: `ffmpeg -y -i input.wav -c:a libvorbis -q:a 5 output.ogg`
2. Положи файл в `assets/ponynpc/sounds/dialog/myvoice.ogg`.
3. Зарегистрируй в `assets/ponynpc/sounds.json`:
   ```json
   "myvoice": {
     "sounds": [{ "name": "ponynpc:dialog/myvoice", "stream": false }]
   }
   ```
4. В диалоге укажи: `"sound": "ponynpc:myvoice"`.

**Совет:** Можно использовать любые звуки Minecraft, например `"sound": "minecraft:entity.experience_orb.pickup"`.

### Логика воспроизведения

- Звук играет каждые `sound_chars` символов во время печатания текста.
- Высота тона (`pitch`) случайна в диапазоне `0.85–1.15` — создаёт ощущение живой речи.
- Громкость: `0.4` (не заглушает игровые звуки).

---

## Скины NPC

### Как задать скин

При спавне:
```
/ponynpc spawn my_dialog "ponynpc:textures/skins/twilight.png" "Twilight Sparkle"
```

### Добавление скина в мод

1. Положи PNG-файл скина сюда:
   ```
   submod-ponynpc/src/main/resources/assets/ponynpc/textures/skins/twilight.png
   ```
2. Используй идентификатор `ponynpc:textures/skins/twilight.png` в команде.

> Minecraft ищет ресурс по пути `assets/<namespace>/<path>`, поэтому `ponynpc:textures/skins/twilight.png` → `assets/ponynpc/textures/skins/twilight.png`.

### Форматы скина

| Формат | Пример |
|--------|--------|
| Из ресурсов мода | `ponynpc:textures/skins/my_pony.png` |
| Из ресурсов MLP | `minelittlepony:textures/entity/pony/twilight.png` |
| Из любого ресурс-пака | `mypack:textures/entity/npc.png` |
| Не задан | Случайный фоновый пони из MLP |

### Race и модель

Раса пони определяется **автоматически** из trigger-пикселей скин-текстуры — той же системой, что и в MLP:

| Пиксель (0,0) цвет | Раса |
|--------------------|------|
| Прозрачный | Earth Pony (по умолчанию) |
| Цвет `#0` | Earth Pony |
| ... | (см. документацию MLP) |

Для создания скина пони с нужной расой используй инструменты MLP (например, Pony Skin Editor).

---

## Как работает система диалогов

### Жизненный цикл диалога

```
Игрок ПКМ по NPC
    ↓
Server: interactMob()
    ↓
Server: DialogManager.getDialog(npc.dialogId)
    ↓
Server: PlayerDialogState.getOrCreate(world)
    ↓
Server: buildChoiceData() — помечает выполненные (once) варианты
    ↓
Server → Client: OpenDialogPayload("start" node)
    ↓
Client: DialogScreen.open()
    ↓
[Игрок читает текст, звук печатается]
    ↓
Игрок кликает на вариант
    ↓
Client → Server: DialogChoicePayload(npcUuid, nodeId, choiceId)
    ↓
Server: valидация (нод, выбор)
Server: if once → markChoiceDone()
    ↓
  next == null?         next == "node_id"?
      ↓                     ↓
  CloseDialogPayload    OpenDialogPayload (следующий узел)
      ↓                     ↓
  Client: закрыть     Client: обновить экран
```

### Экран диалога

```
┌─────────────────────────────────────┐
│  TWILIGHT SPARKLE                   │  ← Имя NPC (золото)
│  ─────────────────────────────────  │
│  Привет, путник! Это Понивилль...   │  ← Текст (постепенное появление)
│  Основан земными пони много         │
│  лет назад.                         │
│                            ░░░░░░░  │  ← [ Click to skip ] если ещё печатает
├─────────────────────────────────────┤
│  > Расскажи о городе.               │  ← Активный выбор
│  ✓ Знаешь ли ты секрет?            │  ← Выполненный (once=true, серый)
│  > До свидания!                     │
└─────────────────────────────────────┘
```

---

## Структура пакетов

### OpenDialogPayload (S2C)

Открывает диалог у клиента или переходит к новому узлу.

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |
| `npcName` | String | Имя NPC (для заголовка) |
| `dialogId` | String | ID диалога |
| `nodeId` | String | ID текущего узла |
| `text` | String | Текст узла |
| `sound` | String | Sound event ID |
| `soundChars` | int | Интервал звука |
| `choices` | List\<ChoiceData\> | Список вариантов |

**ChoiceData:** `{ id: String, text: String, done: boolean }`

### DialogChoicePayload (C2S)

Игрок выбрал вариант.

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |
| `nodeId` | String | Текущий узел |
| `choiceId` | String | ID выбора (`"__close__"` = ESC) |

### CloseDialogPayload (S2C)

Сервер закрывает диалог (навигация `next: null`).

| Поле | Тип | Описание |
|------|-----|----------|
| `npcUuid` | UUID | UUID NPC |

---

## Прогресс игрока

Хранится в `PlayerDialogState` (extends `PersistentState`).

- **Сохранение:** `[world]/data/ponynpc_dialog_progress.dat` (NBT)
- **Ключ записи:** `dialogId + ":" + npcUuid` — уникален для каждого NPC
- **Что хранится:** `playerUUID → dialogKey → Set<choiceId>`

Это значит:
- У двух разных NPC с одним диалогом **независимый** прогресс.
- Прогресс сохраняется между сессиями.
- Перезагрузка мира (`/reload`) — прогресс сохраняется в dat-файл.

---

## Расширение в будущем

Мод спроектирован как **база** для расширения. Ключевые точки:

### Новые режимы диалога

Добавление нового режима занимает ~3 шага:

```java
// 1. DialogMode.java
@SerializedName("gpt")
GPT,

// 2. DialogManager.validate()
case GPT -> {
    // validate: api_key, system_prompt, etc.
    yield true;
}

// 3. NetworkHandler.openDialogForPlayer()
case GPT -> openGptDialog(player, npc, dialog);

// 4. Реализация
private static void openGptDialog(...) {
    // async call → when done → sendNode or sendRandomLine
}
```

### GPT-интеллект

В `PonyNpcEntity.interactMob()` вместо статического диалога вызывать API:
```java
GptDialogHandler.sendMessageAsync(player, this, "Player interacted");
```

### Ходьба и патрулирование

`PonyNpcEntity` extends `PathAwareEntity` — готова к AI:
```java
@Override
protected void initGoals() {
    this.goalSelector.add(0, new WanderAroundFarGoal(this, 1.0));
    // ...
}
```

### Квестовая система

В `DialogChoice` добавь поле `actions: List<String>` — список действий при выборе:
```json
"actions": ["give_item:diamond:1", "set_flag:quest_started"]
```

Обработчик в `NetworkHandler.handleChoice()` перед переходом к следующему узлу.

### Условные выборы

Добавь `conditions` в `DialogChoice`:
```json
"conditions": ["has_item:diamond", "flag:quest_done"]
```

Фильтрация в `NetworkHandler.sendNode()` перед отправкой клиенту.

### Кастомный рендеринг (портрет NPC)

В `DialogScreen.render()` добавить рендер модели NPC слева:
- Использовать `InventoryScreen.drawEntity()` для 2D-рендера сущности.

---

## Структура файлов проекта

```
submod-ponynpc/
├── build.gradle                          Сборка (Gradle subproject)
├── gradle.properties                     Версии зависимостей
├── WIKI.md                               Эта документация
│
├── src/main/resources/
│   ├── fabric.mod.json                   Метаданные мода (автор, описание, иконка)
│   ├── assets/ponynpc/
│   │   ├── icon.png                      Иконка в ModMenu (128×128)
│   │   ├── sounds.json                   Регистрация звуков
│   │   ├── sounds/dialog/
│   │   │   ├── talk.ogg                  Звук обычного диалога
│   │   │   └── talk_magic.ogg            Звук магического диалога
│   │   ├── textures/skins/               Скины NPC (PNG, формат MLP)
│   │   │   └── (twilight.png и т.д.)
│   │   └── lang/en_us.json              Переводы
│   └── data/ponynpc/dialogs/
│       ├── example.json                  Пример: mode=quest (ветвистый)
│       └── example_random.json           Пример: mode=random (случайные реплики)
│
└── src/main/java/com/minelittlepony/ponynpc/
    ├── PonyNpcMod.java                   Server entrypoint
    ├── PonyNpcClient.java                Client entrypoint
    │
    ├── entity/
    │   └── PonyNpcEntity.java            Сущность NPC (PathAwareEntity)
    │
    ├── registry/
    │   ├── PonyNpcEntities.java          Регистрация EntityType
    │   └── PonyNpcSounds.java            Регистрация SoundEvent
    │
    ├── dialog/
    │   ├── DialogMode.java               Enum режимов (QUEST, RANDOM + зарезервировано)
    │   ├── DialogData.java               JSON root (mode, nodes/phrases, sound)
    │   ├── DialogNode.java               Узел диалога (QUEST)
    │   ├── DialogChoice.java             Вариант ответа (QUEST)
    │   └── DialogManager.java            Загрузка, валидация по режиму, кеш
    │
    ├── network/
    │   ├── OpenDialogPayload.java        S2C: открыть диалог
    │   ├── CloseDialogPayload.java       S2C: закрыть диалог
    │   ├── DialogChoicePayload.java      C2S: выбор игрока
    │   └── NetworkHandler.java           Серверная логика
    │
    ├── state/
    │   └── PlayerDialogState.java        Прогресс (PersistentState)
    │
    ├── command/
    │   └── PonyNpcCommand.java           /ponynpc команды
    │
    └── client/
        ├── render/
        │   └── PonyNpcRenderer.java      Рендерер (MLP pony models)
        └── screen/
            └── DialogScreen.java         UI диалога
```

---

*Документация актуальна для PonyNPC 1.0.0 / Minecraft 1.21.1 / Fabric*
