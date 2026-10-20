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

import { describe, it, expect } from 'vitest';
import { getInstanceByDom } from 'echarts/core';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { AddOnStatus, OperationalDataInterval } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const addonStatusSchema = {
  type: 'object',
  required: ['messagelog_enabled', 'opmonitoring_enabled'],
  properties: {
    messagelog_enabled: { type: 'boolean' },
    opmonitoring_enabled: { type: 'boolean' },
  },
};

const operationalDataIntervalSchema = {
  type: 'object',
  properties: {
    interval_start_time: { type: 'string' },
    success_count: { type: 'integer' },
    failure_count: { type: 'integer' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const addonStatusFixture: AddOnStatus = {
  messagelog_enabled: true,
  opmonitoring_enabled: true,
};

const emptyIntervalsFixture: OperationalDataInterval[] = [];

validateBody(addonStatusSchema, addonStatusFixture);
validateBody({ type: 'array', items: operationalDataIntervalSchema }, emptyIntervalsFixture);

// ── Handlers ──────────────────────────────────────────────────────────────────

const addonStatusHandler = specHttp.get('/diagnostics/addon-status', ({ response }) =>
  response(200).json(addonStatusFixture),
);

const operationalMonitoringHandler = specHttp.get('/diagnostics/operational-monitoring', ({ response }) =>
  response(200).json(emptyIntervalsFixture),
);

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('Diagnostics Traffic — default filter state and chart render (Browser Mode)', () => {
  it('renders with all filter selects empty, service select disabled, and chart element present', async () => {
    await renderRoute('/diagnostics/traffic', {
      permissions: [Permissions.DIAGNOSTICS],
      msw: [addonStatusHandler, operationalMonitoringHandler],
    });

    await expect.element(page.getByTestId('select-client')).toBeVisible();
    await expect.element(page.getByTestId('select-exchangeRole')).toBeVisible();
    await expect.element(page.getByTestId('select-status')).toBeVisible();
    await expect.element(page.getByTestId('select-service')).toBeVisible();

    const serviceSelectContainer = page.getByTestId('select-service');
    await expect.element(serviceSelectContainer).toBeVisible();
    const serviceContainerEl = serviceSelectContainer.query();
    expect(serviceContainerEl?.classList.contains('v-field--disabled') || serviceContainerEl?.querySelector('input')?.disabled).toBe(true);

    const chartEl = document.querySelector('x-vue-echarts');
    expect(chartEl).not.toBeNull();
  });
});

describe('Diagnostics Traffic — out-of-range request (Browser Mode)', () => {
  it('shows the English text of the error code returned for an out-of-range request', async () => {
    await renderRoute('/diagnostics/traffic', {
      permissions: [Permissions.DIAGNOSTICS],
      msw: [
        addonStatusHandler,
        specHttp.untyped.get('/api/v1/diagnostics/operational-monitoring', () =>
          HttpResponse.json({ status: 400, error: { code: 'operational_data_intervals_out_of_range' } }, { status: 400 }),
        ),
      ],
    });

    await expect
      .element(
        page.getByText(
          'The selected time period is invalid. Set the end date and time to the start or later. If needed, shorten the selected period.',
        ),
      )
      .toBeVisible();
  });
});

function chartSeriesDataLengths(): number[] {
  const chart = getInstanceByDom(document.querySelector('x-vue-echarts .echarts-host') as HTMLElement);
  const series = (chart?.getOption().series ?? []) as Array<{ data: unknown[] }>;
  return series.map((item) => item.data.length);
}

function chartLoadingBar(): Element | null {
  return document.querySelector('x-vue-echarts')?.parentElement?.querySelector(':scope > .v-progress-linear') ?? null;
}

describe('Diagnostics Traffic — end before start (Browser Mode)', () => {
  it('sends no request and shows no data while the end is before the start, and requests again once the end is not before the start', async () => {
    const requests: Array<{ from: string; to: string }> = [];
    await renderRoute('/diagnostics/traffic', {
      permissions: [Permissions.DIAGNOSTICS],
      msw: [
        addonStatusHandler,
        specHttp.untyped.get('/api/v1/diagnostics/operational-monitoring', ({ request }) => {
          const params = new URL(request.url).searchParams;
          const from = params.get('records_from') as string;
          const to = params.get('records_to') as string;
          requests.push({ from, to });
          if (new Date(to) < new Date(from)) {
            return HttpResponse.json({ status: 400, error: { code: 'operational_data_intervals_out_of_range' } }, { status: 400 });
          }
          return HttpResponse.json([{ interval_start_time: from, success_count: 3, failure_count: 1 }]);
        }),
      ],
    });

    await expect.poll(() => requests.length).toBeGreaterThan(0);
    await expect.poll(chartSeriesDataLengths).toEqual([1, 1]);

    const startDate = page.getByTestId('period-start-date').getByRole('textbox');
    const endDate = page.getByTestId('period-end-date').getByRole('textbox');
    await endDate.fill((startDate.query() as HTMLInputElement).value);
    await endDate.click();
    await page.getByTestId('period-start-time').getByRole('textbox').fill('12:00');
    await page.getByTestId('period-end-time').getByRole('textbox').fill('10:00');
    await expect.poll(chartSeriesDataLengths).toEqual([0, 0]);

    const requestsBeforeGuard = requests.length;
    await page.getByTestId('period-end-time').getByRole('textbox').fill('10:30');
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(requests.length).toBe(requestsBeforeGuard);
    expect(page.getByTestId('contextual-alert').query()).toBeNull();
    expect(chartSeriesDataLengths()).toEqual([0, 0]);

    await page.getByTestId('period-end-time').getByRole('textbox').fill('12:00');
    await expect.poll(() => requests.length).toBeGreaterThan(requestsBeforeGuard);
    await expect.poll(chartSeriesDataLengths).toEqual([1, 1]);
    expect(page.getByTestId('contextual-alert').query()).toBeNull();
  });
});

describe('Diagnostics Traffic — end before start while a request is in flight (Browser Mode)', () => {
  it('keeps the chart empty and sends no request when an in-flight request resolves after the end moved before the start', async () => {
    const requests: Array<{ from: string; to: string }> = [];
    let gate: Promise<void> | undefined;
    await renderRoute('/diagnostics/traffic', {
      permissions: [Permissions.DIAGNOSTICS],
      msw: [
        addonStatusHandler,
        specHttp.untyped.get('/api/v1/diagnostics/operational-monitoring', async ({ request }) => {
          const params = new URL(request.url).searchParams;
          const from = params.get('records_from') as string;
          requests.push({ from, to: params.get('records_to') as string });
          await gate;
          return HttpResponse.json([{ interval_start_time: from, success_count: 3, failure_count: 1 }]);
        }),
      ],
    });

    await expect.poll(chartSeriesDataLengths).toEqual([1, 1]);

    const startDate = page.getByTestId('period-start-date').getByRole('textbox');
    const endDate = page.getByTestId('period-end-date').getByRole('textbox');
    await endDate.fill((startDate.query() as HTMLInputElement).value);
    await endDate.click();
    await page.getByTestId('period-end-time').getByRole('textbox').fill('23:00');
    await expect.poll(() => requests.length).toBeGreaterThan(1);
    await new Promise((resolve) => setTimeout(resolve, 300));

    let release!: () => void;
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requestsBeforeDelayed = requests.length;
    await page.getByTestId('period-end-time').getByRole('textbox').fill('12:00');
    await expect.poll(() => requests.length).toBe(requestsBeforeDelayed + 1);

    await page.getByTestId('period-start-time').getByRole('textbox').fill('13:00');
    await expect.poll(chartSeriesDataLengths).toEqual([0, 0]);

    release();
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(requests.length).toBe(requestsBeforeDelayed + 1);
    expect(chartSeriesDataLengths()).toEqual([0, 0]);
    expect(page.getByTestId('contextual-alert').query()).toBeNull();
  });
});

describe('Diagnostics Traffic — end before start while a request that then fails is in flight (Browser Mode)', () => {
  it('shows no error and no loading indicator and keeps the chart empty when an in-flight request fails after the end moved before the start', async () => {
    const requests: Array<{ from: string; to: string }> = [];
    let gate: Promise<void> | undefined;
    let failHeldRequest = false;
    await renderRoute('/diagnostics/traffic', {
      permissions: [Permissions.DIAGNOSTICS],
      msw: [
        addonStatusHandler,
        specHttp.untyped.get('/api/v1/diagnostics/operational-monitoring', async ({ request }) => {
          const params = new URL(request.url).searchParams;
          const from = params.get('records_from') as string;
          requests.push({ from, to: params.get('records_to') as string });
          await gate;
          if (failHeldRequest) {
            return HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 });
          }
          return HttpResponse.json([{ interval_start_time: from, success_count: 3, failure_count: 1 }]);
        }),
      ],
    });

    await expect.poll(chartSeriesDataLengths).toEqual([1, 1]);

    const startDate = page.getByTestId('period-start-date').getByRole('textbox');
    const endDate = page.getByTestId('period-end-date').getByRole('textbox');
    await endDate.fill((startDate.query() as HTMLInputElement).value);
    await endDate.click();
    await page.getByTestId('period-end-time').getByRole('textbox').fill('23:00');
    await expect.poll(() => requests.length).toBeGreaterThan(1);
    await new Promise((resolve) => setTimeout(resolve, 300));

    let release!: () => void;
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requestsBeforeHeld = requests.length;
    await page.getByTestId('period-end-time').getByRole('textbox').fill('12:00');
    await expect.poll(() => requests.length).toBe(requestsBeforeHeld + 1);
    await expect.poll(() => chartLoadingBar()).not.toBeNull();

    await page.getByTestId('period-start-time').getByRole('textbox').fill('13:00');
    await expect.poll(chartSeriesDataLengths).toEqual([0, 0]);
    await expect.poll(() => chartLoadingBar()).toBeNull();

    failHeldRequest = true;
    release();
    await new Promise((resolve) => setTimeout(resolve, 600));
    expect(requests.length).toBe(requestsBeforeHeld + 1);
    expect(page.getByTestId('contextual-alert').query()).toBeNull();
    expect(chartSeriesDataLengths()).toEqual([0, 0]);
    expect(chartLoadingBar()).toBeNull();
  });
});
