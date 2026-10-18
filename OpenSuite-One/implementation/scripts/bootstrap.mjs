import { createApplication, createOrg } from '../apps/api/server.mjs';
import { openDatabase } from '../apps/api/db.mjs';
const [workspace,name,email] = process.argv.slice(2);
const password = process.env.OPENSUITE_BOOTSTRAP_PASSWORD;
if (!workspace || !name || !email || !password) {
  console.error('Usage: OPENSUITE_BOOTSTRAP_PASSWORD=<secure-12+character-password> npm run bootstrap -- "Workspace" "Full Name" admin@example.com');
  process.exit(2);
}
const db=openDatabase();
try { const {tenantId}=createOrg(db,{workspace,name,email,password}); console.log('Created owner account and workspace:',tenantId); }
catch(err){console.error('Bootstrap failed:',err.message);process.exitCode=1;}
finally{db.close();}
