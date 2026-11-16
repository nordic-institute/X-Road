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
package org.niis.xroad.edc.extension.vault;

import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.jetbrains.annotations.Nullable;
import org.niis.xroad.common.vault.ResilientVaultCall;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * EDC {@link Vault} over the OpenBao KV v2 mount {@value #MOUNT}: the secret of key {@code k} lives at
 * {@code ds/<k>} in field {@value #CONTENT_FIELD}, the layout stock EDC 1.0.0-RC7 uses. Every call goes
 * through {@link ResilientVaultCall} and nothing is cached, so each read sees the current OpenBao value.
 * Delete removes the secret metadata, the only delete the {@code ds} policy allows.
 */
final class XRoadEdcVault implements Vault {

    static final String MOUNT = "xrd-ds-secret";
    static final String FOLDER = "ds";
    static final String CONTENT_FIELD = "content";

    private static final int HTTP_NOT_FOUND = 404;

    private final VaultSecretsKV2 kv2;
    private final ResilientVaultCall resilientCall;

    XRoadEdcVault(VaultClient vaultClient, ResilientVaultCall resilientCall) {
        this.kv2 = vaultClient.secrets().kv2(MOUNT);
        this.resilientCall = resilientCall;
    }

    @Override
    public @Nullable String resolveSecret(String key) {
        var path = path(key);
        return resilientCall.execute("read", path, () -> read(path));
    }

    @Override
    public Result<Void> storeSecret(String key, String value) {
        var path = path(key);
        resilientCall.run("write", path, () -> await(kv2.updateSecret(path, null, Map.of(CONTENT_FIELD, value))));
        return Result.success();
    }

    @Override
    public Result<Void> deleteSecret(String key) {
        var path = path(key);
        resilientCall.run("delete", path, () -> deleteMetadata(path));
        return Result.success();
    }

    private @Nullable String read(String path) {
        try {
            var data = await(kv2.readSecret(path)).getData();
            return data == null ? null : (String) data.get(CONTENT_FIELD);
        } catch (VaultClientException e) {
            if (isNotFound(e)) {
                return null;
            }
            throw e;
        }
    }

    private void deleteMetadata(String path) {
        try {
            await(kv2.deleteSecretMetadata(path));
        } catch (VaultClientException e) {
            if (!isNotFound(e)) {
                throw e;
            }
        }
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof VaultClientException vaultFailure) {
                throw vaultFailure;
            }
            throw e;
        }
    }

    private static boolean isNotFound(VaultClientException e) {
        return e.getStatus() != null && e.getStatus() == HTTP_NOT_FOUND;
    }

    private static String path(String key) {
        return FOLDER + "/" + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("%2F", "/");
    }
}
