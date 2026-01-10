/**
 * File Encryption System - Frontend Logic
 * Structure: Module Pattern (State, API, UI, App)
 */

// ==========================================
// 1. STATE MODULE
// ==========================================
const State = {
    currentMode: null, // 'encrypt' | 'decrypt'
    currentStep: 1,
    selectedFiles: {
        encrypt: null,
        decrypt: { file: null, dek: null }
    },

    reset() {
        this.currentMode = null;
        this.currentStep = 1;
        this.selectedFiles = {
            encrypt: null,
            decrypt: { file: null, dek: null }
        };
    },

    setMode(mode) {
        this.currentMode = mode;
        this.currentStep = 1;
    }
};

// ==========================================
// 2. API MODULE
// ==========================================
const API = {
    async get(url) {
        const response = await fetch(url);
        return this.handleResponse(response);
    },

    async post(url, body = {}) {
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        return this.handleResponse(response);
    },

    async postFormData(url, formData) {
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });
        return this.handleResponse(response);
    },

    async delete(url) {
        const response = await fetch(url, { method: 'DELETE' });
        // DELETE endpoints might not return JSON content, handle gracefully
        if (!response.ok) throw new Error('Delete operation failed');
        return true;
    },

    async handleResponse(response) {
        const data = await response.json();
        if (!response.ok || !data.success) {
            throw new Error(data.message || 'An error occurred');
        }
        return data.data;
    },

    // Specific API Calls
    listFiles: () => API.get('/api/files/list'),
    uploadFile: (formData) => API.postFormData('/api/files/upload', formData),
    cleanupTemp: () => API.post('/api/files/cleanup-temp'),

    encrypt: {
        select: (filename) => API.post('/api/encrypt/select', { filename }),
        process: (fileId) => API.post(`/api/encrypt/process/${fileId}`)
    },

    decrypt: {
        select: (encryptedFilename, dekFilename) => API.post('/api/decrypt/select', { encryptedFilename, dekFilename }),
        process: (fileId) => API.post(`/api/decrypt/process/${fileId}`)
    }
};

// ==========================================
// 3. UI MODULE
// ==========================================
const UI = {
    elements: {
        modeSelection: 'modeSelection',
        wizardSteps: 'wizardSteps',
        encryptWizard: 'encryptWizard',
        decryptWizard: 'decryptWizard',
        errorAlert: 'errorAlert',
        errorMessage: 'errorMessage',
        encryptFileSelect: 'encryptFileSelect',
        decryptFileSelect: 'decryptFileSelect',
        decryptDekSelect: 'decryptDekSelect'
    },

    getElement(id) {
        return document.getElementById(id);
    },

    show(id) {
        const el = this.getElement(id);
        if (el) el.classList.remove('hidden');
    },

    hide(id) {
        const el = this.getElement(id);
        if (el) el.classList.add('hidden');
    },

    updateStep(step) {
        const steps = document.querySelectorAll('.wizard-step');
        steps.forEach((stepEl, index) => {
            const stepNum = index + 1;
            stepEl.classList.remove('active', 'completed');
            if (stepNum < step) stepEl.classList.add('completed');
            else if (stepNum === step) stepEl.classList.add('active');
        });
    },

    updateProgress(mode, percent, text) {
        const bar = this.getElement(`${mode}Progress`);
        const label = this.getElement(`${mode}ProgressText`);
        if (bar) bar.style.width = `${percent}%`;
        if (label) label.textContent = text;
    },

    populateSelect(id, files, filterFn, currentValue) {
        const select = this.getElement(id);
        if (!select) return;

        select.innerHTML = '<option value="">Select a file...</option>';
        files.filter(filterFn).forEach(file => {
            const option = document.createElement('option');
            option.value = file;
            option.textContent = file;
            select.appendChild(option);
        });

        if (files.includes(currentValue)) {
            select.value = currentValue;
        }
    },

    showError(message) {
        const elMsg = this.getElement(this.elements.errorMessage);
        if (elMsg) elMsg.textContent = message;
        this.show(this.elements.errorAlert);
        setTimeout(() => this.hide(this.elements.errorAlert), 5000);
    },

    resetAll() {
        // Hide wizards
        this.hide(this.elements.encryptWizard);
        this.hide(this.elements.decryptWizard);
        this.hide(this.elements.wizardSteps);
        this.hide(this.elements.errorAlert);

        // Show mode selection
        this.show(this.elements.modeSelection);

        // Reset inputs
        document.querySelectorAll('select').forEach(s => s.value = '');
        this.hide('encryptFileInfo');
    },

    // Helpers
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }
};

