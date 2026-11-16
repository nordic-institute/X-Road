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
import { describe, it, expect, vi } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';

const ISSUER_SERVICE_PATH = '/settings/issuer-service';
const API_KEYS_LIST_PATH = '/settings/api-keys';

function statusHandler(body: object) {
  return specHttp.get('/issuer-service', ({ response }) =>
    response(200).json({
      host: null,
      bound: false,
      host_set: false,
      certificate_ready: false,
      run_in_progress: false,
      can_sign: false,
      status: 'NOT_CONFIGURED',
      ...body,
    }),
  );
}

function chipClassOf(rowTestId: string): string | undefined {
  return page.getByTestId(rowTestId).element().querySelector('.v-chip')?.className;
}

describe('CS Issuer Service tab — fresh server (Browser Mode)', () => {
  it('shows the host and certificate rows red, and the identity not yet bound', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [statusHandler({})],
    });

    await expect.element(page.getByTestId('issuer-service-host-row').getByText('Not set')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-certificate-row').getByText('Not ready')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('Not yet bound')).toBeVisible();
    expect(chipClassOf('issuer-service-host-row')).toMatch(/error-container/);
    expect(chipClassOf('issuer-service-certificate-row')).toMatch(/error-container/);
  });
});

describe('CS Issuer Service tab — status read failure (Browser Mode)', () => {
  it('shows an error notification when the status read fails', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () =>
          HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
  });
});

describe('CS Issuer Service tab — connector TLS certificate link (Browser Mode)', () => {
  it('shows the link to the Dataspace TLS certificate page with view permission', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS, Permissions.VIEW_DS_TLS_CERT],
      msw: [statusHandler({})],
    });

    await expect.element(page.getByTestId('issuer-service-certificate-link')).toBeVisible();
  });

  it('hides the link to the Dataspace TLS certificate page without view permission', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [statusHandler({})],
    });

    await expect.poll(() => page.getByTestId('issuer-service-certificate-link').query()).toBeNull();
  });
});

describe('CS Issuer Service tab — Issuer Service cannot sign (Browser Mode)', () => {
  it('shows the cause when the Issuer Service cannot sign', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [
        statusHandler({
          host: 'issuer.example.org',
          host_set: true,
          certificate_ready: true,
          bound: true,
          can_sign: false,
          can_sign_cause: 'NO_ACTIVE_KEY',
          status: 'FAILED',
        }),
      ],
    });

    await expect
      .element(page.getByTestId('issuer-service-can-sign-row').getByText('No active issuer key is configured'))
      .toBeVisible();
  });
});

describe('CS Settings navigation — Issuer Service tab (Browser Mode)', () => {
  it('lists the Issuer Service tab only with VIEW_SYSTEM_SETTINGS', async () => {
    await renderRoute(API_KEYS_LIST_PATH, {
      permissions: [Permissions.VIEW_API_KEYS, Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [specHttp.untyped.get('/api/v1/api-keys', () => HttpResponse.json([]))],
    });

    await expect.element(page.getByText('Issuer Service')).toBeVisible();
  });

  it('hides the Issuer Service tab without VIEW_SYSTEM_SETTINGS', async () => {
    await renderRoute(API_KEYS_LIST_PATH, {
      permissions: [Permissions.VIEW_API_KEYS],
      msw: [specHttp.untyped.get('/api/v1/api-keys', () => HttpResponse.json([]))],
    });

    await expect.poll(() => page.getByText('Issuer Service').query()).toBeNull();
  });
});

const EDIT_HOST_PERMISSIONS = [Permissions.VIEW_SYSTEM_SETTINGS, Permissions.EDIT_ISSUER_SERVICE_HOST];

describe('CS Issuer Service tab — host edit control (Browser Mode)', () => {
  it('shows the edit control with EDIT_ISSUER_SERVICE_HOST while the issuer DID is not bound', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [statusHandler({})],
    });

    await expect.element(page.getByTestId('issuer-service-host-edit-button')).toBeVisible();
  });

  it('hides the edit control without EDIT_ISSUER_SERVICE_HOST', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [statusHandler({})],
    });

    await expect.element(page.getByTestId('issuer-service-host-row').getByText('Not set')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-host-edit-button').query()).toBeNull();
  });

  it('hides the edit control once the issuer DID is bound', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        statusHandler({
          host: 'issuer.example.org',
          host_set: true,
          bound: true,
          did_to_bind: 'did:web:issuer.example.org',
        }),
      ],
    });

    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('did:web:issuer.example.org')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-host-edit-button').query()).toBeNull();
  });
});

describe('CS Issuer Service tab — host edit dialog (Browser Mode)', () => {
  it('shows the cluster and single-server help text', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [statusHandler({})],
    });

    await page.getByTestId('issuer-service-host-edit-button').click();
    const help = page.getByTestId('issuer-service-host-help');

    await expect.element(help).toMatchTextContent('A cluster needs the cluster DNS name.');
    await expect.element(help).toMatchTextContent("On a single server, use a dedicated DNS name instead of the node's own host name.");

    await page.getByTestId('dialog-cancel-button').click();
    await expect.element(page.getByTestId('issuer-service-host-edit-dialog')).not.toBeInTheDocument();
  });

  it('saves a valid host through the host update and shows the new host once the tab re-reads the status', async () => {
    let host: string | null = null;
    const putSpy = vi.fn();
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        specHttp.get('/issuer-service', ({ response }) =>
          response(200).json({
            host,
            bound: false,
            host_set: host !== null,
            certificate_ready: false,
            run_in_progress: false,
            can_sign: false,
            status: 'NOT_CONFIGURED',
          }),
        ),
        specHttp.put('/issuer-service/host', async ({ request, response }) => {
          const body = await request.json();
          putSpy(body);
          host = body.host;
          return response(200).json({
            host,
            bound: false,
            host_set: true,
            certificate_ready: false,
            run_in_progress: false,
            can_sign: false,
            status: 'WAITING_FOR_CERTIFICATE',
          });
        }),
      ],
    });

    await page.getByTestId('issuer-service-host-edit-button').click();
    await page.getByTestId('issuer-service-host-edit-field').getByRole('textbox').fill('issuer.example.org');
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByTestId('issuer-service-host-edit-dialog')).not.toBeInTheDocument();
    expect(putSpy).toHaveBeenCalledWith({ host: 'issuer.example.org' });
    await expect.element(page.getByTestId('issuer-service-host-row').getByText('issuer.example.org')).toBeVisible();
  });

  it('shows the API validation error inline and keeps the previous host when the host is rejected', async () => {
    const putSpy = vi.fn();
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        statusHandler({ host: 'issuer.example.org', host_set: true }),
        specHttp.untyped.put('/api/v1/issuer-service/host', () => {
          putSpy();
          return HttpResponse.json({ status: 400, error: { code: 'invalid_dns_name' } }, { status: 400 });
        }),
      ],
    });

    await page.getByTestId('issuer-service-host-edit-button').click();
    await page.getByTestId('issuer-service-host-edit-field').getByRole('textbox').fill('localhost');
    submitDialogForm();

    const dialog = page.getByTestId('issuer-service-host-edit-dialog');
    await expect
      .element(dialog.getByTestId('contextual-alert'))
      .toMatchTextContent('The Issuer Service host must be a DNS name, not an IP address or localhost');
    await expect.element(dialog).toBeVisible();
    expect(putSpy).toHaveBeenCalled();
    await expect.element(page.getByTestId('issuer-service-host-row').getByText('issuer.example.org')).toBeVisible();
  });
});
