// The Java service's rules, ported to JavaScript so they can run on a static page.

export const TTL_MS = 15 * 60_000;

export function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Plays `requests` buyers against one stock counter in a random order.
 * 'naive' reads the counter and writes it back later, as two steps other requests can slip between.
 * 'atomic' is one step, like `update ... where available >= 1`.
 * Returns one event per finished request, in the order they finished.
 */
export function race({ stock, requests, mode, seed }) {
  const random = rng(seed);
  let available = stock;
  const running = Array.from({ length: requests }, (_, id) => ({ id, seen: null }));
  const events = [];
  let sold = 0;
  while (running.length) {
    const at = Math.floor(random() * running.length);
    const r = running[at];
    if (mode === 'naive' && r.seen === null) {
      r.seen = available;
      continue;
    }
    const before = mode === 'naive' ? r.seen : available;
    const ok = before >= 1;
    if (ok) {
      available = before - 1;
      sold++;
    }
    events.push({ id: r.id, ok, sold, available });
    running.splice(at, 1);
  }
  return events;
}

export class Stock {
  constructor(available, now = () => Date.now()) {
    this.available = available;
    this.now = now;
    this.reservations = [];
    this.nextId = 1;
  }

  reserve(quantity) {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 1000) return { status: 400, detail: 'Количество от 1 до 1000' };
    if (this.available < quantity) return { status: 409, detail: 'Не хватает товара' };
    this.available -= quantity;
    const reservation = { id: this.nextId++, quantity, status: 'ACTIVE', expiresAt: this.now() + TTL_MS };
    this.reservations.push(reservation);
    return { status: 201, reservation };
  }

  #find(id) {
    return this.reservations.find((r) => r.id === id);
  }

  confirm(id) {
    const r = this.#find(id);
    if (!r) return { status: 404, detail: 'Нет такого резерва' };
    if (r.status !== 'ACTIVE' || r.expiresAt <= this.now()) return { status: 409, detail: `Резерв не активен: ${r.status}` };
    r.status = 'CONFIRMED';
    return { status: 200, reservation: r };
  }

  release(id) {
    const r = this.#find(id);
    if (!r) return { status: 404, detail: 'Нет такого резерва' };
    if (r.status !== 'ACTIVE') return { status: 409, detail: `Резерв не активен: ${r.status}` };
    r.status = 'RELEASED';
    this.available += r.quantity;
    return { status: 200, reservation: r };
  }

  expireDue() {
    let expired = 0;
    for (const r of this.reservations) {
      if (r.status === 'ACTIVE' && r.expiresAt <= this.now()) {
        r.status = 'EXPIRED';
        this.available += r.quantity;
        expired++;
      }
    }
    return expired;
  }
}
