var SDSB_PERIODIC_JOBS = function() {
    function refreshAlerts() {
        if (typeof confBackup == "undefined" || !confBackup.restoreInProgress()) {
            $.ajax({
                url: "/system_status/check_status",
                global: false,
                success: function(response) {
                    showAlerts(response.alerts);
                },
                dataType: "json"
            });
        }

        window.setTimeout(refreshAlerts, 30000);
    }

    $(document).ready(function() {
        refreshAlerts();
    });

    return {
        refreshAlerts: refreshAlerts
    };
}();
