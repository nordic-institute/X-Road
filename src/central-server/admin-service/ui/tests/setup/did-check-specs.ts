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
import { describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';
import type { Locator } from 'vitest/browser';
import { HttpResponse } from 'msw';
import type { RequestHandler } from 'msw';
import { specHttp } from './spec-http';
import type { DidCheck, DidRefusalReason, DidResolutionReason } from '@/openapi-types';

export type DidCheckCase = [name: string, answer: DidCheck, text: string, icon: string, color: string];

export type DidCheckSpecSubject = {
  /** Wording of the checked value in the outcome texts, for example `member DID`. */
  subject: string;
  /** Untyped MSW path of the check endpoint. */
  checkPath: string;
  buttonTestId: string;
  resultTestId: string;
  openDialog: (extraHandlers: RequestHandler[]) => Promise<Locator>;
  /** Edits the dialog so that the check request equals `expectedRequest`. */
  fillForCheck: (dialog: Locator) => Promise<void>;
  expectedRequest: object;
  /** An edit that makes an answer still in flight obsolete, without being a new check. */
  editWhilePending: (dialog: Locator) => Promise<void>;
  /** An edit to make between two checks. */
  editBeforeNewCheck: (dialog: Locator) => Promise<void>;
  /** Refusals only this dialog can show. */
  refusalCases: DidCheckCase[];
};

const SUCCESS: [string, string] = ['check_circle', 'success'];
const WARNING: [string, string] = ['warning', 'warning'];
const ERROR: [string, string] = ['error', 'error'];

export const refusalCase = (reason: DidRefusalReason, text: string): DidCheckCase => [
  `REFUSED ${reason}`,
  { status: 'REFUSED', refusal_reason: reason },
  text,
  ...ERROR,
];

function sharedCases(subject: string): DidCheckCase[] {
  const notResolved = (reason: DidResolutionReason, why: string): DidCheckCase => [
    `NOT_RESOLVED ${reason}`,
    { status: 'NOT_RESOLVED', resolution_reason: reason },
    `The ${subject} does not resolve: ${why}. You can still save.`,
    ...WARNING,
  ];
  const fetchFailure = `The ${subject} does not resolve: its DID document could not be fetched. You can still save.`;
  return [
    ['RESOLVED', { status: 'RESOLVED' }, `The ${subject} resolves.`, ...SUCCESS],
    notResolved('MALFORMED_DID', 'its DID document is malformed'),
    notResolved('NO_DOCUMENT', 'no DID document was found'),
    notResolved('ID_MISMATCH', 'the DID document belongs to another DID'),
    notResolved('FETCH_FAILURE', 'its DID document could not be fetched'),
    ['NOT_RESOLVED without a reason', { status: 'NOT_RESOLVED' }, fetchFailure, ...WARNING],
    [
      'RESOLVER_UNAVAILABLE',
      { status: 'RESOLVER_UNAVAILABLE' },
      `The ${subject} could not be checked because the Issuer Service is unreachable. You can still save.`,
      ...WARNING,
    ],
    ['REFUSED without a reason', { status: 'REFUSED' }, 'Internal error. See server logs for more details', ...ERROR],
    refusalCase('WRONG_SCHEME', 'The DID must be a did:web DID of version v1'),
    refusalCase('DID_TOO_LONG', 'The DID is too long'),
    refusalCase('NOT_CANONICAL', 'The DID is not in its canonical form'),
    refusalCase('HOST_DIFFERS', 'The host of the DID must match the security server address'),
    refusalCase('UNPARSEABLE_REGISTERED_ADDRESS', 'The security server address cannot be read as a host'),
    refusalCase('NON_ASCII_HOST', 'The host of the DID and the security server address must be ASCII'),
    refusalCase('UNDECODABLE_DID', 'The DID cannot be decoded'),
    refusalCase('DID_HELD_BY_ANOTHER_SERVER', 'The DID is already used by another security server'),
    refusalCase('NO_STORED_VALUE', 'The security server has no stored DID to replace'),
  ];
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 200));

/** Registers the check outcome and late-answer specs inside the caller's `describe`. */
export function registerDidCheckSpecs(config: DidCheckSpecSubject) {
  const { subject, checkPath, buttonTestId, resultTestId, openDialog } = config;

  it.each([...sharedCases(subject), ...config.refusalCases])('shows the answer %s', async (_name, answer, text, iconName, iconColor) => {
    const checkSpy = vi.fn();
    const dialog = await openDialog([
      specHttp.untyped.post(checkPath, async ({ request }) => {
        checkSpy(await request.json());
        return HttpResponse.json(answer);
      }),
    ]);

    await config.fillForCheck(dialog);
    await dialog.getByTestId(buttonTestId).click();

    const result = dialog.getByTestId(resultTestId);
    await expect.element(result).toMatchTextContent(text);
    const icon = result.element().querySelector('.v-icon');
    expect(icon?.textContent?.trim()).toBe(iconName);
    expect(icon?.classList).toContain(`text-${iconColor}`);
    expect(checkSpy).toHaveBeenCalledWith(config.expectedRequest);
  });

  describe('late answers', () => {
    const noDocumentText = `The ${subject} does not resolve: no DID document was found. You can still save.`;

    function gatedFirstCheck(firstStatus = 200, firstBody: object = { status: 'RESOLVED' }) {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => (release = resolve));
      let calls = 0;
      const firstServed = vi.fn();
      const handler = specHttp.untyped.post(checkPath, async () => {
        calls += 1;
        if (calls > 1) {
          return HttpResponse.json({ status: 'NOT_RESOLVED', resolution_reason: 'NO_DOCUMENT' });
        }
        await gate;
        firstServed();
        return HttpResponse.json(firstBody, { status: firstStatus });
      });
      return { handler, release, firstServed };
    }

    it('drops an answer that arrives after a field edit', async () => {
      const { handler, release, firstServed } = gatedFirstCheck();
      const dialog = await openDialog([handler]);
      const result = dialog.getByTestId(resultTestId);

      await dialog.getByTestId(buttonTestId).click();
      await config.editWhilePending(dialog);
      release();
      await vi.waitFor(() => expect(firstServed).toHaveBeenCalled());
      await settle();

      await expect.element(result).not.toBeInTheDocument();
    });

    it('drops an answer that arrives after a newer check and shows the newer answer', async () => {
      const { handler, release, firstServed } = gatedFirstCheck();
      const dialog = await openDialog([handler]);
      const result = dialog.getByTestId(resultTestId);

      await dialog.getByTestId(buttonTestId).click();
      await config.editBeforeNewCheck(dialog);
      await dialog.getByTestId(buttonTestId).click();
      await expect.element(result).toMatchTextContent(noDocumentText);
      release();
      await vi.waitFor(() => expect(firstServed).toHaveBeenCalled());
      await settle();

      await expect.element(result).toMatchTextContent(noDocumentText);
    });

    it('shows no error for a failed call that arrives after a field edit', async () => {
      const { handler, release, firstServed } = gatedFirstCheck(500, { status: 500, error: { code: 'internal_error' } });
      const dialog = await openDialog([handler]);

      await dialog.getByTestId(buttonTestId).click();
      await config.editWhilePending(dialog);
      release();
      await vi.waitFor(() => expect(firstServed).toHaveBeenCalled());
      await settle();

      await expect.element(page.getByTestId('contextual-alert')).not.toBeInTheDocument();
    });
  });
}
