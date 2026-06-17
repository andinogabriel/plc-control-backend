/*
 * Seed realistic measurement history (what the Raspberry would have produced) so every dashboard
 * range has data ending "now" — useful for local testing and the defence demo.
 *
 * Run (Mongo in docker, default compose setup):
 *   docker cp scripts/seed-measurements.mongo.js control-mongodb:/tmp/seed.js
 *   docker exec control-mongodb mongosh controlsystem --quiet --file /tmp/seed.js
 *
 * Reads the active config (thresholds + hysteresis) and synthesises a daily sinusoidal
 * temperature/humidity curve whose peaks cross the band, then applies the same hysteresis the
 * controller uses to drive the cooler on/off. Status is derived from the configured band.
 *
 * Tunables: DAYS (history span) and INTERVAL_SECONDS (sample cadence). REPLACE wipes existing
 * measurements first so re-running is idempotent.
 */
const DAYS = 10;
const INTERVAL_SECONDS = 300; // 5 min — keeps 24h/7d smooth under the chart's 1500-point cap
const REPLACE = true;

const cfg = db.configs.find({ active: true }).sort({ createdAt: -1 }).limit(1).toArray()[0]
  ?? db.configs.find().sort({ createdAt: -1 }).limit(1).toArray()[0];

if (!cfg) {
  print('No config found in `configs`. Create a configuration first (POST /api/config), then re-run.');
  quit(1);
}

const tMin = cfg.temperatureMin;
const tMax = cfg.temperatureMax;
const hMin = cfg.humidityMin;
const hMax = cfg.humidityMax;
const hystT = cfg.hysteresisTemperature ?? 0.5;
const round1 = (x) => Math.round(x * 10) / 10;
const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
const noise = (amp) => (Math.random() - 0.5) * 2 * amp;

const tMid = (tMin + tMax) / 2;
const tAmp = (tMax - tMid) * 1.25; // peaks overshoot the max so the cooler actually engages
const hMid = (hMin + hMax) / 2;
const hAmp = (hMax - hMid) * 1.1;

const now = Date.now();
const stepMs = INTERVAL_SECONDS * 1000;
const startMs = now - DAYS * 24 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

const docs = [];
let coolerOn = false;
for (let t = startMs; t <= now; t += stepMs) {
  const phase = (t / DAY_MS) * 2 * Math.PI;
  const temperature = round1(clamp(tMid + tAmp * Math.sin(phase) + noise(0.4), -10, 100));
  const humidity = round1(clamp(hMid + hAmp * Math.sin(phase + 0.7) + noise(1.5), 0, 100));

  // Controller hysteresis: engage at the max, release only after dropping a full band below it.
  if (temperature >= tMax) coolerOn = true;
  else if (temperature <= tMax - hystT) coolerOn = false;

  const tempOut = temperature < tMin || temperature > tMax;
  const humOut = humidity < hMin || humidity > hMax;
  const status = tempOut && humOut ? 'CRITICAL'
    : tempOut ? 'WARNING_TEMP'
      : humOut ? 'WARNING_HUMIDITY'
        : 'NORMAL';

  docs.push({
    temperature,
    humidity,
    coolerOn,
    relayOn: coolerOn,
    status,
    createdAt: new Date(t),
    _class: 'com.control.system.domain.entity.Measurement',
  });
}

if (REPLACE) {
  const removed = db.measurements.deleteMany({}).deletedCount;
  print(`Removed ${removed} existing measurement(s).`);
}
db.measurements.insertMany(docs);
print(`Inserted ${docs.length} measurements from ${new Date(startMs).toISOString()} to ${new Date(now).toISOString()}`);
print(`Config used: T[${tMin}-${tMax}] H[${hMin}-${hMax}] hystT=${hystT}`);
