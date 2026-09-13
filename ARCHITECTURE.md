# Architecture & Developer Guide — Advancement Progress Mod

> **Target Platform:** Minecraft **26.2** (Fabric Loader `>=0.19.5`, Loom `1.17.20`, Java **25**)  
> **Mod ID:** `advancement-progress`  
> **Environment:** **Client-Only** (zero custom network packets; 100% compatible with pure vanilla servers, Realms, Paper/Spigot, and modded Fabric servers)

---

## 1. Executive Summary

`Advancement Progress` is a client-side quality-of-life mod designed to dramatically improve the Minecraft advancement tracking experience. It solves major vanilla limitations:
1. **Tiny viewport:** Vanilla advancements screen is rigidly clamped to a 252×140 window. This mod expands the canvas responsively across the entire screen and applies a clean **`0.65f` zoom-out factor** (inspired by *Better Advancements*), displaying 12–14 vertical rows simultaneously with smooth scrolling and centering.
2. **Hidden advancement progress:** Vanilla provides no built-in way to see which specific criteria have been completed for composite achievements (e.g. *Monsters Hunted*, *Adventuring Time*, *A Balanced Diet*, *Two by Two*). The mod introduces an interactive **Criteria Inspector Panel** with item icons, entity heads, localized names, checkmarks, scrollable list, and a "Hide completed" filter.
3. **In-game HUD Pinning:** Players can pin up to **3 advancements** directly to the top-right corner of their in-game HUD. Pins are saved **per-world** (singleplayer) or **per-server** (multiplayer) in JSON config.
   - **Composite/Progressive advancements:** Displays title, item icon, completed count vs total (e.g. `24/35 (69%)`), and a color-coded micro progress bar.
   - **Simple/Non-progressive advancements:** Displays title, item icon, and the advancement's actual description (wrapped dynamically to 1 or 2 lines; card height automatically expands from 22px to 31px) with status checkmarks (`✔`).
4. **Global & Tab Progress Bars:** Displays an overarching progress bar at the top of the advancements screen (`Total: X / Y (Z%)`) and per-tab mini progress bars on tab headers.
5. **Native Tooltip Integration:** Instead of floating secondary tooltips that block the advancement description, interaction hints (`LMB: inspect criteria • RMB: pin/unpin`) are redirected into the native tooltip layout box.

---

## 2. Project Directory Structure

```
advancement-progress/
├── build.gradle                              # Fabric Loom 1.17.20 build script (Java 25)
├── gradle.properties                         # Dependencies: MC 26.2, Fabric Loader, Fabric API
├── ARCHITECTURE.md                           # This architecture documentation
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── dmystery/
│       │           ├── AdvancementProgress.java         # Common constants, logger, Identifier helper
│       │           ├── AdvancementProgressClient.java   # ClientModInitializer, HUD element registration
│       │           ├── client/
│       │           │   ├── AdvancementCache.java        # In-memory stats cache & dirty flag logic
│       │           │   ├── AdvancementDataLoader.java   # Offline/client data pack advancement discovery
│       │           │   ├── AdvancementProgressConfig.java       # Singleton JSON config (config/advancement-progress.json)
│       │           │   ├── AdvancementProgressConfigScreen.java # Vanilla settings GUI screen
│       │           │   ├── AdvancementScreenLayout.java # Responsive screen bounds & zoom constants
│       │           │   ├── CriterionResolver.java       # Translates criterion IDs to item/entity icons & names
│       │           │   ├── HudPinManager.java           # World-scoped pin persistence & config manager
│       │           │   ├── InspectorPanel.java          # Interactive criteria inspector dialog UI
│       │           │   ├── ModMenuIntegration.java      # Mod Menu API entrypoint (ConfigScreenFactory)
│       │           │   └── PinnedAdvancementsHud.java   # In-game HUD element renderer (HudElement)
│       │           └── mixin/
│       │               ├── AdvancementsScreenMixin.java # Custom window frame, top bars, pin click handler
│       │               ├── AdvancementsScreenAccessor.java  # Accessor for selectedTab and lastScreen
│       │               ├── AdvancementTabMixin.java     # Zoom math, scaled canvas, scissor & tooltip offset
│       │               ├── AdvancementTabTypeMixin.java # Aligns right and bottom tabs to expanded window
│       │               ├── AdvancementWidgetMixin.java  # Pin star badge, native tooltip hint redirection
│       │               ├── ClientAdvancementsMixin.java # Syncs incoming packet updates with cache
│       │               ├── AdvancementTabAccessor.java      # Accessor for tab widgets, scroll & hover
│       │               ├── AdvancementWidgetAccessor.java   # Accessor for node, progress, icon & display
│       │               └── ClientAdvancementsAccessor.java  # Accessor for progress map in ClientAdvancements
│       └── resources/
│           ├── fabric.mod.json                          # Mod metadata (client environment)
│           ├── advancement-progress.mixins.json         # Mixin configuration (Java 25 compatibility)
│           └── assets/
│               └── advancement-progress/
│                   ├── icon.png                         # Mod icon
│                   └── lang/
│                       ├── en_us.json                   # English localizations
│                       └── ru_ru.json                   # Russian localizations
```

