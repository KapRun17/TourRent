/* Run with Node >= 20 on host, after docker compose up -d --wait.
 * Performs real integration checks against the isolated tourrent Compose project.
 */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),evidence=path.join(root,'evidence');fs.mkdirSync(evidence,{recursive:true});
const base=process.env.BASE_URL||'http://localhost:8078',auth=process.env.AUTH_URL||'http://localhost:8180';
const wait=ms=>new Promise(r=>setTimeout(r,ms));const checks=[];let token;
function dc(...args){return execFileSync('docker',['compose',...args],{cwd:root,encoding:'utf8',timeout:90000,stdio:['pipe','pipe','pipe']}).trim();}
function sql(service,db,query){return dc('exec','-T',service,'psql','-U','tourrent','-d',db,'-Atc',query);}
function pass(name,detail){checks.push({name,result:'PASS',detail});console.log('PASS '+name);fs.writeFileSync(path.join(evidence,'verification.json'),JSON.stringify({at:new Date().toISOString(),checks},null,2));}
async function request(method,route,body,headers={}){
 const started=Date.now();const r=await fetch(base+route,{method,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json',...headers},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(12000)});
 const text=await r.text();let data;try{data=JSON.parse(text)}catch{data=text}
 return {status:r.status,data,ms:Date.now()-started,headers:Object.fromEntries(r.headers)};
}
async function until(fn,description,timeout=70000){const start=Date.now();let last;while(Date.now()-start<timeout){try{last=await fn();if(last)return last}catch{}await wait(1000)}throw Error('Timeout: '+description+' '+JSON.stringify(last));}
function dates(offset=10){let d=new Date();d.setDate(d.getDate()+offset);const start=d.toISOString().slice(0,10);d.setDate(d.getDate()+3);return [start,d.toISOString().slice(0,10)];}
function booking(equipmentId=1,quantity=1,offset=10){const [startDate,endDate]=dates(offset);return {equipmentId,quantity,startDate,endDate,recipient:'Прокофьев И.Р.',address:'Москва, проспект Вернадского, д. 78'};}
async function getToken(){
 const r=await fetch(auth+'/realms/tourrent/protocol/openid-connect/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'password',client_id:'tourrent-web',username:'demo',password:'Demo12345!'}),signal:AbortSignal.timeout(5000)});
 const body=await r.json();if(!r.ok)throw Error('OAuth not ready');return body.access_token;
}
(async()=>{
 token=await until(getToken,'Keycloak',180000);pass('OAuth2 token issued',{issuer:auth+'/realms/tourrent'});
 const noAuth=await fetch(base+'/api/rentals');assert.equal(noAuth.status,401);pass('Unauthenticated API request rejected',401);
 const invalid=await fetch(base+'/api/rentals',{headers:{Authorization:'Bearer invalid'}});assert.equal(invalid.status,401);pass('Invalid JWT rejected',401);
 await until(async()=>{const r=await request('GET','/api/equipment');return r.status===200&&r.data.length===6},'Catalog via Gateway',120000);
 const instances=new Set();for(let i=0;i<12;i++){const r=await request('GET','/api/equipment');assert.equal(r.status,200);instances.add(r.headers['x-service-instance']);}
 assert.equal(instances.size,2);pass('Gateway balances between two discovered catalog instances',[...instances]);
 const bad=await request('POST','/api/rentals',{...booking(),quantity:0});assert.equal(bad.status,400);pass('Invalid booking rejected',400);
 const rid='pr8-'+Date.now();const created=await request('POST','/api/rentals',booking(),{'X-Request-ID':rid,'X-User-ID':'spoofed-user'});assert.equal(created.status,201);assert.equal(Number(created.data.total),2070);
 const id=created.data.id;fs.writeFileSync(path.join(evidence,'scenario.json'),JSON.stringify({id,requestId:rid,created:created.data},null,2));pass('Rental creation, price and correlation',{id,total:created.data.total,requestId:created.headers['x-request-id']});
 assert.notEqual(created.data.owner_id,'spoofed-user');pass('Gateway overwrites spoofed owner header',true);
 const streamController=new AbortController();
 const streamPromise=(async()=>{const res=await fetch(base+`/api/rentals/${id}/events`,{headers:{Authorization:'Bearer '+token,Accept:'text/event-stream'},signal:streamController.signal});assert.equal(res.status,200);const reader=res.body.getReader();let result='',decoder=new TextDecoder();while(true){const x=await reader.read();if(x.done)break;result+=decoder.decode(x.value,{stream:true});if(result.includes('event:DELIVERED'))break;}await reader.cancel();return result;})();
 const done=await until(async()=>{let r=await request('GET',`/api/rentals/${id}`);return r.data.status==='DELIVERED'?r.data:null},'Delivery completion');
 const events=await streamPromise;fs.writeFileSync(path.join(evidence,'sse.txt'),events);for(const s of ['CONFIRMED','DELIVERY_CREATED','IN_TRANSIT','DELIVERED'])assert.ok(events.includes('event:'+s));pass('SSE receives outbound status sequence',['CONFIRMED','DELIVERY_CREATED','IN_TRANSIT','DELIVERED']);
 const lines=events.split('\n').filter(x=>x.startsWith('id:'));const last=Number(lines.at(-1).slice(3));
 const replay=await fetch(base+`/api/rentals/${id}/events`,{headers:{Authorization:'Bearer '+token,'Last-Event-ID':String(last-1)},signal:AbortSignal.timeout(5000)});const reader=replay.body.getReader();let chunk='';while(!chunk.includes('\n\n')){const r=await reader.read();chunk+=new TextDecoder().decode(r.value)}await reader.cancel();assert.ok(chunk.includes('event:DELIVERED'));assert.ok(!chunk.includes('event:CONFIRMED'));pass('SSE resumes after Last-Event-ID',{last});
 const eventRaw=sql('rentals-db','rentals',`select payload from outbox where routing_key='rental.confirmed' and payload::jsonb->>'rentalId'='${id}' limit 1`);
 async function publish(raw){const r=await fetch('http://localhost:15678/api/exchanges/%2F/tourrent.events/publish',{method:'POST',headers:{Authorization:'Basic '+Buffer.from('tourrent:tourrent-demo').toString('base64'),'Content-Type':'application/json'},body:JSON.stringify({properties:{delivery_mode:2},routing_key:'rental.confirmed',payload:raw,payload_encoding:'string'})});assert.equal(r.status,200);}
 await publish(eventRaw);await publish(eventRaw);await wait(2000);assert.equal(sql('logistics-db','logistics',`select count(*) from deliveries where rental_id='${id}'`),'1');pass('Repeated RabbitMQ event creates exactly one delivery',{duplicates:2,deliveries:1});
 await request('POST',`/api/rentals/${id}/return`);await until(async()=>{const r=await request('GET',`/api/rentals/${id}`);return r.data.status==='COMPLETED'},'Return completion');pass('Return route reaches COMPLETED',true);
 await until(()=>sql('catalog-db','catalog',`select count(*) from reservations where rental_id='${id}'`)==='0','Reservation release');pass('Completed rental releases catalog reservation',true);
 const concurrent=await Promise.all([request('POST','/api/rentals',booking(5,1,20)),request('POST','/api/rentals',booking(5,1,20))]);assert.deepEqual(concurrent.map(x=>x.status).sort(),[201,409]);pass('Concurrent booking cannot exceed stock',[201,409]);
 const winner=concurrent.find(x=>x.status===201);const cancel=await request('POST',`/api/rentals/${winner.data.id}/cancel`);assert.equal(cancel.status,200);pass('Early rental can be cancelled',cancel.data.status);
 await until(()=>sql('catalog-db','catalog',`select count(*) from reservations where rental_id='${winner.data.id}'`)==='0','Cancelled reservation release');
 const trace=dc('logs','--no-color','api-gateway','rental-service','catalog-service','catalog-service-2','logistics-service').split('\n').filter(x=>x.includes(rid)).join('\n');fs.writeFileSync(path.join(evidence,'request-trace.log'),trace);assert.ok(trace.includes('RESERVED'));assert.ok(trace.includes('CONSUME'));assert.ok(trace.includes('POST /api/rentals'));pass('One requestId traces Gateway, rental, catalog and RabbitMQ consumers',rid);
 fs.writeFileSync(path.join(evidence,'containers.txt'),dc('ps'));
 const config=await(await fetch('http://localhost:8888/rental-service/default/main')).json();assert.ok(config.version);assert.ok(config.propertySources.some(x=>x.name.includes('/config-repository')));pass('Config Server uses a committed Git repository',{version:config.version});
 console.log('Starting catalog outage check');
 dc('stop','catalog-service','catalog-service-2');
 try{
  const failures=[];for(let i=0;i<3;i++)failures.push(await request('POST','/api/rentals',booking(2,1,25)));
  for(const r of failures){assert.equal(r.status,503);assert.ok(r.ms<10000);}
  const health=await request('GET','/api/system');assert.equal(health.data.diagnostics.circuitBreaker,'OPEN');
  fs.writeFileSync(path.join(evidence,'outage.json'),JSON.stringify({failures:failures.map(x=>({status:x.status,ms:x.ms,body:x.data})),system:health.data},null,2));pass('Catalog outage produces bounded 503 and opens circuit',failures.map(x=>({status:x.status,ms:x.ms})));
 }finally{dc('start','catalog-service','catalog-service-2');}
 await until(async()=>{const r=await request('GET','/api/equipment');return r.status===200},'Catalog recovery',120000);await wait(9000);
 const recovered=await request('POST','/api/rentals',booking(4,1,25));assert.equal(recovered.status,201);pass('Creation recovers after catalog restart',201);
 await request('POST',`/api/rentals/${recovered.data.id}/cancel`);
 const final=await request('GET','/api/system');fs.writeFileSync(path.join(evidence,'system.json'),JSON.stringify(final.data,null,2));
 console.log('VERIFIED '+checks.length+' integration checks');
})().catch(error=>{console.error(error);checks.push({name:'FAILED',result:'FAIL',error:error.message});fs.writeFileSync(path.join(evidence,'verification.json'),JSON.stringify({at:new Date().toISOString(),checks},null,2));process.exitCode=1;});
