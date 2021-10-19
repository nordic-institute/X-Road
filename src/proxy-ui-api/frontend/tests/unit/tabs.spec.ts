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
import TabsBase from '@/components/layout/TabsBase.vue';
import Vue from 'vue';

import { user as userModule } from '@/store/modules/user';
import Vuex, { StoreOptions } from 'vuex';
import router from '@/router';

import VueRouter from 'vue-router';

import * as mockData from './roles';

//Vue.use(Vuex);

describe('Tabs', () => {

  let localVue;
  let vuetify;
  let wrapper: any;
  let store: any;

  beforeEach(() => {

    const storeOptions: StoreOptions<RootState> = {
      modules: {
        userModule,
      },
    };

    store = new Vuex.Store<RootState>(storeOptions);

    const div = document.createElement('div');
    div.id = 'root';
    document.body.appendChild(div);

    localVue = createLocalVue(); // because of vuetify, we should use a localVue instance
    vuetify = new Vuetify();

    localVue.use(VueRouter);

    // to render vuetify dialog, vuetify requres the v-app component
    // so we pack our component into a "bridge" component
    const App = localVue.component('App', {
      components: { TabsBase },
      data() {
        return { dialog: false };
      },
      template: `
         <v-app>
           <TabsBase
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
        $t: (key: string) => key, // Mock for vue-i18n localisation plugin. Show just the key instead of localisation.
      },
      stubs: {
        RouterLink: RouterLinkStub,
      },
    });
  });

  it('test securityOfficer', async () => {
    store.commit('setUsername', mockData.securityOfficer.username);
    store.commit('setPermissions', mockData.securityOfficer.permissions);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="clients"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="keys"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="diagnostics"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="settings"]').exists()).toBe(true);
    expect(wrapper.element).toMatchSnapshot();
  });

  it('registrationOfficer', async () => {
    store.commit('setUsername', mockData.registrationOfficer.username);
    store.commit('setPermissions', mockData.registrationOfficer.permissions);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="clients"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="keys"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="diagnostics"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="settings"]').exists()).toBe(false);
    expect(wrapper.element).toMatchSnapshot();
  });

  it('test serviceAdministrator', async () => {
    store.commit('setUsername', mockData.serviceAdministrator.username);
    store.commit('setPermissions', mockData.serviceAdministrator.permissions);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="clients"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="keys"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="diagnostics"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="settings"]').exists()).toBe(false);
    expect(wrapper.element).toMatchSnapshot();
  });

  it('test systemAdministrator', async () => {
    store.commit('setUsername', mockData.systemAdministrator.username);
    store.commit('setPermissions', mockData.systemAdministrator.permissions);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="clients"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="keys"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="diagnostics"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="settings"]').exists()).toBe(true);
    expect(wrapper.element).toMatchSnapshot();
  });

  it('test  securityserverObserver', async () => {
    store.commit('setUsername', mockData.securityserverObserver.username);
    store.commit('setPermissions', mockData.securityserverObserver.permissions);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="clients"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="keys"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="diagnostics"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="settings"]').exists()).toBe(true);
    expect(wrapper.element).toMatchSnapshot();
  });
});
