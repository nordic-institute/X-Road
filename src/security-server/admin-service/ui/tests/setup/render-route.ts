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
import { defineComponent, h, type Plugin } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import { createRouter, createWebHashHistory, RouterView, type Router } from 'vue-router';
import { render } from 'vitest-browser-vue';
import { createValidators } from '@niis/shared-ui/src/plugins/vee-validate';
import { i18n as sharedI18n } from '@niis/shared-ui/src/plugins/i18n';
import { useAppState, XrdApp, Color, createXrdVuetify } from '@niis/shared-ui';
import axios from 'axios';
import type { RequestHandler } from 'msw';
import deepmerge from 'deepmerge';
import { worker } from './browser-setup';

import routes from '@/router/routes';
import { Permissions } from '@/global';
import { useUser } from '@/store/modules/user';
import { TokenInitStatus } from '@/openapi-types';

export interface RenderRouteOptions {
  msw?: RequestHandler[];
  permissions?: string[];
}

const DEFAULT_PERMISSIONS: string[] = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_SERVICES,
  Permissions.VIEW_CLIENT_ACL_SUBJECTS,
  Permissions.VIEW_CLIENT_INTERNAL_CERTS,
  Permissions.VIEW_CLIENT_LOCAL_GROUPS,
  Permissions.ADD_OPENAPI3,
  Permissions.ENABLE_DISABLE_WSDL,
];

const VEE_VALIDATE_MESSAGES_EN: Record<string, string> = {
  required: 'The {field} field is required',
  email: 'The {field} field must be a valid email',
  max: 'The {field} field may not be greater than 0:{length} characters',
  min: 'The {field} field must be at least 0:{length} characters',
  between: 'The {field} field must be between 0:{min} and 1:{max}',
  confirmed: 'The {field} field confirmation does not match',
  is: 'The {field} field is not valid',
  url: 'The {field} field is not a valid URL',
};

async function loadMessages(): Promise<Record<string, unknown>> {
  const [sharedUiEn, ssEn] = await Promise.all([
    import('@niis/shared-ui/src/locales/en.json'),
    import('@/locales/en.json'),
  ]);
  return deepmerge.all([
    sharedUiEn.default as Record<string, unknown>,
    ssEn.default as Record<string, unknown>,
    { validation: { messages: VEE_VALIDATE_MESSAGES_EN } },
  ]);
}

let messagesPromise: Promise<void> | null = null;

function ensureMessages(): Promise<void> {
  if (messagesPromise) return messagesPromise;
  messagesPromise = loadMessages().then((merged) => {
    sharedI18n.global.setLocaleMessage('en', merged);
  });
  return messagesPromise;
}

export async function renderRoute(
  path: string,
  options: RenderRouteOptions = {},
): Promise<{ router: Router }> {
  const { permissions = DEFAULT_PERMISSIONS } = options;

  await ensureMessages();

  if (options.msw && options.msw.length > 0) {
    worker.use(...options.msw);
  }

  axios.defaults.baseURL = '/api/v1';

  const pinia = createPinia();
  pinia.use(createPersistedState({ storage: sessionStorage }));
  setActivePinia(pinia);

  const appState = useAppState();
  appState.setSessionAlive(true);

  const userStore = useUser();
  userStore.$patch({
    authenticated: true,
    permissions,
    bannedRoutes: [],
    initializationStatus: {
      is_anchor_imported: true,
      is_server_code_initialized: true,
      is_server_owner_initialized: true,
      software_token_init_status: TokenInitStatus.INITIALIZED,
      is_management_services_configured: true,
    },
  });

  const router = createRouter({
    history: createWebHashHistory(),
    routes,
  });

  const vuetify = createXrdVuetify(Color.B_600, Color.B_50, Color.B_100, Color.B_800);

  const AppShell = defineComponent({
    render: () =>
      h(
        XrdApp,
        { loginView: false, initialUserView: false, onLogout: () => {} },
        { default: () => h(RouterView) },
      ),
  });

  render(AppShell, {
    global: {
      plugins: [
        pinia,
        router,
        vuetify,
        sharedI18n as unknown as Plugin,
        createValidators() as unknown as Plugin,
      ],
    },
  });

  await router.push(path);
  await router.isReady();

  return { router };
}
