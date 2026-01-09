// Application State
let currentMode = null;
let currentStep = 1;
let selectedFiles = {
    encrypt: null,
    decrypt: { file: null, dek: null }
};

// Initialize
document.addEventListener('DOMContentLoaded', function () {
    setupHandlers();
    refreshFileList();
});

// Setup Handlers
function setupHandlers() {
    // Encryption File Select
    document.getElementById('encryptFileSelect').addEventListener('change', (e) => {
        const filename = e.target.value;
        if (filename) {
            document.getElementById('encryptFileName').textContent = filename;
            document.getElementById('encryptFileInfo').classList.remove('hidden');
            document.getElementById('encryptNextBtn').disabled = false;
            selectedFiles.encrypt = filename;
        } else {
            document.getElementById('encryptFileInfo').classList.add('hidden');
            document.getElementById('encryptNextBtn').disabled = true;
            selectedFiles.encrypt = null;
        }
    });

    // Decryption File Selects
    document.getElementById('decryptFileSelect').addEventListener('change', checkDecryptReady);
    document.getElementById('decryptDekSelect').addEventListener('change', checkDecryptReady);
}

// Refresh File List
async function refreshFileList() {
    try {
        const response = await fetch('/api/files/list');
        const result = await handleResponse(response);
        const files = result.data;

        // Populate Encrypt Select (All files except .dek?)
        const encryptSelect = document.getElementById('encryptFileSelect');
        populateSelect(encryptSelect, files, f => !f.endsWith('.dek') && !f.endsWith('.encrypted'));

        // Populate Decrypt File Select (.encrypted only)
        const decryptFileSelect = document.getElementById('decryptFileSelect');
        populateSelect(decryptFileSelect, files, f => f.endsWith('.encrypted'));

        // Populate Decrypt DEK Select (.dek only)
        const decryptDekSelect = document.getElementById('decryptDekSelect');
        populateSelect(decryptDekSelect, files, f => f.endsWith('.dek'));

    } catch (error) {
        console.error('Failed to load file list', error);
        showError('파일 목록을 불러오지 못했습니다.');
    }
}

function populateSelect(selectElement, files, filterFn) {
    const currentVal = selectElement.value;
    selectElement.innerHTML = '<option value="">파일을 선택하세요...</option>';

    files.filter(filterFn).forEach(file => {
        const option = document.createElement('option');
        option.value = file;
        option.textContent = file;
        selectElement.appendChild(option);
    });

    // Maintain selection if possible
    if (files.includes(currentVal)) {
        selectElement.value = currentVal;
    }
}

// Mode Selection
function selectMode(mode) {
    currentMode = mode;
    currentStep = 1;
    document.getElementById('modeSelection').classList.add('hidden');
    document.getElementById('wizardSteps').classList.remove('hidden');

    if (mode === 'encrypt') {
        document.getElementById('encryptWizard').classList.remove('hidden');
    } else {
        document.getElementById('decryptWizard').classList.remove('hidden');
    }
    updateWizardStep(1);
    refreshFileList(); // Refresh list on mode switch
}

function resetWizard() {
    currentMode = null;
    currentStep = 1;
    selectedFiles = { encrypt: null, decrypt: { file: null, dek: null } };

    document.getElementById('encryptWizard').classList.add('hidden');
    document.getElementById('decryptWizard').classList.add('hidden');
    document.getElementById('wizardSteps').classList.add('hidden');
    document.getElementById('errorAlert').classList.add('hidden');

    // Reset selects
    document.querySelectorAll('select').forEach(s => s.value = '');

    // Hide info
    document.getElementById('encryptFileInfo').classList.add('hidden');

    document.getElementById('modeSelection').classList.remove('hidden');
}

function updateWizardStep(step) {
    currentStep = step;
    const steps = document.querySelectorAll('.wizard-step');
    steps.forEach((stepEl, index) => {
        const stepNum = index + 1;
        stepEl.classList.remove('active', 'completed');
        if (stepNum < step) stepEl.classList.add('completed');
        else if (stepNum === step) stepEl.classList.add('active');
    });
}

function checkDecryptReady() {
    const encFile = document.getElementById('decryptFileSelect').value;
    const dekFile = document.getElementById('decryptDekSelect').value;

    selectedFiles.decrypt.file = encFile;
    selectedFiles.decrypt.dek = dekFile;

    document.getElementById('decryptNextBtn').disabled = !(encFile && dekFile);
}

