(function(INIT, $, undefined) {
    $(document).ready(function() {
        $("#sidebar, #server-info").hide();
        $("#main, #content").css("width", "100%");

        $("#init-container #submit").click(function() {
            var params = $("#init-container form").serializeObject();

            $.post(action("init"), params, function() {
                alert("init.index.initialized", null, function() {
                    redirect("members");
                });
            }, "json");

            return false;
        });
    });
}(window.INIT = window.INIT || {}, jQuery));
