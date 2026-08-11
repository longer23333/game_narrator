const normalized = value => String(value || '').trim().toLocaleLowerCase();

export function mergeTagViews(existing, additions, removals) {
  const removed = new Set((removals || []).map(normalized).filter(Boolean));
  const result = (existing || []).filter(tag => !removed.has(normalized(tag?.name))).map(tag => ({...tag}));
  for (const raw of additions || []) {
    const name = String(raw || '').trim();
    if (!name) continue;
    const key = normalized(name);
    const current = result.find(tag => normalized(tag?.name) === key);
    if (current) {
      current.userAdded = true;
      current.sources = [...new Set([...(current.sources || []), 'USER'])];
    } else {
      result.push({name, userAdded:true, sources:['USER']});
    }
  }
  return result;
}
