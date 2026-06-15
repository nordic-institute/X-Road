/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Institute for Interoperability Solutions (NIIS)
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
import { page } from 'vitest/browser';
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

// MIGRATED-FROM: 0910-ss-diagnostics-traffic.feature :: "Default filter and traffic chart is displayed"
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
