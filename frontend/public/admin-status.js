const scopes={
    users:['ACTIVE','DISABLED'],
    projects:['DRAFT','PROCESSING','READY','FAILED','ARCHIVED'],
    'cloud-sync':['LOCAL_ONLY','PENDING','SYNCING','SYNCED','FAILED','PERMANENT_FAILURE'],
    audit:[]
};
const classes={ACTIVE:'ok',READY:'ok',COMPLETED:'ok',SYNCED:'ok',ADMIN:'ok',DISABLED:'bad',FAILED:'bad',PERMANENT_FAILURE:'bad'};
const retryable=new Set(['FAILED','PERMANENT_FAILURE','LOCAL_ONLY']);
const adminStatus=Object.freeze({
  filterValues:scope=>[...(scopes[scope]||[])],
  badgeClass:value=>classes[String(value)]||'',
  canRetry:value=>retryable.has(String(value))
});
export default adminStatus;
