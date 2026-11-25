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
import { afterEach, describe, it, expect, vi } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse, type RequestHandler } from 'msw';
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

    await expect.element(page.getByTestId('issuer-service-can-sign-row').getByText('No active issuer key is configured')).toBeVisible();
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

const INITIALIZE_PERMISSIONS = [Permissions.VIEW_SYSTEM_SETTINGS, Permissions.INITIALIZE_ISSUER_SERVICE];

const READY_TO_INITIALIZE = {
  host: 'issuer.example.org',
  host_set: true,
  certificate_ready: true,
  bound: false,
  run_in_progress: false,
  can_sign: false,
  did_to_bind: 'did:web:issuer.example.org',
  status: 'READY_TO_INITIALIZE',
};

describe('CS Issuer Service tab — Initialize button visibility (Browser Mode)', () => {
  it('shows the Initialize button with INITIALIZE_ISSUER_SERVICE', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler(READY_TO_INITIALIZE)],
    });

    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeVisible();
  });

  it('hides the Initialize button without INITIALIZE_ISSUER_SERVICE', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [statusHandler(READY_TO_INITIALIZE)],
    });

    await expect.element(page.getByTestId('issuer-service-host-row')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-initialize-button').query()).toBeNull();
  });
});

describe('CS Issuer Service tab — Initialize button enabled state (Browser Mode)', () => {
  it('is disabled while the host is not set', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler({ ...READY_TO_INITIALIZE, host: null, host_set: false, did_to_bind: undefined })],
    });

    await expect.element(page.getByTestId('issuer-service-host-row').getByText('Not set')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });

  it('is disabled while the certificate is not ready', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler({ ...READY_TO_INITIALIZE, certificate_ready: false })],
    });

    await expect.element(page.getByTestId('issuer-service-certificate-row').getByText('Not ready')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });

  it('is disabled while a run is in progress', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler({ ...READY_TO_INITIALIZE, run_in_progress: true, status: 'INITIALIZING' })],
    });

    await expect.element(page.getByTestId('issuer-service-run-in-progress')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });

  it('is enabled when nothing is bound, host and certificate are ready and no run is in progress', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler(READY_TO_INITIALIZE)],
    });

    await expect.element(page.getByTestId('issuer-service-initialize-button')).not.toBeDisabled();
  });

  it('is greyed out once the DID is bound, with a healthy Issuer Service', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler({ ...READY_TO_INITIALIZE, bound: true, can_sign: true, status: 'READY' })],
    });

    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('did:web:issuer.example.org')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });

  it('is greyed out once the DID is bound, with an unhealthy Issuer Service', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [statusHandler({ ...READY_TO_INITIALIZE, bound: true, can_sign: false, can_sign_cause: 'NO_ACTIVE_KEY', status: 'FAILED' })],
    });

    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('did:web:issuer.example.org')).toBeVisible();
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });

  it('shows the bound DID once bound, in place of the DID that would bind', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        statusHandler({
          ...READY_TO_INITIALIZE,
          bound: true,
          can_sign: true,
          status: 'READY',
          bound_did: 'did:web:other-issuer.example.org',
        }),
      ],
    });

    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('did:web:other-issuer.example.org')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-identity-row').getByText('did:web:issuer.example.org').query()).toBeNull();
  });
});

describe('CS Issuer Service tab — Initialize confirmation (Browser Mode)', () => {
  it('shows the DID to bind and the cannot-change warning, and cancelling makes no call', async () => {
    const initializeSpy = vi.fn();
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        statusHandler(READY_TO_INITIALIZE),
        specHttp.untyped.post('/api/v1/issuer-service/initialize', () => {
          initializeSpy();
          return new HttpResponse(null, { status: 202 });
        }),
      ],
    });

    await page.getByTestId('issuer-service-initialize-button').click();

    await expect.element(page.getByTestId('dialog-title')).toHaveTextContent('Initialize the Issuer Service');
    await expect.poll(() => page.getByTestId('dialog-simple').element().textContent).toMatch(/did:web:issuer\.example\.org/);
    await expect.poll(() => page.getByTestId('dialog-simple').element().textContent).toMatch(/cannot change afterwards/);

    await page.getByTestId('dialog-cancel-button').click();

    await expect.poll(() => page.getByTestId('dialog-title').query()).toBeNull();
    expect(initializeSpy).not.toHaveBeenCalled();
  });

  it('calls initialize when confirmed', async () => {
    const initializeSpy = vi.fn();
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        statusHandler(READY_TO_INITIALIZE),
        specHttp.post('/issuer-service/initialize', ({ response }) => {
          initializeSpy();
          return response(202).empty();
        }),
      ],
    });

    await page.getByTestId('issuer-service-initialize-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => initializeSpy.mock.calls.length).toBe(1);
  });

  it('shows the API error on an initialize refusal', async () => {
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        statusHandler(READY_TO_INITIALIZE),
        specHttp.untyped.post('/api/v1/issuer-service/initialize', () =>
          HttpResponse.json({ status: 409, error: { code: 'issuer_service_already_running' } }, { status: 409 }),
        ),
      ],
    });

    await page.getByTestId('issuer-service-initialize-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('contextual-alert')).toMatchTextContent('Initialization is already running');
  });
});

