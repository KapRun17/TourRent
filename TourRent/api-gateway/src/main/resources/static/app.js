/* No external CDN: OAuth2 Authorization Code + PKCE, browser fetch-based SSE. */
const $=s=>document.querySelector(s),content=$('#content');
const state={items:[],rentals:[],category:'Все снаряжение',search:'',selected:null,events:[],stream:null,config:null,token:sessionStorage.getItem('access_token')};
const labels={CONFIRMED:'Аренда подтверждена',DELIVERY_CREATED:'Доставка оформлена',IN_TRANSIT:'Курьер в пути',DELIVERED:'Снаряжение у вас',RETURN_REQUESTED:'Возврат оформлен',RETURN_IN_TRANSIT:'Возвращается на склад',COMPLETED:'Аренда завершена',CANCELLED:'Аренда отменена'};
const money=n=>new Intl.NumberFormat('ru-RU',{maximumFractionDigits:0}).format(n)+' ₽';
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const date=s=>new Date(s).toLocaleDateString('ru-RU',{day:'numeric',month:'short'});
const today=(offset=0)=>{let d=new Date();d.setDate(d.getDate()+offset);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')};
function notice(text){$('#notice').hidden=!text;$('#notice').textContent=text||'';}
function claim(){try{return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(state.token.split('.')[1].replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0))))}catch{return {}}}
function authenticated(){if(!state.token)return false;if(claim().exp*1000<Date.now()){state.token=null;sessionStorage.removeItem('access_token');return false}return true;}
function account(){const ok=authenticated();$('#account').textContent=ok?('◉ '+(claim().preferred_username||'Мой аккаунт')+' · выйти'):'Войти в аккаунт ↗';}
async function api(path,options={}){
 const response=await fetch(path,{...options,headers:{'Content-Type':'application/json',...(state.token?{Authorization:'Bearer '+state.token}:{}),...options.headers}});
 const raw=await response.text();let body;try{body=JSON.parse(raw)}catch{body={detail:raw}}
 if(!response.ok){let message=body.detail||'Сервис временно недоступен. Повторите запрос позже';if(response.status===401)message='Войдите в аккаунт, чтобы продолжить.';throw Object.assign(new Error(message),{status:response.status,requestId:response.headers.get('X-Request-ID')});}
 return body;
}
function encode(bytes){return btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');}
async function login(){
 const verifier=encode(crypto.getRandomValues(new Uint8Array(48))),nonce=encode(crypto.getRandomValues(new Uint8Array(24)));
 sessionStorage.setItem('pkce_verifier',verifier);sessionStorage.setItem('oauth_state',nonce);sessionStorage.setItem('after_login',location.hash||'#catalog');
 const challenge=encode(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier))));
 const q=new URLSearchParams({client_id:state.config.clientId,response_type:'code',scope:'openid profile',redirect_uri:location.origin+'/',state:nonce,code_challenge:challenge,code_challenge_method:'S256'});
 location.assign(state.config.authUrl+'/realms/'+state.config.realm+'/protocol/openid-connect/auth?'+q);
}
async function callback(){
 const q=new URLSearchParams(location.search);if(!q.has('code'))return;
 if(q.get('state')!==sessionStorage.getItem('oauth_state'))throw new Error('Не удалось проверить ответ входа. Войдите ещё раз.');
 const response=await fetch(state.config.authUrl+'/realms/'+state.config.realm+'/protocol/openid-connect/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:state.config.clientId,code:q.get('code'),redirect_uri:location.origin+'/',code_verifier:sessionStorage.getItem('pkce_verifier')})});
 const tokens=await response.json();if(!response.ok)throw new Error('Не удалось завершить вход');
 state.token=tokens.access_token;sessionStorage.setItem('access_token',state.token);sessionStorage.setItem('id_token',tokens.id_token||'');sessionStorage.removeItem('pkce_verifier');sessionStorage.removeItem('oauth_state');
 history.replaceState({},'',location.pathname+(sessionStorage.getItem('after_login')||'#catalog'));
}
$('#account').onclick=()=>{
 if(!authenticated()){login();return;}
 const hint=sessionStorage.getItem('id_token');state.token=null;sessionStorage.clear();
 const q=new URLSearchParams({client_id:state.config.clientId,post_logout_redirect_uri:location.origin+'/'});if(hint)q.set('id_token_hint',hint);
 location.assign(state.config.authUrl+'/realms/tourrent/protocol/openid-connect/logout?'+q);
};
function illustration(kind){
 const common='<ellipse cx="115" cy="119" rx="75" ry="9" fill="#a4b39a" opacity=".3"/>';
 const drawings={
 tent:'<path d="M26 111 104 28l89 83Z" fill="#718a67"/><path d="M104 28v83H50Z" fill="#b0ba89"/><path d="m104 40 48 71h-48Z" fill="#284c3b"/><path d="m97 48-37 63h37Z" fill="#d7d3a9"/><path d="m26 111-12 8m179-8 13 8M104 28l7-15" stroke="#6d775f" stroke-width="2"/><path d="m104 29 24 6-19-14Z" fill="#d9a369"/>',
 sleeping:'<path d="M80 30c0-18 63-20 65 0l9 74c2 23-80 23-78 0Z" fill="#be8d63"/><ellipse cx="112" cy="30" rx="31" ry="17" fill="#755b47"/><ellipse cx="112" cy="30" rx="22" ry="11" fill="#dfc3a1"/><path d="m83 57 62 0m-65 17h68m-69 18h72m-69 18h66" stroke="#a27653" fill="none" stroke-width="2"/><path d="m139 44 5 66" stroke="#e4c8a4" stroke-width="3"/>',
 backpack:'<path d="M82 28q30-20 60 0l9 81q-38 24-79 0Z" fill="#657e69"/><rect x="85" y="23" width="54" height="25" rx="9" fill="#3e5c49"/><rect x="86" y="67" width="53" height="43" rx="8" fill="#91a089"/><path d="M95 29v30m31-30v30m-34 26h38" stroke="#c5b38b" stroke-width="4"/><rect x="66" y="62" width="13" height="39" rx="5" fill="#486453"/><rect x="145" y="61" width="12" height="38" rx="5" fill="#486453"/>',
 stove:'<ellipse cx="113" cy="104" rx="35" ry="17" fill="#bd9d71"/><path d="M78 82v22c0 20 71 20 70 0V82Z" fill="#c8aa79"/><ellipse cx="113" cy="82" rx="35" ry="16" fill="#d5bf9a"/><path d="M112 46v28m-26-26 27 13 28-13" stroke="#5b6c5b" stroke-width="5" fill="none"/><path d="m105 43 9-21 7 21" fill="#d7a067"/><path d="M84 78 69 65m73 13 14-13" stroke="#526454" stroke-width="4"/>',
 mat:'<path d="m70 36 64-12 31 74-68 16Z" fill="#9ba78b"/><path d="m78 47 61-12m-56 25 61-12m-55 25 61-12m-55 25 61-12m-56 25 61-12" stroke="#788a6e" stroke-width="3"/><ellipse cx="130" cy="106" rx="38" ry="17" fill="#526f56"/><ellipse cx="130" cy="106" rx="27" ry="11" fill="#acb699"/><ellipse cx="130" cy="106" rx="13" ry="5" fill="#6d8368"/>'};
 return '<svg aria-hidden="true" viewBox="0 0 230 140">'+common+(drawings[kind]||drawings.tent)+'</svg>';
}
function hero(){return `<div class="hero"><div><div class="eyebrow">ВРЕМЯ ВЫЙТИ ЗА ГОРОД</div><h1>Снаряжение для<br>вашего маршрута.</h1><p>Надёжные вещи для больших и маленьких приключений.<br>Выберите даты — остальное мы доставим.</p><span class="hero-tag">↟ Проверено перед каждым походом</span></div><svg class="hero-art" viewBox="0 0 380 240"><circle cx="286" cy="66" r="30" fill="#e6ac6c"/><path d="m0 240 147-192 147 192Z" fill="#486c51"/><path d="m126 77 21-29 25 33-22-10-12 13Z" fill="#d4dac5"/><path d="m111 240 124-150 145 150Z" fill="#6e8863"/><path d="m0 215 180-56 200 51v30H0Z" fill="#284d3b"/><path d="m260 240 34-93 33 93m-21-8 31-77 32 77" fill="#173d30"/><path d="m65 239 39-75 48 75Z" fill="#dcad73"/><path d="m104 175 24 64h-24Z" fill="#9c754c"/><path d="m106 199 15 40h-15Z" fill="#244637"/></svg></div>`;}
function requireLogin(){content.innerHTML=hero()+`<div class="empty"><h2>Ваш следующий поход — здесь</h2><p>Войдите, чтобы посмотреть каталог, забронировать снаряжение и отслеживать доставку.</p><button class="primary" id="login-main">Войти в TourRent ↗</button></div><div class="demo-note">Учебный аккаунт: <code>demo</code> · пароль <code>Demo12345!</code></div>`;$('#login-main').onclick=login;}
function renderProducts(){
 const filtered=state.items.filter(x=>(state.category==='Все снаряжение'||x.category===state.category)&&x.name.toLowerCase().includes(state.search.toLowerCase()));
 $('#products').innerHTML=filtered.map(x=>`<article class="product"><div class="product-art"><span class="stock">${x.stock} шт. в прокате</span>${illustration(x.illustration)}</div><div class="product-body"><div class="product-category">${esc(x.category)}</div><h3>${esc(x.name)}</h3><p>${esc(x.description)}</p><div class="product-bottom"><span class="price">${money(x.dailyPrice)} <small>/ день</small></span><button class="choose" data-book="${x.id}">Выбрать даты ↗</button></div></div></article>`).join('')||'<div class="empty">Ничего не найдено. Попробуйте другой запрос.</div>';
 document.querySelectorAll('[data-book]').forEach(b=>b.onclick=()=>openBooking(Number(b.dataset.book)));
}
async function catalog(){
 if(!authenticated()){requireLogin();return;}
 try{state.items=await api('/api/equipment');content.innerHTML=hero()+`<div class="heading-row"><div><h2>Соберите свой комплект</h2><p>Для выходных на природе и новых вершин</p></div><input class="search" id="search" placeholder="⌕  Найти снаряжение" aria-label="Поиск снаряжения"></div><div class="categories">${['Все снаряжение',...new Set(state.items.map(x=>x.category))].map(c=>`<button class="category ${state.category===c?'active':''}" data-category="${esc(c)}">${esc(c)}</button>`).join('')}</div><div id="products" class="products"></div>`;
  $('#search').value=state.search;$('#search').oninput=e=>{state.search=e.target.value;renderProducts()};document.querySelectorAll('[data-category]').forEach(b=>b.onclick=()=>{state.category=b.dataset.category;document.querySelectorAll('.category').forEach(x=>x.classList.toggle('active',x===b));renderProducts()});renderProducts();
 }catch(e){notice(e.message);content.innerHTML='<div class="empty"><h2>Каталог временно недоступен</h2><p>Попробуйте обновить страницу через несколько секунд.</p></div>';}
}
let bookingItem;
function openBooking(id){bookingItem=state.items.find(x=>x.id===id);$('#booking-title').textContent=bookingItem.name;$('#start').min=today();$('#start').value=today(3);$('#end').min=today(1);$('#end').value=today(6);$('#quantity').value=1;$('#form-error').textContent='';$('#confirm').disabled=false;total();$('#booking').showModal();}
function total(){if(!bookingItem)return;const days=(new Date($('#end').value)-new Date($('#start').value))/86400000;$('#total').textContent=days>0?money(days*Number($('#quantity').value)*bookingItem.dailyPrice):'—';}
['#start','#end','#quantity'].forEach(x=>$(x).oninput=total);$('.modal-close').onclick=()=>$('#booking').close();
$('#booking-form').onsubmit=async e=>{e.preventDefault();$('#confirm').disabled=true;$('#form-error').textContent='';try{const rental=await api('/api/rentals',{method:'POST',body:JSON.stringify({equipmentId:bookingItem.id,startDate:$('#start').value,endDate:$('#end').value,quantity:Number($('#quantity').value),recipient:$('#recipient').value.trim(),address:$('#address').value.trim()})});state.selected=rental.id;$('#booking').close();notice('Аренда подтверждена. Мы готовим снаряжение к доставке.');if(location.hash==='#rentals')await rentals();else location.hash='#rentals';}catch(error){$('#form-error').textContent=error.message+(error.requestId?' · Код обращения: '+error.requestId:'');}finally{$('#confirm').disabled=false;}};
function pill(status){return `<span class="pill ${['IN_TRANSIT','RETURN_IN_TRANSIT'].includes(status)?'moving':['DELIVERED','COMPLETED'].includes(status)?'done':''}">${labels[status]||esc(status)}</span>`;}
async function rentals(){
 if(!authenticated()){requireLogin();return;}
 try{state.rentals=await api('/api/rentals');$('#rental-count').textContent=state.rentals.filter(r=>!['COMPLETED','CANCELLED'].includes(r.status)).length;
 if(!state.rentals.some(r=>r.id===state.selected))state.selected=state.rentals[0]?.id;
 if(!state.rentals.length){content.innerHTML='<div class="page-header"><div class="eyebrow">ВАШИ ПЛАНЫ</div><h1>Моя аренда</h1></div><div class="empty"><h2>Пока без рюкзака?</h2><p>Выберите снаряжение в каталоге. Здесь появятся ваша аренда и статусы доставки.</p><a class="primary" href="#catalog">Перейти в каталог →</a></div>';return;}
 renderRentals();connectStream();
 }catch(e){notice(e.message);}
}
function renderRentals(){
 const active=state.rentals.filter(r=>!['COMPLETED','CANCELLED'].includes(r.status));
 content.innerHTML=`<div class="page-header"><div class="eyebrow">ВСЁ ГОТОВО К ПРИКЛЮЧЕНИЮ</div><h1>Моя аренда</h1><p>Ваши бронирования, доставка и возвращение снаряжения</p></div><div class="stats"><div class="stat"><span>Активных бронирований</span><strong>${active.length}</strong></div><div class="stat"><span>Всего оформлено</span><strong>${state.rentals.length}</strong></div><div class="stat"><span>Завершено поездок</span><strong>${state.rentals.filter(r=>r.status==='COMPLETED').length}</strong></div></div><div class="rental-grid"><div>${state.rentals.map(r=>`<article class="rental-card ${state.selected===r.id?'selected':''}" data-rental="${r.id}"><div class="row"><h3>${esc(r.equipment_name)}</h3>${pill(r.status)}</div><div class="meta">${date(r.start_date)} — ${date(r.end_date)} · ${r.quantity} шт.<br>${esc(r.recipient)}<br>⌖ ${esc(r.address)}</div><div class="row"><strong>${money(r.total)}</strong><span class="small muted">№ ${r.id.slice(0,8).toUpperCase()}</span></div><div class="rental-actions"><button class="secondary" data-track="${r.id}">Отследить доставку</button>${r.status==='DELIVERED'?`<button class="primary" data-action="return" data-id="${r.id}">Оформить возврат ↗</button>`:''}${['CONFIRMED','DELIVERY_CREATED'].includes(r.status)?`<button class="secondary danger" data-action="cancel" data-id="${r.id}">Отменить</button>`:''}</div></article>`).join('')}</div><aside><div class="panel"><div class="row"><h3>Путь вашего снаряжения</h3><span class="status-heading"><i class="live-dot"></i>В реальном времени</span></div><ul class="timeline" id="timeline"></ul><div class="trace">Код обращения: ${esc(state.rentals.find(r=>r.id===state.selected)?.request_id||'')}</div></div><div class="panel"><h3>После поездки</h3><p class="muted">Когда снаряжение будет у вас, нажмите «Оформить возврат». Курьер заберёт комплект по адресу доставки.</p><a class="secondary" href="#catalog">Подобрать ещё снаряжение ↗</a></div></aside></div>`;
 drawTimeline();document.querySelectorAll('[data-track]').forEach(b=>b.onclick=()=>{state.selected=b.dataset.track;state.events=[];renderRentals();connectStream()});document.querySelectorAll('[data-action]').forEach(b=>b.onclick=async()=>{b.disabled=true;try{await api(`/api/rentals/${b.dataset.id}/${b.dataset.action}`,{method:'POST'});await rentals();}catch(e){notice(e.message);b.disabled=false;}});
}
function drawTimeline(){const target=$('#timeline');if(!target)return;target.innerHTML=state.events.length?state.events.map(e=>`<li><strong>${esc(labels[e.status]||e.status)}</strong><time>${new Date(e.occurred_at).toLocaleString('ru-RU')}</time></li>`).join(''):'<li><strong>Подключаемся к обновлениям…</strong></li>';}
let streamId=null;
async function connectStream(){
 if(!state.selected)return;if(streamId===state.selected&&state.stream)return;
 state.stream?.abort();state.stream=new AbortController();streamId=state.selected;state.events=[];
 try{
  const res=await fetch(`/api/rentals/${state.selected}/events`,{headers:{Authorization:'Bearer '+state.token,Accept:'text/event-stream'},signal:state.stream.signal});if(!res.ok)throw Error('Не удалось подключиться к обновлениям');
  const reader=res.body.getReader(),decoder=new TextDecoder();let buffer='';
  while(true){const {done,value}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true}).replaceAll('\r\n','\n');let index;while((index=buffer.indexOf('\n\n'))>=0){const block=buffer.slice(0,index);buffer=buffer.slice(index+2);const raw=block.split('\n').filter(l=>l.startsWith('data:')).map(l=>l.slice(5)).join('\n');if(!raw)continue;const event=JSON.parse(raw);if(!state.events.some(e=>e.sequence===event.sequence))state.events.push(event);const rental=state.rentals.find(r=>r.id===streamId);if(rental)rental.status=event.status;drawTimeline();if(location.hash==='#rentals')renderRentals();}}
 }catch(e){if(e.name!=='AbortError')notice('Обновления временно недоступны. Перейдите в каталог и откройте аренду снова.');}finally{state.stream=null;}
}
async function system(){
 if(!authenticated()){requireLogin();return;}
 content.innerHTML='<div class="loading">Проверяем доступность компонентов…</div>';
 try{const data=await api('/api/system');content.innerHTML=`<div class="heading-row"><div class="page-header"><div class="eyebrow">ПРОЗРАЧНОСТЬ РАБОТЫ</div><h1>Состояние системы</h1><p>Доступность сервисов и инфраструктура приложения</p></div><button class="secondary" id="refresh-system">Обновить ↻</button></div><div class="service-grid">${data.services.map(s=>`<article class="service-card"><span class="${s.status==='UP'?'up':'down'}">● ${s.status==='UP'?'Доступен':'Недоступен'}</span><h3>${{'catalog-service':'Каталог снаряжения','rental-service':'Сервис аренды','logistics-service':'Сервис логистики'}[s.name]}</h3><p class="small muted">${esc(s.name)}</p><div class="small muted">Экземпляров: ${s.instances.length}</div>${s.instances.map(i=>`<code>${esc(i)}</code>`).join('')}</article>`).join('')}</div><div class="system-details"><section class="panel"><h3>Взаимодействие компонентов</h3><div class="system-line">Вход в аккаунт<span>${esc(data.auth)}</span></div><div class="system-line">Обмен событиями<span>${esc(data.broker)}</span></div><div class="system-line">Конфигурация<span>Git · Config Server</span></div><div class="system-line">Маршрутизация<span>Gateway · Eureka · LoadBalancer</span></div><div class="system-line">Обновления статусов<span>WebFlux · SSE</span></div></section><section class="panel"><h3>Защита от отказов</h3><div class="system-line">Circuit Breaker каталога<span>${esc(data.diagnostics.circuitBreaker)}</span></div><div class="system-line">Событий ожидают отправки<span>${esc(data.diagnostics.pendingOutbox??'—')}</span></div><p class="muted">При недоступности каталога оформление аренды завершится понятной ошибкой. Уже оформленные доставки продолжают работать.</p></section></div>`;$('#refresh-system').onclick=system;}catch(e){notice(e.message);content.innerHTML='<div class="empty">Не удалось получить состояние системы</div>';}
}
async function route(){const current=location.hash.slice(1)||'catalog';account();document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===current));$('#page-name').textContent={catalog:'Каталог',rentals:'Моя аренда',system:'Состояние системы'}[current]||'Каталог';if(current!=='rentals'){state.stream?.abort();state.stream=null;streamId=null;}await ({catalog,rentals,system}[current]||catalog)();}
window.addEventListener('hashchange',route);
(async()=>{try{state.config=await(await fetch('/ui-config')).json();await callback();await route();}catch(e){notice(e.message);}})();
