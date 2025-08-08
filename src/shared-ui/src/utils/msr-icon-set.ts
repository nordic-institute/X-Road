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
  collapse: 'arrow_drop_up',
  complete: '...',
  cancel: 'cancel',
  close: 'close',
  delete: 'delete_forever',
  clear: '...',
  success: '...',
  info: '...',
  warning: 'warning',
  error: 'error',
  prev: 'chevron_left',
  next: 'chevron_right',
  checkboxOn: 'check_box filled',
  checkboxOff: 'check_box_outline_blank',
  checkboxIndeterminate: 'indeterminate_check_box',
  delimiter: '...',
  sortAsc: 'arrow_upward',
  sortDesc: 'arrow_downward',
  sort: '...',
  expand: 'arrow_drop_down',
  menu: '...',
  subgroup: '...',
  dropdown: 'arrow_drop_down',
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
  treeviewCollapse: '...',
  treeviewExpand: '...',
  eyeDropper: '...',
  upload: '...',
  color: '...',
};

interface ExtendedIconProps extends IconProps {
  filled?: boolean | '';
}

const msr: IconSet = {
  component: (props: ExtendedIconProps) => {
    const classes = ['msr'];
    if (props.filled === '' || props.filled) {
      classes.push('filled');
    }

    return h(VClassIcon, { ...props, class: classes });
  },
};

export { msrAliases, msr };
