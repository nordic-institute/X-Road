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
package org.eclipse.edc.verifiablecredentials.signature;

import com.nimbusds.jose.JOSEObject;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;

public class ExtJWSObject extends JWSObject {
    public ExtJWSObject(JWSHeader header, Payload payload) {
        super(header, payload);
    }

    public ExtJWSObject(Base64URL firstPart, Base64URL secondPart, Base64URL thirdPart) throws ParseException {
        super(firstPart, secondPart, thirdPart);
    }

    public ExtJWSObject(Base64URL firstPart, Payload payload, Base64URL thirdPart) throws ParseException {
        super(firstPart, payload, thirdPart);
    }

    public static JWSObject parseExt(final String s, final Payload detachedPayload)
            throws ParseException {

        Base64URL[] parts = JOSEObject.split(s);

        if (parts.length != 3) {
            throw new ParseException("Unexpected number of Base64URL parts, must be three", 0);
        }

        if (!parts[1].toString().isEmpty()) {
            throw new ParseException("The payload Base64URL part must be empty", 0);
        }

        return new ExtJWSObject(parts[0], detachedPayload, parts[2]);
    }

    @Override
    public byte[] getSigningInput() {
        /*
        TODO jose JWT library saves byte arrays to string before returning them.
         This in some cases leads to signature corruption as original binary data is lost.
         */

        byte[] firstPart = (getHeader().toBase64URL().toString() + '.').getBytes(StandardCharsets.UTF_8);

        byte[] secondPart;
        if (getPayload().getOrigin() == Payload.Origin.BYTE_ARRAY) {
            secondPart = getPayload().toBytes();
        } else {
            secondPart = getPayload().toString().getBytes(StandardCharsets.UTF_8);
        }
        byte[] result = new byte[firstPart.length + secondPart.length];

        System.arraycopy(firstPart, 0, result, 0, firstPart.length);
        System.arraycopy(secondPart, 0, result, firstPart.length, secondPart.length);

        return result;
    }
}
