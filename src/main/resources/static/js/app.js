// Application State
let currentMode = null;
let currentStep = 1;
let encryptionData = {};
let decryptionData = {};

// Initialize
document.addEventListener('DOMContentLoaded', function () {
    setupFileUploadHandlers();
});

// Mode Selection
function selectMode(mode) {
    currentMode = mode;
    currentStep = 1;

    // Hide mode selection
    document.getElementById('modeSelection').classList.add('hidden');

    // Show wizard steps
    document.getElementById('wizardSteps').classList.remove('hidden');

    // Show appropriate wizard
    if (mode === 'encrypt') {
        document.getElementById('encryptWizard').classList.remove('hidden');
        updateWizardStep(1);
    } else {
        document.getElementById('decryptWizard').classList.remove('hidden');
        updateWizardStep(1);
    }
}

// Reset Wizard
function resetWizard() {
    currentMode = null;
    currentStep = 1;
    encryptionData = {};
    decryptionData = {};

    // Hide all wizards
    document.getElementById('encryptWizard').classList.add('hidden');
    document.getElementById('decryptWizard').classList.add('hidden');
    document.getElementById('wizardSteps').classList.add('hidden');
    document.getElementById('errorAlert').classList.add('hidden');

    // Reset file inputs
    document.getElementById('encryptFileInput').value = '';
    document.getElementById('decryptFileInput').value = '';
    document.getElementById('decryptDekInput').value = '';

    // Hide file info
    document.getElementById('encryptFileInfo').classList.add('hidden');
    document.getElementById('decryptFileInfo').classList.add('hidden');
    document.getElementById('decryptDekInfo').classList.add('hidden');

    // Show mode selection
    document.getElementById('modeSelection').classList.remove('hidden');
}

// Update Wizard Steps
function updateWizardStep(step) {
    currentStep = step;

    const steps = document.querySelectorAll('.wizard-step');
    steps.forEach((stepEl, index) => {
        const stepNum = index + 1;
        stepEl.classList.remove('active', 'completed');

        if (stepNum < step) {
            stepEl.classList.add('completed');
        } else if (stepNum === step) {
            stepEl.classList.add('active');
        }
    });
}

// File Upload Handlers
function setupFileUploadHandlers() {
    // Encryption file upload
    const encryptUploadArea = document.getElementById('encryptUploadArea');
    const encryptFileInput = document.getElementById('encryptFileInput');

    encryptUploadArea.addEventListener('click', () => encryptFileInput.click());
    encryptFileInput.addEventListener('change', handleEncryptFileSelect);

    // Drag and drop
    encryptUploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        encryptUploadArea.classList.add('drag-over');
    });

    encryptUploadArea.addEventListener('dragleave', () => {
        encryptUploadArea.classList.remove('drag-over');
    });

    encryptUploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        encryptUploadArea.classList.remove('drag-over');
        if (e.dataTransfer.files.length > 0) {
            encryptFileInput.files = e.dataTransfer.files;
            handleEncryptFileSelect();
        }
    });

    // Decryption file upload
    const decryptFileUploadArea = document.getElementById('decryptFileUploadArea');
    const decryptFileInput = document.getElementById('decryptFileInput');

    decryptFileUploadArea.addEventListener('click', () => decryptFileInput.click());
    decryptFileInput.addEventListener('change', handleDecryptFileSelect);

    // Decryption DEK upload
    const decryptDekUploadArea = document.getElementById('decryptDekUploadArea');
    const decryptDekInput = document.getElementById('decryptDekInput');

    decryptDekUploadArea.addEventListener('click', () => decryptDekInput.click());
    decryptDekInput.addEventListener('change', handleDecryptDekSelect);
}

// Handle Encrypt File Selection
function handleEncryptFileSelect() {
    const fileInput = document.getElementById('encryptFileInput');
    const file = fileInput.files[0];

    if (file) {
        document.getElementById('encryptFileName').textContent = file.name;
        document.getElementById('encryptFileSize').textContent = formatFileSize(file.size);
        document.getElementById('encryptFileInfo').classList.remove('hidden');
        document.getElementById('encryptNextBtn').disabled = false;
        encryptionData.file = file;
    }
}

// Handle Decrypt File Selection
function handleDecryptFileSelect() {
    const fileInput = document.getElementById('decryptFileInput');
    const file = fileInput.files[0];

    if (file) {
        document.getElementById('decryptFileName').textContent = file.name;
        document.getElementById('decryptFileSize').textContent = formatFileSize(file.size);
        document.getElementById('decryptFileInfo').classList.remove('hidden');
        decryptionData.file = file;
        checkDecryptReady();
    }
}

