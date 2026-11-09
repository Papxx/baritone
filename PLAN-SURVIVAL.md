# Plan: Survival-Features und Schematic-Bauen für die eigene Baritone-Variante (MC 26.2 + 26.3)

Stand: 07.11.2026 · Status: **in Umsetzung**, Fortschritt in [Abschnitt 0](#0-fortschritt-und-wiedereinstieg) · Inhalt: Survival (Abschnitt 3), Schematic-Bauen (Abschnitt 4)

> ## ⭐ PRIORITÄT 1: Litematica (wichtigster Punkt des Plans)
> **`#litematica` muss zuverlässig funktionieren, auf 26.2 und 26.3, auch bei großen Platzierungen.** Das kommt vor
> allem anderen dran.
> - **Stand 07.11.2026:** auf 26.2 im Code umgesetzt und getestet, die Abnahme im Spiel steht noch aus (Abschnitt 0).
> - Problem (F10): Baritone liest die Blöcke aus Litematicas Vorschau-Welt, die sehr wahrscheinlich nur Chunks in
>   Sichtweite enthält. Bei großen Platzierungen gelten entfernte Teile dann als Luft. Der Bau bleibt unvollständig,
>   oder Baritone baut dort vorhandene Blöcke ab.
> - Lösung (B0.6): direkt aus den Schematic-Daten lesen (`getSubRegionContainer(name).get(x, y, z)`, in Litematica
>   0.28.8 für 26.2 und 0.29.1 für 26.3 vorhanden). Drehen und Spiegeln selbst übernehmen, nicht kopieren,
>   Versionsfehler abfangen (F9).
> - Details: [Abschnitt 4.2, B0.6](#-priorität-1-b06-litematica-direkt-lesen). Reihenfolge: [Abschnitt 6](#6-reihenfolge), Schritt 1.

Diese Variante ist privat (Standalone-Mod, Mod-ID `baritone`, kein Push). Grundlage ist Meteors 26.2-Branch mit
den eigenen Fixes (atomisches Speichern, Pfadsuche-Hänger, Speicherleck beim Serverwechsel, ProGuard ohne `jmods`).


---

## 0. Fortschritt und Wiedereinstieg

Stand 07.11.2026. Alles liegt **uncommittet** in der 26.2-Arbeitskopie (Commits nur nach Freigabe, siehe 0.1).
Tests: 80 JUnit-Tests grün (`gradlew :test --offline`), voller Build grün (`gradlew build`, JAVA_HOME auf JDK 25).
**Im Spiel ist noch nichts davon abgenommen**, die Checkliste steht in 0.2.

### 0.1 Erledigt (Code und Tests)

| Punkt | Ergebnis | Wichtige Dateien |
|---|---|---|
| ⭐ B0.6 Litematica direkt lesen | Liest jede aktivierte Unterregion direkt aus `getSubRegionContainer(name).get(x, y, z)`, ohne Kopie. Position und Blockzustand werden wie in Litematicas `SchematicPlacingUtils.placeBlocksWithinChunk` gedreht und gespiegelt (die Quellen von sakura-ryoko/litematica `LTS/26.2` und `26.3` sind dort identisch). F10 per Quelltext bestätigt: `canHandleChunk` lädt nur Client-Chunks (außer bei `loadEntireSchematics`), fehlende Chunks sind Luft, gefüllt wird asynchron. Strukturleere zählt als „egal“. Ein `LinkageError` wird als „Litematica-Version nicht kompatibel“ gemeldet. Die Stubs `WorldSchematic`/`SchematicWorldHandler` sind entfernt (der `Level`-Konstruktor im Stub war ein Portierungsrisiko) | `utils/schematic/litematica/LitematicaRegionTransform.java`, `LitematicaRegionSchematic.java`, `LitematicaHelper.java`, Stubs in `src/schematica_api`, Test `LitematicaRegionSchematicTest` (144 Dreh-/Spiegel-Kombinationen gegen eine Nachbildung von Litematica, per Mutation gegengeprüft) |
| B0.1 Testbarkeit | `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` laufen in JUnit (etwa 5 s). **Aber:** Item-Komponenten (`ItemStack` mit Daten, `DataComponents.FOOD` usw.) sind im Test nicht gebunden („Components not bound yet“). Logik deshalb so schneiden, dass sie ohne `ItemStack` testbar ist | – |
| B0.2 dynamische Eigenschaften | Setting `buildIgnoreDynamicProperties` (an): `age`, `stage`, `hatch`, `berries`, `honey_level`, Laub-`distance`/`persistent`, Gerüst-`distance`, `moisture`, `snowy`, `power` und einige Sculk-/Knarzherz-Zustände. Vergleich über die Property-Identität (`BlockStateProperties`), nie über Namen | `process/builder/BuilderPropertyRules.java`, Test `BuilderPropertyRulesTest` |
| B0.3 abgeleitete Eigenschaften | Setting `buildIgnoreDerivedProperties` (an), zweistufig: (1) überall eine feste Liste pro Blockklasse (Zaun/Scheibe/Gitter, Mauer, Redstone-Verbindungen, Treppenform, Pilzseiten, Zauntor `in_wall`, Notenblock-Instrument, Gerüst `bottom`, Dicke von Tropfstein/Schwefelstachel). (2) Im Haupt-Thread zusätzlich `Block.updateFromNeighbourShapes(desired, world, pos)` (`validAt`), aber nur bei gleichem Block, damit „Tür ohne Oberteil wird Luft“ keine Luft als korrekt durchgehen lässt. Ranken und Glühflechten bleiben streng (gleiches Property-Objekt `NORTH` wie der Zaun). **Bekannte Grenze:** Die Pfadsuche läuft in einem eigenen Thread ohne Weltzugriff und kennt nur Stufe 1. Ein Block, der erst über Stufe 2 als korrekt gilt, bekommt dort beim Abbau den Multiplikator 1 statt `breakCorrectBlockPenaltyMultiplier`, ein Pfad könnte also durch ihn graben. Der Builder selbst baut ihn nicht ab | `BuilderProcess.validAt`, `BuilderPropertyRules` |
| B0.4 unbekannte Blöcke | Sponge, Litematica-Datei und MCEdit ersetzen unbekannte Blöcke **nicht mehr still durch Luft**. Der Plan nahm für Sponge einen Abbruch an, tatsächlich war es auch dort Luft. Beim Laden erscheint eine Meldung im Chat, die Positionen bleiben unberührt (`null` in `StaticSchematic` heißt „nicht Teil der Schematic“). Unbekannte Eigenschaftswerte brechen nicht mehr ab, es gilt der Standardwert mit Meldung. **Abweichung vom Plan:** Boolean `buildFailOnUnknownBlocks` (aus) statt `buildUnknownBlocks = skip/fail`, und „skip“ heißt „unberührt lassen“ statt „als Luft bauen“, weil Luft vorhandene Blöcke abbauen würde. Nebenbei behoben: `LitematicaSchematic.getDirect` warf in Lücken zwischen Unterregionen eine Exception, `MapArtSchematic` vertrug kein `null`, der `FileInputStream` in `build()` wurde nie geschlossen | `utils/schematic/format/UnknownBlocks.java`, Formate in `format/defaults`, Test `UnknownBlocksTest` |
| B0.5 Materialliste | Befehl **`#materials`** (Alias `#mats`) statt `#build materials`/`#build status`, weil `#build` als erstes Argument einen Dateinamen nimmt: `#materials [status] [seite]` für den laufenden Bau, `#materials file <datei> [seite]` für eine Datei. Zeigt Bedarf, Getragenes, den Inhalt von Shulkern im Inventar und die Fehlmenge, Ausrichtungen sind zusammengefasst. Türen und Betten zählen einmal, Doppelstufen doppelt, Kerzen/Seegurken/Schnee nach Anzahl. Blöcke ohne Item (Wasser, Lava, Feuer) stehen extra | `process/builder/MaterialList.java`, `command/defaults/MaterialsCommand.java`, `BuilderProcess.collectMissing`, Test `MaterialListTest` |
| 0.2 Spawner-Vermeidung | Sucht jetzt `BlockUtils.blockToString(Blocks.SPAWNER)` (= `spawner`), so wie der Cache speichert. Der Fehler steckt upstream noch in allen Branches | `utils/pathing/Avoidance.java` |
| 0.3 Mob-Vermeidung | Nur `Enemy`. Neutrale (`NeutralMob`) nur wütend oder angreifend: `isAggressive()` ist synchronisiert, die Wutzeit des Zombifizierten Piglins dagegen nicht. Spinnen nur im Dunkeln oder angreifend. Schwefelwürfel nur gezündet oder mit Archetyp „hot“ (Magma, Kontaktschaden) bzw. „explosive“ (TNT); die Archetypen sind eine synchronisierte Registry | `Avoidance.isDangerous` |
| 0.4 Schwefelstacheln | Beide Stellen prüfen jetzt `instanceof SpeleothemBlock` (Basisklasse von Tropfstein und Schwefelstachel). Ehrlich gesagt: Vanilla machte Stacheln über `SpeleothemBlock.isPathfindable` schon vorher unpassierbar, die Änderung macht das nur ausdrücklich | `MovementHelper`, Test `SulfurHazardsTest` |
| 0.5 Geysire | Statt der Tags reicht der synchronisierte Zustand `PotentSulfurBlock.STATE`: `DORMANT`/`ERUPTING`/`CONTINUOUS` ist ein Geysir (`DRY` = kein Wasser darüber, `WET` = Wasser ohne Magma/Lava darunter). Die Wassersäule darüber (höchstens `ALLOWED_WATER_BLOCKS_ABOVE` = 4 Blöcke) ist nicht begehbar und wird im Chunk-Cache als `AVOID` gespeichert. Grenze: Die Luft über der Wasseroberfläche wird nicht geprüft (Luft gilt ohne Positionsprüfung als begehbar) | `MovementHelper.isGeyser`/`inGeyserColumn`, `cache/ChunkPacker.java` |
| 0.6 Übelkeitswolke | **Bewusst nicht umgesetzt:** Übelkeit verzerrt in Java Edition nur das Bild und hat für einen Bot keine Wirkung | – |
| 1.1 Auto-Essen | `AutoEatProcess` (temporär, Priorität 5.05, knapp unter dem Inventory-Pauser) und Befehl `#eat`. Settings `autoEat` (aus), `autoEatHungerThreshold` (14), `autoEatHealthThreshold` (10), `autoEatBlacklist` (goldene Äpfel). Gefährliches Essen wird generisch erkannt (Teleport-Effekt, schädlicher Statuseffekt auch mit nur einer Wahrscheinlichkeit, verdächtiger Eintopf), es gibt keine feste Liste. Isst aus Hotbar, Offhand und mit `allowInventory` aus dem Inventar. Die Auswahl bestraft verschenkte Hungerpunkte stark. Gestartet wird über `useItem`, das nie mit dem anvisierten Block interagiert. Weil Minecraft das Item jeden Tick loslässt, in dem die Use-Taste nicht gedrückt ist (`handleKeybinds` → `releaseUsingItem`), bricht ein Mixin-Inject `releaseUsingItem` ab, solange gegessen wird. Gegessen wird nur bei `isSafeToCancel`, am Boden und nicht im Wasser, bei ≤ 3 Herzen auch ohne diese Bedingungen. Beim Elytra-Flug und bei offenem Container nie, auch nicht bei wenig Herzen (sonst würde Auto-Essen den Elytra-Prozess mitten im Flug pausieren) | `process/survival/AutoEatProcess.java`, `FoodChoice.java`, `command/defaults/EatCommand.java`, Inject in `MixinPlayerController`, Test `FoodChoiceTest` |

### 0.2 Offen: Abnahme im Spiel (26.2, danach 26.3)

- **B0.6:** Eine Platzierung, die größer als die Sichtweite ist, vollständig bauen, ohne vorhandene Blöcke abzubauen. Drehung 0/90/180/270, Spiegelung, mehrere Unterregionen, Treppen und Türen richtig ausgerichtet. Mit einer absichtlich unpassenden Litematica-Version prüfen, dass eine Meldung statt eines Absturzes kommt.
- **B0.2/B0.3:** Kaktus- und Zuckerrohrfarm, Baum mit Laub, Zaun-/Scheiben-/Mauer-Raster, Redstone-Leitung, drei Pilzstiele nebeneinander (Upstream #4318): keine Abbauschleifen, keine falschen „Missing materials“.
- **B0.4:** Eine 26.3-Schematic auf 26.2 laden: Die Meldung erscheint, die Positionen bleiben unberührt.
- **B0.5:** `#materials` während eines Baus und `#materials file <name>`. Ein Shulker mit Material im Inventar zählt mit (im Unit-Test nicht prüfbar).
- **0.2/0.3:** Mit `avoidance` an: Spawner und Zombies werden gemieden, Kühe ignoriert, ein Wolf erst bei Wut, Schwefelwürfel mit Magma gemieden.
- **0.5:** In einer Schwefelhöhle mit Geysir schwimmt `#goto` nicht durch die Wassersäule.
- **1.1:** `autoEat` an, Hunger senken (z. B. sprinten): Pathing pausiert, isst und macht weiter. Vor einer Tür, einer Truhe und einem Bett essen: Es darf **keine** Blockinteraktion passieren. Chorusfrucht, Spinnenauge und goldener Apfel werden nie gegessen. `#eat` testen, außerdem Essen nur im Inventar mit `allowInventory`.

### 0.3 Nächste Schritte (Reihenfolge nach Abschnitt 6)

1. Abnahme im Spiel (0.2), Fehler beheben.
2. **1.3 Volles Inventar beim `#mine`** → **1.2 Rückzug bei Gefahr** (Rest von Schritt 4).
3. **B2 Ausrichtung** → **B3a Skalierung** → **B1 Formate**.
4. Danach 2.1 Auto-Fackel → 2.2 Auto-Schlafen, dann B3 → 2.3 → B5, dann nach Lust.
5. 26.3-Port, sobald 0.1 freigegeben ist.

Aus Phase 0 noch offen: **0.1 Branches**, braucht die Freigabe für lokale Commits (Abschnitt 7).

### 0.4 Hinweise für den Wiedereinstieg

- Build: `JAVA_HOME` auf `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot` setzen, dann `gradlew build`. Nur Tests: `gradlew :test --offline`. Wichtig ist `:test` mit Doppelpunkt, denn `test` läuft auch in den Unterprojekten und scheitert offline an kotlin-stdlib. Mod-Jar: `dist/baritone-api-fabric-26.2-SNAPSHOT.jar`.
- `build.gradle` wurde in einer parallelen Sitzung für die VS-Code-Erkennung von Minecraft angepasst (`compileClasspath extendsFrom minecraft, minecraftLibraries`). Der Build-Classpath ist unverändert. Dieselbe Sitzung hat am 08.11.2026 Compiler- und IDE-Warnungen in vielen Dateien bereinigt, Details und offene Gradle-9-Punkte in [`UEBERGABE.md`](UEBERGABE.md).
- Litematica-Quellen zum Nachlesen: GitHub `sakura-ryoko/litematica`, Branches `LTS/26.2` und `26.3`. Die Jars gibt es über die Modrinth-API (0.28.8 = 26.2, 0.29.1 = 26.3).
- Fallen in 26.2: Betten sind `Blocks.BED`, eine `ColorCollection` (z. B. `Blocks.BED.red()`). `FarmBlock` heißt jetzt `FarmlandBlock`, `SnowyDirtBlock` heißt `SnowyBlock`. Tropfstein und Schwefelstachel erben von `SpeleothemBlock`, dazu gibt es den Tag `BlockTags.SPELEOTHEMS`. Slime und Schwefelwürfel liegen in `entity.monster.cubemob`.
- Neue Features liegen in eigenen Paketen: `process/builder` (Builder-Hilfen) und `process/survival` (Survival-Processes). Dort weitermachen, damit Upstream-Merges einfach bleiben.

---

## 1. Was Baritone schon kann (nicht neu bauen)

| Bereich | Vorhanden |
|---|---|
| Abbau | `#mine`, `legitMine`, `#tunnel`, Aufsammeln gedroppter Items (`mineScanDroppedItems`), `#pickup` |
| Farmen | `#farm` für Weizen, Karotten, Kartoffeln, Rote Bete, Kürbis, Melone, Netherwarze, Kakao, Zuckerrohr, Bambus, Kaktus. Nachpflanzen, Knochenmehl |
| Bewegung | Wassereimer-Fall bis 23 Blöcke, Leitern/Ranken, Parkour, Türen/Zauntore, Elytra (`#elytra`) |
| Sicherheit | Feuer, Magma, Lava und Beerensträucher werden gemieden. Mob-Vermeidung `avoidance` (Standard aus) |
| Inventar | `autoTool`, `allowInventory` (Werkzeug/Blöcke in die Hotbar), `itemSaver` (Werkzeug-Haltbarkeit) |
| Orte | Automatische Bett- und Todes-Wegpunkte, `#home`, `#waypoints` |
| Bauen | `#build` (Sponge v1/v2, Litematica v7, MCEdit), `#litematica` (Platzierung aus der Litematica-Mod), `#sel` (set/walls/shell/sphere/cylinder/replace/cleararea/copy/paste), Schichten (`buildInLayers`), Wiederholen (`buildRepeat`), Drehen und Spiegeln, Ersatzblöcke, `mapArtMode` |

**Was fehlt:** Essen, Licht, Schlafen, Umgang mit vollem Inventar, Rückzug bei wenig Leben, Bäume, Tiere, Crafting.
Außerdem kennt Baritone die neuen Gefahren aus 26.2 nicht. Der Builder hat eigene Schwächen, siehe Abschnitt 4.

---

## 2. Versionsstrategie 26.2 + 26.3

**Fakten (recherchiert):**
- 26.2 „Chaos Cubed“ erschien am 16.06.2026, 26.3 „Wilderness Bound“ am 15.09.2026. Beide brauchen mindestens Java 25.
- cabaletta und Meteor haben beide schon einen `26.3`-Branch.
- 26.3 hat Fenster und Eingabe von GLFW auf SDL3 umgestellt. Baritones Code nutzt aber weder GLFW noch `InputConstants`
  oder `KeyMapping` direkt (geprüft per grep), das Risiko ist also gering.
- nether-pathfinder ist eine native Bibliothek und unabhängig von der Minecraft-Version.

**Vorgehen (wie Upstream: ein Branch pro Version, Änderungen werden nach vorne gemergt):**
1. Den aktuellen Stand lokal als Branch `privat/26.2` sichern. **Dafür sind lokale Commits nötig, vorher fragen.**
2. `privat/26.3` vom Meteor- oder cabaletta-Branch `26.3` abzweigen. Darauf die eigenen Commits aus `privat/26.2`
   übernehmen, also die Fixes und die Standalone-Umstellung.
3. Jedes Feature zuerst auf 26.2 bauen und testen, dann `privat/26.2` → `privat/26.3` mergen.
4. **Versionsneutral programmieren**, damit derselbe Code auf beiden Versionen kompiliert:
   - Block- und Item-Gruppen über **Tags** abfragen (`BlockTags.LOGS`, `BlockTags.SAPLINGS`, `ItemTags.*`), nicht über
     feste Listen. Dann funktioniert zum Beispiel Pappelholz aus 26.3 automatisch.
   - Was es nur in einer Version gibt (z. B. das Strohbett aus 26.3), per Registry-ID nachschlagen:
     `BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace("straw_bed"))`. Fehlt der Eintrag, ist die Funktion aus.
     So braucht man keinen eigenen Code-Zweig pro Version.
5. Checkliste für den 26.3-Port: Build, Tests, Mixins laden, Eingabe (Laufen, Klicken, Blick) im Spiel prüfen
   (wegen SDL3), Rendering der Pfade prüfen (OIT-Transparenz neu in 26.3).

---

## 3. Features nach Phasen

Größe: S = unter 1 Tag, M = 1–3 Tage, L = mehr als 3 Tage.

Gemeinsames Muster für alles, was kurz unterbrechen muss (Essen, Fackel setzen, Schlafen): ein **temporärer Process**
nach dem Vorbild von `InventoryPauserProcess`. Er gibt `REQUEST_PAUSE` zurück, solange er arbeitet, sonst `DEFER`,
und hat eine Priorität über den normalen Processes. Nur bei `isSafeToCancel` unterbrechen, nie mitten im Sprung oder
Brückenbau. Blick und Klicks laufen über `LookBehavior` und den `InputOverrideHandler`
(keine Paket-Tricks, damit `antiCheatCompatibility` greift).

### Phase 0: Grundlagen und schnelle Fixes

| # | Thema | Umsetzung | Größe |
|---|---|---|---|
| 0.1 | Branches 26.2/26.3 anlegen (siehe Abschnitt 2) | Braucht Freigabe für lokale Commits | S |
| 0.2 | **Bug: Spawner-Vermeidung wirkt nie.** [Avoidance.java](src/main/java/baritone/utils/pathing/Avoidance.java) sucht `"mob_spawner"`, der Block heißt aber seit 1.13 `spawner` | Auf `BlockUtils.blockToString(Blocks.SPAWNER)` umstellen | S |
| 0.3 | **Mob-Vermeidung zu grob:** Jeder `Mob` zählt, also auch Kühe und Schafe | Nur `Enemy` werten, Neutrale nur wenn wütend, dazu Schwefelwürfel mit Magma („Hot“) | S |
| 0.4 | **26.2-Gefahr: Schwefelstacheln** (`SULFUR_SPIKE`, fallen und verletzen wie Tropfstein) | In [MovementHelper.java](src/main/java/baritone/pathing/movement/MovementHelper.java) statt `== POINTED_DRIPSTONE` den Tag `BlockTags.SPELEOTHEMS` prüfen | S |
| 0.5 | **26.2-Gefahr: Geysire.** Potenter Schwefel mit Magma darunter bricht alle 50 s aus, mit Lava darunter dauerhaft. Die Fontäne steigt etwa 7 Blöcke pro Wasserblock und schleudert hoch, danach droht Fallschaden | Spalte über `POTENT_SULFUR` mit Block aus `CAUSES_*_GEYSER_ERUPTIONS` darunter als `AVOID` werten, beim Chunk-Packen und in `MovementHelper` | M |
| 0.6 | 26.2: Nebelwolke über potentem Schwefel (Übelkeit) | Nur leichte Strafkosten für Wasser darüber, keine Sperre | S |

Abnahme: In einer Schwefelhöhle (26.2) läuft `#goto` nicht durch Stacheln oder über Geysire. Ist `avoidance` an,
werden Kühe ignoriert, Zombies und Spawner aber gemieden.

### Phase 1: Überleben (wichtigster Teil)

**1.1 Auto-Essen** · Größe M
- Neuer temporärer `AutoEatProcess`. Er wird aktiv bei Hunger unter `autoEatHungerThreshold` (z. B. 14) oder bei wenig
  Leben, sofern Hunger unter 20.
- Nahrungsauswahl: Hotbar zuerst, sonst mit `allowInventory` aus dem Inventar. Bewertet über `DataComponents.FOOD`
  (`nutrition` und `saturation`, in 26.2 geprüft).
- **Nie essen:** Chorusfrucht (teleportiert), Kugelfisch, Spinnenauge, verdorbenes Fleisch, roh Hühnchen,
  verdächtiger Eintopf, goldene Äpfel. Ausnahmen per Setting `autoEatBlacklist`.
- Ablauf: Slot wählen, Rechtsklick halten bis gegessen, alten Slot zurück, Pfad fortsetzen.
- Nicht während Elytra-Flug oder im Wasser essen, außer bei sehr wenig Leben.
- Settings: `autoEat`, `autoEatHungerThreshold`, `autoEatBlacklist`. Befehl `#eat` zum Testen.
- Tests: Die reine Logik „welches Essen nehmen“ als JUnit-Test, ohne Minecraft-Start.

**1.2 Rückzug bei Gefahr** · Größe M
- Ist das Leben unter `retreatHealthThreshold` oder ein Creeper näher als 4 Blöcke: Ziel pausieren und mit dem
  vorhandenen `GoalRunAway` Abstand holen. Optional zum `#home`-Wegpunkt laufen und Benachrichtigung über `notifier`.
- Settings: `retreatOnLowHealth`, `retreatHealthThreshold`, `retreatGoHome`.
- **Bewusst kein Kampf** (siehe offene Entscheidungen).

**1.3 Volles Inventar beim `#mine`** · Größe M
- Heute hört `#mine` nicht auf, wenn das Inventar voll ist. Neue Drops bleiben am Boden liegen und werden immer
  wieder angelaufen.
- Bei weniger als `mineMinFreeSlots` freien Slots: Müll wegwerfen (`junkItems`, Standard: Bruchstein, Erde,
  Netherrack, Granit, Diorit, Andesit, Tuff, Tiefenschiefer, Kies), dabei einen Stapel Bauklötze behalten.
- Ist danach immer noch voll: je nach Setting stoppen und zum `#home` laufen, oder Phase 2.3 (Truhe).
- Tests: Auswahl „was ist Müll, was bleibt“ als JUnit-Test.

### Phase 2: Komfort

**2.1 Auto-Fackel** · Größe M
- Beim Laufen in Höhlen oder bei `#tunnel`: Ist am Boden hinter dem Spieler das Blocklicht ≤ `autoTorchLightLevel`
  (Standard 0, denn seit 1.18 spawnen Monster nur bei Blocklicht 0), Fackel an Wand oder Boden setzen.
- Abstand zur letzten Fackel mindestens etwa 8 Blöcke, damit keine Fackeln verschwendet werden.
- Platzieren über `RotationUtils.reachable` und Rechtsklick, wie in den vorhandenen Movements.
- Sind keine Fackeln da: einmal melden, dann normal weiter.
- Settings: `autoTorch`, `autoTorchLightLevel`, `autoTorchSpacing`.

**2.2 Auto-Schlafen** · Größe M (+S für Strohbett)
- Nachts oder bei Gewitter, wenn ein Bett-Wegpunkt oder ein gecachtes Bett in der Nähe ist: hinlaufen, schlafen,
  danach die vorige Aufgabe fortsetzen.
- **Harte Sicherheitsregel:** Betten im Nether und im End **niemals** anklicken, sie explodieren dort.
  Eine Dimensionsprüfung kommt vor jeden Klick.
- **26.3 Strohbett:** Liegt eins im Inventar, statt zu laufen ein Strohbett auf freie, ebene Fläche setzen und
  schlafen. Es ist einmalig, setzt keinen Spawnpunkt und funktioniert nur in der Oberwelt. Per Registry-ID
  nachschlagen (Abschnitt 2), in 26.2 automatisch aus.
- Settings: `autoSleep`, `autoSleepMaxDistance`, `autoSleepUseStrawBed`. Befehl `#sleep`.

**2.3 In Truhe einlagern** · Größe L
- `#mine` mit `mineDepositToChest`: Bei vollem Inventar zur nächsten bekannten Truhe laufen. Truhen und Endertruhen
  stehen schon im Chunk-Cache (`BLOCKS_TO_KEEP_TRACK_OF`), Shulkerkisten im Inventar wären eine Alternative.
- Dort per Shift-Klick einlagern, außer Werkzeugen, Essen, Fackeln und Bauklötzen. Danach zurück zum Abbau.
- Einlagern in einen per `#sel` gewählten Bereich, damit nicht fremde Truhen benutzt werden.
- Das Öffnen der Truhe ist im `GetToBlockProcess` (Rechtsklick) schon halb vorhanden.

### Phase 3: Ausbau

**3.1 Holzfäller `#lumber`** · Größe L
- Ganze Bäume fällen (Stamm-Erkennung über `BlockTags.LOGS`), Setzlinge aufsammeln und nachpflanzen (`BlockTags.SAPLINGS`).
- Optional nur innerhalb einer `#sel`-Auswahl, also als Baumfarm.
- 26.3: Pappeln und umgefallene Bäume funktionieren über die Tags von selbst. Großpilze nicht abbauen.

**3.2 Tiere züchten `#breed <tier>`** · Größe M
- Paare finden, passendes Futter über `Animal.isFood(stack)` und `canFallInLove()` (beide in 26.2 geprüft),
  Cooldown beachten. Nur züchten, nichts töten.

**3.3 Farm erweitern** · Größe S
- Süßbeeren und Leuchtbeeren ernten.
- 26.3: Rote Sträucher und Regalpilze per Knochenmehl. Per Registry-ID, in 26.2 automatisch aus.

**3.4 Einfaches Crafting (Fackeln, Stöcke, Bretter)** · Größe L
- Über das Rezeptbuch (Server füllt das Raster aus), zuerst nur im 2×2-Raster des Inventars.
- Zweck: Fackel-Nachschub für 2.1 aus Kohle und Stöcken.

**3.5 Erze schmelzen** · Größe L · niedrige Priorität
- Rohe Erze in einen bekannten Ofen legen und später abholen.

---

## 4. Schematic-Bauen (Builder)

### 4.1 Befund (recherchiert in Code, Upstream-Issues und gegen die MC-26.2-API)

| # | Problem | Beleg | Auswirkung |
|---|---|---|---|
| F1 | **Ausrichtung wird nicht geplant.** `approxPlaceable` probiert pro Item nur *eine* Ausrichtung, nämlich die aus Spielerposition und Blickrichtung. Die Platzier-Ziele berücksichtigen nicht, auf welcher Seite man für die richtige Ausrichtung stehen muss | [BuilderProcess.java:1004](src/main/java/baritone/process/BuilderProcess.java#L1004), [:383](src/main/java/baritone/process/BuilderProcess.java#L383); Upstream [#4844](https://github.com/cabaletta/baritone/issues/4844) (ZacSharp: „struggles with rotational blocks like stairs and doors“) | „Missing materials“ und Pause, obwohl die Blöcke im Inventar sind (Treppen, Türen, Stämme, Beobachter, Truhen …) |
| F2 | **Veränderliche Eigenschaften werden strikt verglichen:** `age` (Kaktus, Zuckerrohr), `distance` (Laub), Zaun-, Scheiben- und Mauer-Verbindungen, Treppenform, Riesenpilz-Seiten, Redstone-`power` | `valid()` [BuilderProcess.java:1058](src/main/java/baritone/process/BuilderProcess.java#L1058), `buildIgnoreProperties` standardmäßig leer; Upstream [#4744](https://github.com/cabaletta/baritone/issues/4744) (Kaktusfarm), [#4046](https://github.com/cabaletta/baritone/issues/4046), [#4318](https://github.com/cabaletta/baritone/issues/4318) (Pilzstiele) | **Endlosschleife: setzen, „falsch“, abbauen, wieder setzen.** Die Ursache stammt aus meiner Code-Analyse, Upstream hat sie nicht bestätigt. Beim Umsetzen mit der Kaktusfarm reproduzieren |
| F3 | **Formate:** Sponge **v3** wird abgelehnt, obwohl WorldEdit sie seit 7.3 unterstützt (ob v3 dort Standard ist, ist nicht belegt). Litematica **v5/v6** (MC 1.13–1.20.4, sehr verbreitet) werden abgelehnt. Kein DataFixer, also werden umbenannte oder unbekannte Blöcke zum Abbruch (Sponge) oder **still zu Luft** (Litematica). MCEdit verliert die Metadaten (Farben, Ausrichtung). Vanilla-Strukturen (`.nbt`) fehlen | [DefaultSchematicFormats.java:66](src/main/java/baritone/utils/schematic/format/DefaultSchematicFormats.java#L66), [:88](src/main/java/baritone/utils/schematic/format/DefaultSchematicFormats.java#L88), [SpongeSchematic.java:62](src/main/java/baritone/utils/schematic/format/defaults/SpongeSchematic.java#L62), [LitematicaSchematic.java:96](src/main/java/baritone/utils/schematic/format/defaults/LitematicaSchematic.java#L96), [MCEditSchematic.java:76](src/main/java/baritone/utils/schematic/format/defaults/MCEditSchematic.java#L76) | Viele Schematics aus dem Netz laden gar nicht oder bauen falsch |
| F4 | **Große Schematics:** Der vollständige Scan läuft auf dem Haupt-Thread. Die Liste der falschen Blöcke wird bei 100 abgeschnitten (`incorrectSize`) und ist ein `HashSet`, also in zufälliger Reihenfolge statt nach Nähe | [:659](src/main/java/baritone/process/BuilderProcess.java#L659), [:678](src/main/java/baritone/process/BuilderProcess.java#L678); Upstream #3171, #2993, #2877 | Ruckler, Hin- und Herlaufen, bleibt hängen |
| F5 | **Keine Gerüste:** Schwebende Blöcke ohne Nachbarn sind unmöglich. Beim Laufen gesetzte Hilfsblöcke außerhalb der Schematic bleiben stehen | `possibleToPlace` braucht einen festen Nachbarn ([:347](src/main/java/baritone/process/BuilderProcess.java#L347)) | „Unable to do it“, Säulen-Reste in der Landschaft |
| F6 | **Mehrfachklick-Blöcke** fehlen (Kerzen, Seegurken, Schildkröteneier, Schneeschichten, Doppel-Stufen, Repeater-Verzögerung, Türen und Hebel öffnen) | Upstream [#3715](https://github.com/cabaletta/baritone/issues/3715), #4618 (Kerzen) | Bleibt hängen oder baut falsch |
| F7 | **Flüssigkeiten:** Fließendes Wasser führt zu Abbruch („Unreplaceable liquids“). Wasser oder Lava aus der Schematic werden nie gesetzt (kein Eimer), Waterlogging fehlt | [:756](src/main/java/baritone/process/BuilderProcess.java#L756) | Brunnen und Farmen unbaubar |
| F8 | **Material:** Keine Materialliste vor dem Start, kein Nachfüllen aus Truhen oder Shulkern. Fehlt etwas, pausiert der ganze Bau | [:602](src/main/java/baritone/process/BuilderProcess.java#L602) | Ohne ständige Betreuung praktisch nicht nutzbar |
| F9 | **Litematica-Anbindung** ist gegen Stub-Klassen kompiliert. Geprüft per `javap`: Alle 13 aufgerufenen Methoden passen zu **Litematica 0.28.8 (26.2)** und **0.29.1 (26.3)**, aktuell also kompatibel. Ändert Litematica künftig seine API, gibt es aber `NoSuchMethodError` statt einer Meldung | [LitematicaHelper.java:65](src/main/java/baritone/utils/schematic/litematica/LitematicaHelper.java#L65), Modrinth-API | Heute ok, künftig ein Absturzrisiko. **→ ⭐ Priorität 1 (B0.6)** |
| F10 | **`#litematica` liest aus Litematicas „Schematic-Welt“**, und die enthält sehr wahrscheinlich nur Chunks, die der Client geladen hat. Indizien in 0.28.8/0.29.1: `SchematicPlacementManager.canHandleChunk(ClientLevel, …)`, `visibleChunks`. Bei Platzierungen über die Sichtweite hinaus würden die entfernten Teile als **Luft** kopiert. Außerdem wird die ganze Platzierung in ein neues Array kopiert | [LitematicaHelper.java:116-121](src/main/java/baritone/utils/schematic/litematica/LitematicaHelper.java#L116) | Große Platzierungen bleiben unvollständig („Done“ zu früh) oder Baritone baut dort vorhandene Blöcke ab. **Im Spiel verifizieren. → ⭐ Priorität 1 (B0.6)** |
| F11 | **Speicher:** Jede Schematic ist ein `BlockState[x][z][y]` mit 4 Byte pro Block. Fertige Blöcke stehen zusätzlich in einem `LongOpenHashSet` (`observedCompleted`) mit etwa 11 Byte pro Block | [StaticSchematic.java:33](src/main/java/baritone/utils/schematic/StaticSchematic.java#L33), [BuilderProcess.java:82](src/main/java/baritone/process/BuilderProcess.java#L82) | 1000×1000×384 bräuchte über **5 GB** RAM. Große Bauten scheitern schon beim Laden oder später mit OutOfMemory |

**Richtung laut Upstream ([#3715](https://github.com/cabaletta/baritone/issues/3715), ZacSharp):** Bauplanung und
Ausführung trennen, Reihenfolge vom Spieler nach außen, Gerüste richtig handhaben, unmögliche Positionen auf eine
Sperrliste setzen statt zu pausieren, `approxPlaceable` abschaffen, mehrere gültige Zustände pro Position erlauben.
Ein Branch `builder2` wurde begonnen, aber nicht fertig. Mein Plan folgt dieser Richtung, aber schrittweise, damit
jede Stufe für sich nutzbar ist.

**Geprüfte APIs (26.2):** `Block.updateFromNeighbourShapes(state, level, pos)` (Zustand nach Nachbar-Updates),
`DataFixers.getDataFixer()` + `References.BLOCK_STATE` (Upgrade über DataVersion), `BlockStateData.getTag(int)`
(alte MCEdit-IDs mit Metadaten), `StructureTemplate.load(...)` (Vanilla-`.nbt`), `SharedConstants.getCurrentVersion().dataVersion()`.

### 4.2 Stufen

#### ⭐ PRIORITÄT 1: B0.6 Litematica direkt lesen

Das ist der wichtigste Punkt des ganzen Plans und wird **als Erstes** umgesetzt (Größe M).
1. **Reproduzieren:** Eine Platzierung, die größer als die Sichtweite ist, mit dem jetzigen Code bauen und F10
   nachweisen (entfernte Teile kommen als Luft an).
2. **Direkt lesen:** Statt aus der Schematic-Welt die Daten holen über
   `placement.getSchematic().getSubRegionContainer(name).get(x, y, z)`. Diese API ist in 0.28.8 (26.2) und
   0.29.1 (26.3) per `javap` geprüft.
3. **Transformieren:** Positionen **und** Blockzustände selbst drehen und spiegeln (`BlockState.rotate/mirror`),
   zuerst für die Platzierung, dann für die Unterregion. Gegen Litematicas eigene Vorschau abgleichen.
4. **Ansicht statt Kopie:** Kein neues `BlockState[][][]` pro Unterregion. Baritone greift direkt auf Litematicas
   Container zu. Das spart RAM bei großen Platzierungen (F11).
5. **Absichern:** Die Stubs um `LitematicaBlockStateContainer` erweitern. `LinkageError` abfangen und
   „Litematica-Version nicht kompatibel“ melden statt abzustürzen (F9).
6. **Abnahme, auf 26.2 und 26.3:**
   - Eine Platzierung größer als die Sichtweite wird vollständig gebaut, ohne vorhandene Blöcke abzubauen.
   - Drehung (0/90/180/270), Spiegelung und mehrere Unterregionen kommen korrekt an.
   - Treppen und Türen haben nach dem Drehen die richtige Ausrichtung.

Damit Litematica-Bauten danach auch inhaltlich sauber laufen, folgen direkt B0.2/B0.3 (keine Abbauschleifen),
B2 (Ausrichtung) und B3a (große Bauten).

**B0 Schnelle Fixes** (je S, zuerst machen)
- **B0.1 Testbarkeit klären:** Prüfen, ob `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` in JUnit
  laufen. Wenn ja, lassen sich Formate und Blockvergleiche ohne Spielstart testen. Das entscheidet, wie viel
  Test-Abdeckung die folgenden Stufen bekommen.
- **B0.2 Schleifen stoppen, Teil 1:** Neues Setting `buildIgnoreDynamicProperties` (Standard an). Ignoriert beim
  Vergleich Eigenschaften, die sich von selbst ändern: `age`, `stage`, `distance` (nur Laub und Gerüst), `moisture`,
  `snowy`, `persistent` (Laub).
- **B0.3 Schleifen stoppen, Teil 2:** Auf dem Haupt-Thread vergleichen mit
  `Block.updateFromNeighbourShapes(desired, level, pos)`. Gilt also „so würde der Block hier nach Nachbar-Updates
  aussehen“ als korrekt, dann erledigt das Zäune, Scheiben, Mauern, Treppenformen, Redstone-Verbindungen und
  Pilzseiten generisch. Im Pfad-Thread ist der Weltzugriff nicht sicher. Dort stattdessen eine feste Liste
  abgeleiteter Eigenschaften pro Blockklasse (z. B. `CrossCollisionBlock`, `WallBlock`, `RedStoneWireBlock`,
  `StairBlock.shape`). Wichtig: Diese Liste darf **nicht** global über Eigenschaftsnamen laufen, denn Ranken und
  Glühflechten nutzen dieselben Namen (`north` …) mit echter Bedeutung.
- **B0.4 Unbekannte Blöcke klar melden:** Beim Laden auflisten („3 unbekannte Blocktypen: poplar_planks ×120 …“).
  Setting `buildUnknownBlocks` = `skip` (als Luft bauen) oder `fail`. Nie mehr stilles Luft-Ersetzen.
- **B0.5 Materialliste:** `#build materials <datei>` und `#build status` zeigen Bedarf, vorhandene Menge
  (Inventar, auch Inhalt von Shulkern im Inventar) und Fehlmenge. Ausrichtungen werden dabei zusammengefasst.
- **B0.6 Litematica direkt lesen:** ⭐ Priorität 1, steht oben eigenständig.

**B1 Formate** (M)
- Sponge **v3** lesen (Wurzel `Schematic`, `Blocks.Palette`/`Blocks.Data`, `DataVersion`).
- **DataFixer-Upgrade** der Palette über die gespeicherte DataVersion: Sponge v2/v3 (`DataVersion`), Litematica
  (`MinecraftDataVersion`). Damit gehen auch Litematica **v5/v6** wieder.
- MCEdit über `BlockStateData.getTag(id << 4 | meta)` korrekt umwandeln (Farben und Ausrichtung bleiben erhalten).
- Vanilla-Struktur **`.nbt`** über `StructureTemplate` (mit DataFixer-Upgrade). Damit gehen auch
  **Create-Schematics**: gleiches Format, gleicher `schematics`-Ordner.
- **Axiom-Blueprints (`.bp`):** Es gibt keine öffentliche Spezifikation, deshalb nicht geplant. Stattdessen aus
  Axiom als Schematic exportieren (ob das geht, ist nicht verifiziert).
- Grenze: Upgrades gehen nur vorwärts. Eine 26.3-Schematic auf 26.2 enthält unbekannte Blöcke, die behandelt B0.4.
- Tests: kleine Testdateien pro Format im Test erzeugen und parsen (falls B0.1 klappt).

**B2 Ausrichtungsbewusstes Platzieren** (L, größter Hebel gegen F1)
- `approxPlaceable` ersetzen: Ein Item zählt als „kann Block X platzieren“, egal in welcher Ausrichtung. Ob die
  Ausrichtung passt, wird erst beim Platzieren entschieden. Damit verschwinden die falschen „Missing materials“.
- **Steh-Positionen planen:** Für den Zielzustand die Kandidaten-Stehplätze rund um den Block durchsimulieren
  (`getStateForPlacement` mit Blickrichtung, Klickseite und Trefferpunkt, wie heute in `hasAnyItemThatWouldPlace`).
  Das Ziel wird dann ein Stehplatz, von dem aus der richtige Zustand entsteht, statt irgendein Nachbarfeld.
- Das Ergebnis pro Zustand cachen, als „Platzier-Rezept“ aus relativem Stehplatz, Blick und Seite. Generisch, ohne
  Sonderfall pro Block. Abgedeckt sind Treppen, Stämme, Beobachter, Kolben, Trichter, Truhen (schleichen gegen
  ungewolltes Verbinden), Türen, Falltüren, Betten, Schilder.
- `buildIgnoreDirection` bleibt als Notausgang.

**B3a Skalierung für große Bauten** (L, nötig weil die Baugrößen „unterschiedlich“ sind, siehe F4/F11)
- **Kompakte Speicherung:** Palette (Liste der vorkommenden Zustände) plus bitgepackte Indizes pro 16×16×16-Abschnitt.
  Abschnitte aus reiner Luft werden gar nicht gespeichert. Das gilt für alle Formate (`StaticSchematic` ersetzen,
  API `IStaticSchematic` bleibt gleich).
- **Fertig-Markierung** als Bitset pro Chunk statt `LongOpenHashSet`, also 1 Bit statt etwa 11 Byte pro Block.
- **Scan:** nur in geladenen Chunks, mit Zeitbudget pro Tick, nach Nähe zum Spieler. Für entfernte Teile nur
  Chunk-Zusammenfassungen („hier ist noch etwas offen“) statt Einzelpositionen.
- Ziel: 1000×1000×384 mit unter etwa 300 MB RAM. Kleine Bauten werden dadurch nicht langsamer.
- Abnahme: im Test eine riesige Schematic synthetisch erzeugen, RAM-Verbrauch und Tick-Zeit messen (Messwerte vor
  und nach dem Umbau).

**B3 Bauplaner** (L)
- **Reihenfolge:** von unten nach oben. Innerhalb der Schicht nach Abstand zum Spieler sortieren statt
  `HashSet`-Reihenfolge. Stützende Blöcke zuerst (`canSurvive`: Fackeln nach Wänden, Teppiche nach Boden).
- **Gerüste:** Für Positionen ohne festen Nachbarn einen Hilfsblock setzen und in einer Liste vermerken. Am Ende
  alle Gerüste und alle außerhalb der Schematic gesetzten Lauf-Hilfsblöcke wieder abbauen.
- **Sperrliste statt Pause:** Unerreichbare oder unmögliche Positionen für einige Minuten überspringen, weiterbauen
  und am Ende auflisten. Der Bau stoppt nur noch, wenn wirklich nichts mehr geht.
- **Scan entlasten:** Geänderte Positionen über Block-Änderungs-Events als „neu prüfen“ markieren statt ständig
  alles neu zu scannen. Das baut auf dem Zeitbudget-Scan aus B3a auf.

**B4 Sonderblöcke** (je S–M, nach Bedarf)
- Mehrfachklick: Kerzen, Seegurken, Schildkröteneier, Schneeschichten, Doppel-Stufen (gleiches Item erneut auf
  denselben Block).
- Zustand per Rechtsklick einstellen: Repeater, Comparator, Notenblock, Tür, Falltür und Zauntor offen/zu, Hebel.
  Mit Vorsicht, denn das kann Redstone auslösen. Standard aus.
- Flüssigkeiten: Quellen mit Eimer setzen. Fließendes Wasser stoppen, indem die Quelle zugesetzt wird.
  Waterlogged = Block setzen, dann Wasser hinein.
- Werkzeug-Umwandlung: Ackerboden (Hacke), Trampelpfad (Schaufel), entrindete Stämme (Axt). **26.3:** Hacke, Axt
  und Schaufel nutzen dafür jetzt die Komponente `minecraft:block_transformer`, 26.2 noch fest verdrahtet. Das
  braucht eine kleine Adapter-Klasse pro Branch.

**B5 Survival-Materiallogistik** (L, nutzt den Truhen-Code aus Phase 2.3)
- Fehlt Material: in nahen Truhen und Endertruhe suchen, bevorzugt in einer per `#sel` markierten
  „Material-Truhe“. Holen, zurück, weiterbauen.
- Beim Freiräumen (`cleararea`, Abbau falscher Blöcke) die Drops einsammeln und Müll wegwerfen (Phase 1.3).
- Später optional: fehlende einfache Materialien (Stein, Erde, Sand) automatisch per `#mine` besorgen.

**B6 Versionsbesonderheiten**
- **26.2:** Schwefel- und Zinnober-Treppen, -Stufen und -Mauern laufen über die generische Logik (B2). Bei den
  Schwefelstacheln (`SULFUR_SPIKE`) verschmelzen Spitzen nur, wenn **ohne Schleichen** platziert wird. Baritone
  schleicht aber immer beim Platzieren. Also für diese Blöcke ohne Schleichen platzieren, wenn die Schematic
  verschmolzene Spitzen will, oder die Eigenschaft ignorieren (beim Umsetzen am Block prüfen). Keine Gerüste
  über Geysiren (Phase 0.5).
- **26.3:** Woll- und Beton-Treppen/-Stufen sowie das Pappel-Set laufen über B2. Strohbett = zweiteiliges Bett,
  der Builder darf es nur setzen, nie benutzen. Regalpilze an der Wand: die Größen-Eigenschaft prüfen (Knochenmehl
  ändert sie). Kissen sind **Entities** und werden nicht gebaut, das kommt als Hinweis in die Materialliste.
  `block_transformer` siehe B4.

**Abnahme (In-Game-Testset, auf 26.2 und 26.3):**
- Treppenhaus mit allen vier Treppen-Richtungen, ohne `buildIgnoreDirection`
- Zaun-, Scheiben- und Mauer-Raster
- Kaktus- und Zuckerrohrfarm (keine Abbauschleife)
- Baum mit Laub, Redstone-Leitung
- Tür und Falltür
- Schwebender Block mit Gerüst-Rückbau
- Brunnen mit Wasser
- Flache Fläche 200×200 (keine Ruckler) und eine synthetische Riesen-Schematic (RAM und Tick-Zeit messen, B3a)
- **Jede Quelle einmal:** WorldEdit `.schem` (Sponge v2 und v3), altes `.schematic` (MCEdit), `.litematic` v6 und
  v7, Litematica-Platzierung im Spiel (auch größer als die Sichtweite, mit Drehung, Spiegelung und Unterregionen),
  Vanilla-Strukturblock-`.nbt`, Create-Schematic, eine 26.3-Datei auf 26.2

---

## 5. Querschnitt

- **Settings:** Alles neue ist standardmäßig **aus**. Ausnahmen sind Sicherheitsregeln und reine Fehlerbehebungen
  wie B0.2/B0.3 (gegen die Abbauschleifen), die an sind, aber per Setting abschaltbar bleiben.
- **Sicherheitsregeln:** Bett nur in der Oberwelt anklicken. Nie gefährliches Essen. Nie Blöcke abbauen, die an
  Flüssigkeit grenzen (gilt schon).
- **Tests:** Die reine Logik (Essensauswahl, Müllauswahl, Licht- und Abstandsentscheidung) als JUnit-Tests, ohne
  Minecraft-Start. Alles andere über eine In-Game-Checkliste pro Feature in einer eigenen Testwelt.
- **Mergebarkeit:** Neue Features möglichst in **neuen Dateien** (eigene Processes und Commands) mit nur kleinen
  Einhängepunkten in bestehenden Dateien. So bleiben spätere Upstream-Updates mergebar.
- **Server:** Automatisierung nur dort einsetzen, wo die Regeln sie erlauben.

## 6. Reihenfolge

1. ✅ (Code, Abnahme im Spiel offen) **⭐ PRIORITÄT 1: B0.6 Litematica direkt lesen.** Wichtigster Punkt, kommt vor allem anderen.
   Zuerst auf 26.2 umsetzen und abnehmen, dann sofort auf 26.3 übernehmen und dort abnehmen.
2. ✅ (Code, Abnahme im Spiel offen) **B0.2/B0.3** (keine Abbauschleifen), weil das Litematica-Bauten direkt betrifft. Danach der Rest von B0.
3. ✅ bis auf 0.1 (braucht die Commit-Freigabe) und 0.6 (bewusst weggelassen): Phase 0 komplett (klein, behebt echte Fehler, bereitet 26.3 vor)
4. 1.1 Auto-Essen ✅ (Code) → **1.3 Volles Inventar ← hier weitermachen** → 1.2 Rückzug
5. **B2 Ausrichtung** → **B3a Skalierung** (beide wichtig für Litematica-Bauten) → **B1 Formate** (alle Quellen)
6. 2.1 Auto-Fackel → 2.2 Auto-Schlafen
7. **B3 Bauplaner** → 2.3 Truhen → **B5 Materiallogistik** (baut auf 2.3 auf)
8. Danach je nach Lust: B4, 3.1, 3.2, …

Begründung:
- **Litematica ist der wichtigste Punkt (Vorgabe vom 05.11.2026).** Deshalb steht B0.6 ganz vorn, gefolgt von allem,
  was Litematica-Bauten direkt verbessert: keine Abbauschleifen (B0.2/B0.3), richtige Ausrichtung (B2), große
  Bauten (B3a).
- B1 (Dateiformate) rückt dahinter. Für Platzierungen in der Litematica-Mod braucht man B1 nicht, denn die Mod liest
  ihre Dateien selbst.
- Survival und Builder wechseln sich danach ab, damit beides früh nutzbar wird.
- B5 hängt an 2.3, und B2 ist die Voraussetzung dafür, dass B3 sinnvoll planen kann.

Nach jedem Feature: Tests, Build mit JDK 25 (`JAVA_HOME` setzen), In-Game-Check auf 26.2, dann Merge auf 26.3 und dort
noch einmal prüfen.

## 7. Offene Entscheidungen

- **Kampf:** Gar nicht (empfohlen, Baritone-Philosophie, auf Servern oft verboten), nur Notwehr gegen den Angreifer,
  oder ganz weglassen und Meteor parallel nutzen?
- **Commits:** Darf ich lokal auf Branches committen (ohne Push)? Das ist nötig für 26.2/26.3 parallel.
- **Basis für 26.3:** Meteors `26.3` plus Standalone-Umstellung oder direkt cabalettas `26.3` (schon standalone)?

**Beantwortet (05.11.2026):**
- Builder-Quellen: **alle** (WorldEdit `.schem`/`.schematic`, Litematica-Dateien, Litematica-Mod im Spiel,
  Vanilla-Strukturblöcke). Daraus folgt B0.6 + B1 komplett, plus Create-`.nbt`. Axiom `.bp` geht nicht, siehe B1.
- Baugrößen: **unterschiedlich**, von klein bis sehr groß. Daraus folgt die neue Stufe B3a.

## Quellen

- [Minecraft Wiki: Java Edition 26.2](https://minecraft.wiki/w/Java_Edition_26.2)
- [Minecraft Wiki: Java Edition 26.3](https://minecraft.wiki/w/Java_Edition_26.3)
- [Minecraft Wiki: 26.3 Development versions](https://minecraft.wiki/w/Java_Edition_26.3/Development_versions)
- [Beebom: 26.3 Wilderness Bound Roundup](https://beebom.com/minecraft-26-3-update-roundup/)
- [GGServers: 26.2 Chaos Cubed](https://ggservers.com/blog/minecraft-java-edition-26-2-everything-you-need-to-know-about-the-chaos-cubed-update/)
- Branch-Liste per `git ls-remote` von cabaletta/baritone und MeteorDevelopment/baritone
- APIs per `javap` gegen `minecraft-merged-deobf-26.2.jar` geprüft (`DataComponents.FOOD`, `Animal.isFood`, `BlockTags.SPELEOTHEMS`, `Block.updateFromNeighbourShapes`, `DataFixers`, `BlockStateData.getTag`, `StructureTemplate.load`, …)
- Builder-Issues bei cabaletta: [#3715 Overhaul](https://github.com/cabaletta/baritone/issues/3715), [#4844 Litematica materials](https://github.com/cabaletta/baritone/issues/4844), [#4744 Bau-Schleife](https://github.com/cabaletta/baritone/issues/4744), [Liste offener Build-Issues](https://github.com/cabaletta/baritone/issues?q=is%3Aissue+is%3Aopen+build+schematic)
- [WorldEdit 7.3.0 (Sponge-v3-Unterstützung)](https://modrinth.com/mod/worldedit/version/7.3.0)
- [Litemapy: Litematic-Format](https://litemapy.readthedocs.io/en/latest/litematics.html), [nucleation: Versionsgrenzen v4–v7](https://docs.rs/crate/nucleation/0.3.16/source/src/formats/litematic.rs)
- [Litematica auf Modrinth](https://modrinth.com/mod/litematica). Versionen über die Modrinth-API ermittelt: 0.28.8 (26.2, 05.09.2026), 0.29.1 (26.3, 27.09.2026). Signaturen per `javap` geprüft
- [Axiom-Doku: Blueprints](https://axiomdocs.moulberry.com/editor/windows/blueprints.html) (kein öffentliches Dateiformat)
- [Create-Schematics-Guide 2026](https://space-node.net/blog/create-mod-schematics-guide-2026) (Create nutzt das Vanilla-`.nbt`-Format)
