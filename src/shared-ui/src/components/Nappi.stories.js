import Nappi from './Nappi.vue';

export default {
  title: 'X-Road/Demo button',
  component: Nappi,
  argTypes: {
    disabled: { control: 'boolean' },
    buttonText: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { Nappi },
  template: '<nappi @onClick="onClick" v-bind="$props">{{label}}</nappi>',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  label: 'Button v1',
};

export const Secondary = Template.bind({});
Secondary.args = {
  label: 'Button v2',
};
