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
import deepmerge from 'deepmerge';
import { i18n as sharedI18n } from '@niis/shared-ui/src/plugins/i18n';

async function loadMessages(): Promise<Record<string, unknown>> {
  const [sharedUiEn, ssEn, veeValidateEn] = await Promise.all([
    import('@niis/shared-ui/src/locales/en.json'),
    import('@/locales/en.json'),
    import('@vee-validate/i18n/dist/locale/en.json'),
  ]);
  return deepmerge.all([
    sharedUiEn.default as Record<string, unknown>,
    ssEn.default as Record<string, unknown>,
    { validation: veeValidateEn.default },
  ]) as Record<string, unknown>;
}

let messagesPromise: Promise<void> | null = null;

export function ensureMessages(): Promise<void> {
  if (messagesPromise) return messagesPromise;
  messagesPromise = loadMessages().then((merged) => {
    sharedI18n.global.setLocaleMessage('en', merged);
  });
  return messagesPromise;
}
