# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference - Section Markers

The HTML file contains detailed section markers for navigation. When editing, use these markers to quickly jump to relevant code:

| Section | Lines | Description |
|---------|-------|-------------|
| **HTML HEAD & METADATA** | 1-17 | CDN deps, meta tags |
| **JAVASCRIPT MODULES** | 17-19610 | All JS using Revealing Module Pattern |
| ├─ Logger | 28-123 | Debug logging |
| ├─ KeyboardManager | 128-214 | Keyboard shortcuts |
| ├─ NotificationManager | 219-438 | Toast notifications |
| ├─ UndoManager | 443-576 | Undo/redo commands |
| ├─ Command | 598-613 | Command base class |
| ├─ SmartSearch | 615-831 | Fuzzy card search |
| ├─ DeckStatistics | 834-1019 | Deck analysis |
| ├─ ThemeManager | 1021-1163 | Theme management |
| ├─ BulkOperations | 1165-1288 | Multi-card operations |
| **GLOBAL STATE VARIABLES** | 1289-1470 | All global state |
| **CORE FUNCTIONS** | 1669-1750 | init(), initializeUI() |
| **EVENT LISTENERS** | 4706-6089 | All UI event bindings |
| **CARD DATABASE FUNCTIONS** | ~7150-8300 | Search, load, autocomplete |
| **DECK MANAGEMENT** | ~10056-11000 | Save, load, export, import |
| **ShootoutManager** | 18921-19700+ | Tournament mode |
| **BoardStateManager** | 19611-20450 | Sandbox state |
| **BoardRenderer** | 20451-25023 | 2D/3D rendering |
| **BODY & StorageManager** | 25173-25845 | IndexedDB wrapper |

### Section Marker Maintenance

When making significant changes to the codebase:

1. **After adding a new module:**
   - Add its marker comment in the MODULES section header
   - Include line range, purpose, and key methods

2. **After adding new global variables:**
   - Update the GLOBAL STATE section marker with new variable names

3. **After modifying initialization:**
   - Update the init sequence in the CORE FUNCTIONS section marker

4. **After line shifts >100 lines:**
   - Update line numbers in affected section markers
   - Use `grep -n "=====" kaihari*.html` to find all markers

5. **Marker Format:**
   ```javascript
   // ============================================================================
   // MODULE: ModuleName
   // Lines: XXX-YYY
   // Purpose: Brief description
   // Key Methods: method1(), method2()
   // ============================================================================
   ```

## Project Overview

This is a Yu-Gi-Oh! deck building and tournament preparation tool ("kaihari's master tool"). It is a **single-file HTML application** (`kaihari master tool.html`) containing all JavaScript modules, styles, and UI in one self-contained file. The application runs entirely in the browser with no backend.

**Key Features:**
- Deck builder with main/side/extra deck management
- YDK/YDKX file import/export (Yu-Gi-Oh! deck formats)
- Card database with search, filters, and autocomplete
- Siding pattern management for tournament play
- Shootout mode for testing matchups
- Sandbox board simulator (2D/3D with Three.js)
- PDF export for siding patterns
- IndexedDB storage for offline deck/file persistence

## Running the Application

Simply open `kaihari master tool.html` in a modern web browser. No build process, dependencies, or server required.

For development during editing:
1. Edit the HTML file directly
2. Refresh the browser to see changes (Ctrl+R / F5)
3. Check browser console for logs (F12)

## Code Architecture

### Module Pattern

All JavaScript uses the **Revealing Module Pattern** with IIFEs:
```javascript
const ModuleName = (function() {
    'use strict';

    // Private variables and functions

    return {
        // Public API
    };
})();
```

Key modules (declared as global `const`):
- **Logger** - Debug logging with levels, history export
- **KeyboardManager** - Global keyboard shortcut registration
- **NotificationManager** - Toast notifications with history
- **UndoManager** - Command pattern for undo/redo operations
- **Command** - Base class for undoable actions
- **SmartSearch** - Fuzzy card search with Levenshtein distance, synergy detection
- **DeckStatistics** - Card type/level/attribute distribution, recommendations
- **ThemeManager** - Dark/light/cyber/classic themes
- **BulkOperations** - Multi-card add/remove/move operations
- **BoardStateManager** - Sandbox board state management
- **BoardRenderer** - 2D/3D board rendering with Three.js
- **StorageManager** - IndexedDB wrapper for YDK files, decks, siding patterns
- **DuelSimulator** - Duel simulation logic

### Global State

