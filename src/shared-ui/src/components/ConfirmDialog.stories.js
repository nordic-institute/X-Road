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
import ConfirmDialog from './ConfirmDialog.vue';

export default {
  title: 'X-Road/Confirm dialog',
  component: ConfirmDialog,
  argTypes: {
    dialog: { control: 'boolean' },
    loading: { control: 'boolean' },
    disableSave: { control: 'boolean' },
    title: { control: 'text' },
    content: { control: 'text' },
    saveButtonText: { control: 'text' },
    cancelButtonText: { control: 'text' },
    showClose: { control: 'boolean' },
    width: { control: 'number' },
    accept: { action: 'accept' },
    cancel: { action: 'cancel' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { ConfirmDialog },
  template:
    '<confirm-dialog @accept="accept" @cancel="cancel" v-bind="$props">{{label}}</confirm-dialog>',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  dialog: true,
  label: 'Hello world!',
};

export const Secondary = Template.bind({});
Secondary.args = {
  dialog: true,
  label: 'This is a very very long label for a button',
};
