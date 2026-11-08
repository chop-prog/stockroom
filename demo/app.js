import { Stock, race } from './stock.js';

const STOCK = 10;
const REQUESTS = 100;
const STATUS = { ACTIVE: 'ждёт оплаты', CONFIRMED: 'оплачен', RELEASED: 'отменён', EXPIRED: 'истёк' };
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (s) => document.querySelector(s);

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

let run = 0;

function setScore(lane, sold, done) {
  const score = lane.querySelector('.score');
  score.textContent = `Продано ${sold} из ${STOCK}` + (done ? (sold > STOCK ? ` — лишних заказов: ${sold - STOCK}` : ' — ровно столько, сколько было') : '');
  score.className = `score ${done ? (sold > STOCK ? 'bad' : 'good') : ''}`;
}

function resetLane(lane) {
  lane.querySelector('.dots').replaceChildren(...Array.from({ length: REQUESTS }, () => el('i')));
  setScore(lane, 0, false);
}

function play(lane, events, mine) {
  const dots = lane.querySelectorAll('.dots i');
  let at = 0;
  const step = () => {
    if (mine !== run) return;
    for (const e of events.slice(at, at + (reduced ? REQUESTS : 2))) {
      dots[e.id].className = e.ok ? 'ok' : 'no';
      setScore(lane, e.sold, false);
    }
    at += reduced ? REQUESTS : 2;
    if (at < events.length) setTimeout(step, 28);
    else setScore(lane, events.at(-1).sold, true);
  };
  step();
}

function start() {
  const mine = ++run;
  const seed = Math.floor(Math.random() * 1e9);
  for (const mode of ['naive', 'atomic']) {
    const lane = document.getElementById(mode);
    resetLane(lane);
    play(lane, race({ stock: STOCK, requests: REQUESTS, mode, seed }), mine);
  }
}

// The API playground. Time is simulated so a reservation can expire without waiting.
let offset = 0;
const stock = new Stock(5, () => Date.now() + offset);

function log(line, status) {
  const li = el('li', status >= 400 ? 'bad' : 'good');
  li.append(el('code', null, line), el('b', null, ` ${status}`));
  $('#log').prepend(li);
}

function call(method, path, result) {
  log(`${method} ${path}`, result.status);
  if (result.detail) $('#log').firstChild.append(el('span', null, ` ${result.detail}`));
  render();
}

function render() {
  $('#left').textContent = String(stock.available);
  $('#list').replaceChildren(...[...stock.reservations].reverse().map((r) => {
    const li = el('li', `item ${r.status.toLowerCase()}`);
    li.append(el('span', 'grow', `Резерв №${r.id}: ${r.quantity} шт., ${STATUS[r.status]}`));
    if (r.status === 'ACTIVE') {
      const pay = el('button', null, 'Оплатить');
      pay.type = 'button';
      pay.onclick = () => call('POST', `/api/reservations/${r.id}/confirm`, stock.confirm(r.id));
      const cancel = el('button', null, 'Отменить');
      cancel.type = 'button';
      cancel.onclick = () => call('DELETE', `/api/reservations/${r.id}`, stock.release(r.id));
      li.append(pay, cancel);
    }
    return li;
  }));
}

$('#run').addEventListener('click', start);
$('#form').addEventListener('submit', (e) => {
  e.preventDefault();
  call('POST', '/api/reservations', stock.reserve(Number($('#qty').value)));
});
$('#skip').addEventListener('click', () => {
  offset += 16 * 60_000;
  const expired = stock.expireDue();
  log(`Фоновая задача: истекло резервов — ${expired}`, 200);
  render();
});

resetLane($('#naive'));
resetLane($('#atomic'));
render();
start();
