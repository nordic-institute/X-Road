// test/CustomCard.spec.js

// Libraries
import Vue from 'vue';
import Vuetify from 'vuetify';

// Components
//import CustomCard from '@/components/CustomCard'
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
    const msg = 'Welcome to Vuetify';
    const wrapper = mount(LargeButton, {
      localVue,
      vuetify,
      propsData: { msg },
    });

    /*
    const wrapper = shallowMount(HelloWorld, {
      propsData: { msg }
    }); */
    expect(wrapper.text()).toMatch(msg);
  });

  /*
  it('should have a custom title and match snapshot', () => {
    const wrapper = mount(CustomCard, {
      localVue,
      vuetify,
      propsData: { title: 'Foobar' },
    })

    // With jest we can create snapshot files of the HTML output
    expect(wrapper.html()).toMatchSnapshot()

    // We could also verify this differently
    // by checking the text content
    const title = wrapper.find('.v-card__title > span')

    expect(title.text()).toBe('Foobar')
  })

  it('should emit an event when the action v-btn is clicked', () => {
    const wrapper = mount(CustomCard, {
      localVue,
      vuetify,
      propsData: { title: 'Foobar' },
    })

    const event = jest.fn()
    const button = wrapper.find('.v-btn')

    // Here we bind a listener to the wrapper
    // instance to catch our custom event
    // https://vuejs.org/v2/api/#Instance-Methods-Events
    wrapper.vm.$on('action-btn:clicked', event)

    expect(event).toHaveBeenCalledTimes(0)

    // Simulate a click on the button
    button.trigger('click')

    // Ensure that our mock event was called
    expect(event).toHaveBeenCalledTimes(1)
  })

*/
});

/* import { shallowMount } from "@vue/test-utils";
import HelloWorld from "@/components/HelloWorld.vue";

describe("HelloWorld.vue", () => {
  it("renders props.msg when passed", () => {
    const msg = "Welcome to Vuetify";
    const wrapper = shallowMount(HelloWorld, {
      propsData: { msg }
    });
    expect(wrapper.text()).toMatch(msg);
  });
});

*/
