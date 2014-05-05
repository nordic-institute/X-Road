function uploadCallbackImportV5Data(response) {
    showMessages(response.messages);
}

$(document).ready(function(){
    $("#import_v5_upload").live("click", function() {
        $("#v5_mapping_file_upload").submit();
        // function uploadCallbackImportV5Data manages post-submission
        // activities on UI part
    });
});
