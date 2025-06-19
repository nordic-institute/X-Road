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

import { h } from 'vue';
import type { IconSet, IconAliases, IconProps } from 'vuetify';
import { VClassIcon } from 'vuetify/lib/composables/icons';

const msrAliases: IconAliases = {
  collapse: 'msr-arrow-drop-up',
  complete: '...',
  cancel: '...',
  close: '...',
  delete: '...',
  clear: '...',
  success: '...',
  info: '...',
  warning: '...',
  error: '...',
  prev: '...',
  next: '...',
  checkboxOn: '...',
  checkboxOff: '...',
  checkboxIndeterminate: '...',
  delimiter: '...',
  sortAsc: '...',
  sortDesc: '...',
  sort: '...',
  expand: 'msr-arrow-drop-down',
  menu: '...',
  subgroup: '...',
  dropdown: '...',
  radioOn: '...',
  radioOff: '...',
  edit: '...',
  ratingEmpty: '...',
  ratingFull: '...',
  ratingHalf: '...',
  loading: '...',
  first: '...',
  last: '...',
  unfold: '...',
  file: '...',
  plus: '...',
  minus: '...',
  calendar: '...',
};

interface ExtendedIconProps extends IconProps {
  filled?: boolean | '';
}

const msr: IconSet = {
  component: (props: ExtendedIconProps) => {
    const classes = ['msr'];
    if (props.filled === '' || props.filled) {
      classes.push('msr-filled');
    }

    return h(VClassIcon, { ...props, class: classes });
  },
};

export { msrAliases, msr };
