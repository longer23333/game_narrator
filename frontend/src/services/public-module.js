// Public scripts are served unchanged; loading them through a script element
// keeps Vite from treating their URLs as source imports.
export function loadPublicModule(url, targetDocument = document) {
  return new Promise((resolve, reject) => {
    const script = targetDocument.createElement('script');
    script.type = 'module';
    script.src = url;
    script.onload = () => resolve();
    script.onerror = () => {
      script.remove();
      reject(new Error(`无法加载页面脚本：${url}，请刷新页面重试。`));
    };
    targetDocument.head.appendChild(script);
  });
}