// Process Encryption
async function processEncryption() {
    try {
        if (!selectedFiles.encrypt) return;

        // Show step 2
        document.getElementById('encryptStep1').classList.add('hidden');
        document.getElementById('encryptStep2').classList.remove('hidden');
        updateWizardStep(2);

        // Select File (server-side)
        updateProgress('encrypt', 20, '파일 확인 중...');

        const selectResponse = await fetch('/api/encrypt/select', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename: selectedFiles.encrypt })
        });

        const selectResult = await handleResponse(selectResponse);
        const fileId = selectResult.data.fileId;

        // Process Encryption
        updateProgress('encrypt', 50, '암호화 및 저장 중...');
        const processResponse = await fetch(`/api/encrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        const result = processResult.data;

        // Complete
        updateProgress('encrypt', 100, '완료!');

        setTimeout(() => {
            document.getElementById('encryptStep2').classList.add('hidden');
            document.getElementById('encryptStep3').classList.remove('hidden');
            updateWizardStep(3);

            document.getElementById('resultOriginalName').textContent = result.originalFilename;
            document.getElementById('resultOriginalSize').textContent = formatFileSize(result.originalSize);
            document.getElementById('resultEncryptedSize').textContent = formatFileSize(result.encryptedSize);
            document.getElementById('resultEncryptedName').textContent = result.encryptedFilename;
            document.getElementById('resultEncryptedDek').textContent = result.encryptedDek.substring(0, 50) + '...';

            refreshFileList(); // Update lists for next time
        }, 500);

    } catch (error) {
        showError(error.message);
        resetToStep1('encrypt');
    }
}

// Process Decryption
async function processDecryption() {
    try {
        if (!selectedFiles.decrypt.file || !selectedFiles.decrypt.dek) return;

        // Show step 2
        document.getElementById('decryptStep1').classList.add('hidden');
        document.getElementById('decryptStep2').classList.remove('hidden');
        updateWizardStep(2);

        // Select Files (server-side)
        updateProgress('decrypt', 20, '파일 확인 중...');

        const selectResponse = await fetch('/api/decrypt/select', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                encryptedFilename: selectedFiles.decrypt.file,
                dekFilename: selectedFiles.decrypt.dek
            })
        });

        const selectResult = await handleResponse(selectResponse);
        const fileId = selectResult.data.fileId;

        // Process Decryption
        updateProgress('decrypt', 50, '복호화 및 저장 중...');
        const processResponse = await fetch(`/api/decrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        const result = processResult.data;

        // Complete
        updateProgress('decrypt', 100, '완료!');

        setTimeout(() => {
            document.getElementById('decryptStep2').classList.add('hidden');
            document.getElementById('decryptStep3').classList.remove('hidden');
            updateWizardStep(3);

            document.getElementById('resultDecryptedName').textContent = result.originalFilename; // This might need adjustment if logic changed
            document.getElementById('resultDecryptedEncSize').textContent = formatFileSize(result.encryptedSize);
            document.getElementById('resultDecryptedSize').textContent = formatFileSize(result.decryptedSize);

            refreshFileList();
        }, 500);

    } catch (error) {
        showError(error.message);
        resetToStep1('decrypt');
    }
}

// Utils
function updateProgress(mode, percent, text) {
    document.getElementById(`${mode}Progress`).style.width = `${percent}%`;
    document.getElementById(`${mode}ProgressText`).textContent = text;
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

async function handleResponse(response) {
    const data = await response.json();
    if (!response.ok || !data.success) {
        throw new Error(data.message || 'An error occurred');
    }
    return data;
}

function showError(message) {
    document.getElementById('errorMessage').textContent = message;
    document.getElementById('errorAlert').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('errorAlert').classList.add('hidden');
    }, 5000);
}

function resetToStep1(mode) {
    if (mode === 'encrypt') {
        document.getElementById('encryptStep2').classList.add('hidden');
        document.getElementById('encryptStep1').classList.remove('hidden');
    } else {
        document.getElementById('decryptStep2').classList.add('hidden');
        document.getElementById('decryptStep1').classList.remove('hidden');
    }
    updateWizardStep(1);
}
