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
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { AgreementTokenKeyStatus } from '@/openapi-types';

const AGREEMENT_TOKEN_KEY_PATH = '/keys/agreement-token-key';

const viewOnly = [Permissions.VIEW_KEYS, Permissions.VIEW_AGREEMENT_TOKEN_KEY];
const viewAndRotate = [...viewOnly, Permissions.ROTATE_AGREEMENT_TOKEN_KEY];

const activeStatus: AgreementTokenKeyStatus = {
  available: true,
  active_key_id: '3',
  active_key_created_at: '2026-01-01T10:20:30Z',
  retained_key_count: 3,
};

const statusHandler = (body: AgreementTokenKeyStatus) =>
  specHttp.get('/dataspace/agreement-token-key', ({ response }) => response(200).json(body));

describe('SS Agreement token key tab — status card (Browser Mode)', () => {
  it('shows the signing key id, its creation time and the number of previous keys', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, { permissions: viewOnly, msw: [statusHandler(activeStatus)] });

    await expect.element(page.getByTestId('agreement-token-key-id')).toHaveTextContent('3');
    await expect.poll(() => page.getByTestId('agreement-token-key-created-at').element().textContent).toMatch(/^2026-01-01 \d{2}:20:30$/);
    await expect.element(page.getByTestId('agreement-token-key-previous-keys')).toHaveTextContent('2');
    await expect.poll(() => page.getByTestId('agreement-token-key-pending').query()).toBeNull();
  });

  it('shows the creation time as unknown when the key has none', async () => {
    const { active_key_created_at: _omitted, ...withoutCreatedAt } = activeStatus;

    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, { permissions: viewOnly, msw: [statusHandler(withoutCreatedAt)] });

    await expect.element(page.getByTestId('agreement-token-key-created-at')).toHaveTextContent('Unknown');
  });

  it('shows a note and excludes the pending key from the previous keys when a pending key waits to start signing', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewOnly,
      msw: [statusHandler({ ...activeStatus, retained_key_count: 4, pending_key_id: '4' })],
    });

    await expect.poll(() => page.getByTestId('agreement-token-key-pending').element().textContent).toMatch(/waiting to start signing/);
    await expect.element(page.getByTestId('agreement-token-key-previous-keys')).toHaveTextContent('2');
  });

  it('shows the tab in the keys and certificates navigation', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, { permissions: viewOnly, msw: [statusHandler(activeStatus)] });

    await expect.element(page.getByTestId('agreement-token-key-tab-button')).toBeVisible();
  });
});

describe('SS Agreement token key tab — rotate permission (Browser Mode)', () => {
  it('hides the rotate button without the rotate permission', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, { permissions: viewOnly, msw: [statusHandler(activeStatus)] });

    await expect.element(page.getByTestId('agreement-token-key-id')).toBeVisible();
    await expect.poll(() => page.getByTestId('rotate-agreement-token-key-button').query()).toBeNull();
  });

  it('shows the rotate button with the rotate permission', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, { permissions: viewAndRotate, msw: [statusHandler(activeStatus)] });

    await expect.element(page.getByTestId('rotate-agreement-token-key-button')).toBeVisible();
  });
});

