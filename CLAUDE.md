# CLAUDE.md

This file guides Claude Code (claude.ai/code) when working with this repository.

## Quick Start

**What this is:** kai's master tool — a Yu-Gi-Oh! deck building and tournament preparation tool.

This repository holds **two** implementations:

| | Location | Status |
|---|---|---|
| **Legacy web tool** | `kai master tool.html` | Feature-complete, maintenance only |
| **Cross-platform app** | `app/` | In progress — the intended future |

**Legacy web tool:** open `kai master tool.html` in a browser, hard-refresh after edits (Ctrl+Shift+R). Everything from "Project Overview" onwards in this file describes that version.

**Cross-platform app:** Kotlin Multiplatform + Compose Multiplatform, targeting Android (landscape tablets), desktop (macOS/Windows/Linux) and iOS from one codebase. See `app/README.md` for its architecture and build instructions.

**Key files:**
- `app/` - The Kotlin Multiplatform rebuild
- `kai master tool.html` - The legacy single-file application (~36,000 lines)
- `CLAUDE.md` - This file
- `3dchibi.glb` - 3D model for animations
- `*.ydkx` - Sample deck files

---

## Multi-Team Trigger

When the user starts a prompt with **"mt"** or **"mt:"**, they want a multi-agent team:

1. Strip the "mt" prefix to get the actual task
2. Create a team using `TeamCreate`
3. Break into 2-4 subtasks with `TaskCreate`
4. Spawn 2-3 general-purpose agent teammates
5. Assign tasks and coordinate work
6. Report results back to the user

---

## Project Overview

**Key Features:**
- Deck builder (main/side/extra deck)
- YDK/YDKX file import/export
- Card database with search, filters, autocomplete
- Siding pattern management for tournaments
- Shootout mode (matchup testing)
- Sandbox board simulator (2D/3D with Three.js)
- PDF export
- IndexedDB persistence

**Tech Stack:** Plain HTML + Tailwind CSS (CDN) + vanilla JS. No framework, no build step.

---

## Code Architecture

### Module Pattern

All JavaScript uses the **Revealing Module Pattern** (IIFEs):

```javascript
const ModuleName = (function() {
    'use strict';

    // Private variables/functions

    return {
        // Public API
        publicMethod() { },
        init() { }
    };
})();
```

### Core Modules (in order of appearance)

| Module | Lines | Purpose |
|---------|-------|---------|
| Logger | ~70-163 | Debug logging with levels |
| KeyboardManager | ~173-259 | Global keyboard shortcuts |
| NotificationManager | ~268-487 | Toast notifications |
| UndoManager | ~496-599 | Command pattern for undo/redo |
| Command | ~609-622 | Base class for undoable actions |
| SmartSearch | ~632-848 | Fuzzy search with Levenshtein distance |
| DeckStatistics | ~857-1041 | Deck analysis & recommendations |
| ThemeManager | ~1050-1191 | Dark/light/cyber/classic themes |
| FilterManager | ~1200-1795 | Quick filter panel (Type, Attribute, Race, Level, ATK/DEF) |
| BulkOperations | ~1804-1976 | Multi-card operations |
| LongPressManager | ~1982-2264 | Long-press gesture handling |
| UITuner | ~2271-2598 | Temporary UI fine-tuning panel |
| BoardStateManager | ~22516-22772 | Sandbox state management |
| SandboxCommandFactory | ~22775-22919 | Command factory for sandbox |
| TurnManager | ~22920-23060 | Turn tracking in sandbox |
| DragDropCoordinator | ~23061-23353 | D&D handling for sandbox |
| BoardRenderer | ~23363-23596 | 2D/3D board rendering |
| ZoneViewManager | ~23597-23862 | Zone visibility management |
| ContextMenuManager | ~23865-24083 | Right-click context menus |
| CardDetailPanel | ~24084-24181 | Card preview/inspector panel |
| DeckSearchModal | ~24182-28289 | Card search modal |
| StorageManager | ~28320-29103 | IndexedDB wrapper |

### Other Key Components

| Component | Lines | Purpose |
|-----------|-------|---------|
| main() | ~3121-3299 | App initialization sequence |
| initializeUI() | ~2940-3105 | Cache DOM element references |
| attachEventListeners() | ~3300-5500+ | All UI event bindings |
| ShootoutManager | ~21644-22508 | Tournament/match mode logic |

---

## Global State

Key global variables (defined ~lines 50-2809):