// ==========================================
// 4. APP CONTROLLER
// ==========================================
const App = {
    init() {
        document.addEventListener('DOMContentLoaded', () => {
            this.setupHandlers();
            this.refreshFileList();
        });
    },

    setupHandlers() {
        // Encrypt File Selection
        UI.getElement('encryptFileSelect').addEventListener('change', (e) => {
            const filename = e.target.value;
            State.selectedFiles.encrypt = filename || null;

            if (filename) {
                UI.getElement('encryptFileName').textContent = filename;
                UI.show('encryptFileInfo');
                UI.getElement('encryptNextBtn').disabled = false;
            } else {
                UI.hide('encryptFileInfo');
                UI.getElement('encryptNextBtn').disabled = true;
            }
        });

        // Decrypt File Selections
        const checkDecrypt = () => {
            const encFile = UI.getElement('decryptFileSelect').value;
            const dekFile = UI.getElement('decryptDekSelect').value;
            State.selectedFiles.decrypt = { file: encFile, dek: dekFile };
            UI.getElement('decryptNextBtn').disabled = !(encFile && dekFile);
        };

        UI.getElement('decryptFileSelect').addEventListener('change', checkDecrypt);
        UI.getElement('decryptDekSelect').addEventListener('change', checkDecrypt);
    },

    async refreshFileList() {
        try {
            const files = await API.listFiles();

            const encryptVal = State.selectedFiles.encrypt;
            const decryptFileVal = State.selectedFiles.decrypt.file;
            const decryptDekVal = State.selectedFiles.decrypt.dek;

            UI.populateSelect('encryptFileSelect', files, f => !f.endsWith('.dek') && !f.endsWith('.encrypted'), encryptVal);
            UI.populateSelect('decryptFileSelect', files, f => f.endsWith('.encrypted'), decryptFileVal);
            UI.populateSelect('decryptDekSelect', files, f => f.endsWith('.dek'), decryptDekVal);

        } catch (error) {
            console.error('List error:', error);
            UI.showError('Failed to load file list.');
        }
    },

    async uploadFiles() {
        const input = UI.getElement('uploadInput');
        const files = input.files;
        if (!files || files.length === 0) {
            alert('Please select files to upload.');
            return;
        }

        try {
            let count = 0;
            for (let i = 0; i < files.length; i++) {
                const formData = new FormData();
                formData.append('file', files[i]);
                await API.uploadFile(formData);
                count++;
            }
            alert(`Successfully uploaded ${count} file(s).`);
            input.value = '';
            await this.refreshFileList();
        } catch (error) {
            console.error('Upload error:', error);
            UI.showError('Failed to upload files: ' + error.message);
        }
    },

    selectMode(mode) {
        State.setMode(mode);
        UI.hide('modeSelection');
        UI.show('wizardSteps');

        if (mode === 'encrypt') {
            UI.show('encryptWizard');
            // Ensure step 1 is visible
            UI.show('encryptStep1');
            UI.hide('encryptStep2');
            UI.hide('encryptStep3');
        } else {
            UI.show('decryptWizard');
            // Ensure step 1 is visible
            UI.show('decryptStep1');
            UI.hide('decryptStep2');
            UI.hide('decryptStep3');
        }

        UI.updateStep(1);
        this.refreshFileList();
    },

    async resetWizard() {
        try {
            await API.cleanupTemp();
            console.log('Temp Cleanup Requested via Start Operation');
        } catch (e) {
            console.warn('Cleanup failed', e);
        }
        State.reset();
        UI.resetAll();
    },

    async restartCurrentMode() {
        const mode = State.currentMode;
        if (!mode) return;

        // Cleanup temp files
        try {
            await API.cleanupTemp();
            console.log('Temp Cleanup Requested');
        } catch (e) {
            console.warn('Cleanup failed', e);
        }

        // Reset UI for current mode
        if (mode === 'encrypt') {
            State.selectedFiles.encrypt = null;
            UI.getElement('encryptFileSelect').value = '';
            UI.hide('encryptFileInfo');
            UI.getElement('encryptNextBtn').disabled = true;

            UI.hide('encryptStep3');
            UI.show('encryptStep1');
        } else {
            State.selectedFiles.decrypt = { file: null, dek: null };
            UI.getElement('decryptFileSelect').value = '';
            UI.getElement('decryptDekSelect').value = '';
            UI.getElement('decryptNextBtn').disabled = true;

            UI.hide('decryptStep3');
            UI.show('decryptStep1');
        }

        this.refreshFileList();
        UI.updateProgress(mode, 0, 'Preparing...');
        UI.updateStep(1);
    },

    // -------------------------------------------------------------------------
    // ENCRYPTION FLOW
    // -------------------------------------------------------------------------
    async processEncryption() {
        if (!State.selectedFiles.encrypt) return;
        const mode = 'encrypt';

        try {
            // UI Transition to Step 2
            UI.hide('encryptStep1');
            UI.show('encryptStep2');
            UI.updateStep(2);

            UI.updateProgress(mode, 20, 'Checking file...');
            const selResult = await API.encrypt.select(State.selectedFiles.encrypt);

            UI.updateProgress(mode, 50, 'Encrypting and saving...');
            const finalResult = await API.encrypt.process(selResult.fileId);

            UI.updateProgress(mode, 100, 'Complete!');

            // UI Transition to Step 3 (Delayed for UX)
            setTimeout(() => {
                UI.hide('encryptStep2');
                UI.show('encryptStep3');
                UI.updateStep(3);
                this.renderEncryptionResults(finalResult);
                this.refreshFileList();
            }, 500);

        } catch (error) {
            UI.showError(error.message);
            this.resetToStep1(mode);
        }
    },

    renderEncryptionResults(result) {
        const setText = (id, txt) => {
            const el = UI.getElement(id);
            if (el) el.textContent = txt;
        };

        setText('resultOriginalName', result.originalFilename || '-');
        setText('resultOriginalSize', UI.formatFileSize(result.originalSize));
        setText('resultEncryptedSize', UI.formatFileSize(result.encryptedSize));
        setText('resultEncryptedName', result.encryptedFilename || '-');

        const dekEl = UI.getElement('resultEncryptedDek');
        if (dekEl) {
            dekEl.textContent = result.encryptedDek || 'Error';
            dekEl.style.color = result.encryptedDek ? 'white' : '#ff6b6b';
        }

        // Setup Downloads
        const btnFile = UI.getElement('downloadEncryptedFileBtn');
        if (btnFile) {
            btnFile.href = `/api/files/download/${encodeURIComponent(result.encryptedFilename)}`;
            btnFile.setAttribute('download', result.encryptedFilename);
        }

        const btnDek = UI.getElement('downloadDekBtn');
        if (btnDek) {
            const dekName = result.originalFilename + '.dek';
            btnDek.href = `/api/files/download/${encodeURIComponent(dekName)}`;
            btnDek.setAttribute('download', dekName);
        }
    },

    // -------------------------------------------------------------------------
    // DECRYPTION FLOW
    // -------------------------------------------------------------------------
    async processDecryption() {
        const { file, dek } = State.selectedFiles.decrypt;
        if (!file || !dek) return;
        const mode = 'decrypt';

        try {
            UI.hide('decryptStep1');
            UI.show('decryptStep2');
            UI.updateStep(2);

            UI.updateProgress(mode, 20, 'Checking files...');
            const selResult = await API.decrypt.select(file, dek);

            UI.updateProgress(mode, 50, 'Decrypting and saving...');
            const finalResult = await API.decrypt.process(selResult.fileId);

            UI.updateProgress(mode, 100, 'Complete!');

            setTimeout(() => {
                UI.hide('decryptStep2');
                UI.show('decryptStep3');
                UI.updateStep(3);
                this.renderDecryptionResults(finalResult);
                this.refreshFileList();
            }, 500);

        } catch (error) {
            UI.showError(error.message);
            this.resetToStep1(mode);
        }
    },

    renderDecryptionResults(result) {
        const setText = (id, txt) => {
            const el = UI.getElement(id);
            if (el) el.textContent = txt;
        };
        setText('resultDecryptedName', result.originalFilename);
        // Note: Decryption result might not return sizes in same structure, adjusting if needed
        // Assuming it does based on previous code
    },

    // Helper to fallback
    resetToStep1(mode) {
        if (mode === 'encrypt') {
            UI.hide('encryptStep2');
            UI.show('encryptStep1');
        } else {
            UI.hide('decryptStep2');
            UI.show('decryptStep1');
        }
        UI.updateStep(1);
    }
};

// ==========================================
// 5. BOOTSTRAP
// ==========================================
// Expose functions to global scope for HTML onclick handlers
window.selectMode = (m) => App.selectMode(m);
window.refreshFileList = () => App.refreshFileList();
window.uploadFiles = () => App.uploadFiles();
window.resetWizard = () => App.resetWizard();
window.restartCurrentMode = () => App.restartCurrentMode();
window.processEncryption = () => App.processEncryption();
window.processDecryption = () => App.processDecryption();

// Initialize App
App.init();
