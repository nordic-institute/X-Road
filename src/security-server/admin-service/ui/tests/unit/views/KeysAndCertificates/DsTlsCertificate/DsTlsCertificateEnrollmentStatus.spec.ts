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
import DsTlsCertificateEnrollmentStatus from '@/views/KeysAndCertificates/DsTlsCertificate/DsTlsCertificateEnrollmentStatus.vue';
import { mount } from '@vue/test-utils';
import { createTestingPinia } from '@pinia/testing';
import { describe, expect, it } from 'vitest';
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';
import { DataspaceTlsCertificateEnrollmentStatus } from '@/openapi-types';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';
import { useNotificationsContainer } from '@niis/shared-ui';
import mockedStore from '../../../mocked-store';

const vuetify = createVuetify({
  components,
  directives,
});

function mountStatus(status: DataspaceTlsCertificateEnrollmentStatus) {
  const pinia = createTestingPinia();
  const store = mockedStore(useDsTlsCertificate);
  store.fetchDsTlsCertificateEnrollmentStatus.mockResolvedValue(status);

  const wrapper = mount(DsTlsCertificateEnrollmentStatus, {
    global: {
      plugins: [pinia, vuetify],
      mocks: {
        $t: (key: string) => key,
      },
    },
  });
  return { wrapper, store };
}

async function flush(wrapper: ReturnType<typeof mount>) {
  await Promise.resolve();
  await wrapper.vm.$nextTick();
}

describe('DsTlsCertificateEnrollmentStatus', () => {
  it('shows a loading indicator while the initial fetch is pending, with no method/renewal/error content yet', async () => {
    const pinia = createTestingPinia();
    const store = mockedStore(useDsTlsCertificate);
    store.fetchDsTlsCertificateEnrollmentStatus.mockReturnValue(new Promise(() => {}));

    const wrapper = mount(DsTlsCertificateEnrollmentStatus, {
      global: {
        plugins: [pinia, vuetify],
        mocks: {
          $t: (key: string) => key,
        },
      },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-test="ds-tls-enrollment-status"]').exists()).toBe(true);
    expect(wrapper.find('.v-card--loading').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('dsTlsCertificate.enrollmentStatus.method');
  });

  it('shows the enrollment method for a manually uploaded certificate, with no renewal time or error', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'MANUAL' });
    await flush(wrapper);

    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.manual');
    expect(wrapper.find('[data-test="ds-tls-enrollment-next-renewal"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('dsTlsCertificate.enrollmentStatus.lastError');
  });

  it('shows the ACME enrollment method and the next scheduled renewal time when reported', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'ACME', next_renewal_time: '2026-09-01T00:00:00Z' });
    await flush(wrapper);

    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.acme');
    expect(wrapper.find('[data-test="ds-tls-enrollment-next-renewal"]').exists()).toBe(true);
  });

  it('surfaces the last error even when no certificate has ever been issued (method NONE)', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'NONE', last_error: 'ACME order failed: timeout' });
    await flush(wrapper);

    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.none');
    expect(wrapper.text()).toContain('ACME order failed: timeout');
  });

  it('shows only the "not configured" method, with no renewal time or error, when no certificate has ever been obtained', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'NONE' });
    await flush(wrapper);

    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.none');
    expect(wrapper.find('[data-test="ds-tls-enrollment-next-renewal"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('dsTlsCertificate.enrollmentStatus.lastError');
  });

  it('shows no error text when the last attempt succeeded', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'ACME' });
    await flush(wrapper);

    expect(wrapper.text()).not.toContain('dsTlsCertificate.enrollmentStatus.lastError');
  });

  it('renders nothing but notifies the user when the fetch fails, rather than showing a stale or misleading status', async () => {
    const pinia = createTestingPinia();
    const store = mockedStore(useDsTlsCertificate);
    store.fetchDsTlsCertificateEnrollmentStatus.mockRejectedValue(new Error('network error'));

    const wrapper = mount(DsTlsCertificateEnrollmentStatus, {
      global: {
        plugins: [pinia, vuetify],
        mocks: {
          $t: (key: string) => key,
        },
      },
    });
    await flush(wrapper);

    expect(wrapper.find('[data-test="ds-tls-enrollment-status"]').exists()).toBe(false);
    expect(useNotificationsContainer(pinia).notifications).toHaveLength(1);
  });

  it('stops showing the loading indicator once the fetch resolves', async () => {
    const { wrapper } = mountStatus({ enrollment_method: 'MANUAL' });
    await flush(wrapper);

    expect(wrapper.find('.v-card--loading').exists()).toBe(false);
  });

  it('re-fetches and updates from manual to ACME when refresh() is called, without remounting', async () => {
    const { wrapper, store } = mountStatus({ enrollment_method: 'MANUAL' });
    await flush(wrapper);
    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.manual');

    store.fetchDsTlsCertificateEnrollmentStatus.mockResolvedValue({
      enrollment_method: 'ACME',
      next_renewal_time: '2026-09-01T00:00:00Z',
    });
    await (wrapper.vm as unknown as { refresh: () => Promise<void> }).refresh();
    await flush(wrapper);

    expect(wrapper.text()).toContain('dsTlsCertificate.enrollmentStatus.method.acme');
    expect(wrapper.find('[data-test="ds-tls-enrollment-next-renewal"]').exists()).toBe(true);
  });
});
