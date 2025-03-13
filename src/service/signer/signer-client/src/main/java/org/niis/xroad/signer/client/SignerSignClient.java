package org.niis.xroad.signer.client;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import org.niis.xroad.signer.api.exception.SignerException;

import java.security.PublicKey;

public interface SignerSignClient {

    byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) throws SignerException;

    byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
            throws SignerException;
}
