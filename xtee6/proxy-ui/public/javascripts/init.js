function uploadCallback(response) {
    if (!response.success) {
        showMessages(response.messages);
        return;
    }
    confirm("anchor.upload_confirm", response.data, function() {
        $.post(action("anchor_init"), null, function() {
            if ($("#serverconf_form").length > 0) {
                $("#anchor_upload_form").hide();
                $("#serverconf_form").show();

                populateOwnerClassSelect();
            } else {
                alert("init.index.initialized", null, function() {
                    redirect("clients");
                });
            }
        }, "json");
    });
}

function populateOwnerClassSelect() {
    if ($("#owner_class").is("[disabled]")) {
        return;
    }

    $.get(action("member_classes"), null, function(response) {
        var select = $("#owner_class").html("");

        $.each(response.data, function() {
            select.append($("<option />").val(this).text(this));
        });

        populateOwnerCodeSelect();
    }, "json");
}

function populateOwnerCodeSelect() {
    var params = {
        member_class: $("#owner_class").val()
    };

    $.get(action("member_codes"), params, function(response) {
        $("#owner_code").autocomplete("option", "source", response.data);
    }, "json");
}

function initConfigurationAnchorActions() {
    $("#anchor_upload_file").change(function() {
        if ($(this).val() != "") {
            $("#anchor_upload_submit").enable();
        }
        $(".selected_file").val($(this).val());
    }).val("");

    $("#anchor_upload_submit").disable();
}

function initServerConfActions() {
    var namefetch = function() {
        var timer = 0;
        return function(callback, ms) {
            clearTimeout(timer);
            timer = setTimeout(callback, ms);
        };
    }();

    $("#owner_class").change(function() {
        populateOwnerCodeSelect();
        $("#owner_code").keyup();
    });

    $("#owner_code").autocomplete({
        delay: 0,
        minLength: 0,
        select: function() {
            $("#owner_code").keyup();
        },
    });

    $("#owner_code").keyup(function() {
        namefetch(function () {
            var params = $("#owner_class, #owner_code").serialize();
            $.get(action("member_name"), params, function(response) {
                $("#owner_name").html(response.data.name);
            });
        }, 500);
    });

    populateOwnerClassSelect();

    $("#submit_serverconf").click(function() {
        var params = $("#serverconf_form").serializeObject();

        $.post(action("serverconf_init"), params, function() {
            alert("init.index.initialized", null, function() {
                redirect("clients");
            });
        }, "json");

        return false;
    });
}

$(document).ready(function() {
    $("#sidebar, #server-info").hide();
    $("#main, #content").css("width", "100%");

    if ($("#anchor_upload_form").length > 0) {
        $("#serverconf_form").hide();
    }

    initConfigurationAnchorActions();
    initServerConfActions();
});
