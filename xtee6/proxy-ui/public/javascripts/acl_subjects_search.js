(function(ACL_SUBJECTS_SEARCH, $, undefined) {

    var dialog = "#acl_subjects_search_dialog";
    var oAclSubjectsSearch;

    var unselectableAclSubjects;
    var onAdd;
    var onNext;

    function enableActions() {
        if ($("#acl_subjects_search .row_selected").length > 0) {
            $("#acl_subjects_search_add_selected").enable();
            $("#acl_subjects_search_next").enable();
        } else {
            $("#acl_subjects_search_add_selected").disable();
            $("#acl_subjects_search_next").disable();
        }

        if ($("#acl_subjects_search .selectable").length > 0) {
            $("#acl_subjects_search_add_all").enable();
        } else {
            $("#acl_subjects_search_add_all").disable();
        }
    }

    function initAclSubjectsSearchDialog() {
        $("#acl_subjects_search_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 700,
            width: "95%",
            open: function() {
                if (onAdd) {
                    $("#acl_subjects_search_next").hide();
                    $("#acl_subjects_search_add_selected").show();
                    $("#acl_subjects_search_add_all").show();
                } else {
                    $("#acl_subjects_search_next").show();
                    $("#acl_subjects_search_add_selected").hide();
                    $("#acl_subjects_search_add_all").hide();
                }

                oAclSubjectsSearch.fnClearTable();
                enableActions();
            },
            buttons: [
                { text: _("clients.acl_subjects_search_dialog.add_selected"),
                  id: "acl_subjects_search_add_selected",
                  click: function() {
                      var subjectIds = [];

                      $.each(oAclSubjectsSearch.fnGetNodes(), function(idx, val) {
                          if ($(val).hasClass("row_selected")) {
                              var subject = oAclSubjectsSearch.fnGetData(val);
                              subjectIds.push(subject.subject_id);
                          }
                      });

                      onAdd(subjectIds);
                      $(dialog).dialog("close");
                  }
                },
                { text: _("clients.acl_subjects_search_dialog.add_all"),
                  id: "acl_subjects_search_add_all",
                  click: function() {



                      confirm("clients.acl_subjects_search_dialog.add_all_confirm", null,
                          function() {

                              var subjectIds = [];

                              $.each(oAclSubjectsSearch.fnGetNodes(), function(idx, val) {
                                  if (!$(val).hasClass("unselectable")) {
                                      var subject = oAclSubjectsSearch.fnGetData(val);
                                      subjectIds.push(subject.subject_id);
                                  }
                              });

                              onAdd(subjectIds);
                              $(dialog).dialog("close");

                          });



                  }
                },
                { text: _("common.next"),
                  id: "acl_subjects_search_next",
                  click: function() {
                      onNext(oAclSubjectsSearch.getFocusData());
                      $(dialog).dialog("close");
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(dialog).dialog("close");
                  }
                }
            ]
        });
    }

    function initAclSubjectsSearchTable() {
        var opts = scrollableTableOpts(345);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "name_description", mRender: util.escape },
            {
                mData: function(source, type, val) {
                    return generateIdElement({
                        "Type": source.type,
                        "Instance": source.instance,
                        "Class": source.member_class,
                        "Code": source.member_group_code,
                        "Subsystem": source.subsystem_code
                    });
                }
            }
        ];
        opts.fnRowCallback = function(nRow, oData) {
            var unselectable = false;

            $.each(unselectableAclSubjects, function(idx, val) {
                if (oData.instance == val.instance &&
                    oData.type == val.type &&
                    oData.member_class == val.member_class &&
                    oData.member_group_code == val.member_group_code &&
                    oData.subsystem_code == val.subsystem_code) {

                    unselectable = true;
                    return false;
                }
            });

            if (unselectable) {
                $(nRow).addClass("unselectable");
            } else {
                $(nRow).addClass("selectable");
            }

            return nRow;
        };

        oAclSubjectsSearch = $("#acl_subjects_search").dataTable(opts);
    }

    function initAclSubjectsSearchActions() {
        $(".advanced_search .search", dialog).click(function() {
            var params = $(".advanced_search", dialog)
                .find("input, select").serializeObject();

            params.client_id = $("#details_client_id").val();

            $.get(action("acl_subjects_search"), params, function(response) {
                oAclSubjectsSearch.fnReplaceData(response.data);
                enableActions();
            }, "json");

            return false;
        });

        $(".simple_search .search", dialog).click(function() {
            var params = $(".simple_search input", dialog).serializeObject();
            params.client_id = $("#details_client_id").val();

            $.get(action("acl_subjects_search"), params, function(response) {
                oAclSubjectsSearch.fnReplaceData(response.data);
                enableActions();
            }, "json");

            return false;
        });

        $("#acl_subjects_search tbody .selectable").live("click", function() {
            if (onNext) {
                oAclSubjectsSearch.setFocus(0, this);
            } else {
                oAclSubjectsSearch.setFocus(0, this, true);
            }
            enableActions();
        });
    }
    
    function initTestability() {
        // add data-name attributes to improve testability
        $("#acl_subjects_search_dialog").parent().attr("data-name", "acl_subjects_search_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
    }

    $(document).ready(function() {
        initAclSubjectsSearchDialog();
        initAclSubjectsSearchTable();
        initAclSubjectsSearchActions();
        initTestability();
    });

    ACL_SUBJECTS_SEARCH.openDialog = function(_unselectableAclSubjects, _onAdd) {
        unselectableAclSubjects = _unselectableAclSubjects;
        onAdd = _onAdd;
        onNext = undefined;
        $(dialog).dialog("open");
    };

    ACL_SUBJECTS_SEARCH.openDialogWithNext = function(_unselectableAclSubjects, _onNext) {
        unselectableAclSubjects = _unselectableAclSubjects;
        onAdd = undefined;
        onNext = _onNext;
        $(dialog).dialog("open");
    };

}(window.ACL_SUBJECTS_SEARCH = window.ACL_SUBJECTS_SEARCH || {}, jQuery));
