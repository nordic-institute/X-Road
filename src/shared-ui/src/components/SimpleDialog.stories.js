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
import SimpleDialog from './SimpleDialog.vue';
import Alert from './Alert.vue';

export default {
  title: 'X-Road/Simple dialog',
  components: { SimpleDialog, Alert },
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
    save: { action: 'save' },
    cancel: { action: 'cancel' },
    message: { control: 'text' },
    showProgressBar: { control: 'boolean' },
    hideSaveButton: { control: 'boolean' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SimpleDialog },
  template: `<simple-dialog @cancel="cancel" @save="save" v-bind="$props">
  <template slot="alert">{{alert}}</template>"
    <template slot="content"><div>{{content}}</div></template>"
    </simple-dialog>`,
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  dialog: true,
  loading: false,
  showClose: true,
  title: 'Title text',
  content: `Lorem ipsum dolor sit amet,
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie.
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit. `,
};

export const LongText = Template.bind({});
LongText.args = {
  dialog: true,
  loading: false,
  title: 'Title text',
  showClose: true,
  content: `This is a very, very long text: Lorem ipsum dolor sit amet,
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie.
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit.
  Ut ac arcu at libero vestibulum condimentum ut sed mi. Nullam egestas
  eu risus at sollicitudin. Sed eleifend pretium eleifend. Vivamus
  aliquet quam et gravida iaculis. Duis quis gravida nibh, vel faucibus felis.`,
};

const Template2 = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SimpleDialog, Alert },
  template: `<simple-dialog @cancel="cancel" @save="save" v-bind="$props">
  <template slot="alert"><alert :show="true" v-bind="$props"/></template>"
    <template slot="content"><div>{{content}}</div></template>"
    </simple-dialog>`,
});

export const AlertTest = Template2.bind({});

AlertTest.args = {
  dialog: true,
  loading: false,
  title: 'Title text',
  showClose: true,
  message: 'Error message!',
  content: `This is a very, very long text: Lorem ipsum dolor sit amet,
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie.
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit.
  Ut ac arcu at libero vestibulum condimentum ut sed mi. Nullam egestas
  eu risus at sollicitudin. Sed eleifend pretium eleifend. Vivamus
  aliquet quam et gravida iaculis. Duis quis gravida nibh, vel faucibus felis.`,
};