```javascript
// Core data
let cardDatabase = [];           // All cards from API
let originalCardDatabase = [];    // Backup for filtering
let easterEggCardPool = [];     // Custom card pool

// Deck state
let currentDeck = { main: [], side: [], extra: [], sidingPatterns: {} };
let currentDeckId = null;        // 'unsaved' or offline deck ID
let currentDeckSource = 'offline'; // 'offline' | 'unsaved'
let isDeckDirty = false;

// YDK files
let ydkFileHandles = new Map();   // FileSystemDirectoryHandle map
let ydkFiles = [];               // Array of loaded YDK files
let defaultDeckName = null;       // Auto-load deck name
let ydkFolder = null;            // Folder handle for sync

// UI state
let databaseVisible = true;
let extraDeckVisible = true;
let sideDeckVisible = true;
let editMode = false;
let cardDatabaseView = 'grid';    // 'list' or 'grid'
let cardDatabaseSource = 'db';   // 'db' or 'all'
let showExtraDeckCards = true;

// Siding
let sidingState = { out: {}, in: {} };
let activeEditingPattern = null;
let universalSidingPatterns = {}; // Deprecated format

// Misc
const UI = { /* DOM element references */ };
```

---

## Data Structures

### Card Object (from the YGOPRODeck API)

```javascript
{
    id: "89812483",           // Konami card ID (string)
    name: "Ash Blossom",
    type: "Effect Monster",
    attribute: "FIRE",
    level: 3,
    atk: 0,
    def: 1800,
    race: "Zombie",
    // ... other properties
}
```

### Deck Structure

```javascript
{
    main: ["89812483", "14558127", ...],  // Card IDs (strings)
    side: [...],
    extra: [...],
    sidingPatterns: {
        "pattern-name": {
            goingFirst: { in: [...], out: [...] },
            goingSecond: { in: [...], out: [...] }
        }
    }
}
```

### YDK File Format

Standard YDK (plain text):
```
#created by ...
#main
89812483
14558127
#extra
...
!side
...
```

YDKX is JSON with multiple configurations and metadata.

---

## Initialization Sequence

The `main()` function (~line 3121) initializes in this order:

1. Show loading overlay
2. `ThemeManager.init()`
3. `initializeUI()` - Cache all DOM references
4. Load easter egg card pool
5. `FilterManager.init()`
6. `createFloatingHelpButton()`
7. `attachEventListeners()`
8. `StorageManager.init()` - IndexedDB setup
9. Initialize resize handles
10. `UITuner.init()`
11. `showView('deckBuilderView')` + hide loading
12. `loadCardDatabase()` - Fetch from API
13. Load offline data (decks, YDK files, siding patterns)
14. Auto-load default deck if set

---

## Common Development Tasks

### Adding a New Module

```javascript
const NewModule = (function() {
    'use strict';

    function privateHelper() { }

    return {
        publicMethod() { },
        init() { }
    };
})();
```

Place before `main()`, call `NewModule.init()` in `main()`.

### Adding a Keyboard Shortcut

```javascript
KeyboardManager.register('ctrl+shift+n',
    () => doSomething(),
    'Description shown in help'
);
```

### Undoable Operations

```javascript
UndoManager.execute(Command.create({
    description: 'Add card to deck',
    execute: () => { /* do it */ },
    undo: () => { /* revert */ }
}));
```

### Notifications

```javascript
NotificationManager.success('Deck saved!');
NotificationManager.error('Failed to load', 'Error');
NotificationManager.warning('Deck is below 40 cards');
NotificationManager.info('Info message');
```

### Logging

```javascript
Logger.debug('Detailed info', { data });
Logger.info('General info');
Logger.warn('Warning message');
Logger.error('Error occurred', error);
```

---

## Deck Builder UI

The deck builder has three resizable sections:
- **Main Deck** (`UI.mainDeckSection`)
- **Side Deck** (`UI.sideDeckSection`)
- **Extra Deck** (`UI.extraDeckSection`)

### Resize Functions

- `initializeDeckResizeHandles()` - Initialize both handles
- `initializeDeckResizeHandle(handleId)` - Core resize logic
- `applyDeckSectionProportions()` - Apply proportional sizing
- `saveDeckResizeConfiguration()` - Persist to localStorage
- `loadDeckResizeConfiguration()` - Restore on load

### Resize Behavior