describe('SS Agreement token key tab — rotate (Browser Mode)', () => {
  it('asks for confirmation, rotates on confirm and refreshes the card', async () => {
    const rotateSpy = vi.fn();
    let rotated = false;

    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewAndRotate,
      msw: [
        specHttp.get('/dataspace/agreement-token-key', ({ response }) =>
          response(200).json(rotated ? { ...activeStatus, retained_key_count: 4, pending_key_id: '4' } : activeStatus),
        ),
        specHttp.untyped.post('/api/v1/dataspace/agreement-token-key/rotate', () => {
          rotateSpy();
          rotated = true;
          return HttpResponse.json({ key_id: '4' });
        }),
      ],
    });

    await expect.element(page.getByTestId('agreement-token-key-id')).toBeVisible();
    await page.getByTestId('rotate-agreement-token-key-button').click();

    await expect.element(page.getByTestId('dialog-title')).toHaveTextContent('Rotate agreement token signing key?');
    await expect.poll(() => page.getByTestId('dialog-simple').element().textContent).toMatch(/Tokens already issued keep working until they expire/);
    await expect.poll(() => page.getByTestId('dialog-simple').element().textContent).toMatch(/within two key refresh intervals/);
    expect(rotateSpy).not.toHaveBeenCalled();

    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => rotateSpy.mock.calls.length).toBe(1);
    await expect.element(page.getByTestId('agreement-token-key-pending')).toBeVisible();
    await expect.element(page.getByTestId('agreement-token-key-previous-keys')).toHaveTextContent('2');
  });

  it('does not rotate when the confirmation is cancelled', async () => {
    const rotateSpy = vi.fn();

    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewAndRotate,
      msw: [
        statusHandler(activeStatus),
        specHttp.untyped.post('/api/v1/dataspace/agreement-token-key/rotate', () => {
          rotateSpy();
          return HttpResponse.json({ key_id: '4' });
        }),
      ],
    });

    await expect.element(page.getByTestId('agreement-token-key-id')).toBeVisible();
    await page.getByTestId('rotate-agreement-token-key-button').click();
    await page.getByTestId('dialog-cancel-button').click();

    await expect.poll(() => page.getByTestId('dialog-title').query()).toBeNull();
    expect(rotateSpy).not.toHaveBeenCalled();
  });

  it('disables the rotate button while the request is in flight', async () => {
    let finishRotate: () => void = () => {};
    const rotateInFlight = new Promise<void>((resolve) => (finishRotate = resolve));

    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewAndRotate,
      msw: [
        statusHandler(activeStatus),
        specHttp.untyped.post('/api/v1/dataspace/agreement-token-key/rotate', async () => {
          await rotateInFlight;
          return HttpResponse.json({ key_id: '4' });
        }),
      ],
    });

    await expect.element(page.getByTestId('rotate-agreement-token-key-button')).not.toBeDisabled();
    await page.getByTestId('rotate-agreement-token-key-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('rotate-agreement-token-key-button')).toBeDisabled();
    finishRotate();
    await expect.element(page.getByTestId('rotate-agreement-token-key-button')).not.toBeDisabled();
  });

  it('disables the rotate button and shows a message when the key material is unavailable', async () => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewAndRotate,
      msw: [statusHandler({ available: false, retained_key_count: 0 })],
    });

    await expect.element(page.getByTestId('agreement-token-key-unavailable')).toBeVisible();
    await expect.element(page.getByTestId('rotate-agreement-token-key-button')).toBeDisabled();
    await expect.poll(() => page.getByTestId('agreement-token-key-id').query()).toBeNull();
  });

  it.each([
    ['core.agreement_token_key_not_available', 'The agreement token signing key is not available'],
    ['core.agreement_token_key_store_failed', 'could not be saved to the secret store'],
    ['core.agreement_token_key_generation_failed', 'Generating the agreement token signing key failed'],
  ])('shows a readable message for the %s error', async (code, text) => {
    await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewAndRotate,
      msw: [
        statusHandler(activeStatus),
        specHttp.untyped.post('/api/v1/dataspace/agreement-token-key/rotate', () =>
          HttpResponse.json({ status: 503, error: { code } }, { status: 503 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('agreement-token-key-id')).toBeVisible();
    await page.getByTestId('rotate-agreement-token-key-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByText(text, { exact: false })).toBeVisible();
  });
});

describe('SS Agreement token key tab — pending key polling (Browser Mode)', () => {
  const pendingStatus: AgreementTokenKeyStatus = { ...activeStatus, retained_key_count: 4, pending_key_id: '4' };
  const switchedStatus: AgreementTokenKeyStatus = { ...activeStatus, active_key_id: '4', retained_key_count: 4 };

  afterEach(() => {
    vi.useRealTimers();
  });

  type ServerState = { status: AgreementTokenKeyStatus | 'error'; gate: Promise<void> | undefined; reads: number };

  async function renderWithServer(initial: AgreementTokenKeyStatus) {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const server: ServerState = { status: initial, gate: undefined, reads: 0 };
    const rendered = await renderRoute(AGREEMENT_TOKEN_KEY_PATH, {
      permissions: viewOnly,
      msw: [
        specHttp.untyped.get('/api/v1/dataspace/agreement-token-key', async () => {
          server.reads++;
          await server.gate;
          return server.status === 'error' ? HttpResponse.json({ status: 500 }, { status: 500 }) : HttpResponse.json(server.status);
        }),
      ],
    });
    await expect.element(page.getByTestId('agreement-token-key-id')).toBeVisible();
    return { ...rendered, server };
  }

  function holdReads(server: ServerState): () => void {
    let release: () => void = () => {};
    server.gate = new Promise<void>((resolve) => (release = resolve));
    return () => {
      server.gate = undefined;
      release();
    };
  }

  it('re-reads the status after 10 seconds while a key is pending and switches the card once it is gone', async () => {
    const { server } = await renderWithServer(pendingStatus);
    await expect.element(page.getByTestId('agreement-token-key-pending')).toBeVisible();
    const readsAfterLoad = server.reads;

    await vi.advanceTimersByTimeAsync(9_999);
    expect(server.reads).toBe(readsAfterLoad);

    server.status = switchedStatus;
    await vi.advanceTimersByTimeAsync(1);

    await expect.poll(() => server.reads).toBe(readsAfterLoad + 1);
    await expect.element(page.getByTestId('agreement-token-key-id')).toHaveTextContent('4');
    await expect.poll(() => page.getByTestId('agreement-token-key-pending').query()).toBeNull();
  });

  it('keeps polling while the key stays pending', async () => {
    const { server } = await renderWithServer(pendingStatus);
    const readsAfterLoad = server.reads;

    await vi.advanceTimersByTimeAsync(10_000);
    await expect.poll(() => server.reads).toBe(readsAfterLoad + 1);
    await vi.advanceTimersByTimeAsync(10_000);

    await expect.poll(() => server.reads).toBe(readsAfterLoad + 2);
  });

  it('starts no read while a previous read is still in flight', async () => {
    const { server } = await renderWithServer(pendingStatus);
    const readsAfterLoad = server.reads;
    const release = holdReads(server);

    await vi.advanceTimersByTimeAsync(10_000);
    await expect.poll(() => server.reads).toBe(readsAfterLoad + 1);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(server.reads).toBe(readsAfterLoad + 1);

    release();
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(server.reads).toBe(readsAfterLoad + 1);
    await vi.advanceTimersByTimeAsync(10_000);

    await expect.poll(() => server.reads).toBe(readsAfterLoad + 2);
  });

  it('stops polling once no key is pending', async () => {
    const { server } = await renderWithServer(pendingStatus);
    server.status = switchedStatus;
    await vi.advanceTimersByTimeAsync(10_000);
    await expect.poll(() => page.getByTestId('agreement-token-key-pending').query()).toBeNull();
    const readsAfterSwitch = server.reads;

    await vi.advanceTimersByTimeAsync(60_000);

    expect(server.reads).toBe(readsAfterSwitch);
  });

  it('does not poll while no key is pending', async () => {
    const { server } = await renderWithServer(activeStatus);
    const readsAfterLoad = server.reads;

    await vi.advanceTimersByTimeAsync(60_000);

    expect(server.reads).toBe(readsAfterLoad);
  });

  it('stops polling when a status read fails', async () => {
    const { server } = await renderWithServer(pendingStatus);
    const readsAfterLoad = server.reads;
    server.status = 'error';

    await vi.advanceTimersByTimeAsync(10_000);
    await expect.poll(() => server.reads).toBe(readsAfterLoad + 1);
    await vi.advanceTimersByTimeAsync(60_000);

    expect(server.reads).toBe(readsAfterLoad + 1);
  });

  it('stops polling when the view is unmounted', async () => {
    const { router, server } = await renderWithServer(pendingStatus);
    await router.push('/forbidden');
    await expect.poll(() => page.getByTestId('agreement-token-key-view').query()).toBeNull();
    const readsAfterUnmount = server.reads;

    await vi.advanceTimersByTimeAsync(60_000);

    expect(server.reads).toBe(readsAfterUnmount);
  });

  it('does not restart polling when a read finishes after the view is unmounted', async () => {
    const { router, server } = await renderWithServer(pendingStatus);
    const release = holdReads(server);
    await vi.advanceTimersByTimeAsync(10_000);
    await expect.poll(() => server.reads).toBeGreaterThan(1);
    await router.push('/forbidden');
    await expect.poll(() => page.getByTestId('agreement-token-key-view').query()).toBeNull();
    const readsAtUnmount = server.reads;

    release();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(server.reads).toBe(readsAtUnmount);
  });
});
