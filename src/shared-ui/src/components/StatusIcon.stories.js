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
import StatusIcon from './StatusIcon.vue';

export default {
  title: 'X-Road/Status icon',
  component: StatusIcon,
  argTypes: {
    status: {
      control: {
        type: 'select',
        options: [
          'ok',
          'ok-disabled',
          'saved',
          'progress-register',
          'progress-register-disabled',
          'progress-delete',
          'error',
          'error-disabled',
          'pending',
          'pending-disabled',
        ],
      },
    },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { StatusIcon },
  template: `
    <div>
    <status-icon v-bind="$props" /><br>
    <status-icon status="ok" /> ok<br>
    <status-icon status="ok-disabled" /> ok-disabled<br>
    <status-icon status="saved" /> saved<br>
    <status-icon status="progress-register" /> progress-register<br>
    <status-icon status="progress-register-disabled" /> progress-register-disabled<br>
    <status-icon status="progress-delete" /> progress-delete<br>
    <status-icon status="error" /> error<br>
    <status-icon status="error-disabled" /> error-disabled<br>
    <status-icon status="pending" /> pending<br>
    <status-icon status="pending-disabled" /> pending-disabled<br>
    </div>`,
});

export const Primary = Template.bind({});
Primary.args = {
  status: 'error',
};
