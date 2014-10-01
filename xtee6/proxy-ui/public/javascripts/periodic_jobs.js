(function(PERIODIC_JOBS, $, undefined) {
    function refreshAlerts() {
        $.ajax({
            url: "/application/alerts",
            global: false,
            success: function(response) {
                showAlerts(response.alerts)
            },
            dataType: "json"
        });

        window.setTimeout(refreshAlerts, 30000);
    }

    $(document).ready(function() {
        refreshAlerts();
    });

    PERIODIC_JOBS.refreshAlerts = refreshAlerts;

}(window.PERIODIC_JOBS = window.PERIODIC_JOBS || {}, jQuery));
