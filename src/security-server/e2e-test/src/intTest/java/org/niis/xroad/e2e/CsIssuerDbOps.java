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
package org.niis.xroad.e2e;

/**
 * Access to the issuer's {@code credential_resource} table, which lives in the {@code ds-issuer-service}
 * schema inside the Central Server's own database. Unlike {@link DsControlPlaneDbOps} and
 * {@link MessagelogDbOps} — which reach a Security Server's own, per-environment database — this always
 * targets the single Central Server database regardless of which environment name is passed.
 *
 * <p>Implemented only by the LXD adapter for now: the k8s dev topology has no plumbing into the Central
 * Server's own database at all (unlike the Security Server side, where each dataspace database runs as
 * its own CNPG cluster), so a scenario needing this must fall back or self-skip there.
 */
public interface CsIssuerDbOps {

    /**
     * Runs an SQL statement against the Central Server database's {@code ds-issuer-service} schema
     * and returns psql's unaligned tuple-only output, trimmed.
     */
    String execCsIssuerSql(String env, String sql);
}
