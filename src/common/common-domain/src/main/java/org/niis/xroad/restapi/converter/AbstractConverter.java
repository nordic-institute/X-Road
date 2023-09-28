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
package org.niis.xroad.restapi.converter;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static ee.ria.xroad.common.util.Fn.self;

public abstract class AbstractConverter<A, B> implements GenericConverter {
    protected final Class<A> classOfA;
    protected final Class<B> classOfB;

    protected final Set<ConvertiblePair> convertibles;

    protected AbstractConverter() {
        Type typeA = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        Type typeB = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.classOfA = (Class) typeA;
        this.classOfB = (Class) typeB;
        this.convertibles = self(new HashSet<>(), self -> {
            self.add(new ConvertiblePair(classOfA, classOfB));
            self.add(new ConvertiblePair(classOfB, classOfA));
        });
    }

    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.unmodifiableSet(convertibles);
    }

    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (classOfA.isAssignableFrom(sourceType.getType()) && classOfB.isAssignableFrom(targetType.getType())) {
            return this.convertToB((A) source);
        } else if (classOfB.isAssignableFrom(sourceType.getType()) && classOfA.isAssignableFrom(targetType.getType())) {
            return this.convertToA((B) source);
        } else {
            throw new IllegalArgumentException("Cannot convert " + sourceType + " to " + targetType);
        }
    }

    public <S, T> T convert(S source) {
        if (source == null) {
            return null;
        }
        Class<S> sourceClass = (Class<S>) source.getClass();
        Class<T> targetClass = (Class<T>) (classOfA.isAssignableFrom(sourceClass) ? classOfB : classOfA);
        return (T) convert(source, TypeDescriptor.valueOf(sourceClass), TypeDescriptor.valueOf(targetClass));
    }

    protected abstract A convertToA(B source);

    protected abstract B convertToB(A source);

    public Converter<A, B> aToB() {
        return this::convertToB;
    }

    public Converter<B, A> bToA() {
        return this::convertToA;
    }

}
