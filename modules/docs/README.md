# Kaihari Master Tool - Modules Directory

This directory contains modularized components of the Kaihari Master Tool. Each module encapsulates specific functionality to reduce AI context length and improve code maintainability.

## Architecture

The modularization follows a hybrid approach:
- **Core modules** (JS files) are loaded on startup and provide foundational functionality
- **Feature modules** (HTML files) are loaded on-demand when specific views are accessed
- All modules share global state defined in the main HTML file
- Modules communicate through direct function calls in the shared global scope

## Module Types

### Core Modules (Loaded on Startup)

#### `core/core-state.js`
- **Purpose**: Documentation of global state variables and utility functions
- **Status**: Already defined in main HTML
- **Content**:
  - Firebase instances (app, auth, db, userId)
  - Card database arrays (cardDatabase, originalCardDatabase, easterEggCardPool)
  - Deck management state (deckLists, offlineDecks, currentDeck, etc.)
  - UI visibility flags (editMode, extraDeckVisible, sideDeckVisible, etc.)
  - Utility functions (createOfflineDeckId, encode/decodeDeckSelectorValue, etc.)

#### `core/ui-common.js`
- **Purpose**: Shared UI components and utility functions
- **Status**: Already defined in main HTML
- **Content**:
  - initializeUI() - Initializes all UI element references
  - showView() - Switches between application views
  - showMessage() - Displays message modal
  - showTooltip() - Shows temporary tooltip notifications
  - showConfirmModal() - Displays confirmation dialog

### Feature Modules (Loaded On-Demand)

All feature modules are currently **placeholder files** that document the structure and line numbers of their implementation in the main HTML. The actual functionality remains in the main HTML file to ensure no breaking changes.

#### `features/card-database.html`
- **Purpose**: Card database UI, search, and autocomplete functionality
- **Related Functions**: Lines 3622-4230 in main HTML

#### `features/deck-builder.html`
- **Purpose**: Deck building and rendering UI
- **Related Functions**: Lines 4231-5XXX in main HTML

#### `features/deck-management.html`
- **Purpose**: Deck loading/saving and YDK file management
- **Related Functions**: Lines 6XXX-7XXX in main HTML

#### `features/advanced-search.html`
- **Purpose**: Advanced search modal and filtering
- **Related Functions**: Lines 8XXX-9XXX in main HTML

#### `features/banlist-management.html`
- **Purpose**: Banlist management
- **Related Functions**: Lines 2XXX-3XXX in main HTML

#### `features/simulator.html`
- **Purpose**: Simulation setup and display
- **Related Functions**: Lines 1XXXX-2XXXX in main HTML

#### `features/report-view.html`
- **Purpose**: Report generation and analytics
- **Related Functions**: Lines 2XXXX-2XXXX in main HTML

#### `features/shootout.html`
- **Purpose**: Shootout mode (siding, simulation, report)
- **Related Functions**: Lines 13738-14520, 17126-17503 in main HTML

#### `features/siding-patterns.html`
- **Purpose**: Siding pattern management
- **Related Functions**: Lines 9866-10623 in main HTML

#### `features/ai-training.html`
- **Purpose**: AI training mode
- **Related Functions**: Lines 13138-13649, 17048-17123 in main HTML

### Storage Modules

#### `storage/storage-manager.html`
- **Purpose**: IndexedDB and localStorage management
- **Related Functions**: Lines 7213-8340 in main HTML

#### `storage/storage-manager.js`
- **Purpose**: Storage management utilities
- **Related Functions**: Lines 7213-8340 in main HTML

#### `storage/pdf-manager.html`
- **Purpose**: PDF generation/export
- **Related Functions**: Lines 13650-13737 in main HTML

#### `features/easter-egg.html`
- **Purpose**: Chibi animation and easter egg pool
- **Related Functions**: Lines 10624-10854, 11550-13137 in main HTML

## Module Loading System

The `ModuleLoader` class in the main HTML handles dynamic loading:

```javascript
class ModuleLoader {
    constructor()
    async loadModule(moduleName)
    async loadCoreModules()
    isLoaded(moduleName)
    unloadModule(moduleName)
}
```

### Loading Triggers

Modules are automatically loaded based on the current view:
- **Deck Builder**: Loads card-database, deck-builder, deck-management
- **Simulator**: Loads simulator module
- **Report**: Loads report-view, pdf-manager
- **AI Training**: Loads ai-training module
- **Shootout**: Loads shootout module
- **Siding Builder**: Loads siding-patterns module

## Benefits of Modularization

1. **Dramatically reduced AI context length**: Work on individual modules (~200-500 lines) instead of entire 17,503-line file
2. **Maintainable codebase**: Clear separation of concerns and functionality
3. **Faster development**: Quick iterations on specific features without loading entire codebase
4. **Reusable components**: Modules can be reused or moved independently
5. **Preserved functionality**: No breaking changes - current implementation remains functional

## Important Notes

- All state remains in the main HTML's global scope
- Modules access state via direct global variable references
- No state duplication or synchronization needed
- Module communication uses direct function calls
- The current implementation is a **documentation framework** - actual functionality remains in main HTML

## Directory Structure

```
modules/
├── core/                 # Foundational modules loaded on startup
│   ├── core-state.js    # Global state documentation
│   └── ui-common.js     # Shared UI utilities
├── features/            # Feature-specific modules loaded on-demand
│   ├── advanced-search.html
│   ├── ai-training.html
│   ├── banlist-management.html
│   ├── card-database.html
│   ├── deck-builder.html
│   ├── deck-management.html
│   ├── easter-egg.html
│   ├── report-view.html
│   ├── shootout.html
│   ├── siding-patterns.html
│   └── simulator.html
├── storage/             # Storage and file management
│   ├── pdf-manager.html
│   ├── storage-manager.html
│   └── storage-manager.js
├── docs/                # Documentation files
│   ├── README.md
│   ├── BANLIST-MODULE-MIGRATION-SUMMARY.md
│   └── PDF-MODULE-MIGRATION-SUMMARY.md
└── tests/               # Test files
    └── pdf-manager-test.js
```

## Development Guidelines

When working on a specific module:

1. Load the module file (not the entire main HTML)
2. Reference the line numbers in the module's HTML comments to locate related code in main HTML
3. Make changes to the module's code
4. When ready to integrate:
   - Copy updated code from module into main HTML
   - Test thoroughly
   - Update module documentation

## Testing

To test module loading:

1. Open browser console
2. Navigate to different views (Deck Builder, Simulator, etc.)
3. Observe console logs for module loading messages

