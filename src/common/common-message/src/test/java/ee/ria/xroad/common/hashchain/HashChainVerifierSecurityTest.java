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
package ee.ria.xroad.common.hashchain;

import ee.ria.xroad.common.CodedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_HASH_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Attack-reproduction tests for HashChainVerifier DoS hardening.
 */
class HashChainVerifierSecurityTest {

    private static final String CHAIN_URI = "/attack-chain.xml";
    private static final String NS_HC = "http://cyber.ee/hashchain";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String SHA256_URI = "http://www.w3.org/2001/04/xmlenc#sha256";
    private static final String DUMMY_DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final int WIDE_FANOUT = HashChainVerifier.MAX_STEPS + 100;

    static {
        org.apache.xml.security.Init.init();
    }

    @Test
    void selfReferencingStepIsRejectedAsMalformed() {
        String chain = buildChain(
                "<ns2:HashStep id=\"STEP0\">"
                        + "<ns2:StepRef URI=\"#STEP0\"/>"
                        + "</ns2:HashStep>");
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).getFaultCode())
                .asString()
                .endsWith(X_MALFORMED_HASH_CHAIN);
    }

    /**
     * Pins per-step memoization: a double-StepRef chain of depth D (D &lt; MAX_DEPTH) where 2^D &gt; MAX_STEPS.
     * Without memoization the doubled references re-resolve shared subtrees, exceeding MAX_STEPS and triggering the
     * step-count error. With memoization each step resolves once (~D times total), so no step-count rejection occurs.
     * Remove memoization and this test flips to a step-count failure.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void subDepthDoubleReferenceChainCompletesWithoutStepCountRejectionPinningMemoization() {
        // Smallest D where 2^D > MAX_STEPS; with memoization resolves in ~D steps, not 2^D.
        int d = 0;
        while ((1L << d) <= HashChainVerifier.MAX_STEPS) {
            d++;
        }
        assertThat(d).as("D must be below MAX_DEPTH for this test to pin memoization").isLessThan(HashChainVerifier.MAX_DEPTH);

        String chain = buildDeepDoubleRefChain(d);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("step count"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void doubleReferenceChainExceedingMaxDepthIsRejectedAsMalformed() {
        String chain = buildDeepDoubleRefChain(HashChainVerifier.MAX_DEPTH + 1);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> {
                    CodedException xre = (CodedException) e;
                    assertThat(xre.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
                    assertThat(xre.getMessage()).contains("maximum depth");
                });
    }

    @Test
    void chainExceedingMaxDepthIsRejectedAsMalformed() {
        String chain = buildLinearChain(HashChainVerifier.MAX_DEPTH + 1);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> {
                    CodedException xre = (CodedException) e;
                    assertThat(xre.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
                    assertThat(xre.getMessage()).contains("maximum depth");
                });
    }

    @Test
    void chainExceedingMaxStepsIsRejectedAsMalformed() {
        String chain = buildShallowWideChain(WIDE_FANOUT);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> {
                    CodedException xre = (CodedException) e;
                    assertThat(xre.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
                    assertThat(xre.getMessage()).contains("step count");
                });
    }

    @Test
    void singleStepExceedingMaxValuesIsRejectedAsMalformed() {
        String chain = buildSingleStepWideValueChain(HashChainVerifier.MAX_VALUES + 1);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> {
                    CodedException xre = (CodedException) e;
                    assertThat(xre.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
                    assertThat(xre.getMessage()).contains("value count");
                });
    }

    /**
     * Feeds {@code resolveHashStep} a step whose value list reports a size far beyond MAX_VALUES without
     * actually holding that many elements. If the MAX_VALUES guard were checked only inside the per-value loop
     * (after {@code new DigestValue[values.size()]}), that allocation would throw OutOfMemoryError before the
     * guard ever runs. Asserting the MALFORMED_HASH_CHAIN rejection instead proves the guard runs first.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void stepReportingHugeValueCountIsRejectedBeforeArrayAllocation() throws Exception {
        HashStepType step = new HashStepType();
        step.setId("STEP0");
        setValues(step, new HugeReportedSizeList(Integer.MAX_VALUE));

        HashChainType chain = new HashChainType();
        chain.getHashStep().add(step);

        Throwable thrown = catchThrowable(() -> resolveHashStep(chain, "#STEP0"));

        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        Throwable cause = ((InvocationTargetException) thrown).getTargetException();
        assertThat(cause).isInstanceOf(CodedException.class);
        CodedException coded = (CodedException) cause;
        assertThat(coded.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
        assertThat(coded.getMessage()).contains("value count");
    }

    /**
     * Primes {@code totalValuesResolved} to 1 via a real resolved step, then feeds a second step reporting
     * {@code Integer.MAX_VALUE} values. {@code totalValuesResolved + values.size()} would overflow to a negative
     * number and slip past an int-addition guard, reaching the allocation. The overflow-safe guard
     * ({@code values.size() > MAX_VALUES - totalValuesResolved}) must reject this regardless.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void secondStepWithOverflowingValueCountIsRejectedAfterPriorValueResolved() throws Exception {
        HashChainType chain = new HashChainType();
        DigestMethodType defaultDigestMethod = new DigestMethodType();
        defaultDigestMethod.setAlgorithm(SHA256_URI);
        chain.setDefaultDigestMethod(defaultDigestMethod);

        HashStepType step0 = new HashStepType();
        step0.setId("STEP0");
        HashValueType realValue = new HashValueType();
        realValue.setDigestValue(Base64.getDecoder().decode(DUMMY_DIGEST));
        step0.getHashValueOrStepRefOrDataRef().add(realValue);

        HashStepType step1 = new HashStepType();
        step1.setId("STEP1");
        setValues(step1, new HugeReportedSizeList(Integer.MAX_VALUE));

        chain.getHashStep().add(step0);
        chain.getHashStep().add(step1);

        HashChainVerifier verifier = newVerifier(chain);
        resolveHashStep(verifier, chain, "#STEP0");

        Throwable thrown = catchThrowable(() -> resolveHashStep(verifier, chain, "#STEP1"));

        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        Throwable cause = ((InvocationTargetException) thrown).getTargetException();
        assertThat(cause).isInstanceOf(CodedException.class);
        CodedException coded = (CodedException) cause;
        assertThat(coded.getFaultCode()).endsWith(X_MALFORMED_HASH_CHAIN);
        assertThat(coded.getMessage()).contains("value count");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void repeatedReferencesToSameStepAreMemoized() {
        String chain = buildShallowRepeatChain(WIDE_FANOUT);
        String result = buildResult(CHAIN_URI + "#STEP0");

        assertThatThrownBy(() -> HashChainVerifier.verify(stream(result), resolver(chain), Collections.emptyMap()))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("step count");
                });
    }

    /**
     * Depth-1 chain: a single root step containing {@code valueCount} HashValue children.
     * Trips MAX_VALUES without any recursion — cannot be confused with MAX_DEPTH or MAX_STEPS.
     */
    private static String buildSingleStepWideValueChain(int valueCount) {
        var sb = new StringBuilder();
        appendChainHeader(sb);
        sb.append("<ns2:HashStep id=\"STEP0\">");
        for (int i = 0; i < valueCount; i++) {
            appendLeafValue(sb);
        }
        sb.append("</ns2:HashStep>");
        sb.append("</ns2:HashChain>");
        return sb.toString();
    }

    /**
     * Builds a hash chain with {@code stepCount} steps where the children of each step are supplied by
     * {@code childrenForStep.apply(stepIndex, stepCount)}. Callers pass a lambda that returns the raw XML
     * children for a given step index; when the returned string is empty the step gets no children (caller
     * must supply a leaf value via {@link #appendLeafValue} if needed).
     */
    private static String buildStepChain(int stepCount, BiFunction<Integer, Integer, String> childrenForStep) {
        var sb = new StringBuilder();
        appendChainHeader(sb);
        for (int i = 0; i < stepCount; i++) {
            sb.append("<ns2:HashStep id=\"STEP").append(i).append("\">");
            sb.append(childrenForStep.apply(i, stepCount));
            sb.append("</ns2:HashStep>");
        }
        sb.append("</ns2:HashChain>");
        return sb.toString();
    }

    private static String buildLinearChain(int stepCount) {
        return buildStepChain(stepCount, (i, total) -> {
            if (i < total - 1) {
                return "<ns2:StepRef URI=\"#STEP" + (i + 1) + "\"/>";
            }
            var sb = new StringBuilder();
            appendLeafValue(sb);
            return sb.toString();
        });
    }

    /**
     * Shallow (depth 2) chain where root references many distinct leaf steps.
     * Trips MAX_STEPS at depth 2 — cannot be confused with MAX_DEPTH.
     */
    private static String buildShallowWideChain(int leafCount) {
        int totalSteps = leafCount + 1;
        return buildStepChain(totalSteps, (i, total) -> {
            if (i == 0) {
                var sb = new StringBuilder();
                for (int j = 1; j <= leafCount; j++) {
                    sb.append("<ns2:StepRef URI=\"#STEP").append(j).append("\"/>");
                }
                return sb.toString();
            }
            var sb = new StringBuilder();
            appendLeafValue(sb);
            return sb.toString();
        });
    }

    /**
     * Shallow (depth 2) chain where root repeats the SAME leaf step reference many times.
     * With memoization: the leaf is resolved once regardless of repetition count.
     * Without memoization: each repetition would re-resolve the leaf, hitting MAX_STEPS.
     */
    private static String buildShallowRepeatChain(int repetitions) {
        return buildStepChain(2, (i, total) -> {
            if (i == 0) {
                var sb = new StringBuilder();
                for (int j = 0; j < repetitions; j++) {
                    sb.append("<ns2:StepRef URI=\"#STEP1\"/>");
                }
                return sb.toString();
            }
            var sb = new StringBuilder();
            appendLeafValue(sb);
            return sb.toString();
        });
    }

    private static String buildDeepDoubleRefChain(int stepCount) {
        return buildStepChain(stepCount, (i, total) -> {
            if (i < total - 1) {
                return "<ns2:StepRef URI=\"#STEP" + (i + 1) + "\"/>"
                        + "<ns2:StepRef URI=\"#STEP" + (i + 1) + "\"/>";
            }
            var sb = new StringBuilder();
            appendLeafValue(sb);
            return sb.toString();
        });
    }

    private static void appendChainHeader(StringBuilder sb) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<ns2:HashChain xmlns:ns2=\"").append(NS_HC).append("\"");
        sb.append(" xmlns=\"").append(NS_DS).append("\">");
        sb.append("<ns2:DefaultDigestMethod Algorithm=\"").append(SHA256_URI).append("\"/>");
    }

    private static void appendLeafValue(StringBuilder sb) {
        sb.append("<ns2:HashValue><DigestValue>").append(DUMMY_DIGEST).append("</DigestValue></ns2:HashValue>");
    }

    private static String buildChain(String stepsXml) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ns2:HashChain xmlns:ns2=\"" + NS_HC + "\""
                + " xmlns=\"" + NS_DS + "\">"
                + "<ns2:DefaultDigestMethod Algorithm=\"" + SHA256_URI + "\"/>"
                + stepsXml
                + "</ns2:HashChain>";
    }

    private static String buildResult(String uri) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ns2:HashChainResult URI=\"" + uri + "\""
                + " xmlns:ns2=\"" + NS_HC + "\""
                + " xmlns=\"" + NS_DS + "\">"
                + "<DigestMethod Algorithm=\"" + SHA256_URI + "\"/>"
                + "<DigestValue>" + DUMMY_DIGEST + "</DigestValue>"
                + "</ns2:HashChainResult>";
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static HashChainReferenceResolver resolver(String chainXml) {
        return new HashChainReferenceResolver() {
            @Override
            public InputStream resolve(String uri) throws IOException {
                if (CHAIN_URI.equals(uri)) {
                    return new ByteArrayInputStream(chainXml.getBytes(StandardCharsets.UTF_8));
                }
                throw new IOException("Unexpected URI: " + uri);
            }

            @Override
            public boolean shouldResolve(String uri, byte[] digestValue) {
                return true;
            }
        };
    }

    /**
     * Sets the backing field of {@code HashStepType.hashValueOrStepRefOrDataRef} directly, bypassing the
     * lazily-initializing getter, so tests can install a custom {@link List} implementation.
     */
    private static void setValues(HashStepType step, List<AbstractValueType> values) throws ReflectiveOperationException {
        Field field = HashStepType.class.getDeclaredField("hashValueOrStepRefOrDataRef");
        field.setAccessible(true);
        field.set(step, values);
    }

    /**
     * Invokes the private {@code HashChainVerifier.resolveHashStep} directly on a hand-built chain, skipping
     * XML parsing entirely so a step can report an arbitrarily large value count with no matching allocation.
     * Creates a fresh verifier instance per call.
     */
    private static Object resolveHashStep(HashChainType chain, String uri) throws ReflectiveOperationException {
        return resolveHashStep(newVerifier(chain), chain, uri);
    }

    /**
     * Invokes the private {@code HashChainVerifier.resolveHashStep} on an already-created verifier instance,
     * so callers can resolve multiple steps in sequence and observe the running {@code totalValuesResolved}
     * counter carry over between calls.
     */
    private static Object resolveHashStep(HashChainVerifier verifier, HashChainType chain, String uri)
            throws ReflectiveOperationException {
        Method method = HashChainVerifier.class.getDeclaredMethod("resolveHashStep", String.class, HashChainType.class);
        method.setAccessible(true);
        return method.invoke(verifier, uri, chain);
    }

    /**
     * Builds a {@code HashChainVerifier} via its private constructor and registers {@code chain} as resolvable
     * under {@link #CHAIN_URI}, bypassing the normal XML-parsing entry point.
     */
    private static HashChainVerifier newVerifier(HashChainType chain) throws ReflectiveOperationException {
        Constructor<HashChainVerifier> constructor = HashChainVerifier.class.getDeclaredConstructor(
                InputStream.class, HashChainReferenceResolver.class, Map.class);
        constructor.setAccessible(true);
        HashChainVerifier verifier = constructor.newInstance(null, resolver(""), Collections.emptyMap());

        Field chainBaseUrisField = HashChainVerifier.class.getDeclaredField("chainBaseUris");
        chainBaseUrisField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<HashChainType, String> chainBaseUris = (Map<HashChainType, String>) chainBaseUrisField.get(verifier);
        chainBaseUris.put(chain, CHAIN_URI);

        return verifier;
    }

    /**
     * A value list that reports {@code reportedSize} from {@link #size()} while actually holding zero elements.
     * Used to prove a size guard runs before any code allocates an array sized from {@code size()}.
     */
    private static final class HugeReportedSizeList extends AbstractList<AbstractValueType> {
        private final int reportedSize;

        HugeReportedSizeList(int reportedSize) {
            this.reportedSize = reportedSize;
        }

        @Override
        public AbstractValueType get(int index) {
            throw new AssertionError("get() must not be called before the MAX_VALUES guard rejects this list");
        }

        @Override
        public int size() {
            return reportedSize;
        }
    }
}
