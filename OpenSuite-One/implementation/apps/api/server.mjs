import http from 'node:http';
import { readFileSync } from 'node:fs';
import { resolve, extname } from 'node:path';
import { randomBytes, randomUUID, scryptSync, timingSafeEqual, createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { openDatabase } from './db.mjs';
import { listNextcloudFiles } from '../../connectors/nextcloud/dav.mjs';

const now = () => new Date().toISOString();
const id = () => randomUUID();
const hash = x => createHash('sha256').update(x).digest('hex');
const lowerEmail = email => String(email || '').trim().toLowerCase();
const field = (v, max = 160, required = false) => {
  if (typeof v !== 'string' || v.length > max || (required && !v.trim())) throw apiError(400, 'VALIDATION_FAILED', 'Invalid or missing text field');
  return v.trim();
};
const apiError = (status, code, message) => Object.assign(new Error(message), { status, code });
const roles = new Set(['owner','admin']);
const tables = {
  tasks: { fields: ['title','description','status','priority','due_date'], required: ['title'] },
  notes: { fields: ['title','body','pinned'], required: ['title'] },
  events: { fields: ['title','location','starts_at','ends_at'], required: ['title','starts_at','ends_at'] },
  contacts: { fields: ['name','email','company','job_title'], required: ['name'] },
};
const maxLen = { title: 220, description: 4000, body: 10000, name: 160, email: 250, company: 200, job_title: 160, location: 300, status: 20, priority: 20, due_date: 40, starts_at: 40, ends_at: 40 };

function normalize(kind, input, partial = false) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw apiError(400,'VALIDATION_FAILED','Expected a JSON object');
  const spec = tables[kind], data = {};
  for (const key of Object.keys(input)) {
    if (key === 'version') continue;
    if (!spec.fields.includes(key)) throw apiError(400, 'VALIDATION_FAILED', 'Unexpected field: ' + key);
    if (key === 'pinned') { if (typeof input[key] !== 'boolean') throw apiError(400,'VALIDATION_FAILED','Invalid pinned'); data.pinned = input.pinned ? 1 : 0; }
    else if (input[key] === null && key === 'due_date') data[key] = null;
    else data[key] = field(input[key], maxLen[key], spec.required.includes(key));
  }
  if (!partial) for (const key of spec.required) if (!(key in data)) throw apiError(400,'VALIDATION_FAILED','Missing field: ' + key);
  if (data.status && !['todo','in_progress','done'].includes(data.status)) throw apiError(400,'VALIDATION_FAILED','Invalid status');
  if (data.priority && !['low','normal','high'].includes(data.priority)) throw apiError(400,'VALIDATION_FAILED','Invalid priority');
  if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) throw apiError(400,'VALIDATION_FAILED','Invalid email');
  for (const key of ['starts_at','ends_at','due_date']) if (data[key] && !Number.isFinite(Date.parse(data[key]))) throw apiError(400,'VALIDATION_FAILED','Invalid date');
  if (data.starts_at && data.ends_at && Date.parse(data.ends_at) <= Date.parse(data.starts_at)) throw apiError(400,'VALIDATION_FAILED','End must be after start');
  return data;
}
function passwordHash(password) {
  if (typeof password !== 'string' || password.length < 12 || password.length > 128) throw apiError(400,'VALIDATION_FAILED','Use a password of 12–128 characters');
  const salt = randomBytes(16).toString('hex');
  return salt + ':' + scryptSync(password,salt,64).toString('hex');
}
function passwordMatch(password, stored) {
  const [salt, result] = stored.split(':');
  const expected = Buffer.from(result,'hex');
  const actual = scryptSync(String(password||''),salt,expected.length);
  return actual.length === expected.length && timingSafeEqual(actual,expected);
}
function isLocal(address) { return ['127.0.0.1','::1','::ffff:127.0.0.1'].includes(address); }
function reply(res, status, data, headers = {}) {
  res.writeHead(status, { 'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'no-store', ...headers });
  res.end(JSON.stringify(data));
}
async function jsonBody(req) {
  let text='';
  for await (const chunk of req) { text += chunk; if (text.length > 102400) throw apiError(413,'VALIDATION_FAILED','Request too large'); }
  try { return text ? JSON.parse(text) : {}; }
  catch { throw apiError(400,'VALIDATION_FAILED','Malformed JSON'); }
}
function guardOrigin(req) {
  const origin = req.headers.origin;
  if (!origin) return;
  let a,b;
  try { a = new URL(origin).host; b = req.headers.host; } catch { throw apiError(403,'FORBIDDEN','Invalid origin'); }
  if (a !== b) throw apiError(403,'FORBIDDEN','Cross-origin mutation denied');
}
function setSession(db, user, tenantId, res) {
  const token = randomBytes(32).toString('base64url'), csrf = randomBytes(32).toString('base64url');
  const expires = Date.now() + 7 * 24 * 3600_000;
  db.prepare('INSERT INTO sessions(hash,user_id,tenant_id,csrf_hash,expires_at) VALUES (?,?,?,?,?)').run(hash(token),user.id,tenantId,hash(csrf),expires);
  const secure = process.env.NODE_ENV === 'production' ? '; Secure' : '';
  res.setHeader('Set-Cookie', `opensuite_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800${secure}`);
  return csrf;
}
function getSession(db, req) {
  const match = /(?:^|;\s*)opensuite_session=([^;]+)/.exec(req.headers.cookie||'');
  if (!match) return null;
  return db.prepare(`SELECT s.hash,s.csrf_hash,s.tenant_id,s.user_id, s.expires_at,
    u.name,u.email,m.role,t.name AS tenant_name FROM sessions s
    JOIN users u ON u.id=s.user_id JOIN memberships m ON m.tenant_id=s.tenant_id AND m.user_id=s.user_id
    JOIN tenants t ON t.id=s.tenant_id WHERE s.hash=? AND s.expires_at>?`).get(hash(match[1]),Date.now()) || null;
}
function csrfGuard(req, session) {
  const value = req.headers['x-csrf-token'];
  if (typeof value !== 'string' || hash(value) !== session.csrf_hash) throw apiError(403,'FORBIDDEN','Invalid CSRF token');
}
function audit(db, actor, action, resource, resourceId) {
  db.prepare('INSERT INTO audit VALUES (?,?,?,?,?,?,?)').run(id(),actor.tenant_id,actor.user_id,action,resource,resourceId,now());
}
function notify(db, actor, kind, title, body = '') {
  db.prepare('INSERT INTO notifications VALUES (?,?,?,?,?,?,?,?)').run(id(),actor.tenant_id,actor.user_id,kind,title,body,null,now());
}
function createOrg(db, {name,email,password,workspace}) {
  const tn=field(workspace,160,true), username=field(name,160,true), mail=lowerEmail(email);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) throw apiError(400,'VALIDATION_FAILED','Invalid email');
  const ph=passwordHash(password), oid=id(), uid=id(), timestamp=now();
  const slug = tn.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,60);
  if (!slug) throw apiError(400,'VALIDATION_FAILED','Invalid workspace');
  try {
    db.exec('BEGIN IMMEDIATE');
    if (db.prepare('SELECT 1 FROM users WHERE email=?').get(mail)) throw apiError(409,'CONFLICT','User exists');
    if (db.prepare('SELECT 1 FROM tenants WHERE slug=?').get(slug)) throw apiError(409,'CONFLICT','Workspace exists');
    db.prepare('INSERT INTO tenants VALUES (?,?,?,?)').run(oid,tn,slug,timestamp);
    db.prepare('INSERT INTO users VALUES (?,?,?,?,?)').run(uid,mail,username,ph,timestamp);
    db.prepare('INSERT INTO memberships VALUES (?,?,?)').run(oid,uid,'owner');
    db.prepare('INSERT INTO audit VALUES (?,?,?,?,?,?,?)').run(id(),oid,uid,'tenant.created','tenant',oid,timestamp);
    db.exec('COMMIT');
  } catch(err) { if (db.isOpen) {try {db.exec('ROLLBACK');} catch{}} throw err; }
  return { id:uid, tenantId:oid };
}
export { createOrg };

