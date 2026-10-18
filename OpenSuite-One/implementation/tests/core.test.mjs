import test from 'node:test';
import assert from 'node:assert/strict';
import { createApplication } from '../apps/api/server.mjs';
import { openDatabase } from '../apps/api/db.mjs';

process.env.NODE_ENV='test';
process.env.DEV_ALLOW_REGISTRATION='1';

let server, db, base;
const user={};
async function request(path,{method='GET',body,who,csrf=true,headers={}}={}){
 const h={...headers};
 if(who?.cookie)h.Cookie=who.cookie;
 if(csrf&&method!=='GET'&&who?.csrf)h['X-CSRF-Token']=who.csrf;
 if(body!==undefined)h['Content-Type']='application/json';
 const response=await fetch(base+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
 const data=await response.json();
 return {status:response.status,data,headers:response.headers};
}
const signup=async (key,workspace)=>{
 const r=await request('/api/register',{method:'POST',body:{name:'Founder '+key,email:key+'@example.com',workspace,password:'long-secure-example-password'}});
 assert.equal(r.status,201,JSON.stringify(r.data));
 user[key]={cookie:r.headers.get('set-cookie').split(';')[0],csrf:r.data.csrf};
};

test('HTTP tenant, auth, CRUD, stale writes, search, audit, notifications and CSRF',async t=>{
 db=openDatabase(':memory:');
 ({server}=createApplication({db}));
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 base='http://127.0.0.1:'+server.address().port;
 t.after(async()=>{await new Promise(resolve=>server.close(resolve));db.close();});
 await t.test('public metadata, static shell and protected APIs',async()=>{
  const health=await request('/api/health'); assert.equal(health.status,200);
  const noAuth=await request('/api/summary');assert.equal(noAuth.status,401);
  const page=await fetch(base);assert.equal(page.status,200);assert.match(await page.text(),/OpenSuite One/);
  assert.match(page.headers.get('content-security-policy'),/frame-ancestors 'none'/);
 });
 await t.test('two independent workspaces',async()=>{
  await signup('alpha','Alpha Studio');await signup('beta','Beta Studio');
  const first=await request('/api/me',{who:user.alpha});const second=await request('/api/me',{who:user.beta});
  assert.notEqual(first.data.tenant.id,second.data.tenant.id);
 });
 let task;
 await t.test('task create, idempotency, patch, stale conflict and tenant isolation',async()=>{
  const body={title:'Prepare launch',description:'Write checklist',status:'todo',priority:'high'};
  const created=await request('/api/tasks',{method:'POST',body,who:user.alpha,headers:{'Idempotency-Key':'alpha-create-1'}});
  assert.equal(created.status,201,JSON.stringify(created.data));task=created.data.item;
  const duplicate=await request('/api/tasks',{method:'POST',body,who:user.alpha,headers:{'Idempotency-Key':'alpha-create-1'}});
  assert.equal(duplicate.status,200);assert.equal(duplicate.data.item.id,task.id);
  const collision=await request('/api/tasks',{method:'POST',body:{...body,title:'Other'},who:user.alpha,headers:{'Idempotency-Key':'alpha-create-1'}});assert.equal(collision.status,409);
  const changed=await request('/api/tasks/'+task.id,{method:'PATCH',body:{version:1,status:'done'},who:user.alpha});assert.equal(changed.status,200,JSON.stringify(changed.data));
  assert.equal(changed.data.item.version,2);
  const stale=await request('/api/tasks/'+task.id,{method:'PATCH',body:{version:1,status:'todo'},who:user.alpha});assert.equal(stale.status,409);
  const foreignGet=await request('/api/tasks/'+task.id,{who:user.beta});assert.equal(foreignGet.status,404);
  const foreignPatch=await request('/api/tasks/'+task.id,{method:'PATCH',body:{version:2,status:'todo'},who:user.beta});assert.equal(foreignPatch.status,404);
  assert.equal((await request('/api/tasks',{who:user.beta})).data.items.length,0);
 });
 await t.test('notes/events/contacts and tenant-trimmed search',async()=>{
  for(const [type,body] of [['notes',{title:'Launch draft',body:'Agenda'}],['events',{title:'Launch sync',starts_at:'2027-01-01T08:00:00Z',ends_at:'2027-01-01T09:00:00Z'}],['contacts',{name:'Launch Partner',email:'someone@example.com'}]]){
   const resp=await request('/api/'+type,{method:'POST',body,who:user.alpha});assert.equal(resp.status,201,JSON.stringify(resp.data));
  }
  const found=await request('/api/search?q=Launch',{who:user.alpha});assert.equal(found.data.results.length,4);
  const isolated=await request('/api/search?q=Launch',{who:user.beta});assert.equal(isolated.data.results.length,0);
  const invalidEvent=await request('/api/events',{method:'POST',who:user.beta,body:{title:'Wrong',starts_at:'2027-01-02T09:00:00Z',ends_at:'2027-01-01T09:00:00Z'}});assert.equal(invalidEvent.status,400);
 });
 await t.test('origin restriction and CSRF requirement',async()=>{
  const forgery=await request('/api/tasks',{method:'POST',who:user.alpha,csrf:false,body:{title:'bad'}});assert.equal(forgery.status,403);
  const offOrigin=await request('/api/tasks',{method:'POST',who:user.alpha,headers:{Origin:'https://other.example.com'},body:{title:'bad'}});assert.equal(offOrigin.status,403);
 });
 await t.test('notifications and owner-only audit trail',async()=>{
  const notes=await request('/api/notifications',{who:user.alpha});assert.equal(notes.status,200);assert.equal(notes.data.items.length,1);
  const mark=await request('/api/notifications/read',{method:'POST',who:user.alpha,body:{id:notes.data.items[0].id}});assert.equal(mark.status,200);
  assert.equal((await request('/api/notifications',{who:user.alpha})).data.items[0].read_at!==null,true);
  const audit=await request('/api/admin/audit',{who:user.alpha});assert.equal(audit.status,200);assert.ok(audit.data.items.length>=5);
  assert.equal((await request('/api/files',{who:user.alpha})).data.configured,false);
 });
 await t.test('logout destroys session',async()=>{
  const logged=await request('/api/logout',{method:'POST',who:user.alpha});assert.equal(logged.status,200);
  const after=await request('/api/me',{who:user.alpha});assert.equal(after.status,401);
  const login=await request('/api/login',{method:'POST',body:{email:'alpha@example.com',password:'long-secure-example-password'}});
  assert.equal(login.status,200);assert.ok(login.data.csrf);
 });
});
