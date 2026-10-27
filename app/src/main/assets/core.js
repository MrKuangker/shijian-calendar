(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ShijianCore = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const CATEGORIES = { life: '生活', work: '工作', study: '学习', other: '其他' };
  const PRIORITIES = { normal: '普通', important: '重要', urgent: '紧急' };
  const pad = n => String(n).padStart(2, '0');
  function dayKey(date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`; }
  function isDate(s) {
    if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
    const d = new Date(s + 'T12:00:00');
    return Number.isFinite(+d) && dayKey(d) === s && +s.slice(0, 4) >= 1900 && +s.slice(0, 4) <= 2199;
  }
  function isLocalTime(s) {
    if (s === '') return true;
    if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s) || !isDate(s.slice(0, 10))) return false;
    return +s.slice(11, 13) <= 23 && +s.slice(14, 16) <= 59;
  }
  function monthDays(year, month, weekStart = 'monday') {
    const first = new Date(year, month, 1, 12);
    const offset = (first.getDay() + (weekStart === 'sunday' ? 0 : 6)) % 7;
    return Array.from({ length: 42 }, (_, i) => {
      const date = new Date(year, month, 1 - offset + i, 12);
      return { key: dayKey(date), day: date.getDate(), inMonth: date.getMonth() === month };
    });
  }
  function normalizeEvent(raw) {
    if (!raw || typeof raw !== 'object' || !isDate(raw.date)) throw new Error('事项日期无效');
    const string = (k, max) => typeof raw[k] === 'string' ? raw[k].slice(0, max).trim() : '';
    const title = string('title', 160), content = string('content', 20000);
    if (!title && !content) throw new Error('写下事件名或内容即可保存');
    const deadline = string('deadline', 16), reminderAt = string('reminderAt', 16), time = string('time', 5);
    if (!isLocalTime(deadline) || !isLocalTime(reminderAt)) throw new Error('截止或提醒时间无效');
    if (time && !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) throw new Error('事项时间无效');
    return {
      id: string('id', 100) || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`),
      date: raw.date, title, content, time, deadline, reminderAt,
      category: Object.hasOwn(CATEGORIES, raw.category) ? raw.category : 'life',
      priority: Object.hasOwn(PRIORITIES, raw.priority) ? raw.priority : 'normal',
      completed: raw.completed === true,
      createdAt: Number.isFinite(raw.createdAt) ? raw.createdAt : Date.now(),
      updatedAt: Number.isFinite(raw.updatedAt) ? raw.updatedAt : Date.now()
    };
  }
  function parseData(input) {
    const data = typeof input === 'string' ? JSON.parse(input) : input;
    if (!data || data.schemaVersion !== 1 || !Array.isArray(data.events) || data.events.length > 20000) throw new Error('这不是有效的时笺备份文件');
    const events = data.events.map(normalizeEvent);
    if (new Set(events.map(e => e.id)).size !== events.length) throw new Error('备份包含重复事项编号');
    const p = data.preferences || {};
    return { schemaVersion: 1, events, preferences: { weekStart: p.weekStart === 'sunday' ? 'sunday' : 'monday', theme: ['light', 'dark', 'system'].includes(p.theme) ? p.theme : 'light' } };
  }
  function mergeData(current, imported) {
    const merged = new Map(current.events.map(e => [e.id, e]));
    for (const e of imported.events) if (!merged.has(e.id) || e.updatedAt > merged.get(e.id).updatedAt) merged.set(e.id, e);
    return { ...current, events: [...merged.values()] };
  }
  function overdue(event, now = new Date()) { return !event.completed && !!event.deadline && +new Date(event.deadline) < +now; }
  function sortEvents(events) {
    const order = { urgent: 0, important: 1, normal: 2 };
    return [...events].sort((a, b) => Number(a.completed) - Number(b.completed) || (a.deadline || `${a.date}T${a.time || '23:59'}`).localeCompare(b.deadline || `${b.date}T${b.time || '23:59'}`) || order[a.priority] - order[b.priority] || a.createdAt - b.createdAt);
  }
  return { CATEGORIES, PRIORITIES, pad, dayKey, isDate, isLocalTime, monthDays, normalizeEvent, parseData, mergeData, overdue, sortEvents };
});
