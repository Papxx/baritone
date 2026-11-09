# Übergabe-Dokumentation (`26.2` Branch – IDE-Setup & Warning-Bereinigung)

**Datum:** 08.11.2026 (geprüft und korrigiert am selben Tag, siehe [Abschnitt 5](#5-prüfung-am-08112026))  
**Repository:** `g:\baritone\baritone` (Branch `26.2`)  
**Status:** Alle Änderungen befinden sich wie in [`PLAN-SURVIVAL.md`](PLAN-SURVIVAL.md) gefordert **uncommitted** im Working-Tree. Tests: 80 JUnit-Tests, 79 grün und 1 übersprungen (der Symlink-Test in `AtomicFileWriterTest` setzt unter Windows ohne Entwicklermodus per `Assume` aus, das ist so gewollt). Voller Build `gradlew build` grün, Mod-Jar unter `dist/baritone-api-fabric-26.2-SNAPSHOT.jar`.

**Abgrenzung:** Diese Übergabe betrifft nur das IDE-Setup und die Warning-Bereinigung. Der Working-Tree enthält außerdem die Survival- und Builder-Arbeit (Litematica, Auto-Essen, Avoidance usw.), die in [`PLAN-SURVIVAL.md`](PLAN-SURVIVAL.md), Abschnitt 0, dokumentiert ist. Dateien wie `Avoidance.java`, `Settings.java` oder `Baritone.java` enthalten beides.

---

## 1. Ursache & Behebung der tausenden IDE-Fehler in VS Code (Eclipse JDT LS)

### Problem
In VS Code (`redhat.java` / Eclipse JDT LS) wurden über **3.700 Compile-Errors** angezeigt (`BlockPos cannot be resolved to a type`, `Minecraft cannot be resolved`, etc.), obwohl der Gradle-Build auf der Kommandozeile funktionierte.

### Ursache
Das `unimined`-Plugin (`xyz.wagyourtail.unimined` 1.4.2-SNAPSHOT) hängt Minecraft (`minecraft-26.2-minecraft-none-merged+fixed-official.jar`) und die Minecraft-Libraries nur an die **FileCollection** `sourceSets.main.compileClasspath` an (`SourceSet.setCompileClasspath(getCompileClasspath().plus(...))`, per `javap` im Unimined-Jar nachgeprüft), nicht an die **Konfiguration** `compileClasspath`. Gradle selbst kompiliert mit der FileCollection, deshalb lief der Build. IDE-Importer wie Buildship (JDT LS) oder der Gradle Build Server lesen dagegen die Konfigurationen und sahen deshalb keine einzige Minecraft-Klasse.

### Lösung in [`build.gradle`](build.gradle#L103-L109)
- `configurations.compileClasspath.extendsFrom(configurations.minecraft, configurations.minecraftLibraries)`: Die Konfiguration enthält jetzt Minecraft und dessen Libraries. Der aufgelöste Compile-Classpath bleibt gleich, denn die FileCollection enthielt diese Dateien schon.
- `configurations.minecraftLibraries.dependencies.withType(ModuleDependency).configureEach { it.transitive = false }`: `minecraftLibraries` ist nicht-transitiv, weil Mojangs Versions-Manifest bereits jede Library einzeln auflistet. Ohne diese Zeile würden die Libraries über das transitive `compileClasspath` zusätzliche (und eventuell andere) Versionen nachziehen.
- Die anderen SourceSets (`api`, `launch`, `test`, `schematica_api`) übernehmen den Classpath im Build wie bisher über `main.compileClasspath` (siehe `sourceSets`-Block). In der IDE sehen sie Minecraft, weil Eclipse/JDT LS alle SourceSets zu **einem** Projekt-Classpath zusammenfasst.
- Ein `eclipse`-Plugin ist **nicht** nötig und nicht eingetragen: Buildship holt sich das Eclipse-Modell selbst über die Gradle Tooling API.
- Ergebnis (geprüft am 08.11.2026): Das Problems-Panel von VS Code zeigt 0 Fehler und 0 Warnungen. JDT LS analysiert die Dateien tatsächlich (die TODO-Hinweise erscheinen).

---

## 2. Behobene Warnings in den 26.2- / Survival- / BuildSrc-Dateien

### [`Settings.java`](src/api/java/baritone/api/Settings.java#L1654-L1682)
- Überflüssiges `@SuppressWarnings("unchecked")` am Konstruktor [`Setting(T value)`](src/api/java/baritone/api/Settings.java#L1654) entfernt.
- Fehlendes `@SuppressWarnings("unchecked")` an [`Setting.getValueClass()`](src/api/java/baritone/api/Settings.java#L1677-L1681) ergänzt (zuvor war nur ein IntelliJ-spezifisches `// noinspection unchecked` vorhanden).

### [`SettingsUtil.java`](src/api/java/baritone/api/utils/SettingsUtil.java#L106-L352)
- Raw-Types `Settings.Setting` durch `Settings.Setting<?>` ersetzt in:
  - [`save(Settings)`](src/api/java/baritone/api/utils/SettingsUtil.java#L106)
  - [`modifiedSettings(Settings)`](src/api/java/baritone/api/utils/SettingsUtil.java#L125-L141)
  - [`settingTypeToString(Settings.Setting<?>)`](src/api/java/baritone/api/utils/SettingsUtil.java#L152)
  - [`settingValueToString(Settings.Setting<?>)`](src/api/java/baritone/api/utils/SettingsUtil.java#L167-L171) und [`settingDefaultToString(Settings.Setting<?>)`](src/api/java/baritone/api/utils/SettingsUtil.java#L173-L177): intern ein Raw-Cast auf die generische Überladung, mit `@SuppressWarnings({"rawtypes", "unchecked"})`
  - [`settingToString(Settings.Setting<?>)`](src/api/java/baritone/api/utils/SettingsUtil.java#L187)
  - [`javaOnlySetting(Settings.Setting<?>)`](src/api/java/baritone/api/utils/SettingsUtil.java#L202)
- Raw-Type `ISettingParser` bei `enum Parser` durch `implements ISettingParser<Object>` parametrisiert ([`SettingsUtil.java`](src/api/java/baritone/api/utils/SettingsUtil.java#L230)), Raw-Type `(Class) type` in [`Parser.accepts(Type)`](src/api/java/baritone/api/utils/SettingsUtil.java#L349-L352) auf `(Class<?>) type` geändert und unvermeidbare generische Casts in [`parseAndApply`](src/api/java/baritone/api/utils/SettingsUtil.java#L206) sowie im [`Parser`-Konstruktor](src/api/java/baritone/api/utils/SettingsUtil.java#L330) mit `@SuppressWarnings` versehen.

### [`Baritone.java`](src/main/java/baritone/Baritone.java#L42-L46)
- Ungenutzte Imports `java.util.ArrayList` und `java.util.List` entfernt.

### [`CachedRegion.java`](src/main/java/baritone/cache/CachedRegion.java#L224-L225)
- Generische Array-Erzeugung auf `(Map<String, List<BlockPos>>[][]) new Map<?, ?>[32][32]` mit `@SuppressWarnings("unchecked")` umgestellt (vermeidet sowohl `rawtypes`- als auch `unchecked`-Warnungen in `javac` und Eclipse JDT LS).

### [`MovementHelper.java`](src/main/java/baritone/pathing/movement/MovementHelper.java#L454-L615)
- Zwei ungenutzte lokale Variablen `Block block = state.getBlock();` in [`canWalkOnPosition`](src/main/java/baritone/pathing/movement/MovementHelper.java#L454) und [`getMiningDurationTicks`](src/main/java/baritone/pathing/movement/MovementHelper.java#L611) entfernt.

### [`MovementTraverse.java`](src/main/java/baritone/pathing/movement/movements/MovementTraverse.java#L39-L42)
- Ungenutzten Import `net.minecraft.world.level.block.LadderBlock` entfernt.
- Außerdem die Debug-Ausgabe `System.out.println("In movement traverse");` in [`updateState`](src/main/java/baritone/pathing/movement/movements/MovementTraverse.java#L249-L254) entfernt (keine Warning, aber Konsolen-Spam; das Verhalten ist sonst unverändert).

### [`Avoidance.java`](src/main/java/baritone/utils/pathing/Avoidance.java#L83-L86)
- `@SuppressWarnings("deprecation")` bei `ctx.player().getLightLevelDependentMagicValue() < 0.5` in [`Avoidance.create`](src/main/java/baritone/utils/pathing/Avoidance.java#L84-L85) ergänzt. Die Methode ist in `Entity` als `@Deprecated` markiert, Minecrafts eigenes `Spider.SpiderTargetGoal` nutzt in 26.2 aber exakt denselben Aufruf mit Schwellwert `0.5f` (beides per `javap` geprüft).

### [`LitematicaSchematic.java`](src/main/java/baritone/utils/schematic/format/defaults/LitematicaSchematic.java#L310-L313)
- `@SuppressWarnings("unused")` an [`LitematicaBitArray.size()`](src/main/java/baritone/utils/schematic/format/defaults/LitematicaSchematic.java#L310) ergänzt.

### BuildSrc (`ProguardTask`, `BaritoneGradleTask`, `CreateDistTask`)
- [`ProguardTask.java`](buildSrc/src/main/java/baritone/gradle/task/ProguardTask.java):
  - Ungenutztes Feld `requiredLibraries` und ungenutzte innere Klasse `Pair<A, B>` entfernt (Überbleibsel nach dem ProGuard-Update).
  - `@SuppressWarnings("deprecation")` an [`runProguard(Path)`](buildSrc/src/main/java/baritone/gradle/task/ProguardTask.java#L236) für `getProject().javaexec(...)` ergänzt. **Achtung:** Das unterdrückt nur die Compiler-Warnung. Gradle meldet zur Laufzeit weiterhin, dass `Project.javaexec(Action)` in Gradle 9.0 entfernt wird. Vor einem Gradle-9-Update muss das auf `ExecOperations.javaexec` umgestellt werden (siehe [Abschnitt 5](#5-prüfung-am-08112026)).
  - Die übrigen Änderungen in dieser Datei (`jdkModuleLibrary`, ProGuard ohne `jmods`) stammen aus dem früheren eigenen Fix und gehören nicht zur Warning-Bereinigung.
- [`BaritoneGradleTask.java`](buildSrc/src/main/java/baritone/gradle/task/BaritoneGradleTask.java):
  - Ungenutzten Import `org.gradle.api.tasks.TaskAction` entfernt.
  - Veraltetes `getProject().getBuildDir()` in [`getRelativeFile(String)`](buildSrc/src/main/java/baritone/gradle/task/BaritoneGradleTask.java#L109-L111) durch die Gradle-8-API `getProject().getLayout().getBuildDirectory().get().getAsFile()` ersetzt.
- [`CreateDistTask.java`](buildSrc/src/main/java/baritone/gradle/task/CreateDistTask.java#L22-L28):
  - Ungenutzte Imports `java.nio.file.OpenOption`, `java.nio.file.StandardOpenOption` und `java.util.Arrays` entfernt.

---

## 3. Behobene Warnings im bestehenden Baritone-Code

### API (`src/api/java`)
- [`BaritoneAPI.java`](src/api/java/baritone/api/BaritoneAPI.java#L38): Veraltetes `Class.forName(...).newInstance()` (deprecated seit Java 9) durch `.getDeclaredConstructor().newInstance()` ersetzt.
- [`IWaypoint.java`](src/api/java/baritone/api/cache/IWaypoint.java#L87-L135): Das bereits angelegte `TAG_LIST` in [`Tag.getByName`](src/api/java/baritone/api/cache/IWaypoint.java#L112) und [`Tag.getAllNames`](src/api/java/baritone/api/cache/IWaypoint.java#L129) anstelle von `Tag.values()` verwendet (vermeidet sowohl die Warning als auch unnötige Array-Allokationen).
- [`IArgParserManager.java`](src/api/java/baritone/api/command/argparser/IArgParserManager.java#L68): `@SuppressWarnings("rawtypes")` an [`getRegistry()`](src/api/java/baritone/api/command/argparser/IArgParserManager.java#L68-L69) ergänzt.
- [`EntityClassById.java`](src/api/java/baritone/api/command/datatypes/EntityClassById.java#L28-L35): Raw-Type `EntityType` durch `EntityType<?>` ersetzt.
- [`Paginator.java`](src/api/java/baritone/api/command/helpers/Paginator.java#L44-L47): `@SafeVarargs` am Varargs-Konstruktor [`Paginator(E... entries)`](src/api/java/baritone/api/command/helpers/Paginator.java#L45) ergänzt.
- [`FillSchematic.java`](src/api/java/baritone/api/schematic/FillSchematic.java#L20-L22): Ungenutzten Import `net.minecraft.world.level.block.Blocks` entfernt.
- [`CylinderMask.java`](src/api/java/baritone/api/schematic/mask/shape/CylinderMask.java#L36-L50): Statische Methoden [`getA`](src/api/java/baritone/api/schematic/mask/shape/CylinderMask.java#L62) und [`getB`](src/api/java/baritone/api/schematic/mask/shape/CylinderMask.java#L66) direkt statt über `this.getA(...)` / `this.getB(...)` aufgerufen.
- [`BlockOptionalMeta.java`](src/api/java/baritone/api/utils/BlockOptionalMeta.java):
  - Ungenutzte private Methode `getVanillaServerPack()` samt Feld `getVanillaServerPack` und den dadurch ungenutzten Imports (`VanillaPackResources`, `javax.annotation.Nullable`, `java.lang.reflect.Method`) entfernt.
  - `@SuppressWarnings("unchecked")` an [`castToIProperty(Object)`](src/api/java/baritone/api/utils/BlockOptionalMeta.java#L107-L110) ergänzt.

### Launch / Mixins (`src/launch/java`)
- [`MixinCommandSuggestionHelper.java`](src/launch/java/baritone/launch/mixins/MixinCommandSuggestionHelper.java#L56): Raw-Type `ParseResults` durch `ParseResults<?>` ersetzt.
- [`MixinPalettedContainer.java`](src/launch/java/baritone/launch/mixins/MixinPalettedContainer.java):
  - Redundanten Import `baritone.utils.accessor.IPalettedContainer.IData` entfernt (`MixinPalettedContainer` implementiert bereits `IPalettedContainer<T>`, der verschachtelte Typ `IData` ist dadurch sichtbar).
  - `@SuppressWarnings("unchecked")` an [`data()`](src/launch/java/baritone/launch/mixins/MixinPalettedContainer.java#L86) und [`sneaky(Throwable, Class)`](src/launch/java/baritone/launch/mixins/MixinPalettedContainer.java#L96-L98) ergänzt.

### Main (`src/main/java`)
- [`LookBehavior.java`](src/main/java/baritone/behavior/LookBehavior.java#L223-L224): Expliziten Cast `(float)` bei `desiredYaw += (float) this.randomYawOffset;` und `desiredPitch += (float) this.randomPitchOffset;` ergänzt. Die Addition läuft jetzt in `float` statt in `double` mit anschließendem Cast. Das ändert höchstens die letzte Stelle der Rundung und ist für die Zufallsabweichung ohne praktische Wirkung.
- [`FasterWorldScanner.java`](src/main/java/baritone/cache/FasterWorldScanner.java#L177): `@SuppressWarnings("unchecked")` an [`visitSection`](src/main/java/baritone/cache/FasterWorldScanner.java#L177-L178) für den Mixin-Interface-Cast auf `IPalettedContainer<BlockState>` ergänzt.
- [`ExampleBaritoneControl.java`](src/main/java/baritone/command/ExampleBaritoneControl.java#L111-L175): Raw-Type `Settings.Setting` durch `Settings.Setting<?>` ersetzt.
- [`ArgParserManager.java`](src/main/java/baritone/command/argparser/ArgParserManager.java#L30-L93):
  - In [`getParserStateless`](src/main/java/baritone/command/argparser/ArgParserManager.java#L38) und [`getParserStated`](src/main/java/baritone/command/argparser/ArgParserManager.java#L50) Raw-Type-Methodenreferenzen `IArgParser.Stateless.class::cast` / `IArgParser.Stated.class::cast` durch typisierte Wildcard-Casts (`p -> (IArgParser.Stateless<?>) p` bzw. `p -> (IArgParser.Stated<?, ?>) p`) ersetzt, das Ergebnis auf den generischen Rückgabetyp gecastet und mit `@SuppressWarnings("unchecked")` annotiert. In `getParserStated` ist dabei ein doppeltes `.map(IArgParser.Stated.class::cast)` weggefallen.
  - `@SuppressWarnings("rawtypes")` an `registry` und `getRegistry()` ergänzt.
- [`ElytraCommand.java`](src/main/java/baritone/command/defaults/ElytraCommand.java#L34-L36): Ungenutzten Import `net.minecraft.world.level.Level` entfernt.
- [`FollowCommand.java`](src/main/java/baritone/command/defaults/FollowCommand.java#L53-L162): Raw-Types `EntityType` und `IDatatypeFor` durch `EntityType<?>` und `IDatatypeFor<?>` ersetzt.
- [`SetCommand.java`](src/main/java/baritone/command/defaults/SetCommand.java#L80-L230): Raw-Type `Settings.Setting` durch `Settings.Setting<?>` ersetzt und `@SuppressWarnings("unchecked")` beim Boolean-Setting-Cast ergänzt.
- [`Path.java`](src/main/java/baritone/pathing/calc/Path.java#L32-L35): Ungenutzten Import `java.util.LinkedList` entfernt.
- [`MovementFall.java`](src/main/java/baritone/pathing/movement/movements/MovementFall.java#L38-L98): Ungenutzte lokale Variable `Block destBlock = destState.getBlock();` sowie den dadurch ungenutzten Import `net.minecraft.world.level.block.Block` entfernt.
- [`MovementPillar.java`](src/main/java/baritone/pathing/movement/movements/MovementPillar.java#L33-L35): Ungenutzten Import `net.minecraft.core.BlockPos` entfernt.
- [`PrecomputedData.java`](src/main/java/baritone/pathing/precompute/PrecomputedData.java#L49-L68): Fehlende `case NO -> {}` Zweige in den drei `Ternary`-Switch-Anweisungen in [`fillData`](src/main/java/baritone/pathing/precompute/PrecomputedData.java#L45) ergänzt.
- [`ElytraProcess.java`](src/main/java/baritone/process/ElytraProcess.java#L70): Redundantes Superinterface `IBaritoneProcess` aus der `implements`-Liste entfernt (bereits über `BaritoneProcessHelper` und `IElytraProcess` implementiert).
- [`BlockStateOctreeInterface.java`](src/main/java/baritone/process/elytra/BlockStateOctreeInterface.java#L20-L40): Ungenutzten Import `net.minecraft.world.level.dimension.DimensionType` und ungenutztes Feld `context` entfernt.
- [`ElytraBehavior.java`](src/main/java/baritone/process/elytra/ElytraBehavior.java):
  - Ungenutzten Import `net.minecraft.world.level.chunk.ChunkSource` entfernt.
  - Ungenutzte lokale Variablen `minY` und `y` in [`PathManager.tick()`](src/main/java/baritone/process/elytra/ElytraBehavior.java#L162) sowie `cx` und `cz` in [`simulate(...)`](src/main/java/baritone/process/elytra/ElytraBehavior.java#L1192) entfernt.
  - Fehlende `hashCode()`-Implementierungen passend zu `equals(Object)` in [`SolverContext`](src/main/java/baritone/process/elytra/ElytraBehavior.java#L845-L848) und [`FireworkBoost`](src/main/java/baritone/process/elytra/ElytraBehavior.java#L907-L913) ergänzt. Beide sind konsistent zu `equals`: `SolverContext` vergleicht den Pfad per Referenz und hasht ihn deshalb mit `System.identityHashCode`, und alle nicht geboosteten `FireworkBoost` sind gleich und liefern deshalb `0`.
- [`NetherPathfinderContext.java`](src/main/java/baritone/process/elytra/NetherPathfinderContext.java):
  - Ungenutzten Import `net.minecraft.world.level.chunk.PaletteResize` entfernt.
  - `@SuppressWarnings({"unchecked", "removal"})` an [`writeChunkData`](src/main/java/baritone/process/elytra/NetherPathfinderContext.java#L298) ergänzt.
- [`GuiClick.java`](src/main/java/baritone/utils/GuiClick.java#L66-L150): Veraltetes statisches `Helper.mc` durch das geerbte `this.minecraft`-Feld von `net.minecraft.client.gui.screens.Screen` ersetzt. Sicher, weil das Feld in 26.2 `protected final` ist und schon im `Screen`-Konstruktor gesetzt wird (per `javap` geprüft).
- [`PlayerMovementInput.java`](src/main/java/baritone/utils/PlayerMovementInput.java#L59-L62): `leftImpulse *= 0.3D; forwardImpulse *= 0.3D;` in `0.3F` geändert (verhindert implizite `double`-zu-`float`-Konvertierung). Vanilla rechnet an dieser Stelle ebenfalls in `float` (`LocalPlayer.modifyInput`: Attribut `SNEAKING_SPEED` → `float` → `Vec2.scale(float)`).
- [`ToolSet.java`](src/main/java/baritone/utils/ToolSet.java#L21-L24): Ungenutzten Import `net.minecraft.core.component.DataComponents` entfernt.

---

## 4. Gradle- & IDE-Konfigurationsbereinigung

- [`build.gradle`](build.gradle#L168): Veraltete Groovy-Space-Assignment-Syntax `options.encoding "UTF-8"` im `javadoc`-Task durch `options.encoding = "UTF-8"` ersetzt (behebt die Gradle-9.0-Deprecation-Warnung im Gradle Problems Report).
- [`fabric/build.gradle`](fabric/build.gradle#L82-L106): Veraltete Space-Assignments (`proguardVersion "7.9.1"`, `compType "fabric"`, `groupId "meteordevelopment"`, `artifactId "baritone"`) durch `=`-Zuweisungen ersetzt.
- [`buildSrc/build.gradle`](buildSrc/build.gradle#L51-L53): Explizites UTF-8-Encoding für `JavaCompile`-Tasks (`it.options.encoding = "UTF-8"`) ergänzt.
- [`.gitignore`](.gitignore#L27-L32): `bin/` unter `# Eclipse Files` aufgenommen, damit die von VS Code / Eclipse JDT LS erzeugten Build-Ordner (`baritone/bin/` und `baritone/fabric/bin/`) `git status` nicht verschmutzen.

---

## 5. Prüfung am 08.11.2026

Jede Angabe oben wurde gegen `git diff` und den aktuellen Quelltext geprüft.

| Prüfung | Ergebnis |
|---|---|
| `gradlew :test --offline --rerun-tasks` (JDK 25) | 80 Tests, 0 Fehler, 1 übersprungen (Symlink-Test, siehe Status oben) |
| `gradlew build --warning-mode all` | grün, `dist/baritone-api-fabric-26.2-SNAPSHOT.jar` neu erzeugt |
| Space-Assignment-Deprecations im Build-Log | keine mehr |
| VS Code Problems-Panel | 0 Fehler, 0 Warnungen |
| Zeilenanker in diesem Dokument | gegen die aktuellen Dateien geprüft und korrigiert |

**Gegenüber der ersten Fassung korrigiert:**
- Abschnitt 1: Ein `plugins { id 'eclipse' }` gibt es nicht (und es ist nicht nötig). Die fehlende Zeile, die `minecraftLibraries` nicht-transitiv hält, ist ergänzt. Die Ursache ist genauer beschrieben, und die Aussage, die MC-Klassen würden an `apiCompileClasspath` usw. „vererbt“, stimmte auf Ebene der Gradle-Konfigurationen nicht.
- Teststand: 1 Test wird übersprungen, nicht alle 80 laufen durch.
- Ergänzt: entfernte Debug-Ausgabe in `MovementTraverse`, Hinweise zu Rundung (`LookBehavior`, `PlayerMovementInput`) und zur `hashCode`-Konsistenz, Laufzeit-Deprecation von `javaexec`.
- Links von absoluten `file:///g:/...`-Pfaden auf relative Pfade umgestellt, falsche Zeilenanker korrigiert.

**Bewusst offen (keine Fehler, aber für später):**
- Gradle meldet beim vollen Build noch drei Deprecations, die alle in Gradle 9 bzw. 10 brechen: `Project.javaexec(Action)` in unserem `ProguardTask` (auf `ExecOperations` umstellen), `FileTreeElement.getMode()` im Shadow-Plugin (`:fabric:shadowJar`) und `Task.project` zur Ausführungszeit in Unimineds `:fabric:remapJar`. Die letzten beiden liegen in Fremd-Plugins und lassen sich nur durch deren Updates beheben.
- `javac` warnt weiterhin, dass `sun.misc.Unsafe` eine interne API ist (`NetherPathfinderContext`, `BlockOptionalMeta`). Diese Warnung lässt sich mit `@SuppressWarnings` nicht abschalten. Sie bleibt, solange Baritone `Unsafe` nutzt.
