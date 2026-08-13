(() => {
 const dialog=document.querySelector('#readiness-dialog'),grid=document.querySelector('#readiness-grid'),summary=document.querySelector('#readiness-summary');
 if(!dialog||!grid)return;
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 async function refresh(){summary.textContent='正在检查…';grid.innerHTML='';try{const response=await fetch('/api/debug/release-readiness',{cache:'no-store'});if(!response.ok)throw new Error(`就绪度读取失败（HTTP ${response.status}）`);const report=await response.json();summary.className=`readiness-summary ${String(report.overall).toLowerCase()}`;summary.textContent=`总体：${report.overall} · ${new Date(report.generatedAt).toLocaleString()}`;grid.innerHTML=(report.checks||[]).map(check=>`<article class="readiness-card ${String(check.status).toLowerCase()}"><header><i></i><strong>${escape(check.label)}</strong><b>${escape(check.status)}</b></header><p>${escape(check.summary)}</p><small>${check.evidenceAt?`证据：${escape(new Date(check.evidenceAt).toLocaleString())}`:'当前没有证据时间'}</small></article>`).join('');}catch(error){summary.className='readiness-summary red';summary.textContent=error.message;}}
 document.querySelector('#readiness-open')?.addEventListener('click',()=>{dialog.showModal();refresh()});
 document.querySelector('#readiness-close')?.addEventListener('click',()=>dialog.close());
 document.querySelector('#readiness-refresh')?.addEventListener('click',refresh);
 dialog.addEventListener('click',event=>{if(event.target===dialog)dialog.close()});
})();
