import { gsap } from 'gsap';
import './memphis-theme.css';

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
const animated = new WeakSet();

function reveal(scope = document) {
  if (reduceMotion.matches) return;
  const nodes = [...scope.querySelectorAll?.(
    '[data-page]:not([hidden]) > *, .task-card, .asset-list article, .segment-search-results article, .roadmap-list article, .card, .chart-card, .storage-card, .step'
  ) || []].filter(node => !animated.has(node) && node.getClientRects().length);
  nodes.forEach(node => animated.add(node));
  if (nodes.length) gsap.fromTo(nodes,
    { autoAlpha: 0, y: 24, rotate: index => index % 2 ? 0.7 : -0.7 },
    { autoAlpha: 1, y: 0, rotate: 0, duration: 0.52, stagger: 0.055, ease: 'back.out(1.35)', clearProps: 'transform' }
  );
}

function animateActivePage() {
  if (reduceMotion.matches) return;
  const page = document.querySelector('[data-page]:not([hidden])');
  if (!page) return;
  gsap.fromTo(page, { autoAlpha: 0, x: 28 }, { autoAlpha: 1, x: 0, duration: 0.38, ease: 'power3.out' });
  reveal(page);
}

document.addEventListener('click', event => {
  const control = event.target.closest('button, a, summary, [role="button"]');
  if (control && !reduceMotion.matches) gsap.fromTo(control, { scale: 0.96 }, { scale: 1, duration: 0.3, ease: 'elastic.out(1, .45)' });
  if (event.target.closest('[data-view-link], [data-tab]')) requestAnimationFrame(animateActivePage);
});

document.addEventListener('toggle', event => {
  if (event.target instanceof HTMLDetailsElement && event.target.open && !reduceMotion.matches) {
    const body = event.target.querySelector(':scope > :not(summary)');
    if (body) gsap.fromTo(body, { autoAlpha: 0, y: -10 }, { autoAlpha: 1, y: 0, duration: 0.3, ease: 'power2.out' });
  }
}, true);

const observer = new MutationObserver(records => {
  if (records.some(record => record.type === 'childList')) reveal(document);
  if (records.some(record => record.type === 'attributes' && record.attributeName === 'hidden')) animateActivePage();
});
observer.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['hidden', 'open'] });

document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('toggle', () => {
  if (dialog.open && !reduceMotion.matches) gsap.fromTo(dialog, { autoAlpha: 0, scale: 0.94, y: 24 }, { autoAlpha: 1, scale: 1, y: 0, duration: 0.36, ease: 'back.out(1.5)' });
}));

window.GameNarratorMotion = { reveal, animateActivePage };
requestAnimationFrame(() => { document.documentElement.classList.add('motion-ready'); reveal(document); });
