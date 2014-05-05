
function uploadCallbackIdentifierMapping(response) {
    showMessages(response.messages);
}

$(document).ready(function(){
    $("#identifier_mapping_upload").live("click", function() {
        $("#identifier_mapping_file_upload").submit();
        // function uploadCallbackIdentifierMapping manages post-submission
        // activities on UI part
    });
});