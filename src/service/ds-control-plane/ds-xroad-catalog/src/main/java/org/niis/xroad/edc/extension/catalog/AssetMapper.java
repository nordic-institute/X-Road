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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Maps between X-Road {@link ServiceId} domain objects and EDC {@link Asset} instances.
 * Package-private utility class per D-10.
 */
@Slf4j
final class AssetMapper {

    static final int SERVICE_ID_PARTS_WITH_VERSION = 6;
    static final int SERVICE_ID_PARTS_WITHOUT_VERSION = 5;

    private AssetMapper() {
        // utility class
    }

    /**
     * Encodes a ServiceId to a stable asset ID string using {@link XRoadId#asEncodedId()}.
     * Format: INSTANCE:memberClass:memberCode:subsystemCode:serviceCode[:serviceVersion]
     * Uses ':' separator (canonical X-Road encoding).
     */
    static String encodeAssetId(ServiceId serviceId) {
        var encoded = serviceId.asEncodedId();
        if (log.isTraceEnabled()) {
            log.trace("encodeAssetId serviceCode={} encoded={}", serviceId.getServiceCode(), encoded);
        }
        return encoded;
    }

    /**
     * Decodes an asset ID string back to {@link ServiceId.Conf}.
     * Handles 5 parts (no version) and 6 parts (with version).
     *
     * @return decoded ServiceId.Conf, or null for malformed IDs
     */
    @Nullable
    static ServiceId.Conf decodeAssetId(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return null;
        }
        var parts = assetId.split(String.valueOf(XRoadId.ENCODED_ID_SEPARATOR));
        var result = switch (parts.length) {
            case SERVICE_ID_PARTS_WITH_VERSION -> createServiceIdWithVersion(parts);
            case SERVICE_ID_PARTS_WITHOUT_VERSION -> createServiceIdWithoutVersion(parts);
            default -> {
                log.warn("Malformed asset ID '{}': expected {} or {} colon-separated parts, got {}",
                        assetId, SERVICE_ID_PARTS_WITHOUT_VERSION, SERVICE_ID_PARTS_WITH_VERSION, parts.length);
                yield null;
            }
        };
        if (log.isTraceEnabled()) {
            log.trace("decodeAssetId input={} result={}", assetId, result != null ? result.asEncodedId() : "null");
        }
        return result;
    }

    private static ServiceId.Conf createServiceIdWithVersion(String[] parts) {
        int i = 0;
        return ServiceId.Conf.create(parts[i++], parts[i++], parts[i++], parts[i++], parts[i++], parts[i]);
    }

    private static ServiceId.Conf createServiceIdWithoutVersion(String[] parts) {
        int i = 0;
        return ServiceId.Conf.create(parts[i++], parts[i++], parts[i++], parts[i++], parts[i]);
    }

    /**
     * Maps a {@link ServiceId} to an EDC {@link Asset} with DCAT-compliant properties per D-03.
     */
    static Asset toAsset(ServiceId.Conf serviceId, String participantContextId) {
        var assetId = encodeAssetId(serviceId);
        var title = buildTitle(serviceId);
        var description = buildDescription(serviceId);
        var keywords = buildKeywords(serviceId);

        if (log.isTraceEnabled()) {
            log.trace("toAsset serviceId={} assetId={} title={} keywords={}",
                    serviceId.asEncodedId(), assetId, title, keywords);
        }
        return Asset.Builder.newInstance()
                .id(assetId)
                .participantContextId(participantContextId)
                .property(EDC_NAMESPACE + "name", serviceId.getServiceCode())
                // Full IRIs (with authority) so Titanium JSON-LD compaction doesn't trip
                // IRI_CONFUSED_WITH_PREFIX. dcat is a registered prefix in the DSP context
                // (dspace-v2025-1.jsonld); emitting raw "dcat:keyword" as a property key gives
                // Titanium a scheme-only URI it parses as a confusable CURIE. Titanium re-shortens
                // the full IRI back to dcat:keyword / dct:title on the wire.
                .property("http://purl.org/dc/terms/title", title)
                .property("http://purl.org/dc/terms/description", description)
                .property("http://www.w3.org/ns/dcat#keyword", keywords)
                .build();
    }

    /**
     * Builds title from serviceCode and optional serviceVersion.
     * D-03: "serviceCode:serviceVersion" or just "serviceCode" if version is null.
     */
    private static String buildTitle(ServiceId serviceId) {
        return serviceId.getServiceVersion() != null
                ? serviceId.getServiceCode() + ":" + serviceId.getServiceVersion()
                : serviceId.getServiceCode();
    }

    /**
     * Builds description from ServiceId fields.
     * D-04: "X-Road service {serviceCode}:{serviceVersion} provided by {memberCode}/{subsystemCode}"
     */
    private static String buildDescription(ServiceId serviceId) {
        var sb = new StringBuilder("X-Road service ")
                .append(serviceId.getServiceCode());
        if (serviceId.getServiceVersion() != null) {
            sb.append(':').append(serviceId.getServiceVersion());
        }
        sb.append(" provided by ").append(serviceId.getMemberCode());
        if (serviceId.getSubsystemCode() != null) {
            sb.append('/').append(serviceId.getSubsystemCode());
        }
        return sb.toString();
    }

    /**
     * Builds keyword list from ServiceId fields.
     * D-03: dcat:keyword = list containing memberClass, subsystemCode (null omitted).
     */
    private static List<String> buildKeywords(ServiceId serviceId) {
        var keywords = new ArrayList<String>();
        keywords.add(serviceId.getMemberClass());
        if (serviceId.getSubsystemCode() != null) {
            keywords.add(serviceId.getSubsystemCode());
        }
        return keywords;
    }
}
