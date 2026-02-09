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
import MemberOrGroupSelectionStep from '@/views/Clients/ServiceClients/MemberOrGroupSelectionStep.vue';
import { mount } from '@vue/test-utils';
import { createTestingPinia } from '@pinia/testing';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';
import { XrdBtn } from '@niis/shared-ui';
import { ServiceClient } from '@/openapi-types';
import { useServiceClients } from '@/store/modules/service-clients';
import mockedStore from '../../../mocked-store';
import { setActivePinia } from 'pinia';

// Large number here because that is what has caused issues in the past
const SERVICE_CLIENTS_COUNT = 2000;

const serviceClients: ServiceClient[] = Array(SERVICE_CLIENTS_COUNT).fill(null).map((_, index) => ({
  id: `service-client-${index}`,
  name: `Service Client ${index}`,
  service: `service-${index}`,
}));

const vuetify = createVuetify({
  components,
  directives,
});

const pinia = createTestingPinia();
setActivePinia(pinia);

const store = mockedStore(useServiceClients)
store.fetchCandidates.mockResolvedValue(serviceClients);

const wrapper = mount(MemberOrGroupSelectionStep, {
  props: {
    id: 'member-or-group-selection-step',
    serviceClients: [
      {
        id: 'service-client-a',
        name: 'Service Client-a',
        service: 'service-a',
      },
      {
        id: 'service-client-b',
        name: 'Service Client-b',
        service: 'service-b',
      },
    ],
  },
  global: {
    components: {
      XrdBtn,
    },
    plugins: [pinia, vuetify],
    mocks: {
      $t: (key: string) => key,
    },
  },
});

describe('MemberOrGroupSelectionStep', () => {
  beforeEach(()=>{

  })

  it('component renders', () => {
    expect(wrapper.exists()).toBe(true);
  });

  it('all service client rows are rendered', () => {
    const serviceClientElements = wrapper.find('[data-test="service-clients-table"]').findAll('tbody tr');
    expect(serviceClientElements.length).toBe(SERVICE_CLIENTS_COUNT);
  });

  it('selecting a service client emits the set-step event with the specific client', async () => {
    const nextButton = wrapper.findAllComponents<typeof XrdBtn>(XrdBtn).find((button) => button.text() === 'action.next');
    expect(nextButton).not.toBeUndefined();

    const selectedId = Math.floor(Math.random() * SERVICE_CLIENTS_COUNT);

    const serviceClientElements = wrapper.find('[data-test="service-clients-table"]').findAll('tbody tr');
    await serviceClientElements[selectedId].findComponent(components.VRadio).trigger('click');

    expect(nextButton!.props().disabled).toBeFalsy();
    await nextButton!.trigger('click');
    await wrapper.vm.$nextTick()

    const emits = wrapper.emitted('set-step');
    expect(emits).toHaveLength(1);
    const emit = emits![0][0] as ServiceClient;
    expect(emit.id).toBe(serviceClients[selectedId].id);
  });

});
