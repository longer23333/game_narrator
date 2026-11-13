import {describe, expect, it} from 'vitest';
import {applyAdminVisibility} from '../../public/admin-visibility.js';

describe('管理员功能可见性', () => {
  it('普通用户看不到只对管理员有效的入口', () => {
    const elements = [{hidden:false}, {hidden:false}];
    applyAdminVisibility({querySelectorAll: () => elements}, false);
    expect(elements.every(element => element.hidden)).toBe(true);
  });

  it('管理员身份确认后显示管理入口', () => {
    const element = {hidden:true};
    applyAdminVisibility({querySelectorAll: () => [element]}, true);
    expect(element.hidden).toBe(false);
  });
});
