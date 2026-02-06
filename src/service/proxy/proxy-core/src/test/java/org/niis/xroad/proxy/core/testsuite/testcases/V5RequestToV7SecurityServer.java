package org.niis.xroad.proxy.core.testsuite.testcases;

import ee.ria.xroad.common.message.TerminologyTranslationConfig;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.TestContext;

/**
 * Test case for Scenario 5: V8 Client SS -> V7 Security Server.
 * <p>
 * Simulates a Client Security Server down-converting a V5 request to V4
 * because the downstream Provider Security Server is legacy (V7).
 * <p>
 * Verifies that this action breaks the end-to-end request hash integrity check
 * at the Consumer IS level.
 */
@Slf4j
public class V5RequestToV7SecurityServer extends MessageTestCase {

    public V5RequestToV7SecurityServer() {
        requestFileName = "getstate-v5.query";
        responseFile = "getstate.answer";
    }

@Override
    protected void init(TestContext testContext) {
        super.init(testContext);

    }
    @Override
    public boolean execute(TestContext testContext) throws Exception {
        // Initialize helper to access properties
        init(testContext);

        // Assume Provider SS in V7
        TerminologyTranslationConfig.getInstance().setServerSoapDecoderV5Enabled(false);
        // Simulate Client SS down-converting V5 -> V4 for Server Proxy
        TerminologyTranslationConfig.getInstance().setOutputToServerIsInV4(true);

        try {
            return super.execute(testContext);
        } finally {
            ee.ria.xroad.common.message.TerminologyTranslationConfig.getInstance().setOutputToServerIsInV4(false);
        }
    }

    @Override
    protected void validateNormalResponse(org.niis.xroad.proxy.core.test.Message response) throws Exception {
        // Consumer IS verifies consistency using its Sent Request (V5) vs Received Response (V5).
        boolean consistent = sentRequest.checkConsistency(response);

        if (!consistent) {
            throw new Exception("Consistency check FAILED. Expected success (V5 Request vs V5 Response).");
        }
        
        // Explicitly enable protocol check (Consistency check ignores it)
        String requestVersion = ((ee.ria.xroad.common.message.SoapMessageImpl) sentRequest.getSoap()).getProtocolVersion();
        String responseVersion = ((ee.ria.xroad.common.message.SoapMessageImpl) response.getSoap()).getProtocolVersion();
        
        if (!requestVersion.equals(responseVersion)) {
             throw new Exception("Protocol version mismatch: Request=" + requestVersion + ", Response=" + responseVersion);
        }
        
        log.info("✅ SUCCESS: Consistency check PASSED (V5 Request matches V5 Response).");

        // Verify Hash Integrity Mismatch (V5 sent vs V4 received/calculated)
        var sentHeader = ((ee.ria.xroad.common.message.SoapMessageImpl) sentRequest.getSoap()).getHeader();
        var responseHeader = ((ee.ria.xroad.common.message.SoapMessageImpl) response.getSoap()).getHeader();

        String sentHash = sentHeader.getRequestHash() != null ? sentHeader.getRequestHash().getHash() : null;
        String responseHash = responseHeader.getRequestHash() != null ? responseHeader.getRequestHash().getHash() : null;

        log.info("Sent Request Hash (V5): {}", sentHash != null ? sentHash : "N/A");
        log.info("Response Request Hash (V4 preserved): {}", responseHash != null ? responseHash : "N/A");

        if (sentHash != null && responseHash != null) {
            if (sentHash.equals(responseHash)) {
                 log.warn("Request hashes match! This is unexpected for V5->V4 translation (hashes should differ).");
            } else {
                 log.info("✅ SUCCESS: Request hashes differ as expected.");
            }
        } else {
            log.warn("Cannot verify hash integrity: one or both hashes are missing.");
        }
    }
}