describe('CS Issuer Service tab — run polling (Browser Mode)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  type RunPhase = 'ready' | 'running' | 'done';

  function bodyForPhase(phase: RunPhase) {
    if (phase === 'ready') {
      return READY_TO_INITIALIZE;
    }
    if (phase === 'running') {
      return { ...READY_TO_INITIALIZE, run_in_progress: true, status: 'INITIALIZING' };
    }
    return { ...READY_TO_INITIALIZE, bound: true, can_sign: true, run_in_progress: false, status: 'READY' };
  }

  async function renderAndStartRun(extraHandlers: RequestHandler[] = []) {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let reads = 0;
    let phase: RunPhase = 'ready';

    const rendered = await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () => {
          reads++;
          return HttpResponse.json(bodyForPhase(phase));
        }),
        specHttp.untyped.post('/api/v1/issuer-service/initialize', () => {
          phase = 'running';
          return new HttpResponse(null, { status: 202 });
        }),
        ...extraHandlers,
      ],
    });

    await expect.element(page.getByTestId('issuer-service-initialize-button')).not.toBeDisabled();
    await page.getByTestId('issuer-service-initialize-button').click();
    await page.getByTestId('dialog-save-button').click();
    await expect.element(page.getByTestId('issuer-service-run-in-progress')).toBeVisible();

    return {
      ...rendered,
      reads: () => reads,
      end() {
        phase = 'done';
      },
    };
  }

  async function advancePollingUntilRunEnds() {
    await expect
      .poll(async () => {
        await vi.advanceTimersByTimeAsync(5_000);
        return page.getByTestId('issuer-service-run-in-progress').query();
      })
      .toBeNull();
  }

  it('polls while a run is in progress and stops once the run ends', async () => {
    const { reads, end } = await renderAndStartRun();
    const readsOnceRunning = reads();

    await vi.advanceTimersByTimeAsync(5_000);
    await expect.poll(() => reads()).toBeGreaterThan(readsOnceRunning);

    end();
    await advancePollingUntilRunEnds();
    const readsAfterEnd = reads();

    await vi.advanceTimersByTimeAsync(60_000);
    expect(reads()).toBe(readsAfterEnd);
  });

  it('stops polling when the user leaves the tab', async () => {
    const { router, reads } = await renderAndStartRun();
    const readsOnceRunning = reads();
    await vi.advanceTimersByTimeAsync(5_000);
    await expect.poll(() => reads()).toBeGreaterThan(readsOnceRunning);

    await router.push('/forbidden');
    await expect.poll(() => page.getByTestId('issuer-service-view').query()).toBeNull();
    const readsAtUnmount = reads();

    await vi.advanceTimersByTimeAsync(60_000);
    expect(reads()).toBe(readsAtUnmount);
  });

  it('checks alerts again once the run ends', async () => {
    let alertReads = 0;
    const { end } = await renderAndStartRun([
      specHttp.untyped.get('/api/v1/notifications/alerts', () => {
        alertReads++;
        return HttpResponse.json([]);
      }),
    ]);
    await expect.poll(() => alertReads).toBeGreaterThanOrEqual(1);
    const alertReadsWhileRunning = alertReads;

    end();
    await advancePollingUntilRunEnds();

    await expect.poll(() => alertReads).toBeGreaterThan(alertReadsWhileRunning);
  });
});

describe('CS Issuer Service tab — host save restarts polling (Browser Mode)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  type HostSavePhase = 'notSet' | 'running' | 'done';

  function bodyForHostSavePhase(phase: HostSavePhase) {
    if (phase === 'notSet') {
      return { host: null, bound: false, host_set: false, certificate_ready: false, run_in_progress: false, can_sign: false, status: 'NOT_CONFIGURED' };
    }
    if (phase === 'running') {
      return {
        host: 'issuer.example.org',
        bound: false,
        host_set: true,
        certificate_ready: false,
        run_in_progress: true,
        can_sign: false,
        status: 'INITIALIZING',
      };
    }
    return {
      host: 'issuer.example.org',
      bound: false,
      host_set: true,
      certificate_ready: true,
      run_in_progress: false,
      can_sign: false,
      status: 'WAITING_FOR_CERTIFICATE',
    };
  }

  async function saveHost() {
    await page.getByTestId('issuer-service-host-edit-button').click();
    await page.getByTestId('issuer-service-host-edit-field').getByRole('textbox').fill('issuer.example.org');
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();
    await expect.element(page.getByTestId('issuer-service-host-edit-dialog')).not.toBeInTheDocument();
  }

  it('resumes polling after a host save reports a run already in progress, and clears the banner once the run ends', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let phase: HostSavePhase = 'notSet';

    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () => HttpResponse.json(bodyForHostSavePhase(phase))),
        specHttp.untyped.put('/api/v1/issuer-service/host', () => {
          phase = 'running';
          return HttpResponse.json(bodyForHostSavePhase(phase));
        }),
      ],
    });

    await saveHost();
    await expect.element(page.getByTestId('issuer-service-run-in-progress')).toBeVisible();

    phase = 'done';
    await vi.advanceTimersByTimeAsync(5_000);

    await expect.poll(() => page.getByTestId('issuer-service-run-in-progress').query()).toBeNull();
  });

  it('starts no polling after a host save reports no run in progress', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let reads = 0;

    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () => {
          reads++;
          return HttpResponse.json(bodyForHostSavePhase('notSet'));
        }),
        specHttp.untyped.put('/api/v1/issuer-service/host', () => HttpResponse.json(bodyForHostSavePhase('done'))),
      ],
    });

    await saveHost();
    const readsAfterSave = reads;

    await vi.advanceTimersByTimeAsync(10_000);

    expect(reads).toBe(readsAfterSave);
  });
});