---

## 3. Component Architecture & Responsibilities

```
+-----------------------------------------------------------------------------------+
|                                  INCOMING DATA                                    |
|  - Singleplayer Server / ClientboundUpdateAdvancementsPacket                      |
|  - AdvancementDataLoader (reads vanilla server_data resources on client)          |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
                         +---------------------------------+
                         |      ClientAdvancements         |
                         |   (Vanilla Client Tree State)   |
                         +----------------+----------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                                 CORE CLIENT LOGIC                                 |
|                                                                                   |
|  +--------------------------------+       +------------------------------------+  |
|  |       AdvancementCache         |       |          HudPinManager             |  |
|  | - Total completed / total count|       | - Per-world JSON persistence       |  |
|  | - Per-tab completed & percent  |       | - Max 3 pins limit                 |  |
|  | - Dirty flag recalculation     |       | - Toggle / add / remove / clear    |  |
|  +--------------------------------+       +------------------------------------+  |
|                                                              |                    |
|  +--------------------------------+                          |                    |
|  |      CriterionResolver         |                          |                    |
|  | - Criterion ID -> Item/Entity  |                          |                    |
|  | - Localized name & icon stack  |                          |                    |
|  +--------------------------------+                          |                    |
+--------------------------------------------------------------+--------------------+
                                          |                    |
                 +------------------------+                    +-----------+
                 |                                                         |
                 v                                                         v
+-----------------------------------------------+       +------------------------------------+
|             AdvancementsScreen                |       |        PinnedAdvancementsHud       |
|  (AdvancementsScreenMixin, TabMixin, etc.)    |       |   (Net Render Overlay / In-Game)   |
|                                               |       |                                    |
|  - 9-Slice Enlarged Window (Full Screen)      |       | - Pinned cards in top-right HUD    |
|  - Top Progress Bar: "Total: X / Y (Z%)"      |       | - Composite: count, %, micro-bar   |
|  - Top Pinned Chips Bar: jump / unpin / clear |       | - Simple: title + description      |
|  - 0.65f Zoom-out Advancement Tree View       |       | - Dynamic height (22px / 31px)     |
|  - Golden glowing border & star on pinned     |       | - Visual checkmark on completion   |
|  - Native Tooltip: embedded keybind hints     |       +------------------------------------+
|  - InspectorPanel: interactive criteria popup |
+-----------------------------------------------+
```

### 3.1 com.dmystery
- **`AdvancementProgress`**: Global constant holder (`MOD_ID = "advancement-progress"`), root logger, and `Identifier.fromNamespaceAndPath(MOD_ID, path)` helper.
- **`AdvancementProgressClient`**: Entrypoint implementing `ClientModInitializer`. Registers `PinnedAdvancementsHud` into Fabric's HUD pipeline using `HudElementRegistry.addLast()`.

### 3.2 com.dmystery.client
- **`AdvancementCache`**:
  - Calculates and caches statistics across all tabs: `totalCompleted`, `totalCount`, `totalPercent`, `cachedTotalText`, and per-`AdvancementTab` `TabStats(completed, total, percent)`.
  - Implements dirty-flag tracking (`dirty = true`, `markDirty()`, `updateIfDirty(...)`). Avoids heavy recalculations on every frame.
  - Clears cleanly on world disconnect / world reset (`clear()`).
