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
import { page } from 'vitest/browser';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { MaintenanceMode, SecurityServerConfigurableProperty } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const maintenanceModeSchema = {
  type: 'object',
  required: ['status', 'is_management_services_provider'],
  properties: {
    status: {
      type: 'string',
      enum: ['PENDING_ENABLE_MAINTENANCE_MODE', 'ENABLED_MAINTENANCE_MODE', 'PENDING_DISABLE_MAINTENANCE_MODE', 'DISABLED_MAINTENANCE_MODE'],
    },
    is_management_services_provider: { type: 'boolean' },
    message: { type: 'string' },
  },
};

const configurablePropertySchema = {
  type: 'object',
  properties: {
    property_name: { type: 'string' },
    current_value: { type: 'string' },
    default_value: { type: 'string' },
    scope: { type: 'string' },
  },
};

const addonStatusSchema = {
  type: 'object',
  required: ['messagelog_enabled', 'opmonitoring_enabled'],
  properties: {
    messagelog_enabled: { type: 'boolean' },
    opmonitoring_enabled: { type: 'boolean' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const maintenanceModeDisabledFixture: MaintenanceMode = {
  status: 'DISABLED_MAINTENANCE_MODE',
  is_management_services_provider: false,
};

const maintenanceModeDisabledMgmtFixture: MaintenanceMode = {
  status: 'DISABLED_MAINTENANCE_MODE',
  is_management_services_provider: true,
};

const configurablePropertiesFixture: SecurityServerConfigurableProperty[] = [
  {
    property_name: 'xroad.proxy-ui-api.rate-limit-requests-per-second',
    current_value: '20',
    default_value: '20',
    scope: 'proxy-ui-api',
  },
  {
    property_name: 'xroad.proxy-ui-api.rate-limit-delay-after-requests',
    current_value: '10',
    default_value: '10',
    scope: 'proxy-ui-api',
  },
  {
    property_name: 'xroad.common.some-other-param',
    current_value: '5',
    default_value: '5',
    scope: 'common',
  },
];

validateBody(maintenanceModeSchema, maintenanceModeDisabledFixture);
validateBody(maintenanceModeSchema, maintenanceModeDisabledMgmtFixture);
validateBody({ type: 'array', items: configurablePropertySchema }, configurablePropertiesFixture);
validateBody(addonStatusSchema, { messagelog_enabled: true, opmonitoring_enabled: true });

// ── Base permissions covering the configurable-properties section ─────────────

const systemParamPermissions = [
  Permissions.VIEW_SYS_PARAMS,
  Permissions.CHANGE_CONFIGURATION_PROPERTY,
  Permissions.TOGGLE_MAINTENANCE_MODE,
];

// ── Handlers ──────────────────────────────────────────────────────────────────

const propertiesHandler = specHttp.get('/system/property', ({ response }) =>
  response(200).json(configurablePropertiesFixture),
);

const maintenanceModeDisabledHandler = specHttp.get('/system/maintenance-mode', ({ response }) =>
  response(200).json(maintenanceModeDisabledFixture),
);

const maintenanceModeDisabledMgmtHandler = specHttp.get('/system/maintenance-mode', ({ response }) =>
  response(200).json(maintenanceModeDisabledMgmtFixture),
);

const addonStatusHandler = specHttp.get('/diagnostics/addon-status', ({ response }) =>
  response(200).json({ messagelog_enabled: true, opmonitoring_enabled: true }),
);

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('System Parameters — maintenance mode disabled for management services provider (Browser Mode)', () => {
  it('toggle is disabled when this server is the management services provider', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledMgmtHandler, propertiesHandler],
    });

    const switchContainer = page.getByTestId('maintenance-mode-switch');
    await expect.element(switchContainer).toBeVisible();
    const toggle = switchContainer.getByRole('checkbox');
    await expect.element(toggle).not.toBeChecked();
    await expect.element(toggle).toBeDisabled();
  });

  it('toggle is enabled when this server is NOT the management services provider', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    const switchContainer = page.getByTestId('maintenance-mode-switch');
    await expect.element(switchContainer).toBeVisible();
    const toggle = switchContainer.getByRole('checkbox');
    await expect.element(toggle).not.toBeChecked();
    await expect.element(toggle).not.toBeDisabled();
  });
});

