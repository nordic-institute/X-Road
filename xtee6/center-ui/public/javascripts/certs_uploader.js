var SDSB_CERTS_UPLOADER = function() {
    var afterCertsSubmissionCallback;
    var uploadingTempCerts = false;
    var loadButtons = [];

    /* API functions - start */

    function submitNextCertUpload() {
        if (!uploadingTempCerts) {
            return;
        }

        var nextLoadButton = loadButtons.shift();

        if (nextLoadButton == null) {
            // all temp certs are uploaded
            afterCertsSubmissionCallback();
            afterCertsSubmissionCallback = null;
            uploadingTempCerts = false;

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
            loadButtons.push(uploadButton);
        }

        uploadButton.enable();
    }

    function initSubmittingCerts(afterCertsSubmissionCallback_) {
        afterCertsSubmissionCallback = afterCertsSubmissionCallback_;
        uploadingTempCerts = true;
        submitNextCertUpload();
    }

    /* API functions - end */

    return {
        submitNextCertUpload: submitNextCertUpload,
        manageCertFileSelection: manageCertFileSelection,
        initSubmittingCerts: initSubmittingCerts
    }
}();