- **`AdvancementDataLoader`**:
  - In vanilla Minecraft, the server only synchronizes advancement nodes that are visible or unlocked. This can result in an artificially small total advancement count that increases as the player plays.
  - `AdvancementDataLoader` discovers all displayable advancements on the client by reading `MultiPackResourceManager` (vanilla data pack resources) or referencing `singleplayerServer.getAdvancements()`.
  - Inserts missing display advancements into `clientAdvancements.getTree()` so the total count accurately reflects 100% of all advancements from world start.
- **`AdvancementScreenLayout`**:
  - Computes window sizing based on screen dimensions: `Math.max(252, screenWidth - 24)` and `Math.max(140, screenHeight - 48 - bottomReserved)`.
  - Exposes `getInsideWidth() = windowWidth - 18` and `getInsideHeight() = windowHeight - 27`.
  - Defines the global tree zoom factor `zoom = 0.65f`.
- **`AdvancementProgressConfig`**:
  - Singleton configuration system serialized to `<minecraft_root>/config/advancement-progress.json` with pretty-printed GSON.
  - Controls: `showGlobalBar`, `showTabBadges`, `showTooltipHints`, `treeZoom` (0.5f - 1.0f), `hudEnabled`, `hudPosition` (TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT), `maxPins` (1 - 5), `autoUnpinOnComplete`.
- **`AdvancementProgressConfigScreen`**:
  - In-game settings screen extending vanilla `Screen`, built without heavy external libraries (using `CycleButton`, `AbstractSliderButton`, and `Button`).
  - Accessible directly through Mod Menu or via `ConfigScreenFactory`.
- **`ModMenuIntegration`**:
  - Implements `com.terraformersmc.modmenu.api.ModMenuApi` via `ConfigScreenFactory<?>` to seamlessly integrate the config screen into the Mod Menu UI.
- **`CriterionResolver`**:
  - Takes raw criterion identifier strings (e.g. `minecraft:netherite_chestplate`, `minecraft:blaze`, `minecraft:plains`, or texture filenames like `textures/entity/cat/black.png`).
  - Matches against `BuiltInRegistries.ITEM`, `BuiltInRegistries.ENTITY_TYPE` (yielding spawn eggs or heads), or `biome.*` translations.
  - Formats texture paths or arbitrary keys into clean Title Case labels.
  - Caches results in a static map for instant subsequent lookups.
- **`HudPinManager`**:
  - Manages pinned advancement IDs with a maximum limit of `MAX_PINNED = 3`.
  - Solves the multi-world bug via `getCurrentWorldKey()`:
    - Singleplayer: `"local_" + worldPath.getFileName()` (unique folder name of save file).
    - Dedicated Server: `"server_" + serverIp.replace(':', '_')`.
    - Fallback: dimension key or `"default"`.
  - Automatically loads and persists changes to `<minecraft_root>/config/advancement_progress_pins.json` using pretty-printed Gson.
- **`InspectorPanel`**:
  - A modal panel docked inside the right side of the expanded advancements window.
  - Opened via **Left-Click** on any composite advancement node (`requirements().size() > 1`).
  - Renders title, icon, progress counter, header mini-bar, `✕` close button, `★` pin button, and a toggleable "Hide completed" button.
  - Employs OpenGL scissor clipping (`graphics.enableScissor`) and mouse-wheel scrolling (`mouseScrolled`).
  - Sorts criteria with incomplete tasks at the top so players immediately see what tasks remain.
  - Prevents background node clicks and tooltips from bleeding through (`isMouseOver`).
- **`PinnedAdvancementsHud`**:
  - Implements Fabric's `HudElement`. Rendered in `extractRenderState`.
  - Hidden when inside GUI screens (except `ChatScreen`).
  - Anchored to the top-right corner (`screenWidth - cardWidth - 4`).
  - **Dynamic Card Sizing:**
    - Composite advancements: fixed 22px height with micro progress bar (`fill(..., 0xFF2ECC71)`).
    - Simple advancements: wraps description text; 22px height if 1 line, automatically 31px height if 2 lines. Icon is centered vertically (`y + (cardHeight - 16) / 2`).
    - Completed cards display a bright green `✔` prefix and green outline (`0x882ECC71`).

