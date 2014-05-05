$(document).ready(function() {
    $("#backup").click(function() {
        $.post(action("backup"), function(data) {
            if (data.success == 1) {
                notice(_("backup.done"));
                window.location = action("download?tarfile=" + data.tarfile);
            } else {
                error(_("backup.error", [data.exit_status]));
            }
        });
    });
});