- `mainSide` handle: Resizes main, side+extra scale proportionally
- `sideExtra` handle: Resizes extra, main+side scale proportionally
- Uses `requestAnimationFrame` for 60fps
- Minimum width: 100px per section

### Deck Visibility Toggles

- `toggleDatabase()` - Shows/hides card database
- `toggleExtraDeck()` / `toggleSideDeck()` - Individual sections
- Always preserve width ratios before layout changes

---

## Key Functions by Category

### Card Operations

| Function | Purpose |
|-----------|---------|
| `addCardToDeck(cardId, section)` | Add card to specified deck section |
| `removeCardFromDeck(cardId, section)` | Remove card from section |
| `renderCardDatabase()` | Render card list |
| `createCardElement(card)` | Create DOM element for card |
| `renderCurrentDeck()` | Render all deck sections |

### Deck Management

| Function | Purpose |
|-----------|---------|
| `saveDeck()` | Save current deck |
| `loadDeck(deckId)` | Load a saved deck |
| `exportToYDK()` | Export to YDK format |
| `importFromYDK()` | Import YDK file |
| `newDeck()` | Create fresh deck |

### Storage

Use **StorageManager** for all IndexedDB operations:

| Method | Purpose |
|--------|---------|
| `getYDKFiles()` / `saveYDKFile()` | YDK persistence |
| `getOfflineDecks()` / `saveOfflineDeck()` | Deck storage |
| `getSidingPatterns()` / `saveSidingPattern()` | Siding patterns |

---

## Dependencies (CDN)

- **Tailwind CSS** - Styling
- **jsPDF** - PDF generation
- **html2canvas** - Screenshots for PDF
- **Three.js + GLTFLoader** - 3D rendering
- **Google Fonts** - Inter, JetBrains Mono

---

## Browser Compatibility

Modern browsers with:
- ES6+ (optional chaining, nullish coalescing)
- IndexedDB
- FileSystem Access API (optional, for YDK folder sync)

---

## Development Workflow

1. Edit `kai master tool.html` directly
2. Hard refresh browser (Ctrl+Shift+R)
3. Check console for errors (F12)
4. For IndexedDB issues: DevTools > Application > IndexedDB > `ygo-evaluator-default`

---

## Debugging

### Console Commands

```javascript
// Check deck section widths
console.log({
  main: UI.mainDeckSection?.offsetWidth,
  side: UI.sideDeckSection?.offsetWidth,
  extra: UI.extraDeckSection?.offsetWidth
});

// Clear resize state
localStorage.removeItem('deckResizeConfig');
location.reload();

// Enable debug logging
Logger.setLevel('debug');

// Export log history
Logger.export();
```

### Common Issues

**Deck not resizing:** Clear `deckResizeConfig` from localStorage and refresh.

**Cards missing after resize:** Check `--main-deck-columns` CSS variable is updated.

**IndexedDB problems:** Delete database in DevTools and refresh.

---

## Important Notes

- The app is intentionally single-file for portability
- Card data fetched from `db.ygoprodeck.com/api/v7` on load
- All user data persists locally (IndexedDB + localStorage)
- No server, no authentication, fully client-side
- Line numbers are approximate; search by function/module name

---

## Frontend Aesthetics Guidelines

When creating or modifying UI components, avoid generic "AI slop" aesthetics. Focus on:

**Typography:** Choose distinctive, beautiful fonts. Avoid overused choices like Inter, Roboto, Arial, and system fonts. Even fonts like Space Grotesk have become AI clichés. Make unexpected choices.

**Color & Theme:** Commit to cohesive aesthetics using CSS variables. Dominant colors with sharp accents outperform timid, evenly-distributed palettes. Draw from IDE themes, gaming aesthetics, and cultural influences for inspiration.

**Motion:** Use animations deliberately for effects and micro-interactions. Prioritize CSS-only solutions. Focus on high-impact moments like staggered page load reveals with animation-delay rather than scattered micro-interactions.

**Backgrounds:** Create atmosphere and depth with CSS gradients, geometric patterns, or contextual effects that match the overall aesthetic.

**Avoid:**
- Overused font families (Inter, Roboto, Arial, Space Grotesk)
- Clichéd purple gradients on white backgrounds
- Predictable layouts and cookie-cutter patterns
- Generic designs that lack context-specific character

Interpret creatively and make unexpected choices that feel genuinely designed for this Yu-Gi-Oh! tournament tool context. Vary between light and dark themes, different fonts, different aesthetics. Think outside the distribution!