### 3.3 com.dmystery.mixin
- **`AdvancementsScreenMixin`**:
  - Overrides window rendering with a responsive 9-slice texture frame (`advancementProgress$renderWindowFrame`).
  - Suppresses the vanilla `"Advancements"` title from `HeaderAndFooterLayout` to prevent text peeking behind the top progress bar.
  - Injects the Top Bar:
    - Left side: Overall progress bar (`graphics.fillGradient(...)` green) with centered text and hover tooltip.
    - Right side: Pinned advancement chips (icon, shortened title, close button `✕`), plus a `✕ Все` button when multiple are pinned. Left-click jumps to tab; Right-click or close button unpins.
  - Injects mini progress indicators into each tab header (`type == ABOVE` or `type == BELOW`).
  - Handles mouse clicks:
    - **RMB / MMB on advancement node:** Toggles HUD pin with audio feedback and action bar message (`player.sendOverlayMessage(...)`).
    - **LMB on composite node:** Opens `InspectorPanel`.
    - **LMB on outside area or ESC key:** Closes `InspectorPanel`.
- **`AdvancementTabMixin`**:
  - Applies `0.65f` zoom scaling to the advancement tree canvas.
  - Scissor-clips the viewport cleanly to `inW` × `inH`.
  - Scales background texture tiling offset (`(sX * scale) % 16`) to eliminate scrolling jitter.
  - Scales scroll bounds and clamps scrolling to tree dimensions (`effectiveInW = inW / scale`).
  - Fixes hover hit-testing in `tick()` by mapping `treeMouseX = (int) Math.round(mouseX / scale)`.
  - Adjusts tooltip origin in `extractTooltips()`: computes `adjustedSX = (int) Math.round((sX + nodeX) * scale) - nodeX` so 1:1 crisp tooltips align accurately with the scaled node icon.
- **`AdvancementWidgetMixin`**:
  - Overrides `DisplayInfo.isHidden()` in rendering and mouse-over to display and allow hovering unearned hidden advancements.
  - Renders a glowing golden outline (`graphics.outline(..., 0xFFFFD700)`) and gold star `★` on pinned nodes.
  - **Tooltip Hint Redirection:** Redirects `getfield description` in `extractHover` to append keybind hints (separated by a blank line, formatted concisely as `[LMB] Inspect  •  [RMB] Pin` with gold keys and gray actions) directly into the native tooltip layout box. This guarantees zero visual overlap or collision with advancement descriptions.
- **`AdvancementTabTypeMixin`**:
  - Dynamically recalculates `getX` and `getY` for `AdvancementTabType.RIGHT` and `AdvancementTabType.BELOW` to attach tabs to the outer edges of the enlarged window.
- **`ClientAdvancementsMixin`**:
  - Injects into `update(ClientboundUpdateAdvancementsPacket)` to trigger data loader verification and mark `AdvancementCache` dirty.
- **Accessors (`AdvancementTabAccessor`, `AdvancementWidgetAccessor`, `ClientAdvancementsAccessor`)**:
  - Clean SpongePowered accessor interfaces to read private fields (`widgets`, `hovered`, `scrollX`, `scrollY`, `progress`, `advancementNode`, `display`, `icon`).

---

## 4. Keybindings & User Interaction Matrix

