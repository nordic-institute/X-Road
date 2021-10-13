/*
 * The MIT License
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

import {
  mount,
  shallowMount,
  createLocalVue,
  RouterLinkStub,
} from '@vue/test-utils';
import Vuetify from 'vuetify';
import { RootState } from '@/store/types';
//import AppLogin from '@/views/AppLogin.vue';
import AppBase from '@/views/AppBase.vue';
import TabsBase from '@/components/layout/TabsBase.vue';
import Vue from 'vue';
import Router from 'vue-router';
import { user as userModule } from '@/store/modules/user';
import { module as notificationsModule } from '@/store/modules/notifications';
import { clientsModule } from '@/store/modules/clients';
import { alertsModule } from '@/store/modules/alerts';
import * as global from '@/global';

import Vuex, { Store, StoreOptions } from 'vuex';
//import router from '@/router';
import routes from '@/routes';
import VueRouter from 'vue-router';

import * as mockData from './roles';

//import Vuetify from 'vuetify/lib';

//Vue.use(Vuetify);

// Create the router
const router = new Router({
  routes: routes,
});

//import vuetify from '@/plugins/vuetify';
Vue.use(Vuex);

describe('Tabs', () => {
  //import vuetify from '@/plugins/vuetify';

  let localVue;
  let vuetify;
  let wrapper: any;

  let getters;
  let store: any;

  beforeEach(() => {
    getters = {
      hasInitState: () => true,
      needsInitialization: () => false,
      hasPermission: () => true,
    };

    const storeOptions: StoreOptions<RootState> = {
      modules: {
        userModule,
        notificationsModule,
        alertsModule,
        clientsModule,
      },
    };

    store = new Vuex.Store<RootState>(storeOptions);

    const div = document.createElement('div');
    div.id = 'root';
    document.body.appendChild(div);

    localVue = createLocalVue(); // because of vuetify, we should use a localVue instance
    vuetify = new Vuetify();

    localVue.use(VueRouter);
    //const router = new VueRouter();

    // to render vuetify dialog, vuetify requres the v-app component
    // so we pack our component into a "bridge" component
    const App = localVue.component('App', {
      components: { AppBase },
      data() {
        return { dialog: false };
      },
      template: `
         <v-app>
           <AppBase
           />
         </v-app>
       `,
    });

    wrapper = mount(App, {
      localVue,
      vuetify,
      attachTo: '#root',
      store,
      router,
      mocks: {
        $t: (key: string) => key, // Mock for vue-i18n lovalisation plugin. Show just the key instead of localisation.
        saveResponseAsFile: jest.fn(),
      },
      stubs: {
        RouterLink: RouterLinkStub,
        'xrd-button': true,
        'xrd-search': true,
        'xrd-confirm-dialog': true,
        ContextualAlerts: true,
        'xrd-file-upload': true,
      },
    });

    store.commit('authUser');
    store.commit('setSessionAlive', true);
  });

  it('first', async () => {
    router.replace({ name: global.RouteName.Clients });
    await wrapper.vm.$nextTick();

    store.commit('setUsername', mockData.securityOfficer.username);
    store.commit('setPermissions', mockData.securityOfficer.permissions);
    await wrapper.vm.$nextTick();

    //expect(wrapper.element).toMatchSnapshot();
    expect(wrapper.element).toMatchSnapshot();

    expect(wrapper.find('[data-test="keys"]').exists()).toBe(true);
    await wrapper.find('[data-test="settings"]').trigger('click');

    await router.replace('/settings');
    await wrapper.vm.$nextTick();
    expect(wrapper.element).toMatchSnapshot();
    expect(
      wrapper.find('[data-test="system-parameters-tab-button"]').exists(),
    ).toBe(true);
    expect(
      wrapper.find('[data-test="backupandrestore-tab-button"]').exists(),
    ).toBe(false);
  });
});
