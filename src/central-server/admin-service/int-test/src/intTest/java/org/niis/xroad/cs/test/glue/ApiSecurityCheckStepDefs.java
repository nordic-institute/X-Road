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

package org.niis.xroad.cs.test.glue;


import com.nortal.test.core.report.ReportFormatter;
import com.nortal.test.core.report.html.ReportHtmlTableGenerator;
import feign.FeignException;
import io.cucumber.java.en.Step;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.fail;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ApiSecurityCheckStepDefs extends BaseStepDefs {

    private static final Map<String, Set<String>> SKIP_ENDPOINTS = Map.of(
            "FeignManagementRequestsApi", Set.of("findManagementRequestsInternal"),
            "FeignOpenapiApi", Set.of("downloadOpenApi")
    );

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ReportFormatter reportFormatter;
    @Autowired
    private ReportHtmlTableGenerator reportHtmlTableGenerator;

    private final Random random = new Random();

    @Step("All endpoints should fail with status code {int} without authorization header")
    public void allEndpointsFailWithCode(int code) {
        List<ResultDto> results = new ArrayList<>();

        Map<String, Object> feignClients = applicationContext.getBeansWithAnnotation(FeignClient.class);
        for (Object feignClient : feignClients.values()) {
            Arrays.stream(feignClient.getClass().getDeclaredMethods())
                    .filter(this::isFeignMethod)
                    .forEach(method -> {
                        String status = null;
                        try {
                            if (shouldBeSkipped(feignClient, method)) {
                                status = "SKIPPED";
                            } else {
                                method.invoke(feignClient, getMethodArguments(method));
                            }
                        } catch (InvocationTargetException e) {
                            if (e.getTargetException() instanceof FeignException) {
                                status = String.valueOf(((FeignException) e.getTargetException()).status());
                            } else {
                                status = e.getMessage();
                            }
                        } catch (Exception e) {
                            status = e.getMessage();
                        }
                        results.add(new ResultDto(friendlyName(feignClient), method.getName(), status));
                    });
        }

        generateReport(results, String.valueOf(code));
    }

    private boolean shouldBeSkipped(Object feignClient, Method method) {
        String feignClientName = friendlyName(feignClient);
        return SKIP_ENDPOINTS.containsKey(feignClientName)
                && SKIP_ENDPOINTS.get(feignClientName).contains(method.getName());
    }

    private String friendlyName(Object feignClient) {
        try {
            return String.valueOf(feignClient).split(",")[0].split("=")[1];
        } catch (Exception e) {
            return String.valueOf(feignClient);
        }
    }

    private void generateReport(List<ResultDto> results, String expectedStatus) {
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("#", "Status", "FeignClient", "Method", "Response code"));
        Set<String> resultStatuses = new HashSet<>();
        for (ResultDto result : results) {
            String statusText = getStatusText(result.getStatus(), expectedStatus);
            table.add(List.of(statusText, result.getFeignClient(), result.getMethod(), String.valueOf(result.getStatus())));
            resultStatuses.add(statusText);
        }
        ReportFormatter.Attachment attachment = new ReportFormatter.Attachment().setName("RESULTS");
        attachment.addSection("", ReportFormatter.SectionType.TABLE, reportHtmlTableGenerator.generateTable(table, true));
        reportFormatter.formatAndAddToReport(attachment);
        if (resultStatuses.contains("FAILED")) {
            fail("There are test failures. Please check the report");
        }
    }

    private String getStatusText(String status, String expectedStatus) {
        if ("SKIPPED".equals(status)) {
            return "SKIPPED";
        } else {
            return expectedStatus.equals(status) ? "OK" : "FAILED";
        }
    }

    private boolean isFeignMethod(Method method) {
        return method.getReturnType().equals(ResponseEntity.class);
    }

    private Object[] getMethodArguments(Method method) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            args[i] = createValue(parameters[i].getType());
        }

        return args;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private Object createValue(Class<?> type) {
        if (type.isEnum()) {
            Object[] enumValues = type.getEnumConstants();
            return enumValues[random.nextInt(enumValues.length)];
        } else if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            return random.nextInt();
        } else if (type.equals(Long.TYPE) || type.equals(Long.class)) {
            return random.nextLong();
        } else if (type.equals(Double.TYPE) || type.equals(Double.class)) {
            return random.nextDouble();
        } else if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            return random.nextFloat();
        } else if (type.equals(String.class)) {
            return randomAlphabetic(5);
        } else if (type.equals(BigInteger.class)) {
            return BigInteger.valueOf(random.nextInt());
        } else if (type.equals(Resource.class)) {
            return new ByteArrayResource(new byte[0]);
        }

        return createAndFill(type);
    }

    private Object createAndFill(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                field.set(instance, createValue(field.getType()));
            }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    private static class ResultDto {
        private final String feignClient;
        private final String method;
        private final String status;
    }

}
