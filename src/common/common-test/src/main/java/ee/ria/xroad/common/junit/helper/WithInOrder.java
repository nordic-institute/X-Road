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

import lombok.SneakyThrows;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.mockito.verification.VerificationMode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public interface WithInOrder {

    final class Private {
        private Private() {
        }

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

        public void verify(Consumer<org.mockito.InOrder> inOrderConsumer) {
            inOrderConsumer.accept(inOrder);
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
            if (!isAccessible) {
                field.setAccessible(false);
            }
        }
    }

    @NoCoverage
    private Set<Object> getClassDeclaredFieldMocks(Object thiz, Class<?> aClass) {
        Set<Object> testClassMocks = new HashSet<>();
        if (WithInOrder.class.isAssignableFrom(aClass)) {
            Field[] declaredFields = aClass.getDeclaredFields();
            for (Field field : declaredFields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    Object fieldValue = getFieldValue(thiz, field);
                    if (MockUtil.isMock(fieldValue) || MockUtil.isSpy(fieldValue)) {
                        testClassMocks.add(fieldValue);
                    }
                }
            }
        }
        Class<?> superClass = aClass.getSuperclass();
        if (superClass != null) {
            testClassMocks.addAll(getClassDeclaredFieldMocks(thiz, superClass));
        }
        return testClassMocks;
    }

    @SneakyThrows
    @NoCoverage
    default InOrder inOrder(Object... additionalMocks) {
        Object thiz = this;
        Class<?> aClass = this.getClass();

        Set<Object> testClassMocks = getClassDeclaredFieldMocks(thiz, aClass);

        while (aClass.getEnclosingClass() != null) {
            thiz = getFieldValue(thiz, aClass.getDeclaredField(Private.ENCLOSING_OBJECT_FIELD));
            aClass = aClass.getEnclosingClass();

            if (WithInOrder.class.isAssignableFrom(aClass)) {
                testClassMocks.addAll(getClassDeclaredFieldMocks(thiz, aClass));
            }
        }

        Set<Object> allMocks = new HashSet<>(testClassMocks);
        allMocks.addAll(Arrays.asList(additionalMocks));

        Object[] mocks = allMocks.toArray();

        return new InOrder(Mockito.inOrder(mocks));
    }
}
