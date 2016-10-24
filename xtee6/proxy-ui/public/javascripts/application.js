function clientName(name) {
    return util.escape(name) ||
        "&lt;" + _("clients.client_acl_subjects_tab.client_not_found") + "&gt;";
}

function groupDesc(desc) {
    return util.escape(desc) ||
        "&lt;" + _("clients.client_acl_subjects_tab.group_not_found") + "&gt;";
}
