function clientName(name) {
    return name ||
        "&lt;" + _("clients.client_acl_subjects_tab.client_not_found") + "&gt;";
}

function groupDesc(desc) {
    return desc ||
        "&lt;" + _("clients.client_acl_subjects_tab.group_not_found") + "&gt;";
}
