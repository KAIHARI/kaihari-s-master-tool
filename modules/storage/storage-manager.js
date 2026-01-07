// Storage Manager Module
// Handles IndexedDB, localStorage, YDK file management, and storage diagnostics

const StorageManager = (function() {
    'use strict';

    // Private variables
    let ydkDB = null;
    let isLoadingYDK = false;
    let isAutoLoading = false;

    // Modal HTML template
    const modalHTML = `
    <!-- Comprehensive Storage Management Modal -->
    <div id="storageManagementModal" class="fixed inset-0 bg-black/60 z-40 flex items-center justify-center hidden">
        <div class="bg-[var(--color-surface-1)] rounded-lg shadow-xl p-6 w-full max-w-6xl mx-4 flex flex-col max-h-[90vh]">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-xl font-semibold text-white">Storage Management</h3>
                <button id="storageManagementModalClose"
                    class="text-slate-400 hover:text-white text-2xl font-bold">&times;</button>
            </div>

            <!-- Storage Overview -->
            <div class="mb-6 p-4 bg-slate-800 rounded-lg">
                <h4 class="text-lg font-semibold text-white mb-3">Storage Overview</h4>
                <div id="storageOverview" class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div class="text-center">
                        <div class="text-2xl font-bold text-blue-400" id="totalDecksCount">0</div>
                        <div class="text-slate-400">Saved Decks</div>
                    </div>
                    <div class="text-center">
                        <div class="text-2xl font-bold text-green-400" id="totalYDKFilesCount">0</div>
                        <div class="text-slate-400">YDK Files</div>
                    </div>
                    <div class="text-center">
                        <div class="text-2xl font-bold text-purple-400" id="totalCardsCount">0</div>
                        <div class="text-slate-400">Cached Cards</div>
                    </div>
                    <div class="text-center">
                        <div class="text-2xl font-bold text-orange-400" id="storageUsage">0 KB</div>
                        <div class="text-slate-400">Total Usage</div>
                    </div>
                </div>
            </div>

            <!-- Tab Navigation -->
            <div class="flex flex-wrap gap-2 mb-4 border-b border-slate-700">
                <button class="storage-tab-btn active px-4 py-2 text-sm font-medium text-white border-b-2 border-blue-500"
                    data-tab="visual-ui">Visual UI Settings</button>
                <button class="storage-tab-btn px-4 py-2 text-sm font-medium text-slate-400 hover:text-white"
                    data-tab="card-database">Card Database</button>
                <button class="storage-tab-btn px-4 py-2 text-sm font-medium text-slate-400 hover:text-white"
                    data-tab="decks">Deck Management</button>
                <button class="storage-tab-btn px-4 py-2 text-sm font-medium text-slate-400 hover:text-white"
                    data-tab="ydk-files">YDK Files</button>
                <button class="storage-tab-btn px-4 py-2 text-sm font-medium text-slate-400 hover:text-white"
                    data-tab="diagnostics">Diagnostics</button>
            </div>

            <!-- Tab Content -->
            <div class="flex-1 overflow-y-auto">
                <!-- Visual UI Settings Tab -->
                <div id="visual-ui-tab" class="storage-tab-content">
                    <div class="space-y-4">
                        <div class="flex justify-between items-center">
                            <h4 class="text-lg font-semibold text-white">Visual UI Settings</h4>
                            <div class="flex gap-2">
                                <button id="exportAllVisualUIBtn"
                                    class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-3 rounded-md text-sm">Export
                                    All</button>
                                <label for="importAllVisualUIInput"
                                    class="bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-3 rounded-md text-sm cursor-pointer">Import
                                    All</label>
                                <input type="file" id="importAllVisualUIInput" class="hidden" accept=".json">
                            </div>
                        </div>

                        <!-- Card Sizes -->
                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="flex justify-between items-center mb-3">
                                <h5 class="font-semibold text-white">Card Sizes</h5>
                                <div class="flex gap-2">
                                    <button
                                        class="export-category-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="card-sizes">Export</button>
                                    <button
                                        class="reset-category-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="card-sizes">Reset</button>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm">
                                <div>Main Deck: <span class="text-blue-400" id="mainDeckScaleValue">68px</span></div>
                                <div>Side Deck: <span class="text-blue-400" id="sideDeckScaleValue">68px</span></div>
                                <div>Extra Deck: <span class="text-blue-400" id="extraDeckScaleValue">68px</span></div>
                                <div>Database: <span class="text-blue-400" id="dbDeckScaleValue">80px</span></div>
                            </div>
                        </div>

                        <!-- Cards Per Row -->
                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="flex justify-between items-center mb-3">
                                <h5 class="font-semibold text-white">Cards Per Row</h5>
                                <div class="flex gap-2">
                                    <button
                                        class="export-category-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="cards-per-row">Export</button>
                                    <button
                                        class="reset-category-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="cards-per-row">Reset</button>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm">
                                <div>Main Deck: <span class="text-green-400" id="mainDeckCardsPerRowValue">8</span>
                                </div>
                                <div>Side Deck: <span class="text-green-400" id="sideDeckCardsPerRowValue">3</span>
                                </div>
                                <div>Extra Deck: <span class="text-green-400" id="extraDeckCardsPerRowValue">3</span>
                                </div>
                                <div>Database: <span class="text-green-400" id="dbDeckCardsPerRowValue">4</span></div>
                            </div>
                        </div>

                        <!-- Panel Configurations -->
                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="flex justify-between items-center mb-3">
                                <h5 class="font-semibold text-white">Panel Configurations</h5>
                                <div class="flex gap-2">
                                    <button
                                        class="export-category-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="panel-configs">Export</button>
                                    <button
                                        class="reset-category-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="panel-configs">Reset</button>
                                </div>
                            </div>
                            <div class="text-sm text-slate-300">
                                <div>Deck Builder Config: <span class="text-purple-400"
                                        id="deckBuilderConfigStatus">Configured</span></div>
                                <div>Deck Resize Config: <span class="text-purple-400"
                                        id="deckResizeConfigStatus">Configured</span></div>
                            </div>
                        </div>

                        <!-- Visibility States -->
                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="flex justify-between items-center mb-3">
                                <h5 class="font-semibold text-white">Visibility States</h5>
                                <div class="flex gap-2">
                                    <button
                                        class="export-category-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="visibility-states">Export</button>
                                    <button
                                        class="reset-category-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="visibility-states">Reset</button>
                                </div>
                            </div>
                            <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm">
                                <div>Extra Deck: <span class="text-yellow-400" id="extraDeckVisibleValue">Visible</span>
                                </div>
                                <div>Side Deck: <span class="text-yellow-400" id="sideDeckVisibleValue">Visible</span>
                                </div>
                                <div>Database: <span class="text-yellow-400" id="databaseVisibleValue">Visible</span>
                                </div>
                                <div>Card DB View: <span class="text-yellow-400" id="cardDatabaseViewValue">Grid</span>
                                </div>
                            </div>
                        </div>

                        <!-- Onboarding Flags -->
                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="flex justify-between items-center mb-3">
                                <h5 class="font-semibold text-white">Onboarding Flags</h5>
                                <div class="flex gap-2">
                                    <button
                                        class="export-category-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="onboarding-flags">Export</button>
                                    <button
                                        class="reset-category-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs"
                                        data-category="onboarding-flags">Reset</button>
                                </div>
                            </div>
                            <div class="text-sm text-slate-300">
                                <div>Welcome Seen: <span class="text-orange-400" id="hasSeenWelcomeValue">Yes</span>
                                </div>
                                <div>YDK Folder Path: <span class="text-orange-400" id="ydkFolderPathValue">Set</span>
                                </div>
                                <div>Offline Decks Introduced: <span class="text-orange-400"
                                        id="offlineDecksIntroducedValue">Yes</span></div>
                                <div>Cleanup Version: <span class="text-orange-400" id="cleanupVersionValue">9.1</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Card Database Tab -->
                <div id="card-database-tab" class="storage-tab-content hidden">
                    <div class="space-y-4">
                        <div class="flex justify-between items-center">
                            <h4 class="text-lg font-semibold text-white">Card Database Cache</h4>
                            <div class="flex gap-2">
                                <button id="exportCardDatabaseBtn"
                                    class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-3 rounded-md text-sm">Export
                                    Cache</button>
                                <label for="importCardDatabaseInput"
                                    class="bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-3 rounded-md text-sm cursor-pointer">Import
                                    Cache</label>
                                <input type="file" id="importCardDatabaseInput" class="hidden" accept=".json">
                                <button id="resetCardDatabaseBtn"
                                    class="bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-3 rounded-md text-sm">Reset
                                    Cache</button>
                            </div>
                        </div>

                        <div class="p-4 bg-slate-800 rounded-lg">
                            <div class="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                                <div class="text-center">
                                    <div class="text-2xl font-bold text-purple-400" id="cardDatabaseCount">0</div>
                                    <div class="text-slate-400">Cached Cards</div>
                                </div>
                                <div class="text-center">
                                    <div class="text-2xl font-bold text-blue-400" id="cardDatabaseSize">0 KB</div>
                                    <div class="text-slate-400">Cache Size</div>
                                </div>
                                <div class="text-center">
                                    <div class="text-2xl font-bold text-green-400" id="cardDatabaseLastUpdated">Never
                                    </div>
                                    <div class="text-slate-400">Last Updated</div>
                                </div>
                            </div>
                        </div>

                        <div class="p-4 bg-slate-800 rounded-lg">
                            <h5 class="font-semibold text-white mb-2">Cache Information</h5>
                            <p class="text-sm text-slate-300 mb-2">The card database cache improves performance by
                                storing card data locally. Resetting will force a fresh download from the API.</p>
                            <div class="text-xs text-slate-400">
                                <div>Cache Key: <code class="bg-slate-700 px-1 rounded">offlineCardDatabase</code></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Deck Management Tab -->
                <div id="decks-tab" class="storage-tab-content hidden">
                    <div class="space-y-4">
                        <div class="flex justify-between items-center">
                            <h4 class="text-lg font-semibold text-white">Deck Management</h4>
                            <div class="flex gap-2">
                                <button id="exportAllDecksBtn"
                                    class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-3 rounded-md text-sm">Export
                                    All</button>
                                <label for="importDecksInput"
                                    class="bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-3 rounded-md text-sm cursor-pointer">Import
                                    Decks</label>
                                <input type="file" id="importDecksInput" class="hidden" accept=".json">
                                <button id="clearAllDecksBtn"
                                    class="bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-3 rounded-md text-sm">Clear
                                    All</button>
                            </div>
                        </div>

                        <div class="flex items-center gap-2 mb-4">
                            <input type="checkbox" id="selectAllDecks" class="rounded">
                            <label for="selectAllDecks" class="text-sm text-slate-300">Select All</label>
                            <button id="bulkDeleteDecksBtn"
                                class="bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs ml-4"
                                disabled>Delete Selected</button>
                        </div>

                        <div id="decksList" class="space-y-2 max-h-96 overflow-y-auto">
                            <!-- Decks will be listed here -->
                        </div>
                    </div>
                </div>

                <!-- YDK Files Tab -->
                <div id="ydk-files-tab" class="storage-tab-content hidden">
                    <div class="space-y-4">
                        <div class="flex justify-between items-center">
                            <h4 class="text-lg font-semibold text-white">YDK File Management</h4>
                            <div class="flex gap-2">
                                <button id="exportAllYDKFilesBtn"
                                    class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-3 rounded-md text-sm">Export
                                    All</button>
                                <label for="importYDKFilesInput"
                                    class="bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-3 rounded-md text-sm cursor-pointer">Import
                                    Files</label>
                                <input type="file" id="importYDKFilesInput" class="hidden" accept=".ydk,.ydkx" multiple>
                                <button id="syncYDKFolderBtn"
                                    class="bg-purple-600 hover:bg-purple-700 text-white font-bold py-2 px-3 rounded-md text-sm">Sync
                                    Folder</button>
                                <button id="clearAllYDKFilesBtn"
                                    class="bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-3 rounded-md text-sm">Clear
                                    All</button>
                            </div>
                        </div>

                        <div class="flex items-center gap-2 mb-4">
                            <input type="checkbox" id="selectAllYDKFiles" class="rounded">
                            <label for="selectAllYDKFiles" class="text-sm text-slate-300">Select All</label>
                            <button id="bulkDeleteYDKFilesBtn"
                                class="bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs ml-4"
                                disabled>Delete Selected</button>
                        </div>

                        <div id="ydkFilesList" class="space-y-2 max-h-96 overflow-y-auto">
                            <!-- YDK files will be listed here -->
                        </div>
                    </div>
                </div>

                <!-- Diagnostics Tab -->
                <div id="diagnostics-tab" class="storage-tab-content hidden">
                    <div class="space-y-4">
                        <div class="flex justify-between items-center">
                            <h4 class="text-lg font-semibold text-white">Storage Diagnostics</h4>
                            <div class="flex gap-2">
                                <button id="refreshDiagnosticsBtn"
                                    class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-3 rounded-md text-sm">Refresh</button>
                                <button id="performCleanupBtn"
                                    class="bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-3 rounded-md text-sm">Cleanup</button>
                                <button id="resetAllStorageBtn"
                                    class="bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-3 rounded-md text-sm">Reset
                                    All</button>
                            </div>
                        </div>

                        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div class="p-4 bg-slate-800 rounded-lg">
                                <h5 class="font-semibold text-white mb-3">Storage Breakdown</h5>
                                <div id="storageBreakdown" class="space-y-2 text-sm">
                                    <!-- Storage breakdown will be populated here -->
                                </div>
                            </div>

                            <div class="p-4 bg-slate-800 rounded-lg">
                                <h5 class="font-semibold text-white mb-3">Health Check</h5>
                                <div id="healthCheck" class="space-y-2 text-sm">
                                    <!-- Health check results will be populated here -->
                                </div>
                            </div>
                        </div>

                        <div class="p-4 bg-slate-800 rounded-lg">
                            <h5 class="font-semibold text-white mb-3">Cleanup Options</h5>
                            <div class="grid grid-cols-2 md:grid-cols-4 gap-2">
                                <button
                                    class="cleanup-option-btn bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-3 rounded text-xs"
                                    data-cleanup="duplicates">Remove Duplicates</button>
                                <button
                                    class="cleanup-option-btn bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-3 rounded text-xs"
                                    data-cleanup="orphaned">Remove Orphaned</button>
                                <button
                                    class="cleanup-option-btn bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-3 rounded text-xs"
                                    data-cleanup="old-cache">Clear Old Cache</button>
                                <button
                                    class="cleanup-option-btn bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-3 rounded text-xs"
                                    data-cleanup="temp-files">Clear Temp Files</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    `;

    // Module initialization
    async function init() {
        console.log('Storage manager initializing...');

        // Inject modal HTML
        injectModalHTML();

        // Initialize IndexedDB
        await initYDKDatabase();

        // Load existing data
        await loadYDKFilesFromDB();
        await getAllOfflineDecksFromDB();

        // Setup event listeners
        setupEventListeners();

        console.log('Storage manager initialized');
    }

    // Inject modal HTML into the document
    function injectModalHTML() {
        // Find or create container
        let container = document.getElementById('storageManagementModal');
        if (container && container.innerHTML.trim() === '') {
            container.innerHTML = modalHTML.trim();
        } else if (!container) {
            // Append to body if no container exists
            const div = document.createElement('div');
            div.innerHTML = modalHTML.trim();
            document.body.appendChild(div.firstElementChild);
        }
    }

    // --- INDEXEDDB CORE FUNCTIONS ---

    async function initYDKDatabase() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open('YDKStorage', 4); // Increment version to 4 for new schema

            request.onerror = () => reject(request.error);
            request.onsuccess = () => {
                ydkDB = request.result;
                resolve(ydkDB);
            };

            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                if (!db.objectStoreNames.contains('ydkFiles')) {
                    const store = db.createObjectStore('ydkFiles', { keyPath: 'name' });
                    store.createIndex('displayName', 'displayName', { unique: false });
                    store.createIndex('lastModified', 'lastModified', { unique: false });
                }
                if (!db.objectStoreNames.contains('offlineDecks')) {
                    const deckStore = db.createObjectStore('offlineDecks', { keyPath: 'id' });
                    deckStore.createIndex('name', 'name', { unique: false });
                    deckStore.createIndex('updatedAt', 'updatedAt', { unique: false });
                    deckStore.createIndex('createdAt', 'createdAt', { unique: false });
                }
                // Old store (deprecated, will be migrated)
                if (!db.objectStoreNames.contains('sidingPatterns')) {
                    const patternStore = db.createObjectStore('sidingPatterns', { keyPath: 'name' });
                    patternStore.createIndex('createdAt', 'createdAt', { unique: false });
                    patternStore.createIndex('updatedAt', 'updatedAt', { unique: false });
                }
                // New store with UUID-based IDs
                if (!db.objectStoreNames.contains('universalSidingPatterns')) {
                    const newPatternStore = db.createObjectStore('universalSidingPatterns', { keyPath: 'id' });
                    newPatternStore.createIndex('name', 'name', { unique: false });
                    newPatternStore.createIndex('associatedYdk', 'associatedYdk', { unique: false });
                    newPatternStore.createIndex('createdAt', 'createdAt', { unique: false });
                    newPatternStore.createIndex('updatedAt', 'updatedAt', { unique: false });
                }
            };
        });
    }

    async function saveYDKFileToDB(name, content, metadata = {}) {
        if (!ydkDB) await initYDKDatabase();

        const ydkFile = {
            name,
            displayName: metadata.displayName || name.replace(/\.(ydk|ydkx)$/i, ''),
            content,
            lastModified: metadata.lastModified || new Date().toISOString(),
            format: metadata.format || (name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk'),
            cardPool: metadata.cardPool || null
        };

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['ydkFiles'], 'readwrite');
            const store = transaction.objectStore('ydkFiles');
            const request = store.put(ydkFile);

            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    async function getAllYDKFilesFromDB() {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['ydkFiles'], 'readonly');
            const store = transaction.objectStore('ydkFiles');
            const request = store.getAll();

            request.onsuccess = () => {
                const files = (request.result || []).map(file => ({
                    ...file,
                    displayName: file.displayName || file.name.replace(/\.(ydk|ydkx)$/i, ''),
                    format: file.format || (file.name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk'),
                    cardPool: file.cardPool || null
                }));
                resolve(files);
            };
            request.onerror = () => reject(request.error);
        });
    }

    async function saveSidingPatternToDB(pattern) {
        if (!ydkDB) await initYDKDatabase();

        const patternData = {
            name: pattern.name,
            out: pattern.out || [],
            in: pattern.in || [],
            ydkDeckName: pattern.ydkDeckName || undefined,
            createdAt: pattern.createdAt || new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            tags: pattern.tags || []
        };

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['sidingPatterns'], 'readwrite');
            const store = transaction.objectStore('sidingPatterns');
            const request = store.put(patternData);

            request.onsuccess = () => {
                if (typeof universalSidingPatterns !== 'undefined') {
                    universalSidingPatterns[pattern.name] = patternData;
                }
                resolve(request.result);
            };
            request.onerror = () => reject(request.error);
        });
    }

    async function getAllSidingPatternsFromDB() {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['sidingPatterns'], 'readonly');
            const store = transaction.objectStore('sidingPatterns');
            const request = store.getAll();

            request.onsuccess = () => {
                const patterns = {};
                (request.result || []).forEach(pattern => {
                    patterns[pattern.name] = {
                        out: pattern.out || [],
                        in: pattern.in || [],
                        ydkDeckName: pattern.ydkDeckName || undefined,
                        createdAt: pattern.createdAt,
                        updatedAt: pattern.updatedAt,
                        tags: pattern.tags || []
                    };
                });
                if (typeof universalSidingPatterns !== 'undefined') {
                    universalSidingPatterns = patterns;
                }
                resolve(patterns);
            };
            request.onerror = () => reject(request.error);
        });
    }

    async function deleteSidingPatternFromDB(name) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['sidingPatterns'], 'readwrite');
            const store = transaction.objectStore('sidingPatterns');
            const request = store.delete(name);

            request.onsuccess = () => {
                if (typeof universalSidingPatterns !== 'undefined') {
                    delete universalSidingPatterns[name];
                }
                resolve();
            };
            request.onerror = () => reject(request.error);
        });
    }

    async function updateSidingPatternInDB(name, updates) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise(async (resolve, reject) => {
            const transaction = ydkDB.transaction(['sidingPatterns'], 'readwrite');
            const store = transaction.objectStore('sidingPatterns');
            const getRequest = store.get(name);

            getRequest.onsuccess = () => {
                const existing = getRequest.result;
                if (!existing) {
                    reject(new Error('Pattern not found'));
                    return;
                }

                const updated = {
                    ...existing,
                    ...updates,
                    updatedAt: new Date().toISOString()
                };

                const putRequest = store.put(updated);
                putRequest.onsuccess = () => {
                    if (typeof universalSidingPatterns !== 'undefined') {
                        universalSidingPatterns[name] = updated;
                    }
                    resolve(updated);
                };
                putRequest.onerror = () => reject(putRequest.error);
            };
            getRequest.onerror = () => reject(getRequest.error);
        });
    }

    // --- DECK STORAGE FUNCTIONS ---

    async function getOfflineDeckFromDB(id) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['offlineDecks'], 'readonly');
            const store = transaction.objectStore('offlineDecks');
            const request = store.get(id);

            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    async function getAllOfflineDecksFromDB() {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['offlineDecks'], 'readonly');
            const store = transaction.objectStore('offlineDecks');
            const request = store.getAll();

            request.onsuccess = () => {
                const decks = request.result || [];
                if (typeof offlineDecks !== 'undefined') {
                    offlineDecks.length = 0;
                    offlineDecks.push(...decks);
                }
                resolve(decks);
            };
            request.onerror = () => reject(request.error);
        });
    }

    async function saveOfflineDeckToDB(deck) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['offlineDecks'], 'readwrite');
            const store = transaction.objectStore('offlineDecks');
            const request = store.put(deck);

            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    async function deleteOfflineDeckFromDB(id) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['offlineDecks'], 'readwrite');
            const store = transaction.objectStore('offlineDecks');
            const request = store.delete(id);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async function deleteYDKFileFromDB(name) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['ydkFiles'], 'readwrite');
            const store = transaction.objectStore('ydkFiles');
            const request = store.delete(name);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async function clearAllYDKFiles() {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['ydkFiles'], 'readwrite');
            const store = transaction.objectStore('ydkFiles');
            const request = store.clear();

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async function handleClearAllYDKFiles() {
        if (!confirm('Clear all YDK files? This cannot be undone.')) return;

        await clearAllYDKFiles();
        if (typeof ydkFiles !== 'undefined') {
            ydkFiles.length = 0;
        }
        await loadYDKFilesFromDB();
        updateYDKFilesList();
        updateStorageOverview();
        showTooltip('All YDK files cleared');
    }

    async function loadYDKFilesFromDB(options = {}) {
        if (!ydkDB) await initYDKDatabase();

        return new Promise((resolve, reject) => {
            const transaction = ydkDB.transaction(['ydkFiles'], 'readonly');
            const store = transaction.objectStore('ydkFiles');
            const request = store.getAll();

            request.onsuccess = () => {
                const files = (request.result || []).map(file => ({
                    ...file,
                    displayName: file.displayName || file.name.replace(/\.(ydk|ydkx)$/i, ''),
                    format: file.format || (file.name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk'),
                    cardPool: file.cardPool || null
                }));
                if (typeof ydkFiles !== 'undefined') {
                    ydkFiles.length = 0;
                    ydkFiles.push(...files);
                }
                if (typeof updateYDKDeckSelector === 'function') {
                    updateYDKDeckSelector();
                }
                resolve(files);
            };
            request.onerror = () => reject(request.error);
        });
    }

    // --- YDK FOLDER MANAGEMENT ---

    async function tryAccessDefaultYDKFolder() {
        // Try to access previously saved folder
        const savedPath = localStorage.getItem('ydkFolderPath');
        if (!savedPath) return;

        try {
            const handle = await window.showDirectoryPicker({ mode: 'readwrite', startIn: savedPath });
            if (typeof ydkFolder !== 'undefined') {
                ydkFolder = handle;
            }
            localStorage.setItem('ydkFolderPath', savedPath);
            await importYDKFolder();
        } catch (error) {
            console.warn('Could not access default YDK folder:', error);
        }
    }

    async function selectYDKFolder() {
        try {
            const handle = await window.showDirectoryPicker({ mode: 'readwrite' });
            if (typeof ydkFolder !== 'undefined') {
                ydkFolder = handle;
            }
            localStorage.setItem('ydkFolderPath', handle.name);
            showTooltip(`Selected folder: ${handle.name}`);

            // Import all YDK files from the folder
            await importYDKFolder();

        } catch (error) {
            if (error.name !== 'AbortError') {
                console.error('Error selecting YDK folder:', error);
                showTooltip('Error selecting YDK folder.');
            }
        }
    }

    async function importYDKFolder() {
        if (typeof ydkFolder === 'undefined' || !ydkFolder) return;

        try {
            const filesToImport = [];

            // Scan folder for YDK files
            for await (const [name, handle] of ydkFolder.entries()) {
                if ((name.toLowerCase().endsWith('.ydk') || name.toLowerCase().endsWith('.ydkx')) && handle.kind === 'file') {
                    const fileData = await handle.getFile();
                    const content = await fileData.text();
                    filesToImport.push({ name, content, handle, format: name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk' });
                    if (typeof ydkFileHandles !== 'undefined') {
                        ydkFileHandles.set(name, handle);
                    }
                }
            }

            // Save all files to IndexedDB
            for (const file of filesToImport) {
                await saveYDKFileToDB(file.name, file.content, { format: file.format, displayName: file.name.replace(/\.(ydk|ydkx)$/i, '') });
            }

            // Reload files from IndexedDB
            await loadYDKFilesFromDB();

            // Enable/disable UI elements
            if (typeof UI !== 'undefined') {
                if (UI.ydkDeckSelector) UI.ydkDeckSelector.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
                if (UI.setDefaultDeckBtn) UI.setDefaultDeckBtn.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
            }

        } catch (error) {
            console.error('Error importing YDK folder:', error);
            showTooltip('Error importing YDK files from folder.');
        }
    }

    async function importSingleYDKFile(event) {
        const files = event.target.files;
        if (!files || files.length === 0) return;

        try {
            // Check if this is a folder selection (webkitdirectory)
            if (files.length > 1 || (files[0] && files[0].webkitRelativePath && files[0].webkitRelativePath.includes('/'))) {
                // This is a folder selection
                console.log(`[DEBUG] Folder selection detected with ${files.length} files`);

                // Import all YDK files from the folder
                const filesToImport = [];

                for (const file of files) {
                    if (file.name.toLowerCase().endsWith('.ydk') || file.name.toLowerCase().endsWith('.ydkx')) {
                        const content = await file.text();
                        filesToImport.push({
                            name: file.name,
                            content,
                            format: file.name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk',
                            displayName: file.name.replace(/\.(ydk|ydkx)$/i, '')
                        });
                    }
                }

                // Save all files to IndexedDB
                for (const fileData of filesToImport) {
                    await saveYDKFileToDB(fileData.name, fileData.content, {
                        format: fileData.format,
                        displayName: fileData.displayName
                    });
                }

                // Reload files from IndexedDB
                await loadYDKFilesFromDB();

                // Enable/disable UI elements
                if (typeof UI !== 'undefined') {
                    if (UI.ydkDeckSelector) UI.ydkDeckSelector.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
                    if (UI.setDefaultDeckBtn) UI.setDefaultDeckBtn.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
                }

                showTooltip(`Imported ${filesToImport.length} YDK files from folder`);
            } else {
                // This is a single file selection
                const file = files[0];
                console.log(`[DEBUG] Single file selection: ${file.name}`);

                const reader = new FileReader();
                reader.onload = async (e) => {
                    const content = e.target.result;
                    await saveYDKFileToDB(file.name, content, {
                        format: file.name.toLowerCase().endsWith('.ydkx') ? 'ydkx' : 'ydk',
                        displayName: file.name.replace(/\.(ydk|ydkx)$/i, '')
                    });

                    // Reload files from IndexedDB
                    await loadYDKFilesFromDB();

                    // Enable/disable UI elements
                    if (typeof UI !== 'undefined') {
                        if (UI.ydkDeckSelector) UI.ydkDeckSelector.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
                        if (UI.setDefaultDeckBtn) UI.setDefaultDeckBtn.disabled = typeof ydkFiles !== 'undefined' ? ydkFiles.length === 0 : true;
                    }

                    showTooltip(`Imported YDK file: ${file.name}`);
                };
                reader.readAsText(file);
            }
        } catch (error) {
            console.error('Error importing YDK file(s):', error);
            showTooltip('Error importing YDK file(s).');
        }

        // Clear the input
        event.target.value = null;
    }

    async function scanYDKFolder() {
        if (typeof ydkFolder === 'undefined' || !ydkFolder) return;

        if (typeof ydkFiles !== 'undefined') {
            ydkFiles = [];
        }
        if (typeof ydkFileHandles !== 'undefined') {
            ydkFileHandles = new Map();
        }
        try {
            for await (const [name, handle] of ydkFolder.entries()) {
                if ((name.toLowerCase().endsWith('.ydk') || name.toLowerCase().endsWith('.ydkx')) && handle.kind === 'file') {
                    if (typeof ydkFiles !== 'undefined') {
                        ydkFiles.push({
                            name: name,
                            handle: handle,
                            displayName: name.replace('.ydk', '')
                        });
                    }
                    if (typeof ydkFileHandles !== 'undefined') {
                        ydkFileHandles.set(name, handle);
                    }
                }
            }
            if (typeof updateYDKDeckSelector === 'function') {
                updateYDKDeckSelector();
            }
        } catch (error) {
            console.error('Error scanning YDK folder:', error);
            if (typeof showMessage === 'function') {
                showMessage('Error scanning YDK folder.');
            }
        }
    }

    function updateYDKFolderStatus() {
        if (typeof UI === 'undefined' || !UI.ydkFolderStatus) return;

        if (typeof ydkFiles !== 'undefined' && ydkFiles.length > 0) {
            UI.ydkFolderStatus.textContent = `${ydkFiles.length} YDK files stored`;
            UI.ydkFolderStatus.className = 'text-green-400 text-sm';

            // Show sync button if folder was previously selected
            const savedFolderPath = localStorage.getItem('ydkFolderPath');
            if (savedFolderPath && UI.syncFolderBtn) {
                UI.syncFolderBtn.classList.remove('hidden');
                UI.syncFolderBtn.textContent = `Sync: ${savedFolderPath}`;
            }
        } else {
            UI.ydkFolderStatus.textContent = 'No YDK files stored';
            UI.ydkFolderStatus.className = 'text-gray-400 text-sm';

            // Hide sync button
            if (UI.syncFolderBtn) UI.syncFolderBtn.classList.add('hidden');
        }
    }

    function updateYDKDeckSelector() {
        if (typeof UI === 'undefined' || !UI.ydkDeckSelector) return;

        // Clear existing options
        UI.ydkDeckSelector.innerHTML = '';

        if (typeof ydkFiles === 'undefined' || ydkFiles.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No YDK files found';
            UI.ydkDeckSelector.appendChild(option);
            return;
        }

        // Add options for each YDK file
        ydkFiles.forEach((file, index) => {
            const option = document.createElement('option');
            option.value = index;
            option.textContent = file.displayName;
            UI.ydkDeckSelector.appendChild(option);
        });

        // Enable the dropdown since we have files
        UI.ydkDeckSelector.disabled = false;
        if (typeof setDefaultDeckBtn !== 'undefined' && UI.setDefaultDeckBtn) UI.setDefaultDeckBtn.disabled = false;

        // Select the default deck in dropdown if set (but don't auto-load it here)
        if (typeof defaultDeckName !== 'undefined' && defaultDeckName) {
            const defaultIndex = ydkFiles.findIndex(f => f.displayName === defaultDeckName);
            if (defaultIndex !== -1) {
                UI.ydkDeckSelector.value = defaultIndex;
                // Note: autoLoadYDKOnStartup will handle to actual loading
            }
        }
    }

    async function loadYDKDeck(index) {
        console.log(`[DEBUG] loadYDKDeck called with index: ${index}`);

        // Prevent loading if already loading
        if (isLoadingYDK) {
            console.log(`[DEBUG] Already loading a YDK deck, skipping duplicate call`);
            return;
        }

        // Ensure card database is loaded before proceeding
        if (typeof isDatabaseLoaded !== 'undefined' && !isDatabaseLoaded) {
            console.log(`[DEBUG] Card database not loaded yet, waiting...`);
            while (typeof isDatabaseLoaded !== 'undefined' && !isDatabaseLoaded) {
                await new Promise(resolve => setTimeout(resolve, 100));
            }
            console.log(`[DEBUG] Card database loaded, proceeding with YDK load`);
        }

        if (typeof ydkFiles === 'undefined' || !ydkFiles[index]) {
            console.log(`[DEBUG] No YDK file found at index ${index}`);
            return;
        }

        // Auto-save current deck and switch to selected YDK deck
        try {
            isLoadingYDK = true; // Set guard
            const file = ydkFiles[index];
            console.log(`[DEBUG] Loading YDK file: ${file.displayName}`);
            const content = file.content; // Content is already stored in IndexedDB
            console.log(`[DEBUG] YDK content length: ${content.length}`);

            // Parse YDK content
            const lines = content.split('\n').map(l => l.trim()).filter(Boolean);
            let currentSection = '';
            const importedDeck = { main: [], side: [], extra: [] };

            for (const line of lines) {
                if (line === '#main') { currentSection = 'main'; continue; }
                if (line === '!side') { currentSection = 'side'; continue; }
                if (line === '#extra') { currentSection = 'extra'; continue; }
                if ((currentSection === 'main' || currentSection === 'side' || currentSection === 'extra') && !isNaN(parseInt(line))) {
                    importedDeck[currentSection].push(line);
                }
            }

            console.log(`[DEBUG] Parsed deck - Main: ${importedDeck.main.length}, Side: ${importedDeck.side.length}, Extra: ${importedDeck.extra.length}`);

            // Use the new auto-switch function
            if (typeof saveCurrentAndSwitchToImported === 'function') {
                await saveCurrentAndSwitchToImported(importedDeck, file.displayName, file.format === 'ydkx', null);
            }

        } catch (error) {
            console.error('Error loading YDK deck:', error);
            showTooltip('Error loading YDK deck.');
        } finally {
            isLoadingYDK = false; // Reset guard
            console.log(`[DEBUG] Finished loading YDK deck, reset guard`);
        }
    }

    function setDefaultYDKDeck() {
        if (typeof UI === 'undefined' || !UI.ydkDeckSelector) return;

        const selectedIndex = UI.ydkDeckSelector.value;
        if (selectedIndex === '' || (typeof ydkFiles !== 'undefined' && !ydkFiles[selectedIndex])) return;

        if (typeof defaultDeckName !== 'undefined') {
            defaultDeckName = ydkFiles[selectedIndex].displayName;
        }
        localStorage.setItem('defaultYDKDeck', defaultDeckName);
        showTooltip(`Set ${defaultDeckName} as default deck`);
    }

    function loadDefaultYDKDeck() {
        const saved = localStorage.getItem('defaultYDKDeck');
        if (saved && typeof defaultDeckName !== 'undefined') {
            defaultDeckName = saved;
        }
    }

    async function autoLoadYDKOnStartup() {
        if (typeof ydkFiles === 'undefined' || ydkFiles.length === 0) return;

        isAutoLoading = true; // Set flag to prevent saves during auto-load

        try {
            if (ydkFiles.length === 1) {
                // Auto-load single YDK file
                await loadYDKDeck(0);
                showTooltip(`Auto-loaded: ${ydkFiles[0].displayName}`);
            } else if (typeof defaultDeckName !== 'undefined' && defaultDeckName) {
                // Load default deck if set
                const defaultIndex = ydkFiles.findIndex(f => f.displayName === defaultDeckName);
                if (defaultIndex !== -1) {
                    await loadYDKDeck(defaultIndex);
                    showTooltip(`Auto-loaded default deck: ${defaultDeckName}`);
                } else {
                    showTooltip(`Found ${ydkFiles.length} YDK files. Please select one from dropdown.`);
                }
            } else {
                showTooltip(`Found ${ydkFiles.length} YDK files. Please select one from dropdown.`);
            }
        } finally {
            isAutoLoading = false; // Clear flag after auto-load is complete
        }
    }

    function renderYDKFilesList() {
        if (typeof UI === 'undefined' || !UI.ydkFilesList) return;

        UI.ydkFilesList.innerHTML = '';

        if (typeof ydkFiles === 'undefined' || ydkFiles.length === 0) {
            UI.ydkFilesList.innerHTML = '<p class="text-gray-400 text-center">No YDK files stored</p>';
            return;
        }

        ydkFiles.forEach((file, index) => {
            const fileEl = document.createElement('div');
            fileEl.className = 'flex items-center justify-between bg-slate-700 p-3 rounded-lg';

            const lastModified = new Date(file.lastModified).toLocaleDateString();

            fileEl.innerHTML = `
                <div class="flex-1">
                    <div class="font-medium text-white">${file.displayName}</div>
                    <div class="text-sm text-gray-400">Imported: ${lastModified}</div>
                </div>
                <div class="flex gap-2">
                    <button class="delete-ydk-file bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-sm" data-name="${file.name}">Delete</button>
                </div>
            `;

            // Add delete event listener
            const deleteBtn = fileEl.querySelector('.delete-ydk-file');
            deleteBtn.addEventListener('click', async () => {
                if (confirm(`Delete ${file.displayName}?`)) {
                    await deleteYDKFileFromDB(file.name);
                    await loadYDKFilesFromDB();
                    renderYDKFilesList();
                    if (typeof showMessage === 'function') {
                        showMessage(`Deleted ${file.displayName}`);
                    }
                }
            });

            UI.ydkFilesList.appendChild(fileEl);
        });
    }

    // --- STORAGE MANAGEMENT UI FUNCTIONS ---

    function openStorageManagementModal() {
        const modal = document.getElementById('storageManagementModal');
        if (!modal) return;

        // Initialize the modal with current data
        updateStorageOverview();
        updateVisualUISettings();
        updateCardDatabaseInfo();
        updateDecksList();
        updateYDKFilesList();
        updateDiagnostics();

        modal.classList.remove('hidden');
    }

    function updateStorageOverview() {
        const totalDecks = typeof offlineDecks !== 'undefined' ? offlineDecks.length : 0;
        const totalYDKFiles = typeof ydkFiles !== 'undefined' ? ydkFiles.length : 0;
        const totalCards = typeof cardDatabase !== 'undefined' ? cardDatabase.length : 0;

        // Calculate storage usage (approximate)
        let storageUsage = 0;
        try {
            // Estimate localStorage usage
            for (let key in localStorage) {
                if (localStorage.hasOwnProperty(key)) {
                    storageUsage += localStorage[key].length;
                }
            }
            // Estimate IndexedDB usage (rough approximation)
            storageUsage += totalDecks * 1000; // ~1KB per deck
            storageUsage += totalYDKFiles * 500; // ~500B per YDK file
            storageUsage += totalCards * 200; // ~200B per card
        } catch (e) {
            console.warn('Could not calculate storage usage:', e);
        }

        const totalDecksCount = document.getElementById('totalDecksCount');
        const totalYDKFilesCount = document.getElementById('totalYDKFilesCount');
        const totalCardsCount = document.getElementById('totalCardsCount');
        const storageUsageEl = document.getElementById('storageUsage');

        if (totalDecksCount) totalDecksCount.textContent = totalDecks;
        if (totalYDKFilesCount) totalYDKFilesCount.textContent = totalYDKFiles;
        if (totalCardsCount) totalCardsCount.textContent = totalCards;
        if (storageUsageEl) storageUsageEl.textContent = formatBytes(storageUsage);
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    function updateVisualUISettings() {
        // Update card sizes
        const mainDeckScaleValue = document.getElementById('mainDeckScaleValue');
        const sideDeckScaleValue = document.getElementById('sideDeckScaleValue');
        const extraDeckScaleValue = document.getElementById('extraDeckScaleValue');
        const dbDeckScaleValue = document.getElementById('dbDeckScaleValue');

        if (mainDeckScaleValue) mainDeckScaleValue.textContent = localStorage.getItem('mainDeckScale') || '69px';
        if (sideDeckScaleValue) sideDeckScaleValue.textContent = localStorage.getItem('sideDeckScale') || '46px';
        if (extraDeckScaleValue) extraDeckScaleValue.textContent = localStorage.getItem('extraDeckScale') || '46px';
        if (dbDeckScaleValue) dbDeckScaleValue.textContent = localStorage.getItem('dbDeckScale') || '46px';

        // Update cards per row
        const mainDeckCardsPerRowValue = document.getElementById('mainDeckCardsPerRowValue');
        const sideDeckCardsPerRowValue = document.getElementById('sideDeckCardsPerRowValue');
        const extraDeckCardsPerRowValue = document.getElementById('extraDeckCardsPerRowValue');
        const dbDeckCardsPerRowValue = document.getElementById('dbDeckCardsPerRowValue');

        if (mainDeckCardsPerRowValue) mainDeckCardsPerRowValue.textContent = localStorage.getItem('mainDeckCardsPerRow') || '10';
        if (sideDeckCardsPerRowValue) sideDeckCardsPerRowValue.textContent = localStorage.getItem('sideDeckCardsPerRow') || '4';
        if (extraDeckCardsPerRowValue) extraDeckCardsPerRowValue.textContent = localStorage.getItem('extraDeckCardsPerRow') || '3';
        if (dbDeckCardsPerRowValue) dbDeckCardsPerRowValue.textContent = localStorage.getItem('dbDeckCardsPerRow') || '7';

        // Update panel configs
        const deckBuilderConfigStatus = document.getElementById('deckBuilderConfigStatus');
        const deckResizeConfigStatus = document.getElementById('deckResizeConfigStatus');

        if (deckBuilderConfigStatus) deckBuilderConfigStatus.textContent = localStorage.getItem('deckBuilderConfig') ? 'Configured' : 'Default';
        if (deckResizeConfigStatus) deckResizeConfigStatus.textContent = localStorage.getItem('deckResizeConfig') ? 'Configured' : 'Default';

        // Update visibility states
        const extraDeckVisibleValue = document.getElementById('extraDeckVisibleValue');
        const sideDeckVisibleValue = document.getElementById('sideDeckVisibleValue');
        const databaseVisibleValue = document.getElementById('databaseVisibleValue');
        const cardDatabaseViewValue = document.getElementById('cardDatabaseViewValue');

        if (extraDeckVisibleValue) extraDeckVisibleValue.textContent = typeof extraDeckVisible !== 'undefined' ? (extraDeckVisible ? 'Visible' : 'Hidden') : 'Unknown';
        if (sideDeckVisibleValue) sideDeckVisibleValue.textContent = typeof sideDeckVisible !== 'undefined' ? (sideDeckVisible ? 'Visible' : 'Hidden') : 'Unknown';
        if (databaseVisibleValue) databaseVisibleValue.textContent = typeof databaseVisible !== 'undefined' ? (databaseVisible ? 'Visible' : 'Hidden') : 'Unknown';

        if (cardDatabaseViewValue && typeof UI !== 'undefined' && UI.cardDbList) {
            cardDatabaseViewValue.textContent = UI.cardDbList.classList.contains('card-db-grid') ? 'Grid' : 'List';
        }

        // Update onboarding flags
        const hasSeenWelcomeValue = document.getElementById('hasSeenWelcomeValue');
        const ydkFolderPathValue = document.getElementById('ydkFolderPathValue');
        const offlineDecksIntroducedValue = document.getElementById('offlineDecksIntroducedValue');
        const cleanupVersionValue = document.getElementById('cleanupVersionValue');

        if (hasSeenWelcomeValue) hasSeenWelcomeValue.textContent = localStorage.getItem('hasSeenWelcome') ? 'Yes' : 'No';
        if (ydkFolderPathValue) ydkFolderPathValue.textContent = localStorage.getItem('ydkFolderPath') ? 'Set' : 'Not Set';
        if (offlineDecksIntroducedValue) offlineDecksIntroducedValue.textContent = localStorage.getItem('offlineDecksIntroduced') ? 'Yes' : 'No';
        if (cleanupVersionValue) cleanupVersionValue.textContent = localStorage.getItem('cleanupVersion') || 'None';
    }

    function updateCardDatabaseInfo() {
        const cardCount = typeof cardDatabase !== 'undefined' ? cardDatabase.length : 0;
        const cacheSize = typeof cardDatabase !== 'undefined' ? JSON.stringify(cardDatabase).length : 0;
        const lastUpdated = localStorage.getItem('cardDatabaseLastUpdated') || 'Never';

        const cardDatabaseCount = document.getElementById('cardDatabaseCount');
        const cardDatabaseSize = document.getElementById('cardDatabaseSize');
        const cardDatabaseLastUpdated = document.getElementById('cardDatabaseLastUpdated');

        if (cardDatabaseCount) cardDatabaseCount.textContent = cardCount;
        if (cardDatabaseSize) cardDatabaseSize.textContent = formatBytes(cacheSize);
        if (cardDatabaseLastUpdated) cardDatabaseLastUpdated.textContent = lastUpdated;
    }

    function updateDecksList() {
        const decksList = document.getElementById('decksList');
        if (!decksList) return;

        decksList.innerHTML = '';

        if (typeof offlineDecks !== 'undefined') {
            offlineDecks.forEach(deck => {
                const deckEl = document.createElement('div');
                deckEl.className = 'flex items-center justify-between p-3 bg-slate-700 rounded-lg';
                deckEl.innerHTML = `
                    <div class="flex items-center gap-3">
                        <input type="checkbox" class="deck-checkbox rounded" data-deck-id="${deck.id}">
                        <div>
                            <div class="font-semibold text-white">${deck.name}</div>
                            <div class="text-sm text-slate-400">
                                Main: ${deck.main.length} | Side: ${deck.side.length} | Extra: ${deck.extra.length}
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2">
                        <button class="export-deck-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs" data-deck-id="${deck.id}">Export</button>
                        <button class="delete-deck-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs" data-deck-id="${deck.id}">Delete</button>
                    </div>
                `;
                decksList.appendChild(deckEl);
            });

            // Add event listeners
            decksList.querySelectorAll('.deck-checkbox').forEach(checkbox => {
                checkbox.addEventListener('change', updateBulkDeleteButton);
            });

            decksList.querySelectorAll('.export-deck-btn').forEach(btn => {
                btn.addEventListener('click', (e) => exportIndividualDeck(e.target.dataset.deckId));
            });

            decksList.querySelectorAll('.delete-deck-btn').forEach(btn => {
                btn.addEventListener('click', (e) => deleteIndividualDeck(e.target.dataset.deckId));
            });
        }
    }

    function updateBulkDeleteButton() {
        const selectedDecks = document.querySelectorAll('.deck-checkbox:checked');
        const bulkDeleteBtn = document.getElementById('bulkDeleteDecksBtn');
        if (bulkDeleteBtn) {
            bulkDeleteBtn.disabled = selectedDecks.length === 0;
            bulkDeleteBtn.textContent = `Delete Selected (${selectedDecks.length})`;
        }
    }

    function updateYDKFilesList() {
        const ydkFilesList = document.getElementById('ydkFilesList');
        if (!ydkFilesList) return;

        ydkFilesList.innerHTML = '';

        if (typeof ydkFiles !== 'undefined') {
            ydkFiles.forEach(file => {
                const fileEl = document.createElement('div');
                fileEl.className = 'flex items-center justify-between p-3 bg-slate-700 rounded-lg';
                fileEl.innerHTML = `
                    <div class="flex items-center gap-3">
                        <input type="checkbox" class="ydk-checkbox rounded" data-file-name="${file.name}">
                        <div>
                            <div class="font-semibold text-white">${file.displayName}</div>
                            <div class="text-sm text-slate-400">
                                ${file.format.toUpperCase()} | ${formatBytes(file.content.length)} | ${new Date(file.lastModified).toLocaleDateString()}
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2">
                        <button class="export-ydk-btn bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-2 rounded text-xs" data-file-name="${file.name}">Export</button>
                        <button class="delete-ydk-btn bg-red-600 hover:bg-red-700 text-white font-bold py-1 px-2 rounded text-xs" data-file-name="${file.name}">Delete</button>
                    </div>
                `;
                ydkFilesList.appendChild(fileEl);
            });

            // Add event listeners
            ydkFilesList.querySelectorAll('.ydk-checkbox').forEach(checkbox => {
                checkbox.addEventListener('change', updateBulkDeleteYDKButton);
            });

            ydkFilesList.querySelectorAll('.export-ydk-btn').forEach(btn => {
                btn.addEventListener('click', (e) => exportIndividualYDKFile(e.target.dataset.fileName));
            });

            ydkFilesList.querySelectorAll('.delete-ydk-btn').forEach(btn => {
                btn.addEventListener('click', (e) => deleteIndividualYDKFile(e.target.dataset.fileName));
            });
        }
    }

    function updateBulkDeleteYDKButton() {
        const selectedFiles = document.querySelectorAll('.ydk-checkbox:checked');
        const bulkDeleteBtn = document.getElementById('bulkDeleteYDKFilesBtn');
        if (bulkDeleteBtn) {
            bulkDeleteBtn.disabled = selectedFiles.length === 0;
            bulkDeleteBtn.textContent = `Delete Selected (${selectedFiles.length})`;
        }
    }

    function updateDiagnostics() {
        updateStorageBreakdown();
        updateHealthCheck();
    }

    function updateStorageBreakdown() {
        const breakdown = document.getElementById('storageBreakdown');
        if (!breakdown) return;

        let totalSize = 0;
        const categories = [];

        // Calculate localStorage usage
        for (let key in localStorage) {
            if (localStorage.hasOwnProperty(key)) {
                const size = localStorage[key].length;
                totalSize += size;
                categories.push({
                    name: key,
                    size: size,
                    type: 'localStorage'
                });
            }
        }

        // Add IndexedDB estimates
        if (typeof offlineDecks !== 'undefined') {
            categories.push({
                name: 'Offline Decks',
                size: offlineDecks.length * 1000,
                type: 'IndexedDB'
            });
        }

        if (typeof ydkFiles !== 'undefined') {
            categories.push({
                name: 'YDK Files',
                size: ydkFiles.reduce((sum, file) => sum + file.content.length, 0),
                type: 'IndexedDB'
            });
        }

        breakdown.innerHTML = categories
            .sort((a, b) => b.size - a.size)
            .map(cat => `
                <div class="flex justify-between items-center">
                    <span class="text-slate-300">${cat.name}</span>
                    <span class="text-blue-400">${formatBytes(cat.size)}</span>
                </div>
            `).join('');
    }

    function updateHealthCheck() {
        const healthCheck = document.getElementById('healthCheck');
        if (!healthCheck) return;

        const checks = [];

        // Check for duplicates in card database
        if (typeof cardDatabase !== 'undefined') {
            const cardIds = cardDatabase.map(c => c.id);
            const uniqueIds = new Set(cardIds);
            if (cardIds.length !== uniqueIds.size) {
                checks.push({
                    status: 'warning',
                    message: `${cardIds.length - uniqueIds.size} duplicate cards found`
                });
            } else {
                checks.push({
                    status: 'good',
                    message: 'No duplicate cards'
                });
            }

            // Check for orphaned data
            if (typeof offlineDecks !== 'undefined') {
                const orphanedDecks = offlineDecks.filter(deck => !deck.name || deck.main.length === 0);
                if (orphanedDecks.length > 0) {
                    checks.push({
                        status: 'warning',
                        message: `${orphanedDecks.length} orphaned decks found`
                    });
                } else {
                    checks.push({
                        status: 'good',
                        message: 'No orphaned decks'
                    });
                }
            }
        }

        // Check storage quota
        if (navigator.storage && navigator.storage.estimate) {
            navigator.storage.estimate().then(estimate => {
                const usagePercent = (estimate.usage / estimate.quota) * 100;
                if (usagePercent > 80) {
                    checks.push({
                        status: 'warning',
                        message: `Storage usage: ${usagePercent.toFixed(1)}%`
                    });
                } else {
                    checks.push({
                        status: 'good',
                        message: `Storage usage: ${usagePercent.toFixed(1)}%`
                    });
                }

                healthCheck.innerHTML = checks.map(check => `
                    <div class="flex items-center gap-2">
                        <div class="w-2 h-2 rounded-full ${check.status === 'good' ? 'bg-green-400' : 'bg-yellow-400'}"></div>
                        <span class="text-slate-300">${check.message}</span>
                    </div>
                `).join('');
            });
        }
    }

    // --- EXPORT/IMPORT FUNCTIONS ---

    function exportVisualUISettings(category) {
        const settings = {};

        switch (category) {
            case 'card-sizes':
                settings.mainDeckScale = localStorage.getItem('mainDeckScale') || '69';
                settings.sideDeckScale = localStorage.getItem('sideDeckScale') || '46';
                settings.extraDeckScale = localStorage.getItem('extraDeckScale') || '46';
                settings.dbDeckScale = localStorage.getItem('dbDeckScale') || '46';
                break;
            case 'cards-per-row':
                settings.mainDeckCardsPerRow = localStorage.getItem('mainDeckCardsPerRow') || '10';
                settings.sideDeckCardsPerRow = localStorage.getItem('sideDeckCardsPerRow') || '4';
                settings.extraDeckCardsPerRow = localStorage.getItem('extraDeckCardsPerRow') || '3';
                settings.dbDeckCardsPerRow = localStorage.getItem('dbDeckCardsPerRow') || '7';
                break;
            case 'panel-configs':
                settings.deckBuilderConfig = localStorage.getItem('deckBuilderConfig');
                settings.deckResizeConfig = localStorage.getItem('deckResizeConfig');
                break;
            case 'visibility-states':
                settings.extraDeckVisible = typeof extraDeckVisible !== 'undefined' ? extraDeckVisible : true;
                settings.sideDeckVisible = typeof sideDeckVisible !== 'undefined' ? sideDeckVisible : true;
                settings.databaseVisible = typeof databaseVisible !== 'undefined' ? databaseVisible : true;
                settings.cardDatabaseView = typeof UI !== 'undefined' && UI.cardDbList && UI.cardDbList.classList.contains('card-db-grid') ? 'grid' : 'list';
                break;
            case 'onboarding-flags':
                settings.hasSeenWelcome = localStorage.getItem('hasSeenWelcome');
                settings.ydkFolderPath = localStorage.getItem('ydkFolderPath');
                settings.offlineDecksIntroduced = localStorage.getItem('offlineDecksIntroduced');
                settings.cleanupVersion = localStorage.getItem('cleanupVersion');
                break;
        }

        const blob = new Blob([JSON.stringify(settings, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `visual-ui-${category}-settings.json`;
        a.click();
        URL.revokeObjectURL(url);
    }

    function exportAllVisualUISettings() {
        const allSettings = {
            cardSizes: {
                mainDeckScale: localStorage.getItem('mainDeckScale') || '69',
                sideDeckScale: localStorage.getItem('sideDeckScale') || '46',
                extraDeckScale: localStorage.getItem('extraDeckScale') || '46',
                dbDeckScale: localStorage.getItem('dbDeckScale') || '46'
            },
            cardsPerRow: {
                mainDeckCardsPerRow: localStorage.getItem('mainDeckCardsPerRow') || '10',
                sideDeckCardsPerRow: localStorage.getItem('sideDeckCardsPerRow') || '4',
                extraDeckCardsPerRow: localStorage.getItem('extraDeckCardsPerRow') || '3',
                dbDeckCardsPerRow: localStorage.getItem('dbDeckCardsPerRow') || '7'
            },
            panelConfigs: {
                deckBuilderConfig: localStorage.getItem('deckBuilderConfig'),
                deckResizeConfig: localStorage.getItem('deckResizeConfig')
            },
            visibilityStates: {
                extraDeckVisible: typeof extraDeckVisible !== 'undefined' ? extraDeckVisible : true,
                sideDeckVisible: typeof sideDeckVisible !== 'undefined' ? sideDeckVisible : true,
                databaseVisible: typeof databaseVisible !== 'undefined' ? databaseVisible : true,
                cardDatabaseView: typeof UI !== 'undefined' && UI.cardDbList && UI.cardDbList.classList.contains('card-db-grid') ? 'grid' : 'list'
            },
            onboardingFlags: {
                hasSeenWelcome: localStorage.getItem('hasSeenWelcome'),
                ydkFolderPath: localStorage.getItem('ydkFolderPath'),
                offlineDecksIntroduced: localStorage.getItem('offlineDecksIntroduced'),
                cleanupVersion: localStorage.getItem('cleanupVersion')
            }
        };

        const blob = new Blob([JSON.stringify(allSettings, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'visual-ui-all-settings.json';
        a.click();
        URL.revokeObjectURL(url);

        if (typeof showTooltip === 'function') {
            showTooltip('All visual UI settings exported successfully!');
        }
    }

    function resetVisualUISettings(category) {
        if (!confirm(`Reset ${category} settings to defaults? This cannot be undone.`)) return;

        switch (category) {
            case 'card-sizes':
                localStorage.removeItem('mainDeckScale');
                localStorage.removeItem('sideDeckScale');
                localStorage.removeItem('extraDeckScale');
                localStorage.removeItem('dbDeckScale');
                // Reload the page to apply defaults
                location.reload();
                break;
            case 'cards-per-row':
                localStorage.removeItem('mainDeckCardsPerRow');
                localStorage.removeItem('sideDeckCardsPerRow');
                localStorage.removeItem('extraDeckCardsPerRow');
                localStorage.removeItem('dbDeckCardsPerRow');
                location.reload();
                break;
            case 'panel-configs':
                localStorage.removeItem('deckBuilderConfig');
                localStorage.removeItem('deckResizeConfig');
                location.reload();
                break;
            case 'visibility-states':
                // Reset visibility states to defaults
                if (typeof extraDeckVisible !== 'undefined') extraDeckVisible = true;
                if (typeof sideDeckVisible !== 'undefined') sideDeckVisible = true;
                if (typeof databaseVisible !== 'undefined') databaseVisible = true;
                // Reset card database view to grid
                if (typeof UI !== 'undefined' && UI.cardDbList) {
                    UI.cardDbList.className = 'card-db-grid overflow-y-auto pr-2 flex-grow';
                }
                location.reload();
                break;
            case 'onboarding-flags':
                localStorage.removeItem('hasSeenWelcome');
                localStorage.removeItem('ydkFolderPath');
                localStorage.removeItem('offlineDecksIntroduced');
                localStorage.removeItem('cleanupVersion');
                location.reload();
                break;
        }
    }

    function exportCardDatabaseCache() {
        if (typeof cardDatabase === 'undefined') return;

        const cache = {
            cards: cardDatabase,
            lastUpdated: new Date().toISOString(),
            version: '1.0'
        };

        const blob = new Blob([JSON.stringify(cache, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'card-database-cache.json';
        a.click();
        URL.revokeObjectURL(url);
    }

    function resetCardDatabaseCache() {
        if (!confirm('Reset card database cache? This will force a fresh download from the API.')) return;

        localStorage.removeItem('offlineCardDatabase');
        localStorage.removeItem('cardDatabaseLastUpdated');
        if (typeof cardDatabase !== 'undefined') cardDatabase = [];
        if (typeof originalCardDatabase !== 'undefined') originalCardDatabase = [];
        location.reload();
    }

    function exportIndividualDeck(deckId) {
        if (typeof offlineDecks === 'undefined') return;

        const deck = offlineDecks.find(d => d.id === deckId);
        if (!deck) return;

        const blob = new Blob([JSON.stringify(deck, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${deck.name.replace(/[^a-zA-Z0-9]/g, '_')}.json`;
        a.click();
        URL.revokeObjectURL(url);
    }

    function deleteIndividualDeck(deckId) {
        if (!confirm('Delete this deck? This cannot be undone.')) return;

        if (typeof offlineDecks !== 'undefined') {
            const deckIndex = offlineDecks.findIndex(d => d.id === deckId);
            if (deckIndex === -1) return;

            offlineDecks.splice(deckIndex, 1);
            deleteOfflineDeckFromDB(deckId);
            updateDecksList();
            updateStorageOverview();
            if (typeof showTooltip === 'function') {
                showTooltip('Deck deleted');
            }
        }
    }

    function exportIndividualYDKFile(fileName) {
        if (typeof ydkFiles === 'undefined') return;

        const file = ydkFiles.find(f => f.name === fileName);
        if (!file) return;

        let content = file.content;
        let downloadName = file.name;

        // If it's a YDKX file, convert it to YDK format by stripping the extended section
        const isYDKX = file.format === 'ydkx' || file.name.toLowerCase().endsWith('.ydkx') || content.includes('#ydkx-extended');
        if (isYDKX) {
            // Parse and extract only the standard YDK sections
            const lines = content.split('\n');
            let ydkContent = '';
            let inExtendedSection = false;

            for (const line of lines) {
                const trimmedLine = line.trim();
                if (trimmedLine === '#ydkx-extended') {
                    inExtendedSection = true;
                    continue;
                }
                if (inExtendedSection) {
                    // Skip all lines in the extended section
                    continue;
                }
                // Keep all lines before the extended section
                ydkContent += line + '\n';
            }

            // Remove trailing newlines
            content = ydkContent.trimEnd();

            // Change extension to .ydk
            downloadName = file.name.replace(/\.ydkx$/i, '.ydk');
        }

        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = downloadName;
        a.click();
        URL.revokeObjectURL(url);
    }

    function deleteIndividualYDKFile(fileName) {
        if (!confirm('Delete this YDK file? This cannot be undone.')) return;

        if (typeof ydkFiles !== 'undefined') {
            const fileIndex = ydkFiles.findIndex(f => f.name === fileName);
            if (fileIndex === -1) return;

            ydkFiles.splice(fileIndex, 1);
            deleteYDKFileFromDB(fileName);
            updateYDKFilesList();
            updateStorageOverview();
            if (typeof showTooltip === 'function') {
                showTooltip('YDK file deleted');
            }
        }
    }

    function exportAllDecks() {
        if (typeof offlineDecks === 'undefined') return;

        const blob = new Blob([JSON.stringify(offlineDecks, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'all-decks.json';
        a.click();
        URL.revokeObjectURL(url);
    }

    function clearAllDecks() {
        if (!confirm('Clear all decks? This cannot be undone.')) return;

        if (typeof offlineDecks !== 'undefined') {
            offlineDecks.forEach(deck => {
                deleteOfflineDeckFromDB(deck.id);
            });
            offlineDecks = [];
            updateDecksList();
            updateStorageOverview();
            if (typeof showTooltip === 'function') {
                showTooltip('All decks cleared');
            }
        }
    }

    function bulkDeleteDecks() {
        if (typeof offlineDecks === 'undefined') return;

        const selectedDecks = Array.from(document.querySelectorAll('.deck-checkbox:checked'))
            .map(checkbox => checkbox.dataset.deckId);

        if (selectedDecks.length === 0) return;

        if (!confirm(`Delete ${selectedDecks.length} selected decks? This cannot be undone.`)) return;

        selectedDecks.forEach(deckId => {
            const deckIndex = offlineDecks.findIndex(d => d.id === deckId);
            if (deckIndex !== -1) {
                offlineDecks.splice(deckIndex, 1);
                deleteOfflineDeckFromDB(deckId);
            }
        });

        updateDecksList();
        updateStorageOverview();
        if (typeof showTooltip === 'function') {
            showTooltip(`${selectedDecks.length} decks deleted`);
        }
    }

    function exportAllYDKFiles() {
        if (typeof ydkFiles === 'undefined') return;

        // Create a ZIP-like structure (simplified)
        const files = ydkFiles.map(file => ({
            name: file.name,
            content: file.content,
            lastModified: file.lastModified
        }));

        const blob = new Blob([JSON.stringify(files, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'all-ydk-files.json';
        a.click();
        URL.revokeObjectURL(url);
    }

    function bulkDeleteYDKFiles() {
        if (typeof ydkFiles === 'undefined') return;

        const selectedFiles = Array.from(document.querySelectorAll('.ydk-checkbox:checked'))
            .map(checkbox => checkbox.dataset.fileName);

        if (selectedFiles.length === 0) return;

        if (!confirm(`Delete ${selectedFiles.length} selected YDK files? This cannot be undone.`)) return;

        selectedFiles.forEach(fileName => {
            const fileIndex = ydkFiles.findIndex(f => f.name === fileName);
            if (fileIndex !== -1) {
                ydkFiles.splice(fileIndex, 1);
                deleteYDKFileFromDB(fileName);
            }
        });

        updateYDKFilesList();
        updateStorageOverview();
        if (typeof showTooltip === 'function') {
            showTooltip(`${selectedFiles.length} YDK files deleted`);
        }
    }

    // --- CLEANUP & DIAGNOSTICS FUNCTIONS ---

    function performStorageCleanup() {
        if (!confirm('Perform storage cleanup? This will remove duplicates and orphaned data.')) return;

        // Remove duplicate cards
        if (typeof cardDatabase !== 'undefined') {
            const uniqueCards = [];
            const seenIds = new Set();

            cardDatabase.forEach(card => {
                if (!seenIds.has(card.id)) {
                    seenIds.add(card.id);
                    uniqueCards.push(card);
                }
            });

            const duplicateCount = cardDatabase.length - uniqueCards.length;
            if (duplicateCount > 0) {
                cardDatabase.length = 0;
                cardDatabase.push(...uniqueCards);
                if (typeof originalCardDatabase !== 'undefined') {
                    originalCardDatabase.length = 0;
                    originalCardDatabase.push(...uniqueCards);
                }
                localStorage.setItem('offlineCardDatabase', JSON.stringify(uniqueCards));
            }

            // Remove orphaned decks
            if (typeof offlineDecks !== 'undefined') {
                const validDecks = offlineDecks.filter(deck => deck.name && deck.main.length > 0);
                const orphanedCount = offlineDecks.length - validDecks.length;

                if (orphanedCount > 0) {
                    offlineDecks.length = 0;
                    offlineDecks.push(...validDecks);
                    // Update IndexedDB
                    validDecks.forEach(deck => {
                        saveOfflineDeckToDB(deck);
                    });
                }
            }

            updateStorageOverview();
            updateDiagnostics();
            if (typeof showTooltip === 'function') {
                showTooltip(`Cleanup complete: ${duplicateCount} duplicates, ${orphanedCount} orphaned items removed`);
            }
        }
    }

    function performSpecificCleanup(type) {
        switch (type) {
            case 'duplicates':
                // Remove duplicate cards
                if (typeof cardDatabase !== 'undefined') {
                    const uniqueCards = [];
                    const seenIds = new Set();

                    cardDatabase.forEach(card => {
                        if (!seenIds.has(card.id)) {
                            seenIds.add(card.id);
                            uniqueCards.push(card);
                        }
                    });

                    const duplicateCount = cardDatabase.length - uniqueCards.length;
                    if (duplicateCount > 0) {
                        cardDatabase.length = 0;
                        cardDatabase.push(...uniqueCards);
                        if (typeof originalCardDatabase !== 'undefined') {
                            originalCardDatabase.length = 0;
                            originalCardDatabase.push(...uniqueCards);
                        }
                        localStorage.setItem('offlineCardDatabase', JSON.stringify(uniqueCards));
                        if (typeof showTooltip === 'function') {
                            showTooltip(`Removed ${duplicateCount} duplicate cards`);
                        }
                    } else {
                        if (typeof showTooltip === 'function') {
                            showTooltip('No duplicates found');
                        }
                    }
                }
                break;

            case 'orphaned':
                // Remove orphaned decks
                if (typeof offlineDecks !== 'undefined') {
                    const validDecks = offlineDecks.filter(deck => deck.name && deck.main.length > 0);
                    const orphanedCount = offlineDecks.length - validDecks.length;

                    if (orphanedCount > 0) {
                        offlineDecks.length = 0;
                        offlineDecks.push(...validDecks);
                        if (typeof showTooltip === 'function') {
                            showTooltip(`Removed ${orphanedCount} orphaned decks`);
                        }
                    } else {
                        if (typeof showTooltip === 'function') {
                            showTooltip('No orphaned decks found');
                        }
                    }
                }
                break;

            case 'old-cache':
                // Clear old cache data
                localStorage.removeItem('offlineCardDatabase');
                localStorage.removeItem('cardDatabaseLastUpdated');
                if (typeof showTooltip === 'function') {
                    showTooltip('Old cache cleared');
                }
                break;

            case 'temp-files':
                // Clear temporary files (if any)
                if (typeof showTooltip === 'function') {
                    showTooltip('No temporary files found');
                }
                break;
        }

        updateStorageOverview();
        updateDiagnostics();
    }

    function resetAllStorage() {
        if (!confirm('Reset ALL storage? This will delete everything and cannot be undone.')) return;

        // Clear localStorage
        localStorage.clear();

        // Clear IndexedDB
        if (ydkDB) {
            const transaction = ydkDB.transaction(['ydkFiles', 'offlineDecks'], 'readwrite');
            transaction.objectStore('ydkFiles').clear();
            transaction.objectStore('offlineDecks').clear();
        }

        // Reset global variables
        if (typeof ydkFiles !== 'undefined') ydkFiles.length = 0;
        if (typeof offlineDecks !== 'undefined') offlineDecks.length = 0;
        if (typeof cardDatabase !== 'undefined') cardDatabase.length = 0;
        if (typeof originalCardDatabase !== 'undefined') originalCardDatabase.length = 0;

        if (typeof showTooltip === 'function') {
            showTooltip('All storage reset. Page will reload.');
        }
        setTimeout(() => location.reload(), 1000);
    }

    // --- TAB MANAGEMENT & EVENT LISTENERS ---

    function initializeStorageManagementTabs() {
        const tabButtons = document.querySelectorAll('.storage-tab-btn');
        const tabContents = document.querySelectorAll('.storage-tab-content');

        tabButtons.forEach(button => {
            button.addEventListener('click', () => {
                const targetTab = button.dataset.tab;

                // Update button states
                tabButtons.forEach(btn => {
                    btn.classList.remove('active', 'text-white', 'border-blue-500');
                    btn.classList.add('text-slate-400');
                });
                button.classList.add('active', 'text-white', 'border-blue-500');
                button.classList.remove('text-slate-400');

                // Update tab content visibility
                tabContents.forEach(content => {
                    content.classList.add('hidden');
                });
                const targetContent = document.getElementById(`${targetTab}-tab`);
                if (targetContent) {
                    targetContent.classList.remove('hidden');
                }
            });
        });
    }

    function setupEventListeners() {
        // Tab management
        initializeStorageManagementTabs();

        // Modal close button
        const modalClose = document.getElementById('storageManagementModalClose');
        if (modalClose) {
            modalClose.addEventListener('click', () => {
                document.getElementById('storageManagementModal').classList.add('hidden');
            });
        }

        // Visual UI export/import buttons
        document.querySelectorAll('.export-category-btn').forEach(btn => {
            btn.addEventListener('click', (e) => exportVisualUISettings(e.target.dataset.category));
        });

        document.querySelectorAll('.reset-category-btn').forEach(btn => {
            btn.addEventListener('click', (e) => resetVisualUISettings(e.target.dataset.category));
        });

        // Export All Visual UI Settings button
        const exportAllVisualUIBtn = document.getElementById('exportAllVisualUIBtn');
        if (exportAllVisualUIBtn) {
            exportAllVisualUIBtn.addEventListener('click', exportAllVisualUISettings);
        }

        // Card database buttons
        const exportCardDatabaseBtn = document.getElementById('exportCardDatabaseBtn');
        const resetCardDatabaseBtn = document.getElementById('resetCardDatabaseBtn');

        if (exportCardDatabaseBtn) {
            exportCardDatabaseBtn.addEventListener('click', exportCardDatabaseCache);
        }

        if (resetCardDatabaseBtn) {
            resetCardDatabaseBtn.addEventListener('click', resetCardDatabaseCache);
        }

        // Deck management buttons
        const exportAllDecksBtn = document.getElementById('exportAllDecksBtn');
        const clearAllDecksBtn = document.getElementById('clearAllDecksBtn');
        const bulkDeleteDecksBtn = document.getElementById('bulkDeleteDecksBtn');
        const selectAllDecks = document.getElementById('selectAllDecks');

        if (exportAllDecksBtn) {
            exportAllDecksBtn.addEventListener('click', exportAllDecks);
        }

        if (clearAllDecksBtn) {
            clearAllDecksBtn.addEventListener('click', clearAllDecks);
        }

        if (bulkDeleteDecksBtn) {
            bulkDeleteDecksBtn.addEventListener('click', bulkDeleteDecks);
        }

        if (selectAllDecks) {
            selectAllDecks.addEventListener('change', (e) => {
                document.querySelectorAll('.deck-checkbox').forEach(checkbox => {
                    checkbox.checked = e.target.checked;
                });
                updateBulkDeleteButton();
            });
        }

        // YDK file management buttons
        const exportAllYDKFilesBtn = document.getElementById('exportAllYDKFilesBtn');
        const clearAllYDKFilesBtn = document.getElementById('clearAllYDKFilesBtn');
        const bulkDeleteYDKFilesBtn = document.getElementById('bulkDeleteYDKFilesBtn');
        const selectAllYDKFiles = document.getElementById('selectAllYDKFiles');
        const syncYDKFolderBtn = document.getElementById('syncYDKFolderBtn');
        const importYDKFilesInput = document.getElementById('importYDKFilesInput');

        if (exportAllYDKFilesBtn) {
            exportAllYDKFilesBtn.addEventListener('click', exportAllYDKFiles);
        }

        if (clearAllYDKFilesBtn) {
            clearAllYDKFilesBtn.addEventListener('click', handleClearAllYDKFiles);
        }

        if (bulkDeleteYDKFilesBtn) {
            bulkDeleteYDKFilesBtn.addEventListener('click', bulkDeleteYDKFiles);
        }

        if (selectAllYDKFiles) {
            selectAllYDKFiles.addEventListener('change', (e) => {
                document.querySelectorAll('.ydk-checkbox').forEach(checkbox => {
                    checkbox.checked = e.target.checked;
                });
                updateBulkDeleteYDKButton();
            });
        }

        if (syncYDKFolderBtn) {
            syncYDKFolderBtn.addEventListener('click', () => {
                if (typeof importYDKFolder === 'function') {
                    importYDKFolder();
                }
            });
        }

        if (importYDKFilesInput) {
            importYDKFilesInput.addEventListener('change', importSingleYDKFile);
        }

        // Diagnostics buttons
        const refreshDiagnosticsBtn = document.getElementById('refreshDiagnosticsBtn');
        const performCleanupBtn = document.getElementById('performCleanupBtn');
        const resetAllStorageBtn = document.getElementById('resetAllStorageBtn');

        if (refreshDiagnosticsBtn) {
            refreshDiagnosticsBtn.addEventListener('click', updateDiagnostics);
        }

        if (performCleanupBtn) {
            performCleanupBtn.addEventListener('click', performStorageCleanup);
        }

        if (resetAllStorageBtn) {
            resetAllStorageBtn.addEventListener('click', resetAllStorage);
        }

        // Cleanup option buttons
        document.querySelectorAll('.cleanup-option-btn').forEach(btn => {
            btn.addEventListener('click', (e) => performSpecificCleanup(e.target.dataset.cleanup));
        });
    }

    // Helper function to show tooltip
    function showTooltip(message) {
        if (typeof window.showTooltip === 'function') {
            window.showTooltip(message);
        } else {
            console.log('Storage Manager:', message);
        }
    }

    // Public API
    return {
        init,
        openModal: openStorageManagementModal,
        saveYDKFile: saveYDKFileToDB,
        deleteYDKFile: deleteYDKFileFromDB,
        getYDKFiles: getAllYDKFilesFromDB,
        getOfflineDecks: getAllOfflineDecksFromDB,
        loadYDKFiles: loadYDKFilesFromDB,
        saveOfflineDeck: saveOfflineDeckToDB,
        deleteOfflineDeck: deleteOfflineDeckFromDB,
        saveSidingPattern: saveSidingPatternToDB,
        getSidingPatterns: getAllSidingPatternsFromDB,
        deleteSidingPattern: deleteSidingPatternFromDB,
        loadYDKDeck: loadYDKDeck,
        updateYDKFilesList: updateYDKFilesList,
        // Expose functions that are called from main HTML
        tryAccessDefaultYDKFolder,
        importSingleYDKFile,
        selectYDKFolder,
        setDefaultYDKDeck,
        autoLoadYDKOnStartup,
        updateYDKFolderStatus,
        updateYDKDeckSelector
    };
})();

console.log('Storage manager module loaded');

