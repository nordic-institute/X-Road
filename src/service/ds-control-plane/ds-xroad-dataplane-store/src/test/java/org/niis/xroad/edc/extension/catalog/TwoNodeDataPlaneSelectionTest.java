/*
 * The MIT License
 *
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
package org.niis.xroad.edc.extension.catalog;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.selector.service.EmbeddedDataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.strategy.RandomSelectionStrategy;
import org.eclipse.edc.connector.dataplane.selector.strategy.DefaultSelectionStrategyRegistry;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.participantcontext.spi.types.ParticipantResource.queryByParticipantContextId;

class TwoNodeDataPlaneSelectionTest {

    private static final String MEMBER_CONTEXT = "DEV:COM:222";
    private static final String PULL = XRoadTransferType.PULL.wireValue();
    private static final String NODE_A_URL = "http://127.0.0.1:5590/full/api/v1/dataflows";
    private static final String NODE_B_URL = "http://proxy-b:5590/full/api/v1/dataflows";
    private static final String EXPECTED_ID = "xroad-proxy::" + MEMBER_CONTEXT;

    private final DataPlaneSelectorService nodeA = selectorFor(NODE_A_URL);
    private final DataPlaneSelectorService nodeB = selectorFor(NODE_B_URL);

    @Test
    void bothNodesSelectTheSameDataPlaneIdAndEachReportsItsOwnUrl() {
        var transferProcess = transferProcess(MEMBER_CONTEXT);

        var selectedOnA = nodeA.selectFor(transferProcess);
        var selectedOnB = nodeB.selectFor(transferProcess);

        assertThat(selectedOnA.succeeded()).isTrue();
        assertThat(selectedOnB.succeeded()).isTrue();
        assertThat(selectedOnA.getContent().getId()).isEqualTo(EXPECTED_ID);
        assertThat(selectedOnB.getContent().getId()).isEqualTo(EXPECTED_ID);
        assertThat(selectedOnA.getContent().getUrl()).hasToString(NODE_A_URL);
        assertThat(selectedOnB.getContent().getUrl()).hasToString(NODE_B_URL);
    }

    @Test
    void idSelectedOnOneNodeResolvesOnAnotherNodeAndOnAFreshlyBuiltNode() {
        var dataPlaneId = nodeA.selectFor(transferProcess(MEMBER_CONTEXT)).getContent().getId();
        var freshNode = selectorFor("http://proxy-c:5590/full/api/v1/dataflows");

        var onB = nodeB.findById(dataPlaneId);
        var onFreshNode = freshNode.findById(dataPlaneId);

        assertThat(onB.succeeded()).isTrue();
        assertThat(onB.getContent().getUrl()).hasToString(NODE_B_URL);
        assertThat(onFreshNode.succeeded()).isTrue();
        assertThat(onFreshNode.getContent().getParticipantContextId()).isEqualTo(MEMBER_CONTEXT);
        assertThat(onFreshNode.getContent().getUrl()).hasToString("http://proxy-c:5590/full/api/v1/dataflows");
    }

    @Test
    void searchByParticipantContextReturnsConfiguredTransferTypeOnEveryNode() {
        var query = queryByParticipantContextId(MEMBER_CONTEXT).build();

        for (var node : List.of(nodeA, nodeB)) {
            var result = node.search(query);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).singleElement()
                    .extracting(DataPlaneInstance::getAllowedTransferTypes)
                    .satisfies(types -> assertThat(types).containsExactly(PULL));
        }
    }

    @Test
    void selectionForUnsupportedTransferTypeFindsNoDataPlane() {
        var transferProcess = TransferProcess.Builder.newInstance()
                .id("tp-1")
                .participantContextId(MEMBER_CONTEXT)
                .transferType("Unknown-PULL")
                .build();

        assertThat(nodeA.selectFor(transferProcess).succeeded()).isFalse();
    }

    private static TransferProcess transferProcess(String participantContextId) {
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .participantContextId(participantContextId)
                .transferType(PULL)
                .build();
    }

    private static DataPlaneSelectorService selectorFor(String url) {
        var entries = ConfigFactory.fromMap(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", url,
                "xroad.cp.dataplane.proxy.allowed-source-types", "http,https",
                "xroad.cp.dataplane.proxy.allowed-transfer-types", PULL
        )).getConfig("xroad.cp.dataplane").partition().toList();
        var strategies = new DefaultSelectionStrategyRegistry();
        strategies.add(new RandomSelectionStrategy());
        return new EmbeddedDataPlaneSelectorService(
                new XRoadDataPlaneInstanceStore(entries), strategies, new NoopTransactionContext(), "random");
    }
}
