(() => {
    const input = document.getElementById('file-upload');
    const nameDisplay = document.getElementById('filename');
    const filesContainer = document.getElementById('files-container');
    const dropZone = document.getElementById('drop-zone');
    const tableBody = filesContainer?.querySelector('table tbody');
    const uploadAssetsBtn = document.getElementById('upload-assets-btn');

    // Helper method to convert bytes to a readable format
    const humanFileSize = (bytes, si = true, dp=1) => {
        const thresh = si ? 1000 : 1024;

        if (Math.abs(bytes) < thresh) {
            return bytes + ' B';
        }

        const units = si
            ? ['KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
            : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
        let u = -1;
        const r = 10**dp;

        do {
            bytes /= thresh;
            ++u;
        } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);

        return bytes.toFixed(dp) + ' ' + units[u];
    }

    // File Change Event Handler
    const handleFileChange = () => {
        if (input.files.length) {
            nameDisplay?.classList.add('hidden');
            const tableRowData = document.createDocumentFragment();
            for (let i = 0; i < input.files.length; i++) {
                const file = input.files[i];
                const tr = document.createElement('tr');
                const nameData = document.createElement('td');
                const typeData = document.createElement('td');
                const sizeData = document.createElement('td');
                const statusData = document.createElement('td');
                const statusIcon = document.createElement('img');
                nameData.textContent = file.name;
                typeData.textContent = file.type;
                sizeData.textContent = humanFileSize(file.size);
                statusIcon.src = "/upload-fail.svg";
                statusData.append(statusIcon);
                tr.append(nameData, typeData, sizeData, statusData);
                tableRowData.append(tr);
            }
            tableBody.append(tableRowData);
            filesContainer?.classList.add('show');
        } else {
            nameDisplay.textContent = 'No files chosen';
        }
    };

    const handleAssetsUpload = async (e) => {
        console.log('Clicked upload button');
        if (input.files.length) {
            const formData = new FormData();
            Array.from(input.files).forEach((file) => {
                formData.append('files', file);
            });
            formData.append('folderPath', '/content/dam/assetupload');

            console.log('Form data: ', formData);

            // 3. Send with fetch()
            try {
                const response = await fetch('/api/asset/upload', {
                    method: 'POST',
                    body: formData
                });

                console.log('Response: ', response);

                if (!response.ok) {
                    console.error('Error upload assets to spring boot service: ', response.status);
                }
                const result = await response.json();
                console.log('Upload successful:', result);
            } catch (err) {
                console.error('Upload failed:', err);
            }
        }
    };

    const preventDefaults = (e) => {
        e.preventDefault()
        e.stopPropagation()
    };

    function handleDrop(e) {
        let dt = e.dataTransfer
        let files = dt.files;

        if (files && files.length > 0) {
            handleFiles(files);
        } else {
            console.error("No files were dropped.");
        }
    }

    function handleFiles(files) {
        const dataTransfer = new DataTransfer();
        for (let i = 0; i < files.length; i++) {
            dataTransfer.items.add(files[i]);
        }
        input.files = dataTransfer.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    /* Event Listeners */
    input.addEventListener('change', () => handleFileChange());
    uploadAssetsBtn.addEventListener('click', (e) => handleAssetsUpload(e));
    dropZone.addEventListener('drop', handleDrop, false);

    // Prevent default drag behaviors
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
    });

})();
