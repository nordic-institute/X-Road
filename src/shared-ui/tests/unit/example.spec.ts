// Libraries
import Vuetify from 'vuetify';

// Components
import LargeButton from '@/components/LargeButton.vue';

// Utilities
import { createLocalVue, mount } from '@vue/test-utils';

describe('CustomCard.vue', () => {
  const localVue = createLocalVue();
  let vuetify: any;

  beforeEach(() => {
    vuetify = new Vuetify();
  });

  it('renders props.msg when passed', () => {
    const msg = 'Test';
    const wrapper = mount(LargeButton, {
      localVue,
      vuetify,
      propsData: { msg },
    });

    // Create snapshot files of the HTML output
    expect(wrapper.html()).toMatchSnapshot();
  });
});
