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
import { defineComponent } from 'vue';
import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PollingOptions, usePolling } from '@/composables/use-polling';

type Status = { done: boolean };

function mountPolling(options: PollingOptions<Status>) {
  const PollingHost = defineComponent({
    setup: () => usePolling(options),
    render: () => null,
  });
  return mount(PollingHost);
}

describe('usePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('reports a failed read through onError and keeps polling for the next tick', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    const request = vi
      .fn()
      .mockResolvedValueOnce({ done: false })
      .mockRejectedValueOnce(new Error('read failed'))
      .mockResolvedValueOnce({ done: false });

    const wrapper = mountPolling({
      intervalMs: 1000,
      request,
      shouldContinue: (result) => !result.done,
      onResult,
      onError,
    });

    await wrapper.vm.refresh();
    expect(request).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1000);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(1000);
    expect(request).toHaveBeenCalledTimes(3);
    expect(onResult).toHaveBeenCalledTimes(2);
  });

  it('stops polling once a read reports the run is over', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    const request = vi
      .fn()
      .mockResolvedValueOnce({ done: false })
      .mockResolvedValueOnce({ done: true });

    const wrapper = mountPolling({
      intervalMs: 1000,
      request,
      shouldContinue: (result) => !result.done,
      onResult,
      onError,
    });

    await wrapper.vm.refresh();
    expect(request).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1000);
    expect(request).toHaveBeenCalledTimes(2);
    expect(onResult).toHaveBeenLastCalledWith({ done: true });

    await vi.advanceTimersByTimeAsync(5000);
    expect(request).toHaveBeenCalledTimes(2);
    expect(onError).not.toHaveBeenCalled();
  });

  it('stops polling once the view unmounts', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    const request = vi.fn().mockResolvedValue({ done: false });

    const wrapper = mountPolling({
      intervalMs: 1000,
      request,
      shouldContinue: () => true,
      onResult,
      onError,
    });

    await wrapper.vm.refresh();
    expect(request).toHaveBeenCalledTimes(1);

    wrapper.unmount();

    await vi.advanceTimersByTimeAsync(5000);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
