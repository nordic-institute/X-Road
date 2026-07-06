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
package org.niis.xroad.configuration.migration;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.spring.SpringVaultClient;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

/**
 * Migration-CLI subclass of {@link SpringVaultClient} that implements the three PIN methods left
 * unimplemented by the parent. Mirrors {@code QuarkusVaultClient.setTokenPin/getTokenPin/deleteTokenPin}
 * byte-for-byte so the migration writer and the runtime signer reader share the exact same
 * vault path layout ({@code signer/token-pins/{tokenId}}) and payload schema ({@code {"pin": value}}).
 *
 * <p>Constructed manually (no Spring DI) by {@code MigrationVaultClient.createAndPreflight()}
 * The factory and CLI wiring live in this same package.
 */
@Slf4j
public class MigrationVaultClient extends SpringVaultClient {

    static final String ENV_HOST = "XROAD_SECRET_STORE_HOST";
    static final String ENV_PORT = "XROAD_SECRET_STORE_PORT";
    static final String ENV_SCHEME = "XROAD_SECRET_STORE_SCHEME";
    static final String ENV_TOKEN = "XROAD_SECRET_STORE_TOKEN";

    static final String VAULT_MOUNT = "xrd-secret";
    static final String PREFLIGHT_PATH = "auth/token/lookup-self";

    // SpringVaultClient.vaultClient is private final; subclass cannot inherit access. Shadow with own ref.
    private final VaultKeyValueOperations kvOps;

    MigrationVaultClient(VaultKeyValueOperations kvOps) {
        super(kvOps);
        this.kvOps = kvOps;
    }

    /**
     * Reads {@code XROAD_SECRET_STORE_HOST/PORT/SCHEME/TOKEN} from the process environment, builds a
     * {@link VaultTemplate} against {@code xrd-secret} mount (KV v1), runs a preflight
     * {@code auth/token/lookup-self} read, and returns a configured {@link MigrationVaultClient}.
     *
     * <p>Designed to be invoked once per vault-using subcommand — NOT eagerly at startup.
     * Throws {@link XrdRuntimeException} on any missing env var, parse failure, auth failure, or
     * connectivity / TLS failure.
     *
     * @return a preflight-checked vault client ready for use by the migrators
     */
    public static MigrationVaultClient createAndPreflight() {
        String host = requireEnvVar(ENV_HOST);
        String portStr = requireEnvVar(ENV_PORT);
        String scheme = requireEnvVar(ENV_SCHEME);
        String token = requireEnvVar(ENV_TOKEN);

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR,
                    "%s is not a valid integer: %s", ENV_PORT, portStr);
        }

        VaultEndpoint endpoint = VaultEndpoint.create(host, port);
        endpoint.setScheme(scheme);

        VaultTemplate vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(token));

        preflight(vaultTemplate);

        VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue(
                VAULT_MOUNT, VaultKeyValueOperationsSupport.KeyValueBackend.KV_1);
        return new MigrationVaultClient(kvOps);
    }

    private static String requireEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR,
                    "Required environment variable %s is not set or blank. "
                            + "Ensure /etc/xroad/services/secret-store-local.conf is sourced before invoking migration-cli.",
                    name);
        }
        return value;
    }

    static void preflight(VaultTemplate vaultTemplate) {
        try {
            VaultResponse result = vaultTemplate.read(PREFLIGHT_PATH);
            if (result == null) {
                throw XrdRuntimeException.systemException(INTERNAL_ERROR,
                        "Vault lookup-self returned no response. "
                                + "Verify XROAD_SECRET_STORE_HOST/PORT/SCHEME and OpenBao endpoint configuration.");
            }
            log.debug("Vault preflight: lookup-self succeeded");
        } catch (VaultException e) {
            log.error("Vault lookup-self failed (HTTP/auth error): {}", e.getMessage());
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e,
                    "Vault preflight check failed: %s. Verify %s is valid.",
                    e.getMessage(), ENV_TOKEN);
        } catch (RestClientException e) {
            log.error("Vault unreachable (network/TLS error): {}", e.getMessage());
            throw XrdRuntimeException.systemException(NETWORK_ERROR, e,
                    "Cannot connect to Vault — check %s/%s/%s: %s",
                    ENV_HOST, ENV_PORT, ENV_SCHEME, e.getMessage());
        }
    }

    @Override
    public void setTokenPin(String tokenId, char[] pin) {
        var secret = new HashMap<String, String>();
        secret.put(PIN_KEY, new String(pin));
        kvOps.put(SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId, secret);
    }

    @Override
    public Optional<char[]> getTokenPin(String tokenId) {
        VaultResponse response = kvOps.get(SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId);
        if (response == null || response.getData() == null) {
            return Optional.empty();
        }
        Map<String, Object> data = response.getData();
        Object pinVal = data.get(PIN_KEY);
        return pinVal == null ? Optional.empty() : Optional.of(pinVal.toString().toCharArray());
    }

    @Override
    public void deleteTokenPin(String tokenId) {
        kvOps.delete(SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId);
    }

}
