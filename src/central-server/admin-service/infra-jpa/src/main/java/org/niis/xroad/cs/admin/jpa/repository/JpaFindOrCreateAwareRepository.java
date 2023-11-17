/*
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
package org.niis.xroad.cs.admin.jpa.repository;

import jakarta.persistence.Column;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Id;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.core.entity.AuditableEntity;
import org.niis.xroad.cs.admin.core.entity.AuditableEntity_;
import org.niis.xroad.cs.admin.core.entity.EntityExistsAwareEntity;
import org.niis.xroad.cs.admin.core.repository.FindOrCreateAwareRepository;
import org.niis.xroad.cs.admin.jpa.example.ExampleMatcherExt;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.FluentQuery;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@NoRepositoryBean
public interface JpaFindOrCreateAwareRepository<ENTITY, ID> extends JpaRepository<ENTITY, ID>, FindOrCreateAwareRepository<ENTITY, ID> {

    /**
     * Return or create an equivalent model from the repository.
     */
    default ENTITY findOrCreate(ENTITY model) {
        return findBy(Example.of(model, exampleMatcher(model)), FluentQuery.FetchableFluentQuery::first)
                .orElseGet(() -> save(model));
    }

    /**
     * Return an equivalent model from the repository.
     * <p>
     */
    default Optional<ENTITY> findOpt(ENTITY model) {
        return findBy(Example.of(model, exampleMatcher(model)), FluentQuery.FetchableFluentQuery::first);
    }

    /**
     * Return an equivalent model from the repository.
     * <p>
     */
    default ENTITY findOne(ENTITY model) {
        return findOpt(model)
                .orElseThrow(() -> new EntityNotFoundException(model.toString()));
    }

    /**
     * Create an equivalent model to the repository.
     *
     * @param model        the model to create
     * @param errorMessage the error message to use if the model already exists
     * @return a persisted model
     */
    default ENTITY create(ENTITY model, ErrorMessage errorMessage) {
        Function<ENTITY, ENTITY> ensureNotPresent = entity -> {
            throw new DataIntegrityException(errorMessage, entity.toString());
        };

        return findBy(Example.of(model, exampleMatcher(model)), FluentQuery.FetchableFluentQuery::first)
                .map(ensureNotPresent)
                .orElseGet(() -> save(model));
    }

    private ExampleMatcher exampleMatcher(ENTITY model) {
        ExampleMatcherExt exampleMatcher = ExampleMatcherExt.matching();

        Optional<String> idColumnName = getIdColumnName(model);
        if (idColumnName.isPresent()) {
            boolean isPersisted = model instanceof EntityExistsAwareEntity && ((EntityExistsAwareEntity) model).exists();
            if (isPersisted) {
                return exampleMatcher.withOnlyPaths(idColumnName.get());
            } else {
                exampleMatcher = exampleMatcher.withIgnorePaths(idColumnName.get());
            }
        }

        boolean shouldExcludeTimestamps = model instanceof AuditableEntity;
        if (shouldExcludeTimestamps) {
            return exampleMatcher.withIgnorePaths(AuditableEntity_.CREATED_AT, AuditableEntity_.UPDATED_AT);
        }

        return exampleMatcher;
    }

    private Optional<String> getIdColumnName(ENTITY model) {
        return FieldUtils.getFieldsListWithAnnotation(model.getClass(), Id.class).stream()
                .map(field -> field.getDeclaredAnnotation(Column.class))
                .filter(Objects::nonNull)
                .map(Column::name)
                .findFirst();
    }
}
