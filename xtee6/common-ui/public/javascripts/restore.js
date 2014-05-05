var restoreConfiguration = function() {
    function uploadCallback(response) {
        if (response.success) {
            resetRestoreForm();
        }

        showMessages(response.messages);
    }

    function resetRestoreForm() {
        $("#restore_configuration_file").val("");
        $("#restore_accept").disable();
    }

    return {
        uploadCallback: uploadCallback,
        resetRestoreForm: resetRestoreForm
    }
}();

$(document).ready(function() {
    restoreConfiguration.resetRestoreForm();

    $("#restore_configuration_file").live("change", function(){
        if (isInputFilled($(this))) {
            $("#restore_accept").enable();
        } else {
            $("#restore_accept").disable();
        }
    });
});