Key global variables (lines ~1289-1470):
```javascript
let cardDatabase = [];           // All cards from API
let currentDeck = { main: [], side: [], extra: [], sidingPatterns: {} };
let currentDeckId = null;        // 'unsaved' or offline deck ID
let currentDeckSource = 'offline'; // 'offline' | 'unsaved'
let isDeckDirty = false;
let ydkFiles = [];               // YDK files from IndexedDB
let offlineDecks = [];           // Saved decks
let UI = { /* element references */ };
```

### Card Data Structure

Cards from the API (masterduelmeta.com):
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

### Deck Data Structure

```javascript
{
    main: ["89812483", "14558127", ...],  // Array of card IDs (strings)
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

Standard YDK format (plain text):
```
#created by ...
#main
89812483
14558127
...
#extra
...
!side
...
```

YDKX format (JSON) supports multiple configurations and metadata.

### Event Delegation

The app uses a single `attachEventListeners()` function (line ~4835) that binds all UI events. Due to the single-file nature, DOM elements are cached in the `UI` object via `initializeUI()` which finds elements by ID.

### Storage Layer

**StorageManager** (line ~25024) wraps IndexedDB for:
- `getYDKFiles()` / `saveYDKFile()` - YDK file persistence
- `getOfflineDecks()` / `saveOfflineDeck()` - Deck storage
- `getSidingPatterns()` / `saveSidingPattern()` - Siding patterns
- `loadYDKFiles()` - Bulk load on startup

### Deck Builder UI

The deck builder has three resizable sections:
- **Main Deck** (`UI.mainDeckSection`) - Primary card list
- **Side Deck** (`UI.sideDeckSection`) - Tournament siding cards
- **Extra Deck** (`UI.extraDeckSection`) - Fusion/Synchro/Xyz/Link monsters

**Resize Handle Functions:**
- `initializeDeckResizeHandles()` (line ~4484) - Initializes both resize handles
- `initializeDeckResizeHandle()` (line ~4557) - Core resize logic for a single handle
- `applyDeckSectionProportions()` (line ~3458) - Applies proportional sizing to all sections
- `saveDeckResizeConfiguration()` (line ~4720) - Persists sizes to localStorage
- `loadDeckResizeConfiguration()` (line ~4746) - Restores sizes on page load

**Resize Behavior Pattern:**
- `mainSide` handle: Resizes main deck, while side+extra scale proportionally
- `sideExtra` handle: Resizes extra deck, while main+side scale proportionally
- Uses `requestAnimationFrame` for smooth 60fps updates
- Minimum width: 100px per section

**Key UI Elements (initialized in `initializeUI()`):**
```javascript
UI.mainDeckSection      // line ~1622
UI.sideDeckSection      // line ~1622
UI.extraDeckSection     // line ~1620
UI.deckGridContainer    // line ~1620
UI.cardDatabaseSection  // line ~1584
```

### UI State & Toggles

**Visibility Toggle Functions:**
- `toggleDatabase()` (line ~3376) - Shows/hides card database, preserves deck proportions
- `toggleDatabaseSource()` (line ~8201) - Switches between card sources
- `toggleExtraDeck()` / `toggleSideDeck()` - Show/hide individual deck sections

**State Variables:**
```javascript
let databaseVisible = true;   // Card database visibility state
let extraDeckVisible = true;  // Extra deck section visibility
let sideDeckVisible = true;   // Side deck section visibility
```

**Pattern for preserving proportions during UI changes:**
```javascript
// Before any layout change, capture current ratios
const currentMainWidth = UI.mainDeckSection.offsetWidth;
const currentSideWidth = UI.sideDeckSection.offsetWidth;
const currentExtraWidth = UI.extraDeckSection.offsetWidth;
const totalWidth = currentMainWidth + currentSideWidth + currentExtraWidth;
const mainRatio = currentMainWidth / totalWidth;
// ... etc