export function createApplication({ db = openDatabase(), publicDir = fileURLToPath(new URL('../web/public/',import.meta.url)) } = {}) {
  const limiter = new Map();
  const server = http.createServer(async (req,res) => {
    const requestId=id();
    res.setHeader('X-Request-Id',requestId);
    res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('Permissions-Policy','camera=(), microphone=(), geolocation=()');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    try {
      const url = new URL(req.url, 'http://localhost');
      const path = url.pathname;
      const method = req.method;
      if (!path.startsWith('/api/')) {
        if (!['GET','HEAD'].includes(method)) throw apiError(405,'VALIDATION_FAILED','Method not allowed');
        const assets = { '/':'index.html','/index.html':'index.html','/app.js':'app.js','/style.css':'style.css','/manifest.json':'manifest.json','/sw.js':'sw.js','/icon.svg':'icon.svg' };
        const asset=assets[path];
        if (!asset) { res.writeHead(404);res.end('Not found');return; }
        const mime = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.svg':'image/svg+xml'}[extname(asset)];
        const bytes = readFileSync(resolve(publicDir, asset));
        res.writeHead(200, {'Content-Type':mime,'Cache-Control': asset==='index.html'?'no-store':'public, max-age=120'});
        if (method==='GET') res.end(bytes); else res.end();
        return;
      }
      if (method==='OPTIONS') throw apiError(405,'VALIDATION_FAILED','CORS not enabled');
      if (!['GET','HEAD'].includes(method)) guardOrigin(req);
      const session = getSession(db,req);
      if (path === '/api/health' && method==='GET') return reply(res,200,{ status:'ok', application:'opensuite-one', version:'0.1.0' });
      if (path === '/api/meta' && method==='GET') return reply(res,200,{ version:'0.1.0', registration:process.env.NODE_ENV !== 'production' && process.env.DEV_ALLOW_REGISTRATION === '1' && isLocal(req.socket.remoteAddress), modules:['home','tasks','notes','calendar','contacts','files','search','notifications','admin'] });
      if (path === '/api/register' && method==='POST') {
        if (process.env.NODE_ENV==='production' || process.env.DEV_ALLOW_REGISTRATION!=='1' || !isLocal(req.socket.remoteAddress)) throw apiError(403,'FORBIDDEN','Registration disabled; ask an administrator to provision access');
        const key = req.socket.remoteAddress + ':register';
        const oldTries = limiter.get(key) || {n:0,until:0};
        const tries = oldTries.until < Date.now() ? {n:0,until:0} : oldTries;
        if (tries.n>=10 && tries.until>Date.now()) throw apiError(429,'RATE_LIMITED','Too many requests');
        limiter.set(key,{n:tries.n+1,until:Date.now()+60000});
        const input=await jsonBody(req), user=createOrg(db,input);
        const csrf=setSession(db,user,user.tenantId,res);
        return reply(res,201,{csrf});
      }
      if (path === '/api/login' && method==='POST') {
        const key=req.socket.remoteAddress + ':login';
        const oldAttempt=limiter.get(key) || {n:0,until:0};
        const attempt=oldAttempt.until < Date.now() ? {n:0,until:0} : oldAttempt;
        if (attempt.n>=8 && attempt.until>Date.now()) throw apiError(429,'RATE_LIMITED','Try again later');
        const b=await jsonBody(req), user=db.prepare('SELECT * FROM users WHERE email=?').get(lowerEmail(b.email));
        let good=false;
        try { good=!!user && passwordMatch(b.password,user.password_hash); } catch { /* identical failure */ }
        if (!good) { limiter.set(key,{n:attempt.n+1,until:Date.now()+60000});throw apiError(401,'AUTH_REQUIRED','Invalid credentials'); }
        limiter.delete(key);
        const membership=db.prepare('SELECT tenant_id FROM memberships WHERE user_id=? ORDER BY CASE role WHEN \'owner\' THEN 0 ELSE 1 END LIMIT 1').get(user.id);
        const csrf=setSession(db,user,membership.tenant_id,res);
        return reply(res,200,{csrf});
      }
      if (!session) throw apiError(401,'AUTH_REQUIRED','Sign in required');
      if (path === '/api/me' && method==='GET') {
        // CSRF token is issued at login, not stored in client-accessible cookies.
        return reply(res,200,{user:{id:session.user_id,name:session.name,email:session.email,role:session.role},tenant:{id:session.tenant_id,name:session.tenant_name}});
      }
      if (!['GET','HEAD'].includes(method)) csrfGuard(req,session);
      if (path==='/api/logout' && method==='POST') {
        db.prepare('DELETE FROM sessions WHERE hash=?').run(session.hash);
        res.setHeader('Set-Cookie','opensuite_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0');
        return reply(res,200,{ok:true});
      }
      if (path==='/api/summary' && method==='GET') {
        const counts={};
        for (const table of Object.keys(tables)) counts[table]=db.prepare(`SELECT count(*) n FROM ${table} WHERE tenant_id=?`).get(session.tenant_id).n;
        const complete=db.prepare("SELECT count(*) n FROM tasks WHERE tenant_id=? AND status='done'").get(session.tenant_id).n;
        const recent=db.prepare('SELECT id,title,status,priority,due_date FROM tasks WHERE tenant_id=? ORDER BY updated_at DESC LIMIT 5').all(session.tenant_id);
        const upcoming=db.prepare('SELECT id,title,starts_at,ends_at FROM events WHERE tenant_id=? AND starts_at>=? ORDER BY starts_at LIMIT 5').all(session.tenant_id,now());
        const unread=db.prepare('SELECT count(*) n FROM notifications WHERE tenant_id=? AND user_id=? AND read_at IS NULL').get(session.tenant_id,session.user_id).n;
        return reply(res,200,{counts,completedTasks:complete,recent,upcoming,unread});
      }
      const resource=/^\/api\/(tasks|notes|events|contacts)(?:\/([0-9a-f-]+))?$/.exec(path);
      if (resource) {
        const kind=resource[1], resourceId=resource[2];
        if (method==='GET' && !resourceId) {
          const rows=db.prepare(`SELECT * FROM ${kind} WHERE tenant_id=? ORDER BY updated_at DESC LIMIT 300`).all(session.tenant_id);
          return reply(res,200,{items:rows});
        }
        if (method==='POST' && !resourceId) {
          const b=await jsonBody(req), data=normalize(kind,b);
          const idem=req.headers['idempotency-key'];
          if (idem !== undefined && (typeof idem!=='string' || idem.length>100 || !idem)) throw apiError(400,'VALIDATION_FAILED','Invalid Idempotency-Key');
          const payloadHash=hash(JSON.stringify(data));
          if (idem) {
            const saved=db.prepare('SELECT payload_hash,response FROM idempotency WHERE tenant_id=? AND user_id=? AND method=? AND key=?').get(session.tenant_id,session.user_id,kind+':POST',idem);
            if (saved) {
              if (saved.payload_hash !== payloadHash) throw apiError(409,'CONFLICT','Idempotency key reused with different payload');
              return reply(res,200,JSON.parse(saved.response));
            }
          }
          const rid=id(), timestamp=now();
          const entries={...data,id:rid,tenant_id:session.tenant_id,created_by:session.user_id,owner_id:session.user_id,created_at:timestamp,updated_at:timestamp};
          const cols=['id','tenant_id', kind==='tasks'?'owner_id':'created_by',...Object.keys(data),'created_at','updated_at'];
          const values=cols.map(col=>entries[col]);
          db.exec('BEGIN IMMEDIATE');
          try {
            db.prepare(`INSERT INTO ${kind} (${cols.join(',')}) VALUES (${cols.map(()=>'?').join(',')})`).run(...values);
            audit(db,session,'created',kind,rid);
            if (kind==='tasks') notify(db,session,'task','Task created',data.title);
            const payload={item:db.prepare(`SELECT * FROM ${kind} WHERE id=? AND tenant_id=?`).get(rid,session.tenant_id)};
            if (idem) db.prepare('INSERT INTO idempotency VALUES (?,?,?,?,?,?)').run(session.tenant_id,session.user_id,kind+':POST',idem,payloadHash,JSON.stringify(payload));
            db.exec('COMMIT');
            return reply(res,201,payload);
          } catch(err) { db.exec('ROLLBACK');throw err; }
        }
        if (resourceId) {
          const old=db.prepare(`SELECT * FROM ${kind} WHERE id=? AND tenant_id=?`).get(resourceId,session.tenant_id);
          if (!old) throw apiError(404,'RESOURCE_NOT_FOUND','Record not found');
          if (method==='GET') return reply(res,200,{item:old});
          const actorField=kind==='tasks'?'owner_id':'created_by';
          if (old[actorField]!==session.user_id && !roles.has(session.role)) throw apiError(403,'FORBIDDEN','You cannot modify this record');
          if (method==='PATCH') {
            const b=await jsonBody(req);
            if (!Number.isInteger(b.version) || b.version!==old.version) throw apiError(409,'CONFLICT','Record changed. Refresh before editing');
            const data=normalize(kind,b,true), keys=Object.keys(data);
            if (!keys.length) throw apiError(400,'VALIDATION_FAILED','No changes');
            const merged={...old,...data};
            if (kind==='events' && Date.parse(merged.ends_at)<=Date.parse(merged.starts_at)) throw apiError(400,'VALIDATION_FAILED','End must be after start');
            const timestamp=now();
            const q=db.prepare(`UPDATE ${kind} SET ${keys.map(k=>k+'=?').join(',')},updated_at=?,version=version+1 WHERE id=? AND tenant_id=? AND version=?`)
              .run(...keys.map(k=>data[k]),timestamp,resourceId,session.tenant_id,b.version);
            if (q.changes===0) throw apiError(409,'CONFLICT','Record updated by another user');
            audit(db,session,'updated',kind,resourceId);
            return reply(res,200,{item:db.prepare(`SELECT * FROM ${kind} WHERE id=? AND tenant_id=?`).get(resourceId,session.tenant_id)});
          }
          if (method==='DELETE') {
            db.prepare(`DELETE FROM ${kind} WHERE id=? AND tenant_id=?`).run(resourceId,session.tenant_id);
            audit(db,session,'deleted',kind,resourceId);
            return reply(res,200,{ok:true});
          }
        }
      }
      if (path==='/api/search' && method==='GET') {
        const query=field(url.searchParams.get('q') || '',100);
        if (!query) return reply(res,200,{results:[]});
        const p='%'+query.replaceAll('%','\\%').replaceAll('_','\\_')+'%';
        const results=[];
        for (const [type,col] of [['tasks','title'],['notes','title'],['events','title'],['contacts','name']]) {
          for (const row of db.prepare(`SELECT id,${col} title,updated_at FROM ${type} WHERE tenant_id=? AND ${col} LIKE ? ESCAPE '\\' LIMIT 15`).all(session.tenant_id,p))
            results.push({...row,type});
        }
        return reply(res,200,{results:results.slice(0,50)});
      }
      if (path==='/api/notifications' && method==='GET') {
        const items=db.prepare('SELECT * FROM notifications WHERE tenant_id=? AND user_id=? ORDER BY created_at DESC LIMIT 100').all(session.tenant_id,session.user_id);
        return reply(res,200,{items});
      }
      if (path==='/api/notifications/read' && method==='POST') {
        const b=await jsonBody(req);
        if (typeof b.id !== 'string') throw apiError(400,'VALIDATION_FAILED','Missing notification');
        db.prepare('UPDATE notifications SET read_at=? WHERE id=? AND tenant_id=? AND user_id=?').run(now(),b.id,session.tenant_id,session.user_id);
        return reply(res,200,{ok:true});
      }
      if (path==='/api/files' && method==='GET') {
        try { return reply(res,200,await listNextcloudFiles(session.tenant_id)); }
        catch { throw apiError(503,'PROVIDER_UNAVAILABLE','Nextcloud is unavailable. Retry later or check integration settings'); }
      }
      if (path==='/api/integrations' && method==='GET') {
        const nextcloud = Boolean(process.env.NEXTCLOUD_BASE_URL && process.env.NEXTCLOUD_APP_PASSWORD && process.env.NEXTCLOUD_TENANT_ID === session.tenant_id);
        return reply(res,200,{items:[
          {name:'Nextcloud Files',status:nextcloud?'configured':'not configured',description:'Read-only WebDAV listing; upload/share not yet implemented'},
          {name:'Keycloak SSO',status:'planned',description:'Local authentication only in this milestone'},
          {name:'Collabora Office',status:'planned',description:'WOPI editor integration required'},
          {name:'Mail & Chat',status:'planned',description:'Mail delivery and messaging services not connected'},
          {name:'CRM & ERP',status:'planned',description:'Business applications require adapter and license review'}
        ]});
      }
      if (path==='/api/admin/audit' && method==='GET') {
        if (!roles.has(session.role)) throw apiError(403,'FORBIDDEN','Administrator required');
        return reply(res,200,{items:db.prepare(`SELECT a.id,a.action,a.resource,a.resource_id,a.created_at,u.name actor
          FROM audit a JOIN users u ON u.id=a.actor_id WHERE a.tenant_id=? ORDER BY a.created_at DESC LIMIT 100`).all(session.tenant_id)});
      }
      if (path==='/api/admin/members' && method==='GET') {
        if (!roles.has(session.role)) throw apiError(403,'FORBIDDEN','Administrator required');
        return reply(res,200,{items:db.prepare('SELECT u.id,u.name,u.email,m.role FROM memberships m JOIN users u ON u.id=m.user_id WHERE m.tenant_id=?').all(session.tenant_id)});
      }
      throw apiError(404,'RESOURCE_NOT_FOUND','Endpoint not found');
    } catch(err) {
      const status=err.status || 500;
      if (status===500) console.error('[server error]',requestId,err.message);
      reply(res,status,{error:{code:err.code || 'INTERNAL_ERROR',message:status===500?'Unexpected server error':err.message,requestId}});
    }
  });
  return { server, db };
}
if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const {server}=createApplication();
  const host=process.env.HOST || '127.0.0.1';
  const port=Number(process.env.PORT || 4173);
  server.listen(port,host,()=>console.log(`OpenSuite One at http://${host}:${port}`));
}
