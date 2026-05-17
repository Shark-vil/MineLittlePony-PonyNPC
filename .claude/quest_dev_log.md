# Quest System Dev Log

## Архитектурные решения

### Инстансы данжей
- Одно измерение `ponynpc:dungeon`, смещение X = instanceId * 10000
- Phase 2

### Валюта
- Gold Nugget (физический предмет, `Items.GOLD_NUGGET`) — временно, заменяемо

### Пати
- Кастомная система, Phase 2

### Ghost NPC
- `PonyNpcEntity.visibleToPlayers: Set<UUID>` — если пустой, виден всем
- Mixin на `ThreadedAnvilChunkStorage$EntityTracker.startTracking()` — cancel если игрок не в visibleToPlayers
- GhostNpcManager хранит map playerUuid → List<PonyNpcEntity>

### Proximity Triggers
- В JSON квеста каждый stage может иметь `proximity_trigger`
- ProximityTriggerManager проверяет каждые 20 тиков
- Trigger: dimension + xyz + radius + действие (spawn_ghost_npc / quest_action)

---

## Фазы

### Phase 1 (ТЕКУЩАЯ)
- [x] Quest Engine Core (QuestManager, QuestDefinition, QuestStage)
- [x] Quest State (QuestStateStore, PlayerQuestState)
- [x] Quest Pocket (QuestPocketState — virtual items, death-safe)
- [x] Dialog Integration (conditions/actions в DialogChoice)
- [x] Ghost NPC (visibleToPlayers + EntityTrackerMixin)
- [x] ProximityTriggerManager
- [x] GhostNpcManager
- [x] PonyNpcMod update
- [x] Commands (/ponynpc quest, /ponynpc pocket)
- [x] Mixin config + fabric.mod.json update
- [ ] Example quest JSON (shadow_book_ch1)

### Phase 2
- [ ] Dungeon Instance System (custom dimension, instance slots)
- [ ] Party System

---

## Условия/действия (строковый формат)

### Conditions
- `quest:<id>:stage:<stageId>` — игрок на этом этапе
- `quest:<id>:not_started` — квест не начат
- `quest:<id>:complete` — квест завершён
- `quest:<id>:has_item:<itemId>` — в кармане есть квестовый предмет
- `quest:<id>:counter_gte:<stageId>:<n>` — счётчик этапа >= n

### Actions
- `quest:<id>:start` — начать квест
- `quest:<id>:complete_stage` — завершить текущий этап, перейти к следующему
- `quest:<id>:give_quest_item:<itemId>` — добавить в карман
- `quest:<id>:take_quest_item:<itemId>` — взять из кармана
- `quest:<id>:give_currency:<n>` — дать n gold nuggets
- `quest:<id>:give_item:<ns>:<name>:<count>` — дать MC предмет
- `quest:<id>:collect:<stageId>:<targetId>` — отметить цель в multi_collect
- `ghost_npc:despawn` — удалить текущий ghost NPC для этого игрока

---

## Заметки

- `QuestStateStore` и `QuestPocketState` привязаны к overworld PersistentState (server-wide)
- `GhostNpcManager` — singleton, живёт в памяти сервера, очищается при logout
- Mixin target: `net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker`, method `startTracking`
- Yarn field name for entity: `entity` (final)
