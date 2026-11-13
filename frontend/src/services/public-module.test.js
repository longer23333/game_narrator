// @vitest-environment jsdom
import {describe, expect, it, vi} from 'vitest';
import {loadPublicModule} from './public-module.js';

describe('public module loading', () => {
  it('loads a module script and waits for execution before continuing boot', async () => {
    const doc = document.implementation.createHTMLDocument();
    const completed = vi.fn();
    const pending = loadPublicModule('/app.js', doc).then(completed);
    const script = doc.head.querySelector('script');
    expect(script.type).toBe('module');
    expect(script.getAttribute('src')).toBe('/app.js');
    await Promise.resolve();
    expect(completed).not.toHaveBeenCalled();
    script.dispatchEvent(new Event('load'));
    await pending;
    expect(completed).toHaveBeenCalledOnce();
  });

  it('reports failed loads and removes the failed script', async () => {
    const doc = document.implementation.createHTMLDocument();
    const pending = loadPublicModule('/app.js', doc);
    const rejected = expect(pending).rejects.toThrow('无法加载页面脚本：/app.js');
    doc.head.querySelector('script').dispatchEvent(new Event('error'));
    await rejected;
    expect(doc.head.querySelector('script')).toBeNull();
  });
});
