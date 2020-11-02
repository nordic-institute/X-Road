import ProgressLinear from './ProgressLinear.vue';

export default {
  title: 'X-Road/Progress linear',
  component: ProgressLinear,
  argTypes: {
    active: { control: 'boolean' },
    height: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { ProgressLinear },
  template: `<progress-linear v-bind="$props"/>`,
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  active: true,
  height: '2px',
};
