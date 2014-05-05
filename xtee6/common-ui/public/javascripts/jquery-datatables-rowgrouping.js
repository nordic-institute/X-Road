/*
 * File:        jquery.dataTables.grouping.js
 * Version:     1.2.7.
 * Author:      Jovan Popovic
 *
 * Copyright 2012 Jovan Popovic, all rights reserved.
 *
 * This source file is free software, under either the GPL v2 license or a
 * BSD style license, as supplied with this software.
 *
 * This source file is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Parameters:
 * @aiGroupingColumnIndex Integer  Index of the column that will be used for grouping.
 * @afnGroupClass         Function Function to determine the class of the group row.
 * @afnGroupLabelFormat   Function Function to format group label.
 */
(function ($) {

    $.fn.rowGrouping = function(options) {

        var defaults = {
            aiGroupingColumnIndex: [ "columnName" ],
            asGroupingColumnName: [ 0 ],
            afnGroupClass: [
                function(oData) {
                    return "group";
                }
            ],
            afnGroupLabelFormat: [
                function(oData, sLabel) {
                    return sLabel;
                }
            ]
        };

        return this.each(function(index, elem) {
            var oTable = $(elem).dataTable();

            var aoGroups = new Array();
            $(this).dataTableExt.aoGroups = aoGroups;

            function _fnCreateGroupRow(sGroupKey, sGroupName, oData, iLevel, iColspan, sClass) {
                var nRow  = document.createElement('tr');
                var nCell = document.createElement('td');

                aoGroups[sGroupKey] = {
                    key: sGroupKey,
                    name: sGroupName
                };

                nCell.colSpan = iColspan;
                nCell.innerHTML =
                    properties.afnGroupLabelFormat[iLevel](oData, sGroupName);

                nRow.dataset.id = sGroupName;
                if (iLevel > 0) {
                    nRow.dataset.parentId =
                        oData[properties.asGroupingColumnName[iLevel - 1]];
                }

                nRow.className = sClass;
                nRow.appendChild(nCell);

                return nRow;
            }

            function _fnGroupKey(sGroupName) {
                return sGroupName ? sGroupName.toLowerCase()
                    .replace(/[^a-zA-Z0-9\u0080-\uFFFF]+/g, "-") : "-";
            }

            function _fnDrawCallBackWithGrouping(oSettings) {
                var bUseSecondaryGrouping = properties.asGroupingColumnName.length > 1;

                if (oSettings.aiDisplayMaster.length == 0) {
                    return;
                }

                var nTrs = oTable.$("tr", {"filter": "applied"});
                var iColspan = 0;

                for (var iColIndex = 0; iColIndex < oSettings.aoColumns.length; iColIndex++) {
                    if (oSettings.aoColumns[iColIndex].bVisible) {
                        iColspan += 1;
                    }
                }

                var sLastGroup = null;
                var sLastGroup2 = null;

                for (var i = 0; i < nTrs.length; i++) {
                    var oData = this.fnGetData(nTrs[i]);

                    var sGroupName = oData[properties.asGroupingColumnName[0]];
                    var sGroupKey = _fnGroupKey(sGroupName);

                    if (sLastGroup == null || _fnGroupKey(sGroupName) != _fnGroupKey(sLastGroup)) {
                        // first of group encountered
                        var sClass = properties.afnGroupClass[0](oData);
                        var nRow = _fnCreateGroupRow(sGroupKey, sGroupName, oData, 0, iColspan, sClass);

                        nTrs[i].parentNode.insertBefore(nRow, nTrs[i]);

                        sLastGroup = sGroupName;
                        // reset second level grouping
                        sLastGroup2 = null;
                    }

                    if (bUseSecondaryGrouping) {
                        var sGroup2Name = oData[properties.asGroupingColumnName[1]];
                        var sGroup2Key = _fnGroupKey(sGroup2Name);

                        if (!sGroup2Name) {
                            continue;
                        }

                        if (sLastGroup2 == null || sGroup2Key != _fnGroupKey(sLastGroup2)) {
                            var sClass = properties.afnGroupClass[1](oData);
                            var nRow = _fnCreateGroupRow(sGroup2Key, sGroup2Name, oData, 1, iColspan, sClass);

                            nTrs[i].parentNode.insertBefore(nRow, nTrs[i]);
                            sLastGroup2 = sGroup2Name;
                        }
                    }
                }
            };

            var properties = $.extend(defaults, options);

            oTable.fnSettings().aoDrawCallback.push({
                "fn": _fnDrawCallBackWithGrouping,
                "sName": "fnRowGrouping"
            });

            oTable.fnSettings().aaSortingFixed = new Array();

            $.each(properties.aiGroupingColumnIndex, function(idx, val) {
                oTable.fnSettings().aaSortingFixed.push(
                    [ val, "asc" ]);
            });

            oTable.fnDraw();
        });
    };

})(jQuery);
