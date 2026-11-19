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
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { worker } from '../setup/browser-setup';
import { TokenInitStatus } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';

// Render the init page with a NOT_INITIALIZED system state so all fields are editable.
async function renderInitPage() {
  const view = await renderRoute('/init', {
    permissions: ['INIT_CONFIG'],
    systemStatus: {
      systemStatus: {
        initialization_status: {
          instance_identifier: '',
          central_server_address: '',
          software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
        },
        high_availability_status: { is_ha_configured: false, node_name: undefined },
      },
    },
  });

  await expect.element(page.getByTestId('instance-identifier--input').getByRole('textbox')).toHaveFocus();
  return view;
}

describe('0100 — CS Initialization — PIN match indicator (Browser Mode)', () => {
  it('check-mark appears when confirm PIN matches PIN, disappears on mismatch, submit disabled on mismatch', async () => {
    await renderInitPage();

    await expect.element(page.getByTestId('pin--input')).toBeVisible();

    // No check-mark before anything is typed
    await expect.element(page.getByTestId('confirm-pin-append-input-icon')).not.toBeInTheDocument();

    // Type matching PINs
    await page.getByTestId('pin--input').getByRole('textbox').fill('1111');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('1111');

    await expect.element(page.getByTestId('confirm-pin-append-input-icon')).toBeVisible();
    await expect.element(page.getByTestId('submit-button')).toBeDisabled();

    // Change confirm to mismatch
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('1234');

    await expect.element(page.getByTestId('confirm-pin-append-input-icon')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('submit-button')).toBeDisabled();
  });
});

describe('0100 — CS Initialization — submit gating (Browser Mode)', () => {
  it('submit disabled until all fields filled with matching PINs, disabled again on mismatch', async () => {
    await renderInitPage();

    await expect.element(page.getByTestId('submit-button')).toBeDisabled();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('cs-e2e');
    await page.getByTestId('address--input').getByRole('textbox').fill('valid.example.org');
    await page.getByTestId('pin--input').getByRole('textbox').fill('1111');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('1111');

    await expect.element(page.getByTestId('confirm-pin-append-input-icon')).toBeVisible();
    await expect.element(page.getByTestId('submit-button')).not.toBeDisabled();

    // PIN mismatch disables submit again
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('wrong-one');
    await expect.element(page.getByTestId('submit-button')).toBeDisabled();
  });
});

describe('0100 — CS Initialization — inline error on invalid instance identifier (Browser Mode)', () => {
  it('backend 400 (validation_failure on identifier) renders inline error on identifier field', async () => {
    await renderInitPage();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('INVALID&&::INSTANCE');
    await page.getByTestId('address--input').getByRole('textbox').fill('valid.example.org');
    await page.getByTestId('pin--input').getByRole('textbox').fill('Valid_Pin_11');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('Valid_Pin_11');

    worker.use(
      specHttp.post('/initialization', ({ response }) =>
        response(400).json({
          status: 400,
          error: {
            code: 'validation_failure',
            validation_errors: {
              'initialServerConfDto.instanceIdentifier': ['IdentifierCharsField'],
            },
          },
        } as never),
      ),
    );

    await page.getByTestId('submit-button').click();

    await expect.element(page.getByText('Use valid identifier characters only')).toBeVisible();
  });
});

describe('0100 — CS Initialization — inline error on invalid CS address (Browser Mode)', () => {
  it('backend 400 (validation_failure on address) renders inline error on address field', async () => {
    await renderInitPage();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('CS-E2E');
    await page.getByTestId('address--input').getByRole('textbox').fill('invalid...address...fo');
    await page.getByTestId('pin--input').getByRole('textbox').fill('Valid_Pin_11');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('Valid_Pin_11');

    worker.use(
      specHttp.post('/initialization', ({ response }) =>
        response(400).json({
          status: 400,
          error: {
            code: 'validation_failure',
            validation_errors: {
              'initialServerConfDto.centralServerAddress': ['ValidHostAddressField'],
            },
          },
        } as never),
      ),
    );

    await page.getByTestId('submit-button').click();

    await expect.element(page.getByText('Valid IP address or fully qualified domain name needed')).toBeVisible();
  });
});