describe('System Parameters — configurable properties panels visible (Browser Mode)', () => {
  it('renders the panels container and the proxy-ui-api scope panel', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await expect.element(page.getByTestId('configurable-properties-panels')).toBeVisible();
    await expect.element(page.getByTestId('configurable-properties-panel-proxy-ui-api')).toBeVisible();
  });
});

describe('System Parameters — panel expand shows rows (Browser Mode)', () => {
  it('rows are hidden before expand and visible after clicking the panel title', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await expect.element(page.getByTestId('configurable-properties-panel-proxy-ui-api')).toBeVisible();

    expect(page.getByTestId('configurable-properties-table-body-proxy-ui-api').query()).toBeNull();

    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();

    const rows = page.getByTestId('configurable-properties-table-body-proxy-ui-api').getByTestId('configurable-property-row');
    await expect.element(rows.first()).toBeVisible();
  });
});

describe('System Parameters — edit dialog cancelled (Browser Mode)', () => {
  it('dialog opens, cancel closes it, and the row value is unchanged', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();

    const targetRow = page
      .getByTestId('configurable-properties-table-body-proxy-ui-api')
      .getByTestId('configurable-property-row')
      .filter({ hasText: 'xroad.proxy-ui-api.rate-limit-requests-per-second' });

    await expect.element(targetRow).toBeVisible();

    expect(page.getByTestId('configurable-property-value-field').query()).toBeNull();

    await targetRow.getByTestId('edit-configurable-property-button').click();
    await expect.element(page.getByTestId('configurable-property-value-field')).toBeVisible();

    await page.getByRole('button', { name: /cancel/i }).click();

    expect(page.getByTestId('configurable-property-value-field').query()).toBeNull();

    await expect.element(targetRow).toHaveTextContent(/20/);
  });
});

describe('System Parameters — properties filtered by search (Browser Mode)', () => {
  it('matched row is present and non-matching scope rows are absent after filtering', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await expect.element(page.getByTestId('configurable-properties-panel-proxy-ui-api')).toBeVisible();
    await expect.element(page.getByTestId('configurable-properties-panel-common')).toBeVisible();

    await page.getByTestId('configurable-properties-search').getByRole('textbox').fill('rate-limit-requests-per-second');

    await expect.element(page.getByTestId('configurable-properties-panel-proxy-ui-api')).toBeVisible();

    await expect.element(
      page
        .getByTestId('configurable-properties-table-body-proxy-ui-api')
        .getByTestId('configurable-property-row')
        .filter({ hasText: 'xroad.proxy-ui-api.rate-limit-requests-per-second' }),
    ).toBeVisible();

    expect(page.getByTestId('configurable-properties-panel-common').query()).toBeNull();
  });
});

describe('System Parameters — property edited shows restart warning (Browser Mode)', () => {
  it('restart warning absent before edit and visible after a successful save', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [
        maintenanceModeDisabledHandler,
        propertiesHandler,
        specHttp.patch('/system/property', ({ response }) => response(204).empty()),
      ],
    });

    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();

    expect(page.getByTestId('configurable-properties-restart-warning').query()).toBeNull();

    const targetRow = page
      .getByTestId('configurable-properties-table-body-proxy-ui-api')
      .getByTestId('configurable-property-row')
      .filter({ hasText: 'xroad.proxy-ui-api.rate-limit-requests-per-second' });

    await targetRow.getByTestId('edit-configurable-property-button').click();
    await expect.element(page.getByTestId('configurable-property-value-field')).toBeVisible();

    const input = page.getByTestId('configurable-property-value-field').getByRole('textbox');
    await input.clear();
    await input.fill('25');

    await page.getByRole('button', { name: /save/i }).click();

    await expect.element(page.getByTestId('configurable-property-value-field')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('configurable-properties-restart-warning')).toBeVisible();
  });
});