| Context | Action | Key / Input | Result |
|---|---|---|---|
| **Advancements Tree** | Composite Node (multi-criteria) | **LMB** (Left-Click) | Opens detailed Criteria Inspector Panel |
| **Advancements Tree** | Any Node | **RMB** (Right-Click) or **MMB** (Middle-Click) | Toggles HUD Pin (max 3) with sound & action bar feedback |
| **Advancements Tree** | Background | **Drag LMB** | Smooth canvas panning (zoom-aware) |
| **Advancements Tree** | Any Node | **Hover** | Displays native tooltip with description + embedded action hints at bottom |
| **Top Pinned Chips** | Chip Body | **LMB** | Automatically switches to tab containing that advancement |
| **Top Pinned Chips** | Chip Body | **RMB** | Unpins the advancement |
| **Top Pinned Chips** | `✕` Button on Chip | **LMB** | Unpins the advancement |
| **Top Pinned Chips** | `✕ Все` Button | **LMB** | Clears all pinned advancements |
| **Criteria Inspector** | `✕` Button or Outside | **LMB** / **ESC** | Closes Inspector Panel |
| **Criteria Inspector** | `★` Button | **LMB** | Toggles HUD Pin for currently inspected advancement |
| **Criteria Inspector** | "Hide completed" Button | **LMB** | Filters out completed criteria |
| **Criteria Inspector** | List Area | **Mouse Scroll** | Scrolls through criteria list |
| **Advancements Screen** | `⚙` (Gear Button, 20×20 px) | **LMB** | Opens Advancement Progress Settings screen |
| **Global / In-Game** | Open Settings Hotkey (`key.advancement_progress.open_settings`) | Custom Key (Default: Unassigned) | Instantly opens Advancement Progress Settings screen |
| **In-Game HUD** | Top-right Cards | Active Gameplay / Chat | Shows real-time progress / tasks for pinned achievements |

---

## 5. Critical Nuances, Pitfalls & 26.2 Gotchas

Future developers and AI assistants modifying this codebase **must** heed the following technical rules:

### 5.1 NEVER Call `ClientAdvancementManager.setListener()`
Vanilla `AdvancementsScreen` implements `ClientAdvancements.Listener` and calls `setListener(this)` in `init()`, which triggers initial tab building via `onAddAdvancementRoot()`.
- **Pitfall:** If a custom class or helper calls `clientAdvancements.setListener(customListener)`, it overrides vanilla's listener! Vanilla `AdvancementsScreen` will stop receiving root events, leaving `this.tabs` empty and displaying `"There doesn't seem to be anything here"`.
- **Correct Solution:** Always observe advancement events via Mixin injections into `AdvancementsScreen` (`onAddAdvancementRoot`, `onUpdateAdvancementProgress`, `onAdvancementsCleared`) and `ClientAdvancements.update()`.

### 5.2 Mixin Accessor Interfaces Must NOT Have `default` Methods
In SpongePowered Mixin, any interface annotated with `@Mixin` that declares an `@Accessor` must **never** contain non-accessor default methods:
- **Pitfall:** Adding `default boolean isPinned() { ... }` inside `AdvancementWidgetAccessor` causes Mixin to classify the target class as an interface, failing classloading with `InvalidMixinException`.
- **Correct Solution:** Keep accessor interfaces 100% pure (only `@Accessor` methods). Place utility methods in companion classes like `HudPinManager`.

