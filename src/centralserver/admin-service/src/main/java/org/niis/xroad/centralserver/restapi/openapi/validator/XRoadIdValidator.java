/**
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
package org.niis.xroad.centralserver.restapi.openapi.validator;

import com.google.common.base.CharMatcher;
import org.niis.xroad.centralserver.openapi.model.ClientId;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class XRoadIdValidator implements ConstraintValidator<ValidXRoadId, XRoadId> {

    private static final CharMatcher MATCHER = CharMatcher.javaIsoControl()
            .or(CharMatcher.anyOf(":;%/\\\ufeff\u200b"));

    @Override
    public boolean isValid(XRoadId value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (!isValidPart(value.getInstanceId())) return false;

        if (value instanceof ClientId) {
            var id = (ClientId) value;
            return isValidPart(id.getMemberClass())
                    && isValidPart(id.getMemberCode())
                    && ((id.getType() == XRoadId.TypeEnum.SUBSYSTEM && isValidPart(id.getSubsystemCode()))
                    || ((id.getType() == XRoadId.TypeEnum.MEMBER && id.getSubsystemCode() == null)));
        } else if (value instanceof SecurityServerId) {
            var id = (SecurityServerId) value;
            return isValidPart(id.getMemberClass())
                    && isValidPart(id.getMemberCode())
                    && isValidPart(id.getServerCode());
        }
        return false;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private boolean isValidPart(String part) {
        return part != null
                && !part.isEmpty()
                && part.length() <= 255
                && !MATCHER.matchesAnyOf(part);
    }
}
