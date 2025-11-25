/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common;

import ee.ria.xroad.common.SystemProperties;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class CostTypeSorter<T extends SortableByCostType> {
    private final List<String> defaultOrderedValues = new ArrayList<>();
    private final List<String> freeValues = new ArrayList<>();
    private final List<String> paidValues = new ArrayList<>();
    private final List<String> undefinedCostValues = new ArrayList<>();

    public CostTypeSorter(List<T> toBeSorted) {
        divide(toBeSorted);
    }

    private void divide(List<T> toBeSorted) {
        toBeSorted.stream().filter(tbs -> isNotBlank(tbs.getSortableValue()))
                .forEach(tbs -> {
                    String sortableValue = tbs.getSortableValue().trim();
                    this.defaultOrderedValues.add(sortableValue);
                    if (CostType.FREE.equals(tbs.getCostType())) {
                        this.freeValues.add(sortableValue);
                    }
                    if (CostType.PAID.equals(tbs.getCostType())) {
                        this.paidValues.add(sortableValue);
                    }
                    if (tbs.getCostType() == null || CostType.UNDEFINED.equals(tbs.getCostType())) {
                        this.undefinedCostValues.add(sortableValue);
                    }
                });
    }

    public List<String> sort(SystemProperties.ServicePrioritizationStrategy prioritizationStrategy) {
        List<String> sortedValues = new ArrayList<>();
        switch (prioritizationStrategy) {
            case FREE_FIRST -> {
                sortedValues.addAll(this.freeValues);
                sortedValues.addAll(this.paidValues);
                sortedValues.addAll(this.undefinedCostValues);
            }
            case PAID_FIRST -> {
                sortedValues.addAll(this.paidValues);
                sortedValues.addAll(this.freeValues);
                sortedValues.addAll(this.undefinedCostValues);
            }
            case ONLY_FREE -> sortedValues.addAll(this.freeValues);
            case ONLY_PAID -> sortedValues.addAll(this.paidValues);
            default -> sortedValues.addAll(this.defaultOrderedValues);
        }
        return sortedValues;
    }
}
