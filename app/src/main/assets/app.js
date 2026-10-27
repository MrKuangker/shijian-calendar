/* 时笺 · No dependencies, no analytics, no remote UI. */
(function () {
  'use strict';
  const C = window.ShijianCore, $ = id => document.getElementById(id);
  const native = window.ShijianAndroid;
  const repo = 'MrKuangker/shijian-calendar', repoUrl = `https://github.com/${repo}`;
  const STORAGE = 'shijian.data.v1';
  const paths = {
    book:'<path d="M5 4h14v16H5zM8 2v4m8-4v4M8 10h8m-8 4h5m-5 3h8"/>',
    calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2m-8 3h2"/>',
    'calendar-search':'<rect x="3" y="5" width="17" height="16" rx="3"/><path d="M7 3v4m9-4v4M3 11h17m-5 5 2 2"/><circle cx="12" cy="15" r="3"/>',
    checklist:'<rect x="4" y="3" width="16" height="19" rx="3"/><path d="m7 9 1.5 1.5L11 8m3 1h3m-10 7 1.5 1.5L11 15m3 1h3"/>',
    settings:'<circle cx="12" cy="12" r="3"/><path d="m9 3-1 3-3 1-2 3 2 2-1 3 3 3 3-1 2 2 3-1 1-3 3-1 2-3-2-2 1-3-3-3-3 1-2-2z"/>',
    sprout:'<path d="M12 22v-9C12 5 5 4 2 5c0 7 4 10 10 8Zm0 4c0-8 6-12 10-11 0 7-4 11-10 11"/>',
    shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z"/><path d="m8 12 3 3 5-6"/>',
    'chevron-left':'<path d="m14 6-6 6 6 6"/>','chevron-right':'<path d="m9 6 6 6-6 6"/>',
    plus:'<path d="M12 5v14M5 12h14"/>',x:'<path d="m6 6 12 12M6 18 18 6"/>',check:'<path d="m5 12 4 4L19 6"/>',
    'arrow-right':'<path d="M4 12h16m-6-6 6 6-6 6"/>',search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    flag:'<path d="M5 21V3c5-3 9 3 14 0v10c-5 3-9-3-14 0"/>',bell:'<path d="M5 16h14l-2-3V9a5 5 0 0 0-10 0v4zM10 20h4M12 2v2"/>',
    trash:'<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1"/>',
    download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',upload:'<path d="M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5"/>',
    info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',refresh:'<path d="M20 4v6h-6M4 20v-6h6M5 9a7 7 0 0 1 12-4l3 5M4 14l3 5a7 7 0 0 0 12-4"/>',
    github:'<path d="M9 20c-4 1-4-2-6-2m12 4v-4c0-1 0-2-1-2 4 0 7-2 7-6 0-2-1-3-2-4 0-1 0-3-1-3l-4 2h-4L6 3c-1 0-1 2-1 3-1 1-2 2-2 4 0 4 3 6 7 6-1 1-1 2-1 3v3"/>',
    external:'<path d="M14 3h7v7M21 3l-11 11M10 3H3v18h18v-7"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>'
  };
  const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.book}</svg>`;
  const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  document.querySelectorAll('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon); });
  let data = { schemaVersion: 1, events: [], preferences: { weekStart: 'monday', theme: 'light' } };
  let storageBlocked = false, page = 'calendar', filter = 'pending', query = '', selected = C.dayKey(new Date());
  let year = +selected.slice(0,4), month = +selected.slice(5,7)-1, toastTimer, undoAction, editingSnapshot, checking = false, updateInfo = null;
  const media = matchMedia('(prefers-color-scheme: dark)');
  function toast(message, undo) {
    clearTimeout(toastTimer); undoAction = undo;
    $('toast').replaceChildren(document.createTextNode(message));
    if (undo) { const b = document.createElement('button'); b.textContent='撤销'; b.onclick=() => { const action=undoAction; undoAction=null; action?.(); $('toast').hidden=true; }; $('toast').append(b); }
    $('toast').hidden=false; toastTimer=setTimeout(() => { $('toast').hidden=true; undoAction=null; },undo ? 10000 : 4500);
  }
  function persist(next) {
    if (storageBlocked) { toast('本机数据读取异常，请先导出备份后重启应用'); return false; }
    try {
      const raw = JSON.stringify(next);
      if (raw.length > 8 * 1024 * 1024) throw new Error('记录容量已达上限，请先导出备份');
      if (native) { const response=JSON.parse(native.saveData(raw)); if (!response.ok) throw new Error(response.error || '保存失败'); }
      else localStorage.setItem(STORAGE,raw);
      data=next; applyTheme(); render(); return true;
    } catch(e) { toast(`未能保存：${e.message}`); return false; }
  }
  function load() {
    try {
      const raw = native ? native.loadData() : localStorage.getItem(STORAGE);
      if (raw) { const parsed=JSON.parse(raw); if(parsed.error) throw new Error(parsed.error); data=C.parseData(parsed); }
    } catch(e) { storageBlocked=true; const warning=document.createElement('div'); warning.className='storage-warning'; warning.textContent='记录读取异常。为保护已有数据，已暂停写入；请在「我的」导出备份后重启应用。'; document.querySelector('main').prepend(warning); }
  }
  function applyTheme() {
    let systemDark=media.matches;
    if(native) { try { systemDark=JSON.parse(native.appInfo()).systemDark; }catch(e){} }
    const dark=data.preferences.theme==='dark' || (data.preferences.theme==='system' && systemDark);
    document.documentElement.dataset.theme=dark?'dark':'light';
    document.querySelector('meta[name="theme-color"]').content=dark?'#171e1b':'#f6f7f2';
    if(native) native.setTheme(dark);
  }
  function switchPage(next) {
    page=next; document.querySelectorAll('.page').forEach(el => { el.hidden=el.id!==`page-${next}`; });
    document.querySelectorAll('[data-page]').forEach(el => { el.classList.toggle('active',el.dataset.page===next); if(el.dataset.page===next) el.setAttribute('aria-current','page'); else el.removeAttribute('aria-current'); });
    render(); window.scrollTo({top:0,behavior:'instant'});
  }
  function prettyDate(key,withWeek=false) { const d=new Date(key+'T12:00:00'); return `${d.getMonth()+1}月${d.getDate()}日${withWeek?' · '+['周日','周一','周二','周三','周四','周五','周六'][d.getDay()]:''}`; }
  function prettyTime(value) { return value ? `${+value.slice(5,7)}月${+value.slice(8,10)}日 ${value.slice(11,16)}` : ''; }
  function displayTitle(event) { return event.title || event.content.split('\n').find(s=>s.trim())?.slice(0,50) || '随手记'; }
  function card(event) {
    const isOverdue=C.overdue(event), priority=event.priority==='normal'?'':C.PRIORITIES[event.priority];
    return `<article class="event-card ${event.completed?'completed ':''}priority-${event.priority}" data-id="${esc(event.id)}"><button class="complete-button" data-action="complete" aria-label="${event.completed?'标为未完成':'标为完成'}：${esc(displayTitle(event))}" aria-pressed="${event.completed}">${event.completed?icon('check'):''}</button><button class="event-open" data-action="edit" aria-label="编辑：${esc(displayTitle(event))}"><div class="event-title">${esc(displayTitle(event))}</div>${event.content&&event.title?`<p class="event-snippet">${esc(event.content)}</p>`:''}<div class="event-meta"><span class="category-tag"><b class="dot ${event.category}"></b>${C.CATEGORIES[event.category]}</span>${event.time?`<span>${event.time}</span>`:''}${priority?`<span>${priority}</span>`:''}${event.deadline?`<span class="deadline ${isOverdue?'overdue':''}">${icon('flag')}${isOverdue?'已逾期 · ':''}${prettyTime(event.deadline)}</span>`:''}${event.reminderAt&&!event.completed?`<span class="deadline">${icon('bell')}${prettyTime(event.reminderAt)}</span>`:''}</div></button><i class="event-arrow">${icon('chevron-right')}</i></article>`;
  }
  function empty(kind) {
    const calendar=kind==='day';
    return `<div class="empty-state"><div class="empty-illustration"><svg viewBox="0 0 126 108" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><ellipse cx="63" cy="95" rx="45" ry="5" fill="currentColor" opacity=".04" stroke="none"/><rect x="30" y="20" width="62" height="69" rx="7" fill="var(--surface)" stroke="var(--line)"/><path d="M45 15v13m32-13v13M41 41h37M41 53h24M41 65h30" opacity=".3"/><path d="M80 86c-2-21 7-32 22-31 0 17-9 27-22 31Zm0 0 16-22" stroke="#99b08b"/><path d="M24 63c-10-1-16-9-14-19 12 0 16 8 14 19Z" stroke="#c3cdb6"/><path d="m20 52 8 25" stroke="#c3cdb6"/><circle cx="102" cy="26" r="3" stroke="#c8b58e"/><path d="M17 24v6m-3-3h6" stroke="#c8b58e"/></svg></div><h3>${calendar?'这一天，还留着空白':query?'没有找到相关事项':filter==='completed'?'完成的小事，会在这里相聚':filter==='overdue'?'没有逾期，心里轻一点':'此刻，可以从容一些'}</h3><p>${calendar?'一件小事，一段心情，或一个新的计划。':query?'换个关键词试试看。':'把想做的事写下来，给生活一点方向。'}</p>${calendar?'<button class="text-button" data-action="new">写下第一件事 '+icon('arrow-right')+'</button>':''}</div>`;
  }
  function renderCalendar() {
    $('month-title').textContent=`${year} 年 ${String(month+1).padStart(2,'0')} 月`;
    $('month-en').textContent=new Date(year,month,1).toLocaleString('en-US',{month:'long'}).toUpperCase()+` / ${year}`;
    const prefix=`${year}-${C.pad(month+1)}`, monthly=data.events.filter(e=>e.date.startsWith(prefix));
    const completed=monthly.filter(e=>e.completed).length;
    $('month-summary').innerHTML=`<div><strong>${monthly.length}</strong>本月事项</div><div><strong>${monthly.length-completed}</strong>待完成</div><div><strong>${completed}</strong>已完成</div>`;
    const weeks=data.preferences.weekStart==='monday'?['一','二','三','四','五','六','日']:['日','一','二','三','四','五','六'];
    $('weekdays').innerHTML=weeks.map(w=>`<span class="${w==='六'||w==='日'?'weekend':''}">${w}</span>`).join('');
    const today=C.dayKey(new Date()), byDay=new Map();
    data.events.forEach(e=> { if(!byDay.has(e.date)) byDay.set(e.date,[]); byDay.get(e.date).push(e); });
    $('month-grid').innerHTML=C.monthDays(year,month,data.preferences.weekStart).map(d=>{
      const events=byDay.get(d.key)||[], dots=events.slice().sort((a,b)=>+a.completed-+b.completed).slice(0,3);
      return `<button role="gridcell" class="day${d.inMonth?'':' outside'}${d.key===selected?' selected':''}${d.key===today?' today':''}" data-date="${d.key}" aria-pressed="${d.key===selected}" aria-label="${d.key}，${events.length}件事项${d.key===today?'，今天':''}"><span class="day-number">${d.day}</span><span class="dots">${dots.map(e=>`<b class="dot ${e.category}${e.completed?' completed':''}"></b>`).join('')}</span></button>`;
    }).join('');
    $('selected-weekday').textContent=`${selected===today?'今天 · ':''}${['星期日','星期一','星期二','星期三','星期四','星期五','星期六'][new Date(selected+'T12:00:00').getDay()]}`;
    $('selected-title').textContent=prettyDate(selected);
    const items=C.sortEvents(byDay.get(selected)||[]);
    $('day-events').innerHTML=items.length?`<p class="day-count">${items.length} 件事项 · ${items.filter(e=>!e.completed).length} 件待完成</p>`+items.map(card).join(''):empty('day');
  }
  function renderAgenda() {
    document.querySelectorAll('[data-filter]').forEach(el=>el.classList.toggle('active',el.dataset.filter===filter));
    const term=query.toLocaleLowerCase();
    const items=C.sortEvents(data.events.filter(e => (filter==='all'||filter==='pending'&&!e.completed||filter==='completed'&&e.completed||filter==='overdue'&&C.overdue(e))&&(!term||`${e.title}\n${e.content}`.toLocaleLowerCase().includes(term))));
    if(!items.length) { $('agenda-events').innerHTML=empty('agenda'); return; }
    const groups=new Map(); items.forEach(e=>{ const key=e.deadline?e.deadline.slice(0,10):e.date; if(!groups.has(key)) groups.set(key,[]); groups.get(key).push(e); });
    $('agenda-events').innerHTML=[...groups].map(([key,events])=>`<h3 class="agenda-group-label">${key.slice(0,4)} 年 ${prettyDate(key,true)}</h3>${events.map(card).join('')}`).join('');
  }
  function renderSettings() {
    $('week-start').value=data.preferences.weekStart; $('theme').value=data.preferences.theme;
    $('profile-count').textContent=data.events.length?`已记下 ${data.events.length} 件事，完成 ${data.events.filter(e=>e.completed).length} 件。`:'每一个平凡日子，都值得被记住。';
    try { $('current-version').textContent=native?JSON.parse(native.appInfo()).versionName:'1.0.0 · 网页预览'; }catch(e){}
  }
  function render() { renderCalendar(); renderAgenda(); renderSettings(); }
  function selectDay(key) { selected=key; year=+key.slice(0,4); month=+key.slice(5,7)-1; renderCalendar(); }
  function moveMonth(delta) {
    const next=new Date(year,month+delta,1,12);
    if(next.getFullYear()<1900 || next.getFullYear()>2199) return;
    next.setDate(Math.min(+selected.slice(8,10),new Date(next.getFullYear(),next.getMonth()+1,0).getDate())); selectDay(C.dayKey(next));
  }
  function modalOpened() { document.body.classList.add('modal-open'); }
  function modalClosed() { if(!document.querySelector('dialog[open]')) document.body.classList.remove('modal-open'); }
  function formValue() {
    return { id:$('event-id').value, title:$('event-title').value, content:$('event-content').value, date:$('event-date').value, time:$('event-time').value, category:$('event-category').value, priority:$('event-priority').value, deadline:$('event-deadline').value, reminderAt:$('event-reminder').value };
  }
  function openEditor(id) {
    if(storageBlocked) return toast('请先在「我的」导出备份，重启应用后再编辑');
    $('event-form').reset(); $('form-error').hidden=true;
    const event=id?data.events.find(e=>e.id===id):null;
    $('event-id').value=event?.id||''; $('event-date').value=event?.date||selected;
    for(const [field,key] of [['event-title','title'],['event-content','content'],['event-time','time'],['event-category','category'],['event-priority','priority'],['event-deadline','deadline'],['event-reminder','reminderAt']]) if(event) $(field).value=event[key];
    $('editor-date').textContent=prettyDate($('event-date').value,true); $('editor-title').textContent=event?'编辑这件事':'记一件事'; $('delete-event').hidden=!event;
    editingSnapshot=JSON.stringify(formValue()); $('event-dialog').showModal(); modalOpened();
    // Keep the date and context visible when opening on phones, without forcing a keyboard.
    $('event-dialog').scrollTop=0; document.querySelector('.close-editor').focus({preventScroll:true});
  }
  function ask(title,body,confirm='确认') {
    return new Promise(resolve=>{
      $('confirm-title').textContent=title; $('confirm-body').textContent=body; $('confirm-ok').textContent=confirm;
      $('confirm-dialog').showModal(); modalOpened();
      let done=false; const finish=v=>{ if(done)return; done=true; $('confirm-dialog').close(); modalClosed(); resolve(v); };
      $('confirm-ok').onclick=()=>finish(true); $('confirm-cancel').onclick=()=>finish(false); $('confirm-dialog').oncancel=e=>{e.preventDefault();finish(false);};
    });
  }
  async function closeEditor() {
    if(JSON.stringify(formValue())!==editingSnapshot && !await ask('放下这次编辑？','尚未保存的内容会丢失。','放下编辑')) return;
    $('event-dialog').close(); modalClosed();
  }
  function toggleComplete(id) {
    const event=data.events.find(e=>e.id===id); if(!event) return;
    if(persist({...data,events:data.events.map(e=>e.id===id?{...e,completed:!e.completed,updatedAt:Date.now()}:e)})) toast(event.completed?'已恢复为待完成':'又完成一件事，真好');
  }
  async function saveEvent(e) {
    e.preventDefault(); $('form-error').hidden=true;
    try {
      const raw=formValue(), old=data.events.find(item=>item.id===raw.id), now=Date.now();
      const event=C.normalizeEvent({...raw,createdAt:old?.createdAt||now,updatedAt:now,completed:old?.completed||false});
      if(event.reminderAt && !event.completed && +new Date(event.reminderAt)<=now && (!old || event.reminderAt!==old.reminderAt)) throw new Error('提醒时间需要晚于现在');
      const next={...data,events:old?data.events.map(item=>item.id===event.id?event:item):[...data.events,event]};
      if(persist(next)) { selectDay(event.date); $('event-dialog').close();modalClosed();toast('已收进时笺');if(event.reminderAt&&!event.completed&&native)native.requestNotifications(); }
    }catch(error){ $('form-error').textContent=error.message; $('form-error').hidden=false; }
  }
  async function deleteEvent() {
    const id=$('event-id').value, event=data.events.find(e=>e.id===id);
    if(!event || !await ask('删除这件事？',`「${displayTitle(event).slice(0,40)}」将从日历中移除。删除后可以撤销。`,'删除')) return;
    if(persist({...data,events:data.events.filter(e=>e.id!==id)})) { $('event-dialog').close();modalClosed();toast('事项已删除',()=>{ if(persist({...data,events:[...data.events,event]}))toast('已恢复这件事'); }); }
  }
  async function importData(raw) {
    try {
      if(storageBlocked) throw new Error('请先导出本机原始数据，并重启应用后重试');
      if(raw.length>8*1024*1024)throw new Error('备份文件过大');
      const imported=C.parseData(raw);
      if(!await ask('合并导入备份？',`备份中有 ${imported.events.length} 件事项。同编号的事项保留较新内容，其他现有记录会保留。`,'合并导入')) return;
      const next=C.mergeData(data,imported); if(persist(next))toast(`导入完成，现有 ${next.events.length} 件事项`);
    }catch(e){ toast(`导入失败：${e.message}`); }
  }
  function exportData() {
    if(native) { native.exportData(); return; }
    const raw=storageBlocked?localStorage.getItem(STORAGE):JSON.stringify({...data,exportedAt:new Date().toISOString()},null,2);
    if(!raw) return toast('没有可导出的数据');
    const url=URL.createObjectURL(new Blob([raw],{type:'application/json'})), a=document.createElement('a');a.href=url;a.download=`时笺备份-${C.dayKey(new Date())}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('备份已导出，请妥善保存');
  }
  function updateStatus(status) {
    const el=$('update-status');el.hidden=false;$('check-update').disabled=status.state==='checking'||status.state==='downloading';
    if(status.state==='checking'){el.textContent='正在向 GitHub 检查新版本…';checking=true;}
    else if(status.state==='latest'){el.textContent=`已是最新版本 ${status.versionName||''}。`;checking=false;}
    else if(status.state==='error'){el.textContent=status.message||'暂时无法检查更新，请稍后重试。';checking=false;}
    else if(status.state==='available'){
      updateInfo=status;checking=false;el.innerHTML=`<strong>有新版本 ${esc(status.versionName)}</strong><p>${esc(status.notes||'修复与体验改进')}</p><button class="primary-button" id="download-update">下载更新${icon('download')}</button>`;
      $('download-update').onclick=()=>{if(native)native.downloadUpdate();else window.open(status.url,'_blank','noopener');};
    }else if(status.state==='downloading'){
      el.innerHTML=`<strong>正在下载更新 · ${status.progress>=0?Number(status.progress)+'%':'连接中'}</strong><p>下载后会校验安装包，请保持应用打开。</p><progress ${status.progress>=0?`value="${Number(status.progress)}"`:''} max="100"></progress>`;
    }else if(status.state==='ready'){
      el.innerHTML=`<strong>新版本已准备好</strong><p>覆盖安装会保留日程。安卓系统可能需要你允许本应用安装更新。</p><button class="primary-button" id="install-update">安装更新${icon('arrow-right')}</button>`;$('install-update').onclick=()=>native.installUpdate();
    }
  }
  async function checkUpdate() {
    if(checking)return;updateStatus({state:'checking'});
    if(native){native.checkUpdate();return;}
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
    try { const response=await fetch(`https://api.github.com/repos/${repo}/releases/latest`,{signal:controller.signal,headers:{Accept:'application/vnd.github+json'}});if(!response.ok)throw new Error(response.status===404?'还没有发布版本，请稍后再试。':'GitHub 暂时不可用，请稍后再试。');const r=await response.json(),apk=r.assets.find(a=>a.name==='shijian.apk');if(!apk)throw new Error('最新版本尚无 APK 安装包');updateStatus({state:'available',versionName:r.tag_name,notes:r.body,url:apk.browser_download_url});}catch(e){updateStatus({state:'error',message:e.name==='AbortError'?'连接超时，请检查网络后重试。':e.message});}finally{clearTimeout(timer);}
  }
  window.ShijianApp={ updateStatus, importData, notify:toast, refreshTheme:applyTheme, openEvent:id=>{const event=data.events.find(e=>e.id===id);if(event){selectDay(event.date);switchPage('calendar');openEditor(id);}}, onBack:()=>{if($('confirm-dialog').open){$('confirm-cancel').click();return true;}if($('event-dialog').open){closeEditor();return true;}if($('date-dialog').open){$('date-dialog').close();modalClosed();return true;}if(page!=='calendar'){switchPage('calendar');return true;}return false;} };
  document.querySelectorAll('[data-page]').forEach(el=>el.onclick=()=>switchPage(el.dataset.page));
  document.querySelectorAll('.brand').forEach(el=>el.onclick=e=>{e.preventDefault();switchPage('calendar');});
  $('prev-month').onclick=()=>moveMonth(-1);$('next-month').onclick=()=>moveMonth(1);$('go-today').onclick=()=>selectDay(C.dayKey(new Date()));
  $('month-grid').onclick=e=>{const target=e.target.closest('[data-date]');if(target)selectDay(target.dataset.date);};
  $('add-event').onclick=() => openEditor();$('agenda-add').onclick=()=>openEditor();
  for(const id of ['day-events','agenda-events']) $(id).onclick=e=>{const action=e.target.closest('[data-action]');if(!action)return;if(action.dataset.action==='new')return openEditor();const id=action.closest('[data-id]')?.dataset.id;if(id)(action.dataset.action==='complete'?toggleComplete:openEditor)(id);};
  document.querySelectorAll('.close-editor').forEach(el=>el.onclick=closeEditor);$('event-dialog').oncancel=e=>{e.preventDefault();closeEditor();};
  $('event-form').onsubmit=saveEvent;$('delete-event').onclick=deleteEvent;
  $('event-date').onchange=()=>{if(C.isDate($('event-date').value))$('editor-date').textContent=prettyDate($('event-date').value,true);};
  $('open-date').onclick=()=>{$('jump-date').value=selected;$('date-dialog').showModal();modalOpened();};$('close-date').onclick=()=>{$('date-dialog').close();modalClosed();};$('date-dialog').onclose=modalClosed;
  $('jump-form').onsubmit=e=>{e.preventDefault();if(C.isDate($('jump-date').value)){selectDay($('jump-date').value);$('date-dialog').close();modalClosed();}};
  $('search').oninput=e=>{query=e.target.value.trim();renderAgenda();};document.querySelectorAll('[data-filter]').forEach(el=>el.onclick=()=>{filter=el.dataset.filter;renderAgenda();});
  $('week-start').onchange=e=>persist({...data,preferences:{...data.preferences,weekStart:e.target.value}});$('theme').onchange=e=>persist({...data,preferences:{...data.preferences,theme:e.target.value}});media.addEventListener('change',applyTheme);
  $('export-data').onclick=exportData;$('import-data').onclick=()=>native?native.importData():$('import-file').click();
  $('import-file').onchange=async e=>{const file=e.target.files[0];if(!file)return;if(file.size>8*1024*1024)toast('备份文件超过 8 MB 上限');else await importData(await file.text());e.target.value='';};
  $('check-update').onclick=checkUpdate;$('open-repo').onclick=()=>native?native.openRepository():window.open(repoUrl,'_blank','noopener');
  $('notification-settings').onclick=()=>native?native.notificationSettings():toast('提醒功能在安卓安装版中可用');
  load();applyTheme();render();
  setInterval(()=>{if(!document.querySelector('dialog[open]')){renderCalendar();renderAgenda();}},60000);
})();
