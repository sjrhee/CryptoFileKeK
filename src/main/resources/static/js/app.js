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
        showError('Failed to load file list.');
    }
}

function populateSelect(selectElement, files, filterFn) {
    const currentVal = selectElement.value;
    selectElement.innerHTML = '<option value="">Select a file...</option>';

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

function restartCurrentMode() {
    // 1. Reset selection state for current mode
    if (currentMode === 'encrypt') {
        selectedFiles.encrypt = null;
        document.getElementById('encryptFileSelect').value = '';
        document.getElementById('encryptFileName').textContent = '';
        document.getElementById('encryptFileInfo').classList.add('hidden');
        document.getElementById('encryptNextBtn').disabled = true;

        // Hide Step 3, Show Step 1
        document.getElementById('encryptStep3').classList.add('hidden');
        document.getElementById('encryptStep1').classList.remove('hidden');
    } else if (currentMode === 'decrypt') {
        selectedFiles.decrypt = { file: null, dek: null };
        document.getElementById('decryptFileSelect').value = '';
        document.getElementById('decryptDekSelect').value = '';
        document.getElementById('decryptNextBtn').disabled = true;

        // Hide Step 3, Show Step 1
        document.getElementById('decryptStep3').classList.add('hidden');
        document.getElementById('decryptStep1').classList.remove('hidden');
    }

    // 2. Refresh file lists to show any newly created files
    refreshFileList();

    // 3. Reset progress bar for next run (optional visual cleanup)
    updateProgress(currentMode, 0, 'Preparing...');

    // 4. Update wizard steps
    updateWizardStep(1);
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
        updateProgress('encrypt', 20, 'Checking file...');

        const selectResponse = await fetch('/api/encrypt/select', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename: selectedFiles.encrypt })
        });

        const selectResult = await handleResponse(selectResponse);
        const fileId = selectResult.data.fileId;

        // Process Encryption
        updateProgress('encrypt', 50, 'Encrypting and saving...');
        const processResponse = await fetch(`/api/encrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        const result = processResult.data;

        // Complete
        updateProgress('encrypt', 100, 'Complete!');

        setTimeout(() => {
            document.getElementById('encryptStep2').classList.add('hidden');
            document.getElementById('encryptStep3').classList.remove('hidden');
            updateWizardStep(3);


            // Safely update Original Name
            const elOrigName = document.getElementById('resultOriginalName');
            if (elOrigName) elOrigName.textContent = result.originalFilename || '-';

            // Safely update Original Size
            const elOrigSize = document.getElementById('resultOriginalSize');
            if (elOrigSize) elOrigSize.textContent = formatFileSize(result.originalSize);

            // Safely update Encrypted Size
            const elEncSize = document.getElementById('resultEncryptedSize');
            if (elEncSize) elEncSize.textContent = formatFileSize(result.encryptedSize);

            // Safely update Encrypted Name
            const elEncName = document.getElementById('resultEncryptedName');
            if (elEncName) {
                elEncName.textContent = result.encryptedFilename || '-';
            }

            const dekElement = document.getElementById('resultEncryptedDek');
            if (dekElement) {
                const dekValue = result.encryptedDek;
                if (dekValue && dekValue.length > 0) {
                    dekElement.textContent = dekValue;
                    dekElement.style.color = 'white'; // Ensure white color
                } else {
                    dekElement.textContent = '(Error: Server returned empty key)';
                    dekElement.style.color = '#ff6b6b';
                }
            }

            // Update Download Buttons
            const downloadFileBtn = document.getElementById('downloadEncryptedFileBtn');
            const downloadDekBtn = document.getElementById('downloadDekBtn');

            console.log('Setting download links for session:', fileId);

            if (downloadFileBtn) {
                // Use persistent endpoint based on filename
                downloadFileBtn.href = `/api/files/download/${encodeURIComponent(result.encryptedFilename)}`;
                // User Request: Force the filename to be the actual encrypted filename
                downloadFileBtn.setAttribute('download', result.encryptedFilename);
            }

            if (downloadDekBtn) {
                const dekFilename = result.originalFilename + '.dek';
                downloadDekBtn.href = `/api/files/download/${encodeURIComponent(dekFilename)}`;
                // User Request: Force the filename to be the DEK filename
                downloadDekBtn.setAttribute('download', dekFilename);
            }

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
        updateProgress('decrypt', 20, 'Checking files...');

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
        updateProgress('decrypt', 50, 'Decrypting and saving...');
        const processResponse = await fetch(`/api/decrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        const result = processResult.data;

        // Complete
        updateProgress('decrypt', 100, 'Complete!');

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

// Upload Files
async function uploadFiles() {
    const input = document.getElementById('uploadInput');
    const files = input.files;

    if (!files || files.length === 0) {
        alert('Please select files to upload.');
        return;
    }

    try {
        let uploadedCount = 0;
        for (let i = 0; i < files.length; i++) {
            const formData = new FormData();
            formData.append('file', files[i]);

            const response = await fetch('/api/files/upload', {
                method: 'POST',
                body: formData
            });

            const result = await handleResponse(response);
            if (result.success) uploadedCount++;
        }

        alert(`Successfully uploaded ${uploadedCount} file(s).`);
        input.value = ''; // Clear input

        // Refresh list and try to auto-select uploaded files
        await refreshFileList();

    } catch (error) {
        console.error('Upload failed', error);
        showError('Failed to upload files: ' + error.message);
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