// Handle Decrypt DEK Selection
function handleDecryptDekSelect() {
    const dekInput = document.getElementById('decryptDekInput');
    const file = dekInput.files[0];

    if (file) {
        document.getElementById('decryptDekName').textContent = file.name;
        document.getElementById('decryptDekInfo').classList.remove('hidden');
        decryptionData.dekFile = file;
        checkDecryptReady();
    }
}

// Check if decryption is ready
function checkDecryptReady() {
    const ready = decryptionData.file && decryptionData.dekFile;
    document.getElementById('decryptNextBtn').disabled = !ready;
}

// Process Encryption
async function processEncryption() {
    try {
        // Show step 2
        document.getElementById('encryptStep1').classList.add('hidden');
        document.getElementById('encryptStep2').classList.remove('hidden');
        updateWizardStep(2);

        // Upload file
        updateProgress('encrypt', 30, 'DEK 생성 중...');
        const formData = new FormData();
        formData.append('file', encryptionData.file);

        const uploadResponse = await fetch('/api/encrypt/upload', {
            method: 'POST',
            body: formData
        });

        const uploadResult = await handleResponse(uploadResponse);
        const fileId = uploadResult.data.fileId;

        // Process encryption
        updateProgress('encrypt', 60, '파일 암호화 중...');
        const processResponse = await fetch(`/api/encrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        encryptionData.result = processResult.data;

        // Complete
        updateProgress('encrypt', 100, '완료!');

        setTimeout(() => {
            document.getElementById('encryptStep2').classList.add('hidden');
            document.getElementById('encryptStep3').classList.remove('hidden');
            updateWizardStep(3);
            displayEncryptionResults();
        }, 500);

    } catch (error) {
        showError(error.message);
        resetToStep1('encrypt');
    }
}

// Process Decryption
async function processDecryption() {
    try {
        // Show step 2
        document.getElementById('decryptStep1').classList.add('hidden');
        document.getElementById('decryptStep2').classList.remove('hidden');
        updateWizardStep(2);

        // Upload files
        updateProgress('decrypt', 30, 'DEK 복호화 중...');
        const formData = new FormData();
        formData.append('encryptedFile', decryptionData.file);
        formData.append('encryptedDek', decryptionData.dekFile);

        const uploadResponse = await fetch('/api/decrypt/upload', {
            method: 'POST',
            body: formData
        });

        const uploadResult = await handleResponse(uploadResponse);
        const fileId = uploadResult.data.fileId;

        // Process decryption
        updateProgress('decrypt', 60, '파일 복호화 중...');
        const processResponse = await fetch(`/api/decrypt/process/${fileId}`, {
            method: 'POST'
        });

        const processResult = await handleResponse(processResponse);
        decryptionData.result = processResult.data;

        // Complete
        updateProgress('decrypt', 100, '완료!');

        setTimeout(() => {
            document.getElementById('decryptStep2').classList.add('hidden');
            document.getElementById('decryptStep3').classList.remove('hidden');
            updateWizardStep(3);
            displayDecryptionResults();
        }, 500);

    } catch (error) {
        showError(error.message);
        resetToStep1('decrypt');
    }
}

// Display Encryption Results
function displayEncryptionResults() {
    const result = encryptionData.result;
    document.getElementById('resultOriginalName').textContent = result.originalFilename;
    document.getElementById('resultOriginalSize').textContent = formatFileSize(result.originalSize);
    document.getElementById('resultEncryptedSize').textContent = formatFileSize(result.encryptedSize);
    document.getElementById('resultEncryptedDek').textContent = result.encryptedDek.substring(0, 50) + '...';
}

// Display Decryption Results
function displayDecryptionResults() {
    const result = decryptionData.result;
    document.getElementById('resultDecryptedName').textContent = result.originalFilename;
    document.getElementById('resultDecryptedEncSize').textContent = formatFileSize(result.encryptedSize);
    document.getElementById('resultDecryptedSize').textContent = formatFileSize(result.decryptedSize);
}

// Download Functions
function downloadEncryptedFile() {
    const fileId = encryptionData.result.fileId;
    window.location.href = `/api/encrypt/download/file/${fileId}`;
}

function downloadEncryptedDek() {
    const fileId = encryptionData.result.fileId;
    window.location.href = `/api/encrypt/download/dek/${fileId}`;
}

function downloadDecryptedFile() {
    const fileId = decryptionData.result.fileId;
    window.location.href = `/api/decrypt/download/${fileId}`;
}

// Utility Functions
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
