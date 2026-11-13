// @vitest-environment jsdom
import {expect, it, vi} from 'vitest';
import {runDirectorReview} from '../../public/director-review-state.js';

it('hides old failure during a new review and refreshes after failure', async () => {
  document.body.innerHTML = '<section><header><p class="director-runtime"><small>qwen-plus</small></p><button>重新评审</button></header><p class="old">FAILED 404</p></section>';
  const board = document.querySelector('section');
  const button = board.querySelector('button');
  let fail;
  const refresh = vi.fn(async () => {});
  const pending = runDirectorReview({board, button, start: () => new Promise((resolve, reject) => { fail = reject; }), refresh});
  expect(board.querySelector('.old').hidden).toBe(true);
  expect(board.querySelector('small').hidden).toBe(true);
  expect(button.disabled).toBe(true);
  const rejected = expect(pending).rejects.toThrow('new failure');
  fail(new Error('new failure'));
  await rejected;
  expect(refresh).toHaveBeenCalledOnce();
  expect(button.disabled).toBe(false);
  expect(button.textContent).toBe('重新评审');
  expect(board.hasAttribute('aria-busy')).toBe(false);
});
