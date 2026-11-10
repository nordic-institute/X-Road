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
import { computed, ref, shallowRef, watch, type WatchSource } from 'vue';
import type { DialogSaveHandler } from '@niis/shared-ui';

import { type DidCheck, DidCheckStatus, type DidResolutionReason } from '@/openapi-types';
import { DID_REFUSAL_TEXT } from '@/util/did-refusal-text';

export type DidCheckTexts = {
  resolved: string;
  resolverUnavailable: string;
  resolution: Record<DidResolutionReason, string>;
};

export type DidCheckOutcome = { icon: string; color: 'success' | 'warning' | 'error'; text: string };

export function useDidCheck(options: { request: () => Promise<DidCheck>; resetOn: WatchSource[]; texts: DidCheckTexts }) {
  const { request, resetOn, texts } = options;
  const checking = ref(false);
  const answer = shallowRef<DidCheck | undefined>(undefined);
  let latestCheck = 0;

  watch(
    resetOn,
    () => {
      latestCheck += 1;
      answer.value = undefined;
      checking.value = false;
    },
    { flush: 'sync' },
  );

  const outcome = computed<DidCheckOutcome | undefined>(() => {
    const result = answer.value;
    if (!result) {
      return undefined;
    }
    switch (result.status) {
      case DidCheckStatus.RESOLVED:
        return { icon: 'check_circle', color: 'success', text: texts.resolved };
      case DidCheckStatus.NOT_RESOLVED:
        return {
          icon: 'warning',
          color: 'warning',
          text: (result.resolution_reason && texts.resolution[result.resolution_reason]) ?? texts.resolution.FETCH_FAILURE,
        };
      case DidCheckStatus.RESOLVER_UNAVAILABLE:
        return { icon: 'warning', color: 'warning', text: texts.resolverUnavailable };
      default:
        return {
          icon: 'error',
          color: 'error',
          text: (result.refusal_reason && DID_REFUSAL_TEXT[result.refusal_reason]) ?? 'error_code.internal_error',
        };
    }
  });

  function check(handler: DialogSaveHandler) {
    latestCheck += 1;
    const thisCheck = latestCheck;
    const isLatest = () => thisCheck === latestCheck;
    answer.value = undefined;
    checking.value = true;
    request()
      .then((result) => isLatest() && (answer.value = result))
      .catch((error) => isLatest() && handler.addError(error))
      .finally(() => isLatest() && (checking.value = false));
  }

  return { checking, outcome, check };
}
