import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Stock, TTL_MS, race } from './stock.js';

const seeds = [1, 2, 3, 42, 2026];

test('atomic updates sell exactly the stock, whatever the order', () => {
  for (const seed of seeds) {
    const events = race({ stock: 10, requests: 100, mode: 'atomic', seed });
    assert.equal(events.length, 100);
    assert.equal(events.filter((e) => e.ok).length, 10);
    assert.equal(events.at(-1).available, 0);
  }
});

test('read-then-write oversells', () => {
  for (const seed of seeds) {
    assert.ok(race({ stock: 10, requests: 100, mode: 'naive', seed }).filter((e) => e.ok).length > 10, `seed ${seed}`);
  }
});

function clocked(available) {
  const clock = { t: 0 };
  return [new Stock(available, () => clock.t), clock];
}

test('reserve takes stock and refuses more than is left', () => {
  const [s] = clocked(3);
  assert.equal(s.reserve(2).status, 201);
  assert.equal(s.reserve(2).status, 409);
  assert.equal(s.reserve(0).status, 400);
  assert.equal(s.available, 1);
});

test('confirm keeps stock taken and works once', () => {
  const [s] = clocked(3);
  const { reservation } = s.reserve(1);
  assert.equal(s.confirm(reservation.id).status, 200);
  assert.equal(s.confirm(reservation.id).status, 409);
  assert.equal(s.release(reservation.id).status, 409);
  assert.equal(s.available, 2);
});

test('release returns stock once', () => {
  const [s] = clocked(3);
  const { reservation } = s.reserve(2);
  assert.equal(s.release(reservation.id).status, 200);
  assert.equal(s.release(reservation.id).status, 409);
  assert.equal(s.available, 3);
});

test('an overdue reservation cannot be confirmed and returns stock once', () => {
  const [s, clock] = clocked(4);
  const { reservation } = s.reserve(3);
  assert.equal(s.expireDue(), 0);
  clock.t += TTL_MS;
  assert.equal(s.confirm(reservation.id).status, 409);
  assert.equal(s.expireDue(), 1);
  assert.equal(s.expireDue(), 0);
  assert.equal(s.available, 4);
  assert.equal(reservation.status, 'EXPIRED');
});
