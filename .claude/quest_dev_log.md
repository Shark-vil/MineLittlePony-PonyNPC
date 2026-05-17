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

### Phase 2 (ЗАВЕРШЕНА)
- [x] Party System (Party, PartyManager, /party command)
  - Bug fix: pendingInvites теперь хранится в отдельном Map<UUID, Party> (не в playerParty)
- [x] Dungeon Dimension (ponynpc:dungeon, flat void world)
  - dimension_type/dungeon.json — effects=the_end, no skylight, ambient_light=0.15
  - dimension/dungeon.json — minecraft:flat generator, empty layers, the_void biome
- [x] DungeonDefinition (JSON), DungeonRegistry
  - Загружает из config/ponynpc/dungeons/*.json
  - Копирует shadow_ruins.json из /data/ponynpc/dungeons/ при первом запуске
- [x] DungeonInstance (slot, players, return positions)
  - Slot N: centerX = N*10000+5000, centerZ = 5000
  - ReturnData record: worldKey + xyz + yaw/pitch
- [x] DungeonInstanceManager (solo+party entry, exit, completion)
  - enterDungeon: если игрок — лидер пати, вся пати входит вместе
  - exitDungeon: без наград, только телепорт
  - completeDungeon: награды из DungeonDefinition + телепорт всех участников
  - Телепорт: player.teleport(ServerWorld, x,y,z, Set.<PositionFlag>of(), yaw, pitch)
  - PositionFlag: net.minecraft.network.packet.s2c.play.PositionFlag
- [x] DungeonGenerator (basic platform procedural generation)
  - 40×60 stone_brick floor + 4-high walls + glowstone at wall tops
  - 3-wide entrance gap in south wall
  - 3×3 gold block finish zone near north wall
- [x] Actions: dungeon:enter:<id>, dungeon:exit, dungeon:complete
- [x] Conditions: dungeon:in_instance:<dungeonId>, dungeon:not_in_instance
- [x] PonyNpcMod/Commands update
  - /party — новая команда для всех игроков
  - /ponynpc dungeon list|enter|exit — admin
  - /ponynpc reload — теперь перезагружает и данжи

## Архитектура Phase 2

### Pati system
- Party: leaderUUID + Set<UUID> members, in-memory only
- PartyManager singleton (Map<UUID, Party> playerToParty)
- /party create/invite/join/leave/list/kick

### Dungeon slots
- Slot N центр: X = N*10000+5000, Y=64, Z=5000
- Поиск свободного слота: просто nextSlot++, слоты не переиспользуются пока
  (при exitDungeon слот освобождается и добавляется в freeSlots queue)

### DungeonInstance fields
- slotId: int
- dungeonId: String
- players: Set<UUID> (solo или pati)
- returnData: Map<UUID, ReturnData(world, x,y,z,yaw,pitch)>

### Generation
- Если generate_platform=true: размещаем 40×60 платформу из stone_bricks
- Торчи на стенах каждые 5 блоков
- Открытый вход (начало) + финишная зона (конец)

### Completion flow
1. Dialog action "dungeon:complete:shadow_ruins"
2. QuestActionExecutor → DungeonInstanceManager.completeDungeon(player)
3. Give rewards (items + quest items + currency)
4. Advance quest stage DUNGEON → nextStage
5. Teleport player back to return position

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
