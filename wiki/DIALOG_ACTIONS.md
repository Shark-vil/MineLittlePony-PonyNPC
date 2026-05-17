# Условия и действия диалогов

Диалоговые выборы (`DialogChoice`) поддерживают **условия** и **действия** для интеграции с квестовой системой.

---

## Расширенный формат DialogChoice

```json
{
  "id": "give_card",
  "text": "Вот читательский билет.",
  "next": "access_granted",
  "once": true,
  "conditions": [
    "quest:shadow_book_ch1:stage:return_to_librarian",
    "quest:shadow_book_ch1:has_item:library_card"
  ],
  "hidden_if_condition_fails": true,
  "actions": [
    "quest:shadow_book_ch1:take_quest_item:library_card",
    "quest:shadow_book_ch1:give_quest_item:ruins_map",
    "quest:shadow_book_ch1:complete_stage"
  ]
}
```

---

## Условия (conditions)

Все условия проверяются AND-логикой (все должны выполняться).

| Строка | Описание |
|--------|----------|
| `quest:<id>:stage:<stageId>` | Игрок находится на этом этапе квеста |
| `quest:<id>:not_started` | Квест ещё не начат |
| `quest:<id>:started` | Квест начат (любой этап) |
| `quest:<id>:complete` | Квест полностью завершён |
| `quest:<id>:has_item:<itemId>` | В кармане есть квестовый предмет |
| `quest:<id>:counter_gte:<stageId>:<n>` | Счётчик multi_collect >= n |

### `hidden_if_condition_fails`

- `true` (по умолчанию) — выбор **скрыт** если условие не выполнено
- `false` — выбор **виден но серый** (нельзя выбрать)

---

## Действия (actions)

Выполняются сервером **после** выбора варианта, **до** навигации к следующему узлу.

### Quest actions

| Строка | Описание |
|--------|----------|
| `quest:<id>:start` | Начать квест (если не начат) |
| `quest:<id>:complete_stage` | Завершить текущий этап, перейти к следующему |
| `quest:<id>:complete_stage:<stageId>` | Завершить конкретный этап |
| `quest:<id>:give_quest_item:<itemId>` | Добавить предмет в квестовый карман |
| `quest:<id>:take_quest_item:<itemId>` | Забрать предмет из кармана |
| `quest:<id>:give_currency:<n>` | Дать N золотых самородков |
| `quest:<id>:give_item:<ns>:<name>:<count>` | Дать обычный MC предмет |
| `quest:<id>:collect:<stageId>:<targetId>` | Отметить цель собранной (multi_collect) |

### Ghost NPC actions

| Строка | Описание |
|--------|----------|
| `ghost_npc:despawn` | Удалить текущий ghost NPC для этого игрока |

---

## Примеры

### Начало квеста через диалог

```json
{
  "id": "accept",
  "text": "Хорошо, займусь этим.",
  "next": null,
  "once": false,
  "conditions": ["quest:shadow_book_ch1:not_started"],
  "hidden_if_condition_fails": true,
  "actions": [
    "quest:shadow_book_ch1:start",
    "quest:shadow_book_ch1:complete_stage:talk_to_trixie"
  ]
}
```

### Сдача квестового предмета с наградой

```json
{
  "id": "hand_over_book",
  "text": "Вот книга, как договаривались.",
  "next": "ending_node",
  "once": true,
  "conditions": [
    "quest:shadow_book_ch1:stage:return_to_trixie_1",
    "quest:shadow_book_ch1:has_item:shadow_book"
  ],
  "hidden_if_condition_fails": true,
  "actions": [
    "quest:shadow_book_ch1:take_quest_item:shadow_book",
    "quest:shadow_book_ch1:give_currency:50",
    "quest:shadow_book_ch1:give_item:minecraft:iron_chestplate:1",
    "quest:shadow_book_ch1:give_item:minecraft:diamond_helmet:1",
    "quest:shadow_book_ch1:complete_stage"
  ]
}
```

### Сбор фрагментов (multi_collect)

```json
{
  "id": "ask_location",
  "text": "Куда она могла пойти?",
  "next": "witness_response",
  "once": true,
  "conditions": ["quest:shadow_book_ch1:stage:find_origami_pieces"],
  "hidden_if_condition_fails": true,
  "actions": [
    "quest:shadow_book_ch1:collect:find_origami_pieces:witness_1"
  ]
}
```

---

## Серверная валидация

Все условия проверяются **на сервере** при получении пакета выбора.  
Клиент не является доверенным — попытка выбрать недоступный вариант будет отклонена.
