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
import type { MaintenanceMode } from '@/openapi-types';
import type { ConfigurablePropertyDto } from '@niis/shared-ui';

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
    effective_value: { type: 'string' },
    value_source: { type: 'string', enum: ['DEFAULT', 'COUNTRY_PROFILE', 'DB'] },
    country_profile: { type: 'string' },
    scope: { type: 'string' },
    restart_policy: { type: 'string', enum: ['SERVICES', 'ALL_SERVICES', 'NONE_ARCHIVER'] },
    restart_services: { type: 'array', items: { type: 'string' } },
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

const configurablePropertiesFixture: ConfigurablePropertyDto[] = [
  {
    property_name: 'xroad.proxy-ui-api.rate-limit-requests-per-second',
    current_value: '20',
    default_value: '10',
    effective_value: '20',
    value_source: 'DB',
    scope: 'proxy-ui-api',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-proxy-ui-api'],
  },
  {
    property_name: 'xroad.proxy-ui-api.rate-limit-delay-after-requests',
    default_value: '10',
    effective_value: '15',
    value_source: 'COUNTRY_PROFILE',
    country_profile: 'FI',
    scope: 'proxy-ui-api',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-proxy-ui-api'],
  },
  {
    property_name: 'xroad.proxy-ui-api.empty-param',
    default_value: '',
    effective_value: '',
    value_source: 'DEFAULT',
    scope: 'proxy-ui-api',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-proxy-ui-api'],
  },
  {
    property_name: 'xroad.proxy.some-proxy-param',
    default_value: '1',
    effective_value: '1',
    value_source: 'DEFAULT',
    scope: 'proxy',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-proxy'],
  },
  {
    property_name: 'xroad.common.some-other-param',
    default_value: '5',
    effective_value: '5',
    value_source: 'DEFAULT',
    scope: 'common',
    restart_policy: 'ALL_SERVICES',
    restart_services: [],
  },
  {
    property_name: 'xroad.message-log.archive-max-filesize',
    default_value: '33554432',
    effective_value: '33554432',
    value_source: 'DEFAULT',
    scope: 'message-log-archiver',
    restart_policy: 'NONE_ARCHIVER',
    restart_services: [],
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

describe('System Parameters — value and source columns (Browser Mode)', () => {
  const rowOf = (scope: string, propertyName: string) =>
    page
      .getByTestId(`configurable-properties-table-body-${scope}`)
      .getByTestId('configurable-property-row')
      .filter({ hasText: propertyName });

  it('shows the source of the database, country profile and default rows', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();
    await page.getByTestId('configurable-properties-panel-title-common').click();

    const dbRow = rowOf('proxy-ui-api', 'xroad.proxy-ui-api.rate-limit-requests-per-second');
    await expect.element(dbRow.getByTestId('configurable-property-source')).toHaveTextContent('Database');

    const profileRow = rowOf('proxy-ui-api', 'xroad.proxy-ui-api.rate-limit-delay-after-requests');
    await expect.element(profileRow.getByTestId('configurable-property-source')).toHaveTextContent('Country profile FI');
    await expect.element(profileRow).toMatchTextContent(/15/);

    const defaultRow = rowOf('common', 'xroad.common.some-other-param');
    await expect.element(defaultRow.getByTestId('configurable-property-source')).toHaveTextContent('Default');
  });

  it('shows a dash for an empty effective value', async () => {
    await renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [maintenanceModeDisabledHandler, propertiesHandler],
    });

    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();

    const emptyRow = rowOf('proxy-ui-api', 'xroad.proxy-ui-api.empty-param');
    await expect.element(emptyRow.getByTestId('configurable-property-value')).toHaveTextContent('-');
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

    await expect.element(targetRow).toMatchTextContent(/20/);
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

describe('System Parameters — restart warning lists service names (Browser Mode)', () => {
  const rowOf = (scope: string, propertyName: string) =>
    page
      .getByTestId(`configurable-properties-table-body-${scope}`)
      .getByTestId('configurable-property-row')
      .filter({ hasText: propertyName });

  const editProperty = async (scope: string, propertyName: string, value: string) => {
    await rowOf(scope, propertyName).getByTestId('edit-configurable-property-button').click();
    await expect.element(page.getByTestId('configurable-property-value-field')).toBeVisible();
    const input = page.getByTestId('configurable-property-value-field').getByRole('textbox');
    await input.clear();
    await input.fill(value);
    await page.getByRole('button', { name: /save/i }).click();
    await expect.element(page.getByTestId('configurable-property-value-field')).not.toBeInTheDocument();
  };

  const renderPage = () =>
    renderRoute('/settings/system-parameters', {
      permissions: systemParamPermissions,
      msw: [
        maintenanceModeDisabledHandler,
        propertiesHandler,
        specHttp.patch('/system/property', ({ response }) => response(204).empty()),
      ],
    });

  it('lists the service names of two edited categories', async () => {
    await renderPage();
    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();
    await page.getByTestId('configurable-properties-panel-title-proxy').click();

    await editProperty('proxy-ui-api', 'xroad.proxy-ui-api.rate-limit-requests-per-second', '25');
    await editProperty('proxy', 'xroad.proxy.some-proxy-param', '2');

    await expect
      .element(page.getByTestId('configurable-properties-restart-warning'))
      .toMatchTextContent(/: xroad-proxy-ui-api, xroad-proxy\.$/);
  });

  it('lists a service name once when two properties of one category are edited', async () => {
    await renderPage();
    await page.getByTestId('configurable-properties-panel-title-proxy-ui-api').click();

    await editProperty('proxy-ui-api', 'xroad.proxy-ui-api.rate-limit-requests-per-second', '25');
    await editProperty('proxy-ui-api', 'xroad.proxy-ui-api.rate-limit-delay-after-requests', '16');

    await expect
      .element(page.getByTestId('configurable-properties-restart-warning'))
      .toMatchTextContent(/: xroad-proxy-ui-api\.$/);
  });

  const warning = () => page.getByTestId('configurable-properties-restart-warning');
  const restartAll = () => page.getByTestId('configurable-properties-restart-all');
  const restartServices = () => page.getByTestId('configurable-properties-restart-services');
  const archiverNote = () => page.getByTestId('configurable-properties-archiver-note');

  const openPanels = async (...scopes: string[]) => {
    for (const scope of scopes) {
      await page.getByTestId(`configurable-properties-panel-title-${scope}`).click();
    }
  };

  const editCommon = () => editProperty('common', 'xroad.common.some-other-param', '6');
  const editService = () => editProperty('proxy', 'xroad.proxy.some-proxy-param', '2');
  const editArchiver = () => editProperty('message-log-archiver', 'xroad.message-log.archive-max-filesize', '1024');

  it.each([
    ['common then service', [editCommon, editService]],
    ['service then common', [editService, editCommon]],
    ['common only', [editCommon]],
  ])('a shared setting asks to restart all services without a list: %s', async (_name, edits) => {
    await renderPage();
    await openPanels('common', 'proxy');

    for (const edit of edits) await edit();

    await expect.element(warning()).toMatchTextContent(/^(warning)?All X-Road services need to be restarted for the changes to take effect\.$/);
    expect(restartServices().query()).toBeNull();
    expect(archiverNote().query()).toBeNull();
  });

  it('an archiver-only edit shows the next-run note and no restart sentence', async () => {
    await renderPage();
    await openPanels('message-log-archiver');

    await editArchiver();

    await expect.element(warning()).toMatchTextContent(/^(warning)?The change applies on the next run of the message log archiver\.$/);
    expect(restartAll().query()).toBeNull();
    expect(restartServices().query()).toBeNull();
  });

  it.each([
    ['archiver then service', [editArchiver, editService]],
    ['service then archiver', [editService, editArchiver]],
  ])('an archiver edit and a service edit show both sentences: %s', async (_name, edits) => {
    await renderPage();
    await openPanels('message-log-archiver', 'proxy');

    for (const edit of edits) await edit();

    await expect.element(restartServices()).toMatchTextContent(/: xroad-proxy\.$/);
    await expect.element(archiverNote()).toMatchTextContent(/next run of the message log archiver\.$/);
    expect(restartAll().query()).toBeNull();
  });
});
