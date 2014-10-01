var SDSB_CERTS_UPLOADER = function() {
    var afterCertsSubmissionCallback;
    var uploadingTempCerts = false;
    var loadButtons = []

    /* API functions - start */

    function submitNextCertUpload() {
        if (!isUploadingTempCerts()) {
            return;
        }

        var nextLoadButton = dequeueCertUploadButton();

        if (nextLoadButton == null) {
            performPostCertUploadsAction();
            return;
        }

        nextLoadButton.click();
    }

    /**
     * Handles cert upload event and enqueues upload button if filename is 
     * present.
     * 
     * Assumes that file field and upload button are in same table row
     * ('tr' element).
     * */
    function manageCertFileSelection(fileFieldSelector) {
        var uploadButton = fileFieldSelector.closest("tr").find("td > button");

        if (isInputFilled(fileFieldSelector)) {
            enqueueCertUploadButton(uploadButton);
        }

        uploadButton.enable();
    }

    function initSubmittingCerts(afterSubmissionCallback) {
        afterCertsSubmissionCallback = afterSubmissionCallback;
        setUploadingTempCerts();
        submitNextCertUpload();
    }

    /* API functions - end */

    function enqueueCertUploadButton(button) {
        loadButtons.push(button);
    }

    function dequeueCertUploadButton() {
        return loadButtons.shift();
    }

    function setUploadingTempCerts() {
        uploadingTempCerts = true;
    }

    function clearUploadingTempCerts() {
        uploadingTempCerts = false;
        afterCertsSubmissionCallback = null;
    }

    function isUploadingTempCerts() {
        return uploadingTempCerts;
    }

    /**
     * Action performed when all temp certs are uploaded
     */
    function performPostCertUploadsAction() {
        afterCertsSubmissionCallback();
        clearUploadingTempCerts();
    }

    return {
        submitNextCertUpload: submitNextCertUpload,
        manageCertFileSelection: manageCertFileSelection,
        initSubmittingCerts: initSubmittingCerts
    }
}();
