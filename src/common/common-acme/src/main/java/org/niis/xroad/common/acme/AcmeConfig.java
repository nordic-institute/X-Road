package org.niis.xroad.common.acme;

import ee.ria.xroad.common.SystemProperties;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@UtilityClass
public class AcmeConfig {

    private static final Pattern ACME_CHALLENGE_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    public static final Path ACME_CHALLENGE_PATH = Paths.get(SystemProperties.getConfPath() + "acme-challenge/");

    /**
     * Validates an ACME HTTP-01 challenge token received from an (untrusted) ACME server before it is used
     * to build a file path under {@link #ACME_CHALLENGE_PATH}.
     * <p>
     * Rejects anything that is not a plain RFC 8555 base64url token, and, as defense in depth, verifies that
     * resolving the token under {@link #ACME_CHALLENGE_PATH} does not escape that directory.
     */
    public static boolean isValidChallengeToken(String token) {
        if (token == null || !ACME_CHALLENGE_TOKEN_PATTERN.matcher(token).matches()) {
            return false;
        }
        Path normalizedBase = ACME_CHALLENGE_PATH.normalize();
        Path resolved = normalizedBase.resolve(token).normalize();
        return resolved.startsWith(normalizedBase);
    }
}
