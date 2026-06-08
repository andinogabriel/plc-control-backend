// MongoDB seed for local/testing. Runs automatically on first container init
// (empty data volume). To reseed: `docker compose down -v && docker compose up`.
// Manual run: `mongosh "mongodb://localhost:27017/controlsystem" docker/mongo-init/seed.js`
//
// The `_class` field mirrors what Spring Data MongoDB writes, so the seeded documents are
// indistinguishable from app-created ones.

/* global db, print, ISODate */

const target = db.getSiblingDB('controlsystem');

const CONFIG_CLASS = 'com.control.system.domain.entity.Config';
const MEAS_CLASS = 'com.control.system.domain.entity.Measurement';
const DAY_MS = 86400000;

// --- Configurations: audit history with accented names to exercise the search filters ---
if (target.configs.countDocuments() === 0) {
  const users = [
    { name: 'Gabriel Andinó', email: 'gabriel.andino@example.com' },
    { name: 'José Núñez', email: 'jose.nunez@example.com' },
    { name: 'María Pérez', email: 'maria.perez@example.com' },
    { name: 'Lucía Gómez', email: 'lucia.gomez@example.com' },
    { name: 'Ramón Díaz', email: 'ramon.diaz@example.com' },
    { name: 'Sofía Martínez', email: 'sofia.martinez@example.com' },
  ];

  const total = 12;
  const now = Date.now();
  const configs = [];

  for (let i = 0; i < total; i++) {
    const u = users[i % users.length];
    const daysAgo = (total - i) * 5; // oldest first, newest is active
    configs.push({
      _class: CONFIG_CLASS,
      temperatureMin: 16 + (i % 3),
      temperatureMax: 26 + (i % 4),
      humidityMin: 30 + (i % 5),
      humidityMax: 60 + (i % 6),
      hysteresisTemperature: 1.0 + (i % 3) * 0.5,
      hysteresisHumidity: 2.0 + (i % 2),
      measurementIntervalSeconds: 30,
      createdByName: u.name,
      createdByEmail: u.email,
      clientIp: '192.168.0.' + (10 + i),
      userAgent: 'Mozilla/5.0 (seed-data)',
      deviceFingerprint: 'seed-fp-' + i,
      active: i === total - 1,
      createdAt: new Date(now - daysAgo * DAY_MS),
    });
  }

  target.configs.insertMany(configs);
  print('Seeded ' + configs.length + ' configurations');
}

// --- Measurements: multi-resolution history derived from the active config ---
// Dense in the recent window (so the kiosk's 2 h view and the dashboard 1 h/12 h/24 h ranges,
// sparklines and the cooler timeline have plenty of points) and coarser further back (so the
// week/month ranges and the config history still have context). The newest reading is ~now so
// the system-health badge shows "En línea" right after seeding.
if (target.measurements.countDocuments() === 0) {
  const active = target.configs.findOne({ active: true }) || {};
  const tMin = active.temperatureMin != null ? active.temperatureMin : 18;
  const tMax = active.temperatureMax != null ? active.temperatureMax : 29;
  const hMin = active.humidityMin != null ? active.humidityMin : 31;
  const hMax = active.humidityMax != null ? active.humidityMax : 65;
  const hystT = active.hysteresisTemperature != null ? active.hysteresisTemperature : 2.0;
  const hystH = active.hysteresisHumidity != null ? active.hysteresisHumidity : 3.0;

  const now = Date.now();
  const MIN_MS = 60000;

  // Build timestamps (ms) oldest -> newest from two grids, so the cooler state can be carried
  // forward with real hysteresis.
  const stamps = [];
  // Coarse: 14 days .. 24 h ago, every 30 min.
  for (let t = now - 14 * DAY_MS; t < now - DAY_MS; t += 30 * MIN_MS) stamps.push(t);
  // Dense: last 24 h, every 2 min. Ends exactly at "now" (newest = live-ish).
  for (let t = now - DAY_MS; t <= now; t += 2 * MIN_MS) stamps.push(t);
  stamps.sort(function (a, b) { return a - b; });

  const measurements = [];
  let cooler = false; // carried state for realistic hysteresis (on at max, off at max - hyst)

  for (let i = 0; i < stamps.length; i++) {
    const ms = stamps[i];
    const ts = new Date(ms);
    const hourOfDay = ts.getHours() + ts.getMinutes() / 60;
    const phase = (hourOfDay / 24) * 2 * Math.PI;

    // Daily sinusoid + small noise; afternoons peak above tMax so the cooler cycles on/off
    // (realistic duty cycle + timeline). Occasional spikes add out-of-range variety.
    let temp = 24 + 6 * Math.sin(phase) + (Math.random() * 2 - 1);
    let hum = 52 + 14 * Math.sin(phase + 1) + (Math.random() * 4 - 2);
    if (i % 137 === 0) { temp += 9; }   // heat spike -> WARNING/CRITICAL
    if (i % 191 === 0) { hum += 26; }   // humidity spike

    temp = Math.round(temp * 10) / 10;
    hum = Math.round(Math.max(0, Math.min(100, hum)) * 10) / 10;

    // Stateful cooler control (matches the documented hysteresis logic).
    if (temp >= tMax || hum >= hMax) cooler = true;
    else if (temp <= tMax - hystT && hum <= hMax - hystH) cooler = false;

    let status = 'NORMAL';
    if (temp > tMax + hystT || temp < tMin - hystT) {
      status = 'CRITICAL';
    } else if (temp > tMax || temp < tMin) {
      status = 'WARNING_TEMP';
    } else if (hum > hMax || hum < hMin) {
      status = 'WARNING_HUMIDITY';
    }

    measurements.push({
      _class: MEAS_CLASS,
      temperature: temp,
      humidity: hum,
      coolerOn: cooler,
      relayOn: cooler,
      status: status,
      createdAt: ts,
    });
  }

  target.measurements.insertMany(measurements);
  print('Seeded ' + measurements.length + ' measurements (newest at ~now)');
}
