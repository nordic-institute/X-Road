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
package org.niis.xroad.arch.rule;

import com.societegenerale.commons.plugin.rules.ArchRuleTest;
import com.societegenerale.commons.plugin.service.ScopePathProvider;
import com.societegenerale.commons.plugin.utils.ArchUtils;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.Bean;

import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

public class NoBeanAnnotationWithInitDestroy implements ArchRuleTest {

    @Override
    public void execute(String packagePath, ScopePathProvider scopePathProvider, Collection<String> excludedPaths) {
        methods().that()
                .areAnnotatedWith(Bean.class)
                .should(new BeanWithInitAndDestroyCondition())
                .because("Beans can be defined in multiple places, which leads to verbose definitions"
                        + " and additional points of failures (missed init/destroy methods)")
                .allowEmptyShould(true)
                .check(ArchUtils.importAllClassesInPackage(scopePathProvider.getMainClassesPath(), packagePath, excludedPaths));
    }

    static class BeanWithInitAndDestroyCondition extends ArchCondition<JavaMethod> {

        BeanWithInitAndDestroyCondition() {
            super("@Bean without initMethod and DestroyMethod");
        }

        @Override
        public void check(JavaMethod javaMethod, ConditionEvents events) {
            var destroyMethodValue = javaMethod.getAnnotationOfType(Bean.class).destroyMethod();
            var initMethodValue = javaMethod.getAnnotationOfType(Bean.class).initMethod();

            var destroyMethodSet = StringUtils.isNotBlank(destroyMethodValue)
                    && !destroyMethodValue.equals(AbstractBeanDefinition.INFER_METHOD);
            var initMethodSet = StringUtils.isNotBlank(initMethodValue);

            if (destroyMethodSet || initMethodSet) {
                var message = "Method <%s> is annotated with @Bean and initMethod(%s)/destroyMethod(%s)"
                        .formatted(javaMethod,
                                initMethodValue,
                                destroyMethodValue);
                events.add(SimpleConditionEvent.violated(javaMethod, message));
            }
        }
    }
}
