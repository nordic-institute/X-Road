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

import { flushPromises, mount } from '@vue/test-utils';
import EditAcmeServerDialog from '@/components/certificationServices/EditAcmeServerDialog.vue';
import { createValidators } from '@/plugins/vee-validate';
import { XrdSimpleDialog } from '@niis/shared-ui';
import { createVuetify } from 'vuetify';
import { components } from 'vuetify/components';
import { directives } from 'vuetify/directives';
import waitForExpect from 'wait-for-expect';
import i18n from '@/plugins/i18n';

const vuetify = createVuetify({
  components,
  directives,
});
createValidators().install({});

function getWrapper(
  acmeServerDirectoryUrl: string,
  acmeServerIpAddress: string,
) {
  return mount(EditAcmeServerDialog, {
    props: {
      certificationService: {
        acme_server_directory_url: acmeServerDirectoryUrl,
        acme_server_ip_address: acmeServerIpAddress,
      },
    },
    global: {
      components: {
        XrdSimpleDialog,
      },
      plugins: [vuetify, i18n],
    },
  });
}

describe('EditAcmeServerDialog', () => {
  it('should have correct values in the form', async () => {
    const acmeServerDirectoryUrl = 'https://test-ca/acme';
    const acmeServerIpAddress = '1.2.3.4';
    const wrapper = getWrapper(acmeServerDirectoryUrl, acmeServerIpAddress);

    expect(
      wrapper.getComponent('[data-test="acme-checkbox"]').get('input').element
        .checked,
    ).toBeTruthy(true);
    expect(
      wrapper
        .getComponent('[data-test="acme-server-directory-url-input"]')
        .get('input').element.value,
    ).toBe(acmeServerDirectoryUrl);
    expect(
      wrapper
        .getComponent('[data-test="acme-server-ip-address-input"]')
        .get('input').element.value,
    ).toBe(acmeServerIpAddress);
  });

  it('should only require directory url field', async () => {
    const wrapper = getWrapper('https://test-ca/acme', '1.2.3.4');

    const directoryUrlTextField = wrapper
      .getComponent('[data-test="acme-server-directory-url-input"]')
      .get('input');
    await directoryUrlTextField.setValue('');

    await flushPromises();
    const dialogSaveButton = wrapper.getComponent(
      '[data-test="dialog-save-button"]',
    );
    await waitForExpect(() => {
      expect(dialogSaveButton.attributes('disabled')).toBeDefined();
    });

    const ipAddressTextField = wrapper
      .getComponent('[data-test="acme-server-ip-address-input"]')
      .get('input');
    await directoryUrlTextField.setValue('https://ca:9999/test-acme');
    await ipAddressTextField.setValue('');
    await flushPromises();
    await waitForExpect(() => {
      expect(dialogSaveButton.attributes('disabled')).toBeUndefined();
    });
  });
});
