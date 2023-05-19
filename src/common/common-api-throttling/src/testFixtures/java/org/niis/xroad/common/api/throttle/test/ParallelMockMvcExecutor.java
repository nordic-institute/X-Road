/*
 *  The MIT License
 *  Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.common.api.throttle.test;

import lombok.SneakyThrows;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ParallelMockMvcExecutor implements AutoCloseable {
    private static final int DEFAULT_PARALLELISM = 10;

    private final MockMvc mockMvc;
    private final ExecutorService executorService;

    private final List<Callable<MvcResult>> callables = new ArrayList<>();

    public ParallelMockMvcExecutor(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_PARALLELISM);
    }

    public ParallelMockMvcExecutor(MockMvc mockMvc, int parallelism) {
        this.mockMvc = mockMvc;
        this.executorService = Executors.newFixedThreadPool(parallelism);
    }

    public void run(Callable<MockHttpServletRequestBuilder> requestBuilder, int repeatTimes) {
        for (int i = 0; i < repeatTimes; i++) {
            callables.add(() -> mockMvc.perform(requestBuilder.call()).andReturn());
        }
    }

    public List<MvcResult> getExecuted() throws InterruptedException {
        var futures = executorService.invokeAll(callables);


        return futures.stream()
                .map(this::fromFuture)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private MvcResult fromFuture(Future<MvcResult> future) {
        return future.get();
    }

    @Override
    public void close() throws Exception {
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
    }

}