describe('CS Issuer Service tab — initialize refusal re-reads status (Browser Mode)', () => {
  it('re-reads the status after a refused initialize', async () => {
    let bound = false;
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: INITIALIZE_PERMISSIONS,
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () =>
          HttpResponse.json(bound ? { ...READY_TO_INITIALIZE, bound: true, can_sign: true, status: 'READY' } : READY_TO_INITIALIZE),
        ),
        specHttp.untyped.post('/api/v1/issuer-service/initialize', () => {
          bound = true;
          return HttpResponse.json({ status: 409, error: { code: 'issuer_service_already_running' } }, { status: 409 });
        }),
      ],
    });

    await page.getByTestId('issuer-service-initialize-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('contextual-alert')).toMatchTextContent('Initialization is already running');
    await expect.element(page.getByTestId('issuer-service-initialize-button')).toBeDisabled();
  });
});

describe('CS Issuer Service tab — host save refusal (Browser Mode)', () => {
  it('closes the dialog and re-reads the status when the host save is refused', async () => {
    let bound = false;

    function currentStatus() {
      return bound
        ? {
            host: 'issuer.example.org',
            bound: true,
            host_set: true,
            certificate_ready: false,
            run_in_progress: false,
            can_sign: false,
            status: 'WAITING_FOR_CERTIFICATE',
            did_to_bind: 'did:web:issuer.example.org',
          }
        : { host: null, bound: false, host_set: false, certificate_ready: false, run_in_progress: false, can_sign: false, status: 'NOT_CONFIGURED' };
    }

    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () => HttpResponse.json(currentStatus())),
        specHttp.untyped.put('/api/v1/issuer-service/host', () => {
          bound = true;
          return HttpResponse.json({ status: 409, error: { code: 'issuer_service_already_bound' } }, { status: 409 });
        }),
      ],
    });

    await page.getByTestId('issuer-service-host-edit-button').click();
    await page.getByTestId('issuer-service-host-edit-field').getByRole('textbox').fill('issuer.example.org');
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByTestId('issuer-service-host-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('issuer-service-identity-row').getByText('did:web:issuer.example.org')).toBeVisible();
  });

  it('checks alerts again once a host save succeeds', async () => {
    let alertReads = 0;
    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: EDIT_HOST_PERMISSIONS,
      msw: [
        statusHandler({}),
        specHttp.put('/issuer-service/host', ({ response }) =>
          response(200).json({
            host: 'issuer.example.org',
            bound: false,
            host_set: true,
            certificate_ready: false,
            run_in_progress: false,
            can_sign: false,
            status: 'WAITING_FOR_CERTIFICATE',
          }),
        ),
        specHttp.untyped.get('/api/v1/notifications/alerts', () => {
          alertReads++;
          return HttpResponse.json([]);
        }),
      ],
    });

    await expect.element(page.getByTestId('issuer-service-host-edit-button')).toBeVisible();
    await expect.poll(() => alertReads).toBeGreaterThanOrEqual(1);
    const alertReadsBeforeSave = alertReads;

    await page.getByTestId('issuer-service-host-edit-button').click();
    await page.getByTestId('issuer-service-host-edit-field').getByRole('textbox').fill('issuer.example.org');
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByTestId('issuer-service-host-edit-dialog')).not.toBeInTheDocument();
    await expect.poll(() => alertReads).toBeGreaterThan(alertReadsBeforeSave);
  });
});

describe('CS Issuer Service tab — failed first read (Browser Mode)', () => {
  it('shows a retry action after a failed first read and renders the table once the retry succeeds', async () => {
    let shouldFail = true;

    await renderRoute(ISSUER_SERVICE_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [
        specHttp.untyped.get('/api/v1/issuer-service', () => {
          if (shouldFail) {
            return HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 });
          }
          return HttpResponse.json({
            host: null,
            bound: false,
            host_set: false,
            certificate_ready: false,
            run_in_progress: false,
            can_sign: false,
            status: 'NOT_CONFIGURED',
          });
        }),
      ],
    });

    await expect.element(page.getByTestId('issuer-service-retry-button')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-host-row').query()).toBeNull();

    shouldFail = false;
    await page.getByTestId('issuer-service-retry-button').click();

    await expect.element(page.getByTestId('issuer-service-host-row').getByText('Not set')).toBeVisible();
    await expect.poll(() => page.getByTestId('issuer-service-retry-button').query()).toBeNull();
  });
});