// After layout change, reapply ratios
applyDeckSectionProportions(mainRatio, sideRatio, extraRatio);
```

### Initialization Sequence (`main()` function, line ~1656):

1. Show loading overlay
2. `ThemeManager.init()`
3. `initializeUI()` - Cache DOM element references
4. `loadEasterEggCardPool()`
5. `createFloatingHelpButton()`
6. `attachEventListeners()`
7. `StorageManager.init()` - IndexedDB setup
8. Initialize resize handles
9. Load card database from API
10. `StorageManager.loadYDKFiles()` - Load stored YDK files
11. Auto-load default deck if set

## Common Development Tasks

### Adding a New Module

Use the revealing module pattern at the module level (before `main()`):
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

Then call `NewModule.init()` in `main()`.

### Adding a Keyboard Shortcut

In `attachEventListeners()`:
```javascript
KeyboardManager.register('ctrl+shift+n',
    () => doSomething(),
    'Description shown in help'
);
```

### Modifying Deck Resize Behavior

When modifying resize logic:
1. All three sections must resize together to maintain layout
2. Use proportional scaling (ratios) rather than fixed pixel changes
3. Call `saveDeckResizeConfiguration()` after any manual resize
4. Update `applyDeckSectionProportions()` if changing how ratios are calculated

### Adding UI Toggles That Affect Layout

For any toggle that shows/hides major UI sections:
1. Capture current width ratios BEFORE the DOM change
2. Make the visibility change
3. Use `requestAnimationFrame` or `setTimeout` to wait for DOM reflow
4. Call `applyDeckSectionProportions()` with saved ratios
5. Save the new configuration

### Modifying Card Database Rendering

Key functions:
- `renderCardDatabase()` - Renders the card list
- `createCardElement()` - Creates individual card DOM element
- `renderCurrentDeck()` - Renders main/side/extra decks

### Working with IndexedDB

Always use `StorageManager`, never direct IndexedDB calls. The module handles:
- Database opening/migration
- Transaction management
- Error handling

### Testing Changes

1. Edit the HTML file
2. Hard refresh browser (Ctrl+Shift+R)
3. Check browser console for errors
4. For storage issues: Application tab > IndexedDB > `ygo-evaluator-default`

## Debugging Guide

### Common Issues

**Deck sections not resizing properly:**
1. Check browser console for JavaScript errors
2. Inspect `UI.deckGridContainer` width in DevTools
3. Check for `flex-shrink: 0` or `flex-grow: 0` inline styles preventing resize
4. Clear localStorage: `localStorage.removeItem('deckResizeConfig')`
5. Hard refresh (Ctrl+Shift+R) to clear cached styles

**Empty space appearing after toggling database:**
- Caused by saved resize config with outdated widths
- Fixed by: `localStorage.removeItem('deckResizeConfig')`

**Cards not displaying correctly after resize:**
- Check `autoAdjustCardScalesForManualResize()` is being called
- Verify CSS custom properties `--main-deck-columns`, `--side-deck-columns` are updated

### Useful Console Commands

```javascript
// Log current deck section widths
console.log({
  main: UI.mainDeckSection?.offsetWidth,
  side: UI.sideDeckSection?.offsetWidth,
  extra: UI.extraDeckSection?.offsetWidth
});

// Clear all resize state
localStorage.removeItem('deckResizeConfig');
location.reload();

// Check saved config
JSON.parse(localStorage.getItem('deckResizeConfig'));

// Enable debug logging
Logger.setLevel('debug');
```

### DevTools Tips

**Inspecting deck layout:**
1. Open DevTools (F12)
2. Select the `#deckGridContainer` element
3. In Computed panel, check `width`, `flex-grow`, `flex-shrink` values
4. Look for inline styles overriding CSS rules

**Breakpoints for resize debugging:**
- Line ~4557: Inside `initializeDeckResizeHandle()` - set breakpoint in `mousemove` handler
- Line ~3376: Inside `toggleDatabase()` - set breakpoint to trace toggle flow
- Line ~3458: Inside `applyDeckSectionProportions()` - set breakpoint to verify ratio calculations

## File Structure

```
kaihari master tool/
├── kaihari master tool.html    # Main application (25,845 lines)
├── 3dchibi.glb                 # 3D model for chibi animation
├── 1000053898.png              # Card image asset
├── lab.ydkx                    # Sample deck file
└── CLAUDE.md                   # This file
```

## Key Dependencies (CDN)

- **Tailwind CSS** - Styling (via CDN)
- **jsPDF** - PDF generation
- **html2canvas** - Screenshots for PDF
- **Three.js + GLTFLoader** - 3D board rendering

## Important Patterns

### Undo/Redo

Use the Command pattern via `UndoManager.execute()`:
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
```

### Logging

```javascript
Logger.debug('Search query', { query: 'ash blossom' });
Logger.info('Deck loaded', { name: 'My Deck' });
Logger.warn('Duplicate card', { cardId: '123' });
Logger.error('API error', error);
```

## Browser Compatibility

Modern browsers with ES6+ and IndexedDB support. Uses:
- Optional chaining (`?.`)
- Nullish coalescing (`??`)
- async/await
- Modules (type="module")
- FileSystem Access API (optional, for YDK folder sync)

## Notes

- The app is intentionally single-file for portability
- Card data fetched from masterduelmeta.com API on load
- All user data persists locally via IndexedDB and localStorage
- No server, no authentication, fully client-side
