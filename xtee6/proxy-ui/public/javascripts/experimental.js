function getMethodParam(methodName) {
    return {method: methodName};
}

$(document).ready(function() {
    $("#list_devices").live("click", function(event) {
        $("#list_devices_result").load(action("experiment"),
                getMethodParam("list_devices"));
    });

    $("#activate_device").live("click", function(event) {
        $("#activate_device_result").load(action("experiment"),
                getMethodParam("activate_device"));
    });

    $("#deactivate_device").live("click", function(event) {
        $("#deactivate_device_result").load(action("experiment"),
                getMethodParam("deactivate_device"));
    });

    $("#set_device_friendly_name").live("click", function(event) {
        $("#set_device_friendly_name_result").load(action("experiment"),
                getMethodParam("set_device_friendly_name"));
    });

    $("#set_key_friendly_name").live("click", function(event) {
        $("#set_key_friendly_name_result").load(action("experiment"),
                getMethodParam("set_key_friendly_name"));
    });

    $("#generate_key").live("click", function(event) {
        $("#generate_key_result").load(action("experiment"),
                getMethodParam("generate_key"));
    });

    $("#import_cert").live("click", function(event) {
        $("#import_cert_result").load(action("experiment"),
                getMethodParam("import_cert"));
    });

    $("#activate_cert").live("click", function(event) {
        $("#activate_cert_result").load(action("experiment"),
                getMethodParam("activate_cert"));
    });

    $("#deactivate_cert").live("click", function(event) {
        $("#deactivate_cert_result").load(action("experiment"),
                getMethodParam("deactivate_cert"));
    });

    $("#generate_cert_request").live("click", function(event) {
        $("#generate_cert_request_result").load(action("experiment"),
                getMethodParam("generate_cert_request"));
    });

    $("#delete_cert").live("click", function(event) {
        $("#delete_cert_result").load(action("experiment"),
                getMethodParam("delete_cert"));
    });

    $("#delete_key").live("click", function(event) {
        $("#delete_key_result").load(action("experiment"),
                getMethodParam("delete_key"));
    });

    $("#get_cached_devices").live("click", function(event) {
        $("#get_cached_devices_result").load(action("experiment"),
                getMethodParam("get_cached_devices"));
    });
});
