/*
 * Live Raspberry simulator: stands in for the device so you can smoke-test the dashboard end to
 * end (live "en línea" badge, auto-refreshing charts, cooler reacting, status changes) before the
 * real hardware is connected.
 *
 * It models the actual control loop instead of a free curve: ambient heat pushes the temperature
 * up, the cooler (engaged via the same hysteresis the controller uses) pulls it back down, so the
 * reading oscillates inside the band as a sawtooth — the behaviour the defence is about. Heating/
 * cooling are rate-per-minute (scaled by the post interval), so cooler cycles stay a few minutes
 * long and realistic regardless of how often you post.
 *
 * SCENARIO forces a state on demand, e.g. to show the red alert during the defence:
 *   SCENARIO=normal            (default) — the control loop; mostly NORMAL with brief WARNING_TEMP
 *   SCENARIO=warning-temp      — temperature held above the max
 *   SCENARIO=warning-humidity  — humidity held above the max
 *   SCENARIO=critical          — both out of band (CRITICAL / red alert)
 *
 * Run (needs Node 18+ for global fetch; backend up on :8080):
 *   node scripts/simulate-raspberry.mjs
 *   INTERVAL_MS=3000 node scripts/simulate-raspberry.mjs
 *   SCENARIO=critical node scripts/simulate-raspberry.mjs
 *
 * Stop with Ctrl+C. Posts ~1 reading per INTERVAL_MS; stays under the 30/min measurement cap.
 */
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const INTERVAL_MS = Number(process.env.INTERVAL_MS ?? 5000);
const SCENARIO = process.env.SCENARIO ?? 'normal';
const HEAT_PER_MIN = 1.5; // ambient warming (°C/min)
const COOL_PER_MIN = 3.0; // total drop while the cooler runs (°C/min)

const SCENARIOS = new Set(['normal', 'warning-temp', 'warning-humidity', 'critical']);
if (!SCENARIOS.has(SCENARIO)) {
  console.error(`Unknown SCENARIO "${SCENARIO}". Use one of: ${[...SCENARIOS].join(', ')}`);
  process.exit(1);
}

const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
const round1 = (x) => Math.round(x * 10) / 10;
const noise = (amp) => (Math.random() - 0.5) * 2 * amp;

async function getConfig() {
  const res = await fetch(`${BASE_URL}/api/config/latest`);
  if (!res.ok) throw new Error(`GET /api/config/latest -> ${res.status} (create a config first)`);
  return res.json();
}

async function main() {
  const cfg = await getConfig();
  const { temperatureMin: tMin, temperatureMax: tMax, humidityMin: hMin, humidityMax: hMax } = cfg;
  const hystT = cfg.hysteresisTemperature ?? 0.5;
  const hMid = (hMin + hMax) / 2;
  const heatPerStep = HEAT_PER_MIN * (INTERVAL_MS / 60000);
  const coolPerStep = COOL_PER_MIN * (INTERVAL_MS / 60000);

  console.log(`Simulating against ${BASE_URL} every ${INTERVAL_MS}ms · scenario=${SCENARIO}`);
  console.log(`Band T[${tMin}-${tMax}] H[${hMin}-${hMax}] hystT=${hystT}. Ctrl+C to stop.`);

  let temperature = tMax - hystT; // start at the bottom of the deadband
  let coolerOn = false;
  let step = 0;

  const tick = async () => {
    step += 1;
    let humidity;

    if (SCENARIO === 'normal') {
      // Control loop: warm up, and cool harder while the cooler runs.
      temperature += heatPerStep - (coolerOn ? coolPerStep : 0) + noise(0.1);
      temperature = round1(clamp(temperature, tMin - 5, tMax + 6));
      if (temperature >= tMax) coolerOn = true;
      else if (temperature <= tMax - hystT) coolerOn = false;
      // Humidity: slow sine across the band so it sometimes leaves it.
      humidity = round1(clamp(hMid + (hMax - hMin) * 0.55 * Math.sin(step / 36) + noise(2), 0, 100));
    } else {
      // Forced demo states: hold a value out of band so the matching alert shows.
      const hotTemp = SCENARIO === 'critical' || SCENARIO === 'warning-temp';
      const wetHum = SCENARIO === 'critical' || SCENARIO === 'warning-humidity';
      temperature = round1(hotTemp ? tMax + 3 + noise(0.4) : (tMin + tMax) / 2 + noise(0.3));
      humidity = round1(wetHum ? hMax + 6 + noise(1) : hMid + noise(2));
      coolerOn = hotTemp; // the controller would be fighting the heat
    }

    const tempOut = temperature < tMin || temperature > tMax;
    const humOut = humidity < hMin || humidity > hMax;
    const status = tempOut && humOut ? 'CRITICAL'
      : tempOut ? 'WARNING_TEMP'
        : humOut ? 'WARNING_HUMIDITY'
          : 'NORMAL';

    const body = { temperature, humidity, coolerOn, relayOn: coolerOn, status };
    try {
      const res = await fetch(`${BASE_URL}/api/measurements`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const flag = res.ok ? 'OK' : `HTTP ${res.status}`;
      console.log(`[${new Date().toLocaleTimeString('es-AR', { hour12: false })}] ${flag}  T=${temperature}°C H=${humidity}% cooler=${coolerOn ? 'ON' : 'OFF'} ${status}`);
    } catch (err) {
      console.log(`POST failed (is the backend up?): ${err.message}`);
    }
  };

  await tick();
  setInterval(tick, INTERVAL_MS);
}

main().catch((err) => { console.error(err.message); process.exit(1); });
