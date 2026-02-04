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

package org.niis.xroad.test.framework.core.container;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors Docker container resource usage by periodically logging docker stats.
 */
@Slf4j
public final class DockerStatsMonitor implements AutoCloseable {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(2);
    private static final String DOCKER_STATS_FORMAT =
            "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}";

    private final Duration interval;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DockerStatsMonitor() {
        this(DEFAULT_INTERVAL);
    }

    public DockerStatsMonitor(Duration interval) {
        this.interval = interval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual()
                .name("docker-stats-monitor")
                .factory());
    }

    /**
     * Starts the docker stats monitoring.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                    this::collectAndLogStats,
                    interval.toSeconds(),
                    interval.toSeconds(),
                    TimeUnit.SECONDS
            );
            log.info("Docker stats monitor started (interval: {})", interval);
        }
    }

    /**
     * Stops the docker stats monitoring and releases resources.
     */
    @Override
    @SuppressWarnings("checkstyle:MagicNumber")
    public void close() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping docker stats monitor...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("Docker stats monitor did not terminate gracefully");
                    }
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Docker stats monitor stopped");
        }
    }

    /**
     * Manually trigger stats collection (useful for on-demand logging).
     */
    public void logStatsNow() {
        collectAndLogStats();
    }

    private void collectAndLogStats() {
        try {
            var result = executeDockerStats();
            if (result.exitCode() == 0 && !result.output().isBlank()) {
                log.info("Docker container stats:\n{}", result.output());
            } else if (result.exitCode() != 0) {
                log.warn("Docker stats command failed (exit code: {}): {}", result.exitCode(), result.output());
            }
        } catch (Exception e) {
            log.warn("Failed to collect docker stats: {}", e.getMessage());
        }
    }

    private StatsResult executeDockerStats() throws IOException, InterruptedException {
        var process = new ProcessBuilder("docker", "stats", "--no-stream", "--format", DOCKER_STATS_FORMAT)
                .redirectErrorStream(true)
                .start();

        String output;
        try (var reader = process.inputReader()) {
            output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        }

        int exitCode = process.waitFor();
        return new StatsResult(exitCode, output);
    }

    private record StatsResult(int exitCode, String output) {
    }
}

