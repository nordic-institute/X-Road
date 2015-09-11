var XROAD_TRUSTED_ANCHORS = function() {
    var tab = "#trusted_anchors_tab";
    var anchorClass = "trusted-anchor";
    var anchorTemplateClass = "trusted-anchor-template";
    var noTrustedAnchorsClass = "trusted-anchor-none";

    /* -- PUBLIC - START -- */

    function init() {
        XROAD_CENTERUI_COMMON.openDetailsIfAllowed(
                "configuration_management/can_view_trusted_anchors",
                function() {
            initHandlers();
            showUploadButton();
            updateAnchors();
        });
    }

    function uploadCallback(response) {
        if (response.success) {
            closeFileUploadDialog();
            openAnchorSaveDialog(response.data);
        }

        showMessages(response.messages);
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function showUploadButton() {
        $("#upload_trusted_anchor").show();
    }

    function updateAnchors() {
        clearOldAnchors();
        fillWithNewAnchors();
    }

    function clearOldAnchors() {
        $("div." + anchorClass, tab).remove();
    }

    function fillWithNewAnchors() {
        $.get("configuration_management/trusted_anchors", {},
                function(response){
            renderTrustedAnchors(response.data);
        }, "json");
    }

    function renderTrustedAnchors(anchors) {
        $("." + noTrustedAnchorsClass).remove();

        if (anchors.length == 0) {
            renderNoAnchorsMessage();
            return;
        }

        var anchorTemplate = getAnchorTemplate();

        $.each(anchors, function(index, each) {
            var newAnchorElement = getNewAnchorElement(
                    anchorTemplate, each);
            $("#trusted_anchors_tab").append(newAnchorElement);
        });
    }

    function renderNoAnchorsMessage() {
        var messageElement = $("<p/>", {
            text: _("configuration_management.trusted_anchors.none"),
            class: noTrustedAnchorsClass
        });

        $("#trusted_anchors_tab").append(messageElement);
    }

    function getAnchorTemplate() {
        return $("div." + anchorTemplateClass + ":first");
    }

    function getNewAnchorElement(rawAnchorTemplate, anchor) {
        var result = rawAnchorTemplate.clone();
        result.removeClass(anchorTemplateClass);
        result.addClass(anchorClass);

        var instanceIdentifier = anchor.instance_identifier;
        var anchorHeadingElement = result.find(".box-heading:first");

        if (anchor.can_delete) {
            anchorHeadingElement.append(
                getDeleteButton(anchor.id, instanceIdentifier));
        }

        if (anchor.can_download) {
            anchorHeadingElement.append(getDownloadButton(anchor.id));
        }

        var anchorTitleElement = result.find(".box-title:first");
        anchorTitleElement.text(instanceIdentifier);

        var anchorHashElement = result.find("span.anchor-hash:first");
        anchorHashElement.text(anchor.hash);

        var anchorGeneratedAtElement =
            result.find("span.anchor-generated_at:first");
        var generatedAt = anchor.generated_at;
        anchorGeneratedAtElement.text(generatedAt == null ? "" : generatedAt);

        return result;
    }

    function getDownloadButton(anchorId) {
        return $("<button/>", {
            text:_("configuration_management.trusted_anchors.download"),
            class: "right",
            click: function()  { downloadAnchor(anchorId) }
        });
    }

    function getDeleteButton(anchorId, instanceIdentifier) {
        return $("<button/>", {
            text:_("configuration_management.trusted_anchors.delete"),
            class: "right btn",
            click: function()  { deleteAnchor(anchorId, instanceIdentifier) }
        });
    }

    /* -- REFRESH DATA - END -- */

    /* -- POST REQUESTS - START -- */

    function saveTrustedAnchor(dialog) {
        $.post("configuration_management/save_uploaded_trusted_anchor", {},
                function() {
            updateAnchors();
            $(dialog).dialog("close");
        }, "json");
    }

    function clearTrustedAnchor(dialog) {
        $.post("configuration_management/clear_uploaded_trusted_anchor", {},
                function() {
            $(dialog).dialog("close");
        }, "json");
    }

    function deleteAnchor(anchorId, instanceIdentifier) {
        var confirmParams = { instanceIdentifier: instanceIdentifier };
        var params = {
            id: anchorId,
            instanceIdentifier: instanceIdentifier
        };

        confirm("configuration_management.trusted_anchors.delete_confirm",
                confirmParams, function() {
            $.post(action("delete_trusted_anchor"), params, function() {
                updateAnchors();
            }, "json");
        });
    }

    /* -- POST REQUESTS - END -- */

    /* -- MISC - START -- */

    function initHandlers() {
        $("#upload_trusted_anchor").click(function() {
            openFileUploadDialog(action("upload_trusted_anchor"),
                _("configuration_management.trusted_anchors.upload.dialog_title"));
        });
    }

    function downloadAnchor(anchorId) {
        window.location =
            "configuration_management/download_trusted_anchor?id=" + anchorId
    }

    /* -- MISC - END -- */

    /* -- DIALOGS - START -- */

    function openAnchorSaveDialog(anchorInfo) {
        $("#trusted_anchor_instance").text(anchorInfo.instance);
        $("#trusted_anchor_generated").text(anchorInfo.generated);
        $("#trusted_anchor_hash").text(anchorInfo.hash);

        $("#trusted_anchor_save_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : "auto",
            width : "auto",
            buttons : [
                {
                    text : _("common.confirm"),
                    id: "save_trusted_anchor_ok",
                    click : function() {
                        saveTrustedAnchor(this);
                    }
                }, {
                    text : _("common.cancel"),
                    click : function() {
                        clearTrustedAnchor(this);
                    }
                } ],
            close: function() {
                clearTrustedAnchor($(this));
            }
        }).dialog("open");
    }

    /* -- DIALOGS - END -- */

    return {
        init: init,
        uploadCallback: uploadCallback
    };
}();
