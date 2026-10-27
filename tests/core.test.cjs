const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../app/src/main/assets/core.js');
const event = overrides => C.normalizeEvent({date:'2026-10-27',title:'论文计划',...overrides});
const data = events => ({schemaVersion:1,events,preferences:{weekStart:'monday',theme:'light'}});

test('month navigation spans year/leap boundaries and supports both week starts',()=>{
  const feb=C.monthDays(2024,1); assert.equal(feb.length,42);assert.equal(feb[0].key,'2024-01-29');assert.ok(feb.some(d=>d.key==='2024-02-29'&&d.inMonth));
  assert.equal(C.monthDays(2026,10,'sunday')[0].key,'2026-11-01');
  assert.equal(C.monthDays(2027,0)[0].key,'2026-12-28');
});
test('rejects impossible calendar dates and invalid times',()=>{
  assert.equal(C.isDate('2026-02-29'),false);assert.equal(C.isDate('2024-02-29'),true);
  for(const date of ['2026-13-01','2026-04-31','1899-01-01','2200-01-01'])assert.equal(C.isDate(date),false);
  for(const deadline of ['2026-02-30T12:00','2026-10-27T24:00','2026-10-27T12:61'])assert.throws(()=>event({deadline}));
});
test('title and body are optional separately but never both blank',()=>{
  assert.equal(event({title:'',content:'只记录内容'}).title,'');assert.equal(event({content:''}).content,'');
  assert.throws(()=>event({title:'  ',content:'\n'}));assert.equal(event({title:' A '}).title,'A');
});
test('backup import validates every item before any merge',()=>{
  assert.throws(()=>C.parseData({events:[]}));assert.throws(()=>C.parseData({...data([]),schemaVersion:2}));
  assert.throws(()=>C.parseData(data([event({date:'2026-10-27'}),{date:'invalid'}])));
  const item=event({id:'duplicate'});assert.throws(()=>C.parseData(data([item,item])));
  assert.equal(C.parseData(data([event({category:'__proto__',priority:'constructor'})])).events[0].category,'life');
});
test('merge keeps local records and resolves conflicts by last edit, without duplicating',()=>{
  const local=data([event({id:'a',title:'新记录',updatedAt:200}),event({id:'b'})]);
  const older=data([event({id:'a',title:'旧记录',updatedAt:100}),event({id:'c'})]);
  const merged=C.mergeData(local,older);assert.equal(merged.events.length,3);assert.equal(merged.events.find(e=>e.id==='a').title,'新记录');
  assert.equal(C.mergeData(merged,older).events.length,3);
});
test('completed tasks are never overdue; pending tasks sort by due time',()=>{
  const now=new Date('2026-10-27T12:00');
  assert.equal(C.overdue(event({deadline:'2026-10-27T11:00'}),now),true);
  assert.equal(C.overdue(event({deadline:'2026-10-27T11:00',completed:true}),now),false);
  assert.equal(C.overdue(event({}),now),false);
  const sorted=C.sortEvents([event({id:'later',deadline:'2026-10-28T12:00'}),event({id:'done',completed:true,deadline:'2026-10-26T12:00'}),event({id:'early',deadline:'2026-10-27T10:00'})]);
  assert.deepEqual(sorted.map(e=>e.id),['early','later','done']);
});