describe('0100 — CS Initialization — inline error on weak PIN (Browser Mode)', () => {
  it('backend 400 (token_weak_pin) renders inline error on PIN field', async () => {
    await renderInitPage();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('CS-E2E');
    await page.getByTestId('address--input').getByRole('textbox').fill('valid.example.org');
    await page.getByTestId('pin--input').getByRole('textbox').fill('1');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('1');

    worker.use(
      specHttp.post('/initialization', ({ response }) =>
        response(400).json({
          status: 400,
          error: { code: 'token_weak_pin' },
        } as never),
      ),
    );

    await page.getByTestId('submit-button').click();

    // Message may render in both the PIN field error and a notification; first() handles either.
    await expect.element(page.getByText('The provided pin code was too weak').first()).toBeVisible();
  });
});

describe('0100 — CS Initialization — leaves the view after a successful init (Browser Mode)', () => {
  it('lands on Members once initialization succeeds, after the system status is refreshed', async () => {
    let serverInitialized = false;

    function systemStatus() {
      return serverInitialized
        ? {
            initialization_status: {
              instance_identifier: 'CS-E2E',
              central_server_address: 'valid.example.org',
              software_token_init_status: TokenInitStatus.INITIALIZED,
            },
            high_availability_status: { is_ha_configured: false, node_name: undefined },
          }
        : {
            initialization_status: {
              instance_identifier: '',
              central_server_address: '',
              software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
            },
            high_availability_status: { is_ha_configured: false, node_name: undefined },
          };
    }

    worker.use(
      specHttp.get('/system/status', ({ response }) => response(200).json(systemStatus() as never)),
      specHttp.post('/initialization', ({ response }) => {
        serverInitialized = true;
        return response(201).empty();
      }),
    );

    const { router } = await renderRoute('/init', {
      withGuard: true,
      permissions: [Permissions.INIT_CONFIG, Permissions.VIEW_MEMBERS],
      systemStatus: { systemStatus: systemStatus() },
    });

    await expect.element(page.getByTestId('instance-identifier--input').getByRole('textbox')).toHaveFocus();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('CS-E2E');
    await page.getByTestId('address--input').getByRole('textbox').fill('valid.example.org');
    await page.getByTestId('pin--input').getByRole('textbox').fill('Valid_Pin_11');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('Valid_Pin_11');

    await page.getByTestId('submit-button').click();

    await expect.element(page.getByTestId('members-table')).toBeVisible();
    await expect.element(page.getByTestId('central-server-initialization-page')).not.toBeInTheDocument();
    expect(router.currentRoute.value.name).toBe(RouteName.Members);
  });
});

describe('0100 — CS Initialization — leaves the view after a successful init even when the status re-read fails (Browser Mode)', () => {
  it('still lands on Members when the POST succeeds but the status GET fails', async () => {
    let initialized = false;

    worker.use(
      specHttp.untyped.get('/api/v1/system/status', () =>
        initialized
          ? HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 })
          : HttpResponse.json({
              initialization_status: {
                instance_identifier: '',
                central_server_address: '',
                software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
              },
              high_availability_status: { is_ha_configured: false },
            }),
      ),
      specHttp.post('/initialization', ({ response }) => {
        initialized = true;
        return response(201).empty();
      }),
    );

    const { router } = await renderRoute('/init', {
      withGuard: true,
      permissions: [Permissions.INIT_CONFIG, Permissions.VIEW_MEMBERS],
      systemStatus: {
        systemStatus: {
          initialization_status: {
            instance_identifier: '',
            central_server_address: '',
            software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
          },
          high_availability_status: { is_ha_configured: false, node_name: undefined },
        },
      },
    });

    await expect.element(page.getByTestId('instance-identifier--input').getByRole('textbox')).toHaveFocus();

    await page.getByTestId('instance-identifier--input').getByRole('textbox').fill('CS-E2E');
    await page.getByTestId('address--input').getByRole('textbox').fill('valid.example.org');
    await page.getByTestId('pin--input').getByRole('textbox').fill('Valid_Pin_11');
    await page.getByTestId('confirm-pin--input').getByRole('textbox').fill('Valid_Pin_11');

    await page.getByTestId('submit-button').click();

    await expect.element(page.getByTestId('central-server-initialization-page')).not.toBeInTheDocument();
    expect(router.currentRoute.value.name).toBe(RouteName.Members);
  });
});