### 5.3 Minecraft 26.2 Mojang Mappings Changes
Minecraft 26.2 introduced several API adjustments from older versions:
- **Resource identifiers:** Use `ResourceKey.identifier()` instead of legacy `.location()`.
- **Action bar messages:** Use `player.sendOverlayMessage(Component)` instead of legacy `player.displayClientMessage(..., true)`.
- **Matrix stacks:** `GuiGraphicsExtractor.pose()` returns JOML `Matrix3x2fStack` (2D affine transformations; `translate(float, float)`, `scale(float, float)`).
- **Singleplayer world directory:** Access via `MinecraftServer.getWorldPath(LevelResource.ROOT).getFileName().toString()`.
- **HUD rendering pipeline:** Fabric uses `HudElementRegistry.addLast(Identifier, HudElement)` and `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.

### 5.4 Advancement Requirements: AND of OR Groups
Minecraft advancement requirements are structured as `AdvancementRequirements` (a list of lists of strings: `List<List<String>>`):
- `adv.requirements().size()` represents the number of requirement **groups** (an AND of groups).
- Within each group, completing **any one** criterion satisfies the group (an OR within the group).
- **The "Suit Up" / "Cover Me With Diamonds" Trap:** For "Suit Up", the criteria are `helmet`, `chestplate`, `leggings`, `boots`. However, they are in a single group of size 1: `requirements().size() == 1`.
  - If code treats `requirements().names().size()` as the total requirements, it will incorrectly require 4 pieces and show `1/4` forever even after completion!
  - **Rule:** An advancement is **composite** if and only if `adv.requirements().size() > 1`.
  - In `PinnedAdvancementsHud` and `InspectorPanel`, always clamp `doneCount = Math.min(totalCriteria, doneCount)`.

### 5.5 Zoom Math & Tooltip Alignment
When applying zoom scale (`scale = 0.65f`) via `graphics.pose().scale(scale, scale)`:
- Canvas inside dimensions: `inW = AdvancementScreenLayout.getInsideWidth()`, `inH = AdvancementScreenLayout.getInsideHeight()`.
- Effective virtual canvas size: `effectiveInW = inW / scale`, `effectiveInH = inH / scale`.
- Mouse transformation for node hit-testing (`tick()` and `findWidgetAt()`):
  `treeMouseX = (int) Math.round(insideX / scale)`
- **Tooltip Resolution Preservation:** Tooltips must remain sharp and readable at 1:1 screen resolution. Do not scale down the tooltip matrix! Instead, adjust the anchor origin to match the visual position of the scaled icon:
  ```java
  int adjustedSX = (int) Math.round((sX + hovered.getX()) * scale) - hovered.getX();
  int adjustedSY = (int) Math.round((sY + hovered.getY()) * scale) - hovered.getY();
  hovered.extractHover(graphics, adjustedSX, adjustedSY, fade, x, y);
  ```

### 5.6 Clean Tooltip Hint Injection
Do not render floating secondary tooltips (such as `graphics.setTooltipForNextFrame(hint, mouseX, mouseY + 15)`). Secondary tooltips collide with and obscure the main advancement description.
- Instead, `AdvancementWidgetMixin` redirects the `description` field in `AdvancementWidget.extractHover` to append hint lines directly at the bottom of the native box.

### 5.7 Screen Navigation & KeyMapping in Minecraft 26.2
- **KeyMapping Categories:** In 26.2, `KeyMapping.Category.register(Identifier)` registers custom keybinding categories dynamically. The translation key format is `Identifier.toLanguageKey("key.category")` (e.g. `key.category.advancement-progress.key_category`).
- **Unbound Hotkeys:** Use `InputConstants.Type.KEYSYM` and `InputConstants.UNKNOWN.getValue()` (which evaluates to `-1`).
- **Screen Transitions:** `Minecraft.setScreen(Screen)` was replaced in 26.2 by `Minecraft.setScreenAndShow(Screen)`.
- **Current Screen Retrieval:** `Minecraft.screen` is private in 26.2; retrieve the active screen via `client.gui.screen()`.

### 5.8 Sub-Screen Lifecycle & Preserving AdvancementsScreen
Vanilla `AdvancementsScreen` was never designed to open child screens:
- **Pitfall:** Opening any sub-screen triggers `AdvancementsScreen.removed()`, which calls `advancements.setListener(null)` and sends `ServerboundSeenAdvancementsPacket.closedScreen()`.
- Returning to the existing `AdvancementsScreen` instance via `setScreenAndShow(parent)` leaves `listener == null` because `Screen.init(w, h)` skips `init()` when `initialized == true`. Consequently, tab switching and progress packet handling stop functioning until the screen is closed and reopened with 'L'.
- **Correct Solution:** When closing sub-screens (`AdvancementProgressConfigScreen.onClose()`), construct a fresh `new AdvancementsScreen(adv, lastScreen)` and restore the active tab (`adv.setSelectedTab(currentTabHolder, true)`). Additionally, directly assign `this.selectedTab = tab` inside `AdvancementsScreenMixin.mouseClicked`.

---

## 6. Build & Verification Commands

```powershell
# Clean compile check
.\gradlew compileJava

# Build mod JAR
.\gradlew build

# Launch client with dev environment
.\gradlew runClient
```

Built mod JAR output: `build/libs/advancement-progress-<version>.jar`.
Config location: `<minecraft_run_directory>/config/advancement_progress_pins.json`.
