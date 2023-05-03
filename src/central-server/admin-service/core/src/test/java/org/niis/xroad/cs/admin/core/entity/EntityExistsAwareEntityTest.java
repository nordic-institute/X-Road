/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class EntityExistsAwareEntityTest implements WithInOrder {

    private static final boolean NOT_EXISTS = false;
    private static final boolean EXISTS = true;

    private final Entity notExistingEntity = new Entity(NOT_EXISTS);
    private final Entity existingEntity = new Entity(EXISTS);

    @RequiredArgsConstructor
    private class Entity implements EntityExistsAwareEntity<Entity> {
        @Accessors(fluent = true)
        @Getter
        private final boolean exists;
    }

    @Mock
    private RuntimeException runtimeException;

    @Nested
    @DisplayName("exists()")
    public class ExistsMethod {

        @Test
        @DisplayName("should exist")
        public void shouldExist(TestInfo testInfo) {

            boolean isExists = existingEntity.exists();

            assertTrue(isExists, testInfo.getDisplayName());
        }

        @Test
        @DisplayName("should not exist")
        public void shouldNotExist(TestInfo testInfo) {

            boolean isExists = notExistingEntity.exists();

            assertFalse(isExists, testInfo.getDisplayName());
        }
    }

    @Nested
    @DisplayName("notExists()")
    public class NotExistsMethod {

        @Test
        @DisplayName("should not exist")
        public void shouldExist(TestInfo testInfo) {

            boolean isExists = notExistingEntity.notExists();

            assertTrue(isExists, testInfo.getDisplayName());
        }

        @Test
        @DisplayName("should not not exist")
        public void shouldNotExist(TestInfo testInfo) {

            boolean isExists = existingEntity.notExists();

            assertFalse(isExists, testInfo.getDisplayName());
        }
    }

    @Nested
    @DisplayName("ifExists(Consumer<SELF> consumer)")
    public class IfExistsConsumerMethod implements WithInOrder {

        @Mock
        private Consumer<Entity> entityConsumer;

        @Test
        @DisplayName("should call consumer if entity exist")
        public void shouldCallConsumerIfEntityExist() {
            doNothing().when(entityConsumer).accept(existingEntity);

            existingEntity.ifExists(entityConsumer);

            inOrder().verify(inOrder -> {
                inOrder.verify(entityConsumer).accept(existingEntity);
            });
        }

        @Test
        @DisplayName("should not call consumer if entity exist")
        public void shouldNotCallConsumerIfEntityExist() {

            notExistingEntity.ifExists(entityConsumer);

            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("ifExists(Function<SELF, R> function) ")
    public class IfExistsFunctionMethod implements WithInOrder {

        @Mock
        private Function<Entity, Entity> entityFunction;
        @Mock
        private Entity otherEntity;

        @Test
        @DisplayName("should call function if entity exist")
        public void shouldCallFunctionIfEntityExist() {
            doReturn(otherEntity).when(entityFunction).apply(existingEntity);

            Option<Entity> returnedEntityOption = existingEntity.ifExists(entityFunction);

            assertThat(returnedEntityOption.toJavaOptional())
                    .isPresent()
                    .hasValue(otherEntity);
            inOrder().verify(inOrder -> {
                inOrder.verify(entityFunction).apply(existingEntity);
            });
        }

        @Test
        @DisplayName("should not call function if entity exist")
        public void shouldNotCallFunctionIfEntityExist() {

            Option<Entity> returnedEntityOption = notExistingEntity.ifExists(entityFunction);

            assertThat(returnedEntityOption.toJavaOptional())
                    .isNotPresent();
            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("ifNotExists(Supplier<SELF> supplier) ")
    public class IfNotExistsSupplierMethod implements WithInOrder {

        @Mock
        private Supplier<Entity> entitySupplier;
        @Mock
        private Entity otherEntity;

        @Test
        @DisplayName("should call supplier if entity doesn't exist")
        public void shouldCallSupplierIfEntityDoesntExist() {
            doReturn(otherEntity).when(entitySupplier).get();

            Entity returnedEntity = notExistingEntity.ifNotExists(entitySupplier);

            assertEquals(otherEntity, returnedEntity);
            inOrder().verify(inOrder -> {
                inOrder.verify(entitySupplier).get();
            });
        }

        @Test
        @DisplayName("should not call supplier if entity exist")
        public void shouldNotCallSupplierIfEntityExist() {

            Entity returnedEntity = existingEntity.ifNotExists(entitySupplier);

            assertEquals(existingEntity, returnedEntity);
            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("ifExistsThrow(Supplier<? extends RuntimeException> throwsSupplier)")
    public class IfExistsThrowSupplierMethod implements WithInOrder {

        @Mock
        private Supplier<RuntimeException> runtimeExceptionSupplier;

        @Test
        @DisplayName("should throw an exception if entity exists")
        public void shouldThrowAnExceptionIfEntityExists() {
            doThrow(runtimeException).when(runtimeExceptionSupplier).get();

            ThrowableAssert.ThrowingCallable testable = () -> existingEntity.ifExistsThrow(runtimeExceptionSupplier);

            assertThatThrownBy(testable).isEqualTo(runtimeException);
            inOrder().verify(inOrder -> {
                inOrder.verify(runtimeExceptionSupplier).get();
            });
        }

        @Test
        @DisplayName("should not throw an exception if entity doesn't exist")
        public void shouldNotThrowAnExceptionIfEntityDoesntExist() {

            notExistingEntity.ifExistsThrow(runtimeExceptionSupplier);

            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("ifExistsThrow(Function<SELF, ? extends RuntimeException> throwsSupplier)")
    public class IfExistsThrowFunctionMethod implements WithInOrder {

        @Mock
        private Function<Entity, RuntimeException> runtimeExceptionFunction;

        @Test
        @DisplayName("should throw an exception if entity exists")
        public void shouldThrowAnExceptionIfEntityExists() {
            doThrow(runtimeException).when(runtimeExceptionFunction).apply(existingEntity);

            ThrowableAssert.ThrowingCallable testable = () -> existingEntity.ifExistsThrow(runtimeExceptionFunction);

            assertThatThrownBy(testable).isEqualTo(runtimeException);
            inOrder().verify(inOrder -> {
                inOrder.verify(runtimeExceptionFunction).apply(existingEntity);
            });
        }

        @Test
        @DisplayName("should not throw an exception if entity doesn't exist")
        public void shouldNotThrowAnExceptionIfEntityDoesntExist() {

            notExistingEntity.ifExistsThrow(runtimeExceptionFunction);

            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("ifNotExistsThrow(Supplier<? extends RuntimeException> throwsSupplier)")
    public class IfNotExistsThrowSupplierMethod implements WithInOrder {

        @Mock
        private Supplier<RuntimeException> runtimeExceptionSupplier;

        @Test
        @DisplayName("should throw an exception if entity not exists")
        public void shouldThrowAnExceptionIfEntityNotExists() {
            doThrow(runtimeException).when(runtimeExceptionSupplier).get();

            ThrowableAssert.ThrowingCallable testable = () -> notExistingEntity.ifNotExistsThrow(runtimeExceptionSupplier);

            assertThatThrownBy(testable).isEqualTo(runtimeException);
            inOrder().verify(inOrder -> {
                inOrder.verify(runtimeExceptionSupplier).get();
            });
        }

        @Test
        @DisplayName("should return an entity if entity doesn't not exists")
        public void shouldReturnAnEntityIfEntityDoesntNotDoesntExist() {

            Entity returnedEntity = existingEntity.ifNotExistsThrow(runtimeExceptionSupplier);

            assertEquals(existingEntity, returnedEntity);
            inOrder().verifyNoMoreInteractions();
        }
    }

}
