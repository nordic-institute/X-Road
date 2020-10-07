// MyButton
import MyButton from '../index'

// Utilities
import { shallow, createLocalVue } from '@vue/test-utils'

// Bootstrap
const localVue = createLocalVue()

describe('MyButton', () => {
  function mountFunction (options = {}) {
    return shallow(MyButton, {
      localVue,
      ...options,
    })
  }

  it('should work', () => {
    const wrapper = mountFunction()

    expect(wrapper.html()).toMatchSnapshot()
  })
})
