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

// --- Measurements: 7 days, one every 30 min, derived from the active config ---
if (target.measurements.countDocuments() === 0) {
  const active = target.configs.findOne({ active: true }) || {};
  const tMin = active.temperatureMin != null ? active.temperatureMin : 16;
  const tMax = active.temperatureMax != null ? active.temperatureMax : 27;
  const hMin = active.humidityMin != null ? active.humidityMin : 34;
  const hMax = active.humidityMax != null ? active.humidityMax : 63;
  const hystT = active.hysteresisTemperature != null ? active.hysteresisTemperature : 1.0;

  const now = Date.now();
  const points = 7 * 48; // 7 days * 48 half-hours
  const measurements = [];

  for (let k = points; k > 0; k--) {
    const ts = new Date(now - k * 30 * 60000);
    const hourOfDay = ts.getHours() + ts.getMinutes() / 60;
    const phase = (hourOfDay / 24) * 2 * Math.PI;

    // Daily sinusoid + small noise; occasional spikes for variety.
    let temp = 23 + 5 * Math.sin(phase) + (Math.random() * 2 - 1);
    let hum = 50 + 12 * Math.sin(phase + 1) + (Math.random() * 4 - 2);
    if (k % 53 === 0) { temp += 8; }          // heat spike -> CRITICAL/WARNING
    if (k % 67 === 0) { hum += 25; }          // humidity spike

    temp = Math.round(temp * 10) / 10;
    hum = Math.round(Math.max(0, Math.min(100, hum)) * 10) / 10;

    let status = 'NORMAL';
    if (temp > tMax + hystT || temp < tMin - hystT) {
      status = 'CRITICAL';
    } else if (temp > tMax || temp < tMin) {
      status = 'WARNING_TEMP';
    } else if (hum > hMax || hum < hMin) {
      status = 'WARNING_HUMIDITY';
    }
    const coolerOn = temp >= tMax;

    measurements.push({
      _class: MEAS_CLASS,
      temperature: temp,
      humidity: hum,
      coolerOn: coolerOn,
      relayOn: coolerOn,
      status: status,
      createdAt: ts,
    });
  }

  target.measurements.insertMany(measurements);
  print('Seeded ' + measurements.length + ' measurements');
}
