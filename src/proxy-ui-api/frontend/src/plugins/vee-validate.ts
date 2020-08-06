import { extend, configure } from 'vee-validate';
import { required, email, min, between } from 'vee-validate/dist/rules';
import i18n from '../i18n';
import * as Helpers from '@/util/helpers';

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

extend('restUrl', {
  validate: (value) => {
    if (Helpers.isValidRestURL(value)) {
      return true;
    }
    return false;
  },
  message() {
    // You might want to generate a more complex message with this function.
    return i18n.t('customValidation.invalidRest') as string;
  },
});

extend('wsdlUrl', {
  validate: (value) => {
    if (Helpers.isValidWsdlURL(value)) {
      return true;
    }
    return false;
  },
  message() {
    return i18n.t('customValidation.invalidWsdl') as string;
  },
});

const allowedIdentifierPattern = new RegExp('^((?![:/;%\\\\]).)*$');
extend('xrdIdentifier', {
  validate: (value) => allowedIdentifierPattern.test(value),
  message: () => i18n.t('customValidation.invalidXrdIdentifier') as string,
});
