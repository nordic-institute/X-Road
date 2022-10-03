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
package org.niis.xroad.centralserver.restapi.repository;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.niis.xroad.centralserver.restapi.entity.AuditableEntity;
import org.niis.xroad.centralserver.restapi.entity.AuditableEntity_;
import org.niis.xroad.centralserver.restapi.entity.EntityExistsAware;
import org.niis.xroad.centralserver.restapi.entity.example.ExampleMatcherExt;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.FluentQuery;

import javax.persistence.Column;
import javax.persistence.Id;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@NoRepositoryBean
public interface FindOrCreateAwareRepository<ENTITY, ID> extends JpaRepository<ENTITY, ID> {

    /**
     * Return or create an equivalent model from the repository.
     * <p>
     * todo: this should use findOne (old data model does not guarantee unique identifiers)
     */
    default ENTITY findOrCreate(ENTITY model) {
        return findBy(Example.of(model, exampleMatcher(model)), FluentQuery.FetchableFluentQuery::first)
                .orElseGet(() -> save(model));
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
            throw new EntityExistsException(errorMessage, entity.toString());
        };

        return findBy(Example.of(model, exampleMatcher(model)), FluentQuery.FetchableFluentQuery::first)
                .map(ensureNotPresent)
                .orElseGet(() -> save(model));
    }

    private ExampleMatcher exampleMatcher(ENTITY model) {
        ExampleMatcherExt exampleMatcher = ExampleMatcherExt.matching();

        Optional<String> idColumnName = getIdColumnName(model);
        if (idColumnName.isPresent()) {
            boolean isPersisted = model instanceof EntityExistsAware && ((EntityExistsAware) model).exists();
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
