/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.message;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.Unmarshaller.Listener;
import javax.xml.bind.annotation.XmlElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;
import static ee.ria.xroad.common.ErrorCodes.translateException;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
class RequiredHeaderFieldsChecker extends Listener {

    private final Class<?> clazz;

    @Override
    public void afterUnmarshal(Object target, Object parent) {
        if (target.getClass().isAssignableFrom(clazz)) {
            try {
                checkRequiredFields(target);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    static void checkRequiredFields(Object obj) throws Exception {
        for (Field field : getDeclaredFields(obj.getClass())) {
            XmlElement annotation = SoapUtils.getXmlElementAnnotation(field);
            if (annotation != null) {
                Object value = getValue(field, obj);

                if (annotation.required() && value == null) {
                    throw new CodedException(X_MISSING_HEADER_FIELD,
                            "Required field '%s' is missing",
                            annotation.name());
                }

                if (value != null && value instanceof ValidatableField) {
                    ((ValidatableField) value).validate();
                }
            }
        }
    }

    static Object getValue(Field field, Object obj) throws Exception {
        field.setAccessible(true); // the field might be private
        return field.get(obj);
    }

    private static List<Field> getDeclaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();

        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        return fields;
    }
}
