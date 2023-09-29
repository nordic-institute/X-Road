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
package ee.ria.xroad.common.junit.helper;

import ee.ria.xroad.common.util.NoCoverage;

import io.vavr.CheckedConsumer;
import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import lombok.SneakyThrows;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.mockito.verification.VerificationMode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static ee.ria.xroad.common.junit.helper.WithInOrder.Private.ENCLOSING_OBJECT_FIELD;

public interface WithInOrder {

    final class Private {
        private Private() { }
        static final String ENCLOSING_OBJECT_FIELD = "this$0";
    }

    @NoCoverage
    final class InOrder implements org.mockito.InOrder {

        protected final org.mockito.InOrder inOrder;

        protected InOrder(org.mockito.InOrder inOrder) {
            this.inOrder = inOrder;
        }

        @Override
        public <T> T verify(T t) {
            return inOrder.verify(t);
        }

        @Override
        public <T> T verify(T t, VerificationMode verificationMode) {
            return inOrder.verify(t, verificationMode);
        }

        @Override
        public void verify(MockedStatic<?> mockedStatic, MockedStatic.Verification verification) {
            inOrder.verify(mockedStatic, verification);
        }

        @Override
        public void verify(MockedStatic<?> mockedStatic, MockedStatic.Verification verification,
                           VerificationMode mode) {
            inOrder.verify(mockedStatic, verification, mode);
        }
        @Override
        public void verifyNoMoreInteractions() {
            inOrder.verifyNoMoreInteractions();
        }

        public void verify(CheckedConsumer<org.mockito.InOrder> inOrderConsumer) {
            inOrderConsumer.unchecked().accept(inOrder);
            verifyNoMoreInteractions();
        }
    }

    @SneakyThrows
    @NoCoverage
    private static Object getFieldValue(Object thiz, Field field) {
        boolean isAccessible = field.canAccess(thiz);
        try {
            if (!isAccessible) {
                field.setAccessible(true);
            }
            return field.get(thiz);
        } finally {
            if (isAccessible) {
                field.setAccessible(isAccessible);
            }
        }
    }

    @NoCoverage
    private HashSet getClassDeclaredFieldMocks(Object thiz, Class aClass) {
        return Option.of(aClass)
                .filter(WithInOrder.class::isAssignableFrom)
                .fold(HashSet::empty, __ -> Option.of(aClass)
                        .map(Class::getDeclaredFields)
                        .map(io.vavr.collection.HashSet::of).get()
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .map(field -> getFieldValue(thiz, field))
                        .filter(maybeMock -> MockUtil.isMock(maybeMock) || MockUtil.isSpy(maybeMock))
                )
                .transform(testClassMocks -> {
                    Class superClass = aClass.getSuperclass();
                    boolean hasSuperClass = superClass != null;
                    if (hasSuperClass) {
                        testClassMocks = testClassMocks.addAll(getClassDeclaredFieldMocks(thiz, superClass));
                    }
                    return testClassMocks;
                });
    }

    @SneakyThrows
    @NoCoverage
    default InOrder inOrder(Object... additionalMocks) {
        Object thiz = this;
        Class aClass = this.getClass();

        HashSet testClassMocks = getClassDeclaredFieldMocks(thiz, aClass);

        while (aClass.getEnclosingClass() != null) {
            thiz = getFieldValue(thiz, aClass.getDeclaredField(ENCLOSING_OBJECT_FIELD));
            aClass = aClass.getEnclosingClass();

            boolean enclosingClassWithInOrder = WithInOrder.class.isAssignableFrom(aClass);
            if (enclosingClassWithInOrder) {
                testClassMocks = testClassMocks.addAll(getClassDeclaredFieldMocks(thiz, aClass));
            }
        }

        Object[] mocks = testClassMocks.addAll(HashSet.of(additionalMocks)).toJavaArray();

        return new InOrder(Mockito.inOrder(mocks));
    }

}
