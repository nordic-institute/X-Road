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
import { onBeforeUnmount } from 'vue';

export type PollingOptions<T> = {
  intervalMs: number;
  request: () => Promise<T>;
  shouldContinue: (result: T) => boolean;
  onResult: (result: T) => void;
  onError: (error: unknown) => void;
};

/**
 * Re-reads a status while it keeps asking for it, stopping when a read says to or when the view unmounts.
 *
 * A failed read reports the error through `onError` and leaves the polling schedule as it was, so the
 * next interval retries.
 *
 * Call `refresh()` for the first read and after any action that may change whether polling should continue.
 */
export function usePolling<T>(options: PollingOptions<T>) {
  let timer: ReturnType<typeof setInterval> | undefined;
  let inFlight: Promise<void> | undefined;
  let unmounted = false;

  function stop(): void {
    clearInterval(timer);
    timer = undefined;
  }

  function updateSchedule(keepPolling: boolean): void {
    if (keepPolling && !unmounted) {
      timer ??= setInterval(tick, options.intervalMs);
    } else {
      stop();
    }
  }

  function tick(): void {
    if (!inFlight) {
      void refresh();
    }
  }

  function readOnce(): Promise<void> {
    return options
      .request()
      .then((result) => {
        options.onResult(result);
        updateSchedule(options.shouldContinue(result));
      })
      .catch((error) => {
        options.onError(error);
      });
  }

  function refresh(): Promise<void> {
    const tracked: Promise<void> = (inFlight ?? Promise.resolve()).then(readOnce).finally(() => {
      if (inFlight === tracked) {
        inFlight = undefined;
      }
    });
    inFlight = tracked;
    return tracked;
  }

  onBeforeUnmount(() => {
    unmounted = true;
    stop();
  });

  return { refresh };
}
