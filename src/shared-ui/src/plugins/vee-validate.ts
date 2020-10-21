import { extend, configure } from 'vee-validate';
import { required, email, min, between } from 'vee-validate/dist/rules';
import i18n from '../i18n';

configure({
  // This should be ok, as it is the vee-validate contract
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  defaultMessage: (field, values: any): string => {
    // override the field name.
    values._field_ = i18n.t(`fields.${field}`);

    return i18n.t(`validation.${values._rule_}`, values) as string;
  },
});

// Install required rule and message.
extend('required', required);

// Install email rule and message.
extend('email', email);

// Install min rule and message.
extend('min', min);

// Install between rule and message.
extend('between', between);
