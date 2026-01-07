// --- UI COMMON MODULE ---
// This module contains shared UI components and utilities
// These functions are already defined in main HTML, this file serves as documentation
// and a reference for module developers

// UI Functions (defined in main HTML):

/**
 * initializeUI()
 * Initializes all UI element references by finding elements by their ID
 * Populates the global UI object with element references
 */
function initializeUI() {
    const uiKeys = Object.keys(UI);
    for (const key of uiKeys) {
        UI[key] = document.getElementById(key);
    }
}

/**
 * showView(viewId)
 * Switches between different views in the application
 * @param {string} viewId - The ID of the view to show
 * @example showView('deckBuilderView')
 */
function showView(viewId) {
    // Exit selection mode when leaving deck builder view
    if (viewId !== 'deckBuilderView' && selectionMode.active) {
        exitSelectionMode();
    }

    ['deckBuilderView', 'simulationView', 'reportView', 'aiTrainingView', 'shootoutView', 'shootoutSidingView', 'shootoutSimulatorView', 'shootoutReportView', 'sidingBuilderView'].forEach(id => {
        const view = document.getElementById(id);
        if (view) view.classList.add('hidden');
    });
    const activeView = document.getElementById(viewId);
    if (activeView) activeView.classList.remove('hidden');

    UI.navDeckBuilder.classList.toggle('bg-[var(--color-primary)]', viewId === 'deckBuilderView');
    UI.navSimulator.classList.toggle('bg-[var(--color-primary)]', ['simulationView', 'reportView'].includes(viewId));
    UI.navAiTraining.classList.toggle('bg-[var(--color-primary)]', viewId === 'aiTrainingView');
    UI.navShootout.classList.toggle('bg-[var(--color-primary)]', ['shootoutView', 'shootoutSidingView', 'shootoutSimulatorView', 'shootoutReportView'].includes(viewId));
}

/**
 * showMessage(text)
 * Displays a message modal with the given text
 * @param {string} text - The message to display
 */
function showMessage(text) {
    UI.messageModalText.textContent = text;
    UI.messageModal.classList.remove('hidden');
}

/**
 * showTooltip(text, duration)
 * Displays a temporary tooltip notification
 * @param {string} text - The tooltip text to display
 * @param {number} duration - Duration in milliseconds (default: 3000)
 */
function showTooltip(text, duration = 3000) {
    // Remove any existing tooltip
    const existingTooltip = document.getElementById('tooltip');
    if (existingTooltip) {
        existingTooltip.remove();
    }

    // Create tooltip element
    const tooltip = document.createElement('div');
    tooltip.id = 'tooltip';
    tooltip.textContent = text;
    tooltip.className = 'fixed top-4 right-4 bg-green-600 text-white px-4 py-2 rounded-lg shadow-lg z-50 transform transition-all duration-300 ease-in-out';
    tooltip.style.opacity = '0';
    tooltip.style.transform = 'translateY(-10px)';

    document.body.appendChild(tooltip);

    // Animate in
    requestAnimationFrame(() => {
        tooltip.style.opacity = '1';
        tooltip.style.transform = 'translateY(0)';
    });

    // Auto-dismiss
    setTimeout(() => {
        tooltip.style.opacity = '0';
        tooltip.style.transform = 'translateY(-10px)';
        setTimeout(() => {
            if (tooltip.parentNode) {
                tooltip.parentNode.removeChild(tooltip);
            }
        }, 300);
    }, duration);
}

/**
 * showConfirmModal(text, onConfirm)
 * Displays a confirmation modal with custom action
 * @param {string} text - The confirmation message
 * @param {Function} onConfirm - Callback function to execute on confirmation
 */
function showConfirmModal(text, onConfirm) {
    UI.confirmModalText.textContent = text;
    confirmCallback = onConfirm;
    UI.confirmModal.classList.remove('hidden');
}

console.log('UI common module loaded');

