const pendingScripts = new Map();

export function loadLegacyScript(src, {type = 'classic'} = {}) {
  const key=`${type}:${src}`;
  if (pendingScripts.has(key)) return pendingScripts.get(key);
  const promise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    if (type === 'module') script.type = 'module';
    script.onload = resolve;
    script.onerror = () => reject(new Error(`无法加载 ${src}`));
    document.head.appendChild(script);
  }).catch(error => { pendingScripts.delete(key); throw error; });
  pendingScripts.set(key, promise);
  return promise;
}
