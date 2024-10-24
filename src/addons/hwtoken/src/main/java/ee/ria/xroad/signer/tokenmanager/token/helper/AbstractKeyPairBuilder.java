/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.tokenmanager.token.helper;

import ee.ria.xroad.signer.tokenmanager.module.PrivKeyAttributes;
import ee.ria.xroad.signer.tokenmanager.module.PubKeyAttributes;

import iaik.pkcs.pkcs11.objects.BooleanAttribute;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.PublicKey;

import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.setAllowedMechanisms;

public abstract class AbstractKeyPairBuilder<PU extends PublicKey, PR extends PrivateKey> {
    protected abstract void setPublicKeyAttributes(PU template, PubKeyAttributes attributes);

    protected abstract PU newPublicKeyTemplate();

    protected abstract PR newPrivateKeyTemplate();

    protected PU buildPublicKeyTemplate(byte[] id, String keyLabel, PubKeyAttributes attributes) {
        var template = newPublicKeyTemplate();

        template.getId().setByteArrayValue(id);
        template.getLabel().setCharArrayValue(keyLabel.toCharArray());

        setPublicKeyAttributes(template, attributes);

        // Public key is a token object (not a session object).
        template.getToken().setBooleanValue(Boolean.TRUE);

        safeBooleanSet(attributes.getEncrypt(), template.getEncrypt());
        safeBooleanSet(attributes.getVerify(), template.getVerify());
        safeBooleanSet(attributes.getWrap(), template.getWrap());
        safeBooleanSet(attributes.getVerifyRecover(), template.getVerifyRecover());
        safeBooleanSet(attributes.getTrusted(), template.getTrusted());

        if (attributes.getAllowedMechanisms() != null) {
            setAllowedMechanisms(template, attributes.getAllowedMechanisms());
        }

        return template;
    }

    protected PR buildPrivateKeyTemplate(byte[] id, String keyLabel, PrivKeyAttributes attributes) {
        var template = newPrivateKeyTemplate();

        template.getId().setByteArrayValue(id);
        template.getLabel().setCharArrayValue(keyLabel.toCharArray());

        setPrivateKeyAttributes(template, attributes);

        return template;
    }

    protected void setPrivateKeyAttributes(PrivateKey template, PrivKeyAttributes attributes) {
        // Private key is a token object (not a session object).
        template.getToken().setBooleanValue(Boolean.TRUE);
        // This is a private object.
        template.getPrivate().setBooleanValue(Boolean.TRUE);

        safeBooleanSet(attributes.getSensitive(), template.getSensitive());
        safeBooleanSet(attributes.getDecrypt(), template.getDecrypt());
        safeBooleanSet(attributes.getSign(), template.getSign());
        safeBooleanSet(attributes.getSignRecover(), template.getSignRecover());
        safeBooleanSet(attributes.getUnwrap(), template.getUnwrap());
        safeBooleanSet(attributes.getExtractable(), template.getExtractable());
        safeBooleanSet(attributes.getAlwaysSensitive(), template.getAlwaysSensitive());
        safeBooleanSet(attributes.getWrapWithTrusted(), template.getWrapWithTrusted());
        safeBooleanSet(attributes.getNeverExtractable(), template.getNeverExtractable());

        if (attributes.getAllowedMechanisms() != null) {
            setAllowedMechanisms(template, attributes.getAllowedMechanisms());
        }
    }

    private static void safeBooleanSet(Boolean value, BooleanAttribute attribute) {
        if (value != null) {
            attribute.setBooleanValue(value);
        }
    }


}
