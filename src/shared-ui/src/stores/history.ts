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

import { defineStore } from 'pinia';
import { RouteLocationNormalizedGeneric, RouteLocationNormalized } from 'vue-router';
import { computed, reactive, ref } from 'vue';

type HistoryItem = { location: RouteLocationNormalizedGeneric };

export const useHistory = defineStore('xrd-history', () => {
  const stack = reactive([] as HistoryItem[]);
  const position = ref(0);

  const previousPage = computed(() => (stack.length > 1 ? stack[stack.length - 2] : undefined));

  function push(location: RouteLocationNormalized) {
    if (window.history.state.replaced) {
      stack.pop();
    }
    if (window.history.state.position && position.value <= window.history.state.position) {
      stack.push({ location });
    }
    position.value = window.history.state.position;
  }

  function cameFrom(routeName: string) {
    if (previousPage.value) {
      return 0 <= previousPage.value.location.matched.findIndex((route) => route.name === routeName);
    }
    return false;
  }

  return { position, stack, push, previousPage, cameFrom };
});
