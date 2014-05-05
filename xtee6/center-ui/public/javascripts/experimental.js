$(document).ready(function() {

    $("#create_system_parameters").live("click", function(event) {
        $.post(action("create_system_parameters"), null);
    });

    $("#create_members_and_security_servers").live("click", function(event) {
        $.post(action("create_members_and_security_servers"), null);
    });

    $("#delete_members_and_security_servers").live("click", function(event) {
        $.post(action("delete_members_and_security_servers"), null);
    });

    $("#create_requests").live("click", function(event) {
        $.post(action("create_requests"), null);
    });

    $("#delete_requests").live("click", function(event) {
        $.post(action("delete_requests"), null);
    });

    $("#create_pkis").live("click", function(event) {
        $.post(action("create_pkis"), null);
    });

    $("#delete_pkis").live("click", function(event) {
        $.post(action("delete_pkis"), null);
    });

    $("#create_tsps").live("click", function(event) {
        $.post(action("create_tsps"), null);
    });

    $("#delete_tsps").live("click", function(event) {
        $.post(action("delete_tsps"), null);
    });

    $("#create_global_groups").live("click", function(event) {
        $.post(action("create_global_groups"), null);
    });

    $("#delete_global_groups").live("click", function(event) {
        $.post(action("delete_global_groups"), null);
    });

    $("#delete_system_parameters").live("click", function(event) {
        $.post(action("delete_system_parameters"), null);
    });

    // Create/delete all

    $("#create_all").live("click", function(event) {
        $.post(action("create_all"), null);
    });

    $("#delete_all").live("click", function(event) {
        $.post(action("delete_all"), null);
    });

    // Experiments

    $("#test_failed_transaction").live("click", function(event) {
        $.post(action("test_failed_transaction"), null);
    });

    $("#test_nested_transaction").live("click", function(event) {
        $.post(action("test_nested_transaction"), null);
    });

    $("#test_failed_parent_transaction").live("click", function(event) {
        $.post(action("test_failed_parent_transaction"), null);
    });

    $("#test_getting_remaining_global_groups").live("click", function(event) {
        $.post(action("test_getting_remaining_global_groups"), null);
    });

    $("#test_global_group_addition_and_removal").live("click", function(event) {
        $.post(action("test_global_group_addition_and_removal"), null);
    });
});
