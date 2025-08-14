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
import {
  useRouter, RouteRecordNameGeneric, RouteLocationNormalizedGeneric, RouteLocationAsRelativeGeneric, RouteLocationNormalized,
} from 'vue-router';
import { computed, reactive, ref } from 'vue';
import { XrdMeta, XrdViewTitle } from '../utils';

type HistoryItem = { location: RouteLocationNormalizedGeneric };

type Breadcrumb = { location?: RouteLocationAsRelativeGeneric; title?: XrdViewTitle };

export const useHistory = defineStore('xrd-history', () => {
  const router = useRouter();
  const stack = reactive([] as HistoryItem[]);
  const position = ref(0);

  const previousPage = computed(() => (stack.length > 1 ? stack[stack.length - 2] : undefined));

  const breadcrumbs = computed(() => {
    const currentRouteMeta = router.currentRoute.value.meta as XrdMeta;
    const breadcrumbs = [{ title: currentRouteMeta.title } as Breadcrumb];
    let view = router.currentRoute.value;
    if (previousPage.value) {
      const previousPageLocation = previousPage.value.location;
      for (const routeName of currentRouteMeta.allowBreadcrumbsTo || []) {
        if (previousPageLocation.name === routeName) {
          breadcrumbs.push({
            location: {
              name: previousPageLocation.name,
              query: previousPageLocation.query,
              params: previousPageLocation.params,
            },
            title: previousPageLocation.meta.title as XrdViewTitle,
          });
          view = previousPageLocation;
        }
      }
    }

    if (view.meta.listView) {
      const listViewName = view.meta.listView as RouteRecordNameGeneric;
      const listRoute = router.resolve({ name: listViewName });
      if (listRoute) {
        breadcrumbs.push({
          location: {
            name: listViewName,
          },
          title: listRoute.meta.title as XrdViewTitle,
        });
      }
    }

    return breadcrumbs.reverse();
  });

  function push(location: RouteLocationNormalized) {
    if (window.history.state.replaced) {
      stack.pop();
    }
    if (window.history.state.position && position.value <= window.history.state.position) {
      stack.push({ location });
    }
    position.value = window.history.state.position;
  }

  return { position, stack, push, previousPage, breadcrumbs };
});
