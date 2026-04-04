// Cloudflare Workers - Heart Rate Data Sync API
// Deploy: wrangler deploy
// Bind D1: wrangler d1 create heart-rate-db, then add binding in wrangler.toml

// D1 table creation (run once):
// wrangler d1 execute heart-rate-db --command="CREATE TABLE IF NOT EXISTS heart_rates (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, heart_rate INTEGER NOT NULL, UNIQUE(timestamp));"
// wrangler d1 execute heart-rate-db --command="CREATE TABLE IF NOT EXISTS timer_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, duration_seconds INTEGER NOT NULL, UNIQUE(timestamp));"

export default {
  async fetch(request, env) {
    // Only accept POST
    if (request.method !== 'POST') {
      return new Response(JSON.stringify({ success: false, message: 'Method not allowed' }), {
        status: 405,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    try {
      const body = await request.json();
      const { heartRates = [], timerSessions = [] } = body;

      let syncedHR = 0;
      let syncedTS = 0;

      // Batch upsert heart rates
      if (heartRates.length > 0) {
        const stmt = env.DB.prepare(
          `INSERT INTO heart_rates (timestamp, heart_rate) VALUES (?, ?)
           ON CONFLICT(timestamp) DO UPDATE SET heart_rate = excluded.heart_rate`
        );
        const batch = heartRates.map(r => stmt.bind(r.timestamp, r.heartRate));
        await env.DB.batch(batch);
        syncedHR = heartRates.length;
      }

      // Batch upsert timer sessions
      if (timerSessions.length > 0) {
        const stmt = env.DB.prepare(
          `INSERT INTO timer_sessions (timestamp, duration_seconds) VALUES (?, ?)
           ON CONFLICT(timestamp) DO UPDATE SET duration_seconds = excluded.duration_seconds`
        );
        const batch = timerSessions.map(r => stmt.bind(r.timestamp, r.durationSeconds));
        await env.DB.batch(batch);
        syncedTS = timerSessions.length;
      }

      return new Response(
        JSON.stringify({
          success: true,
          syncedHeartRates: syncedHR,
          syncedTimerSessions: syncedTS
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }
      );
    } catch (e) {
      return new Response(
        JSON.stringify({ success: false, message: e.message }),
        {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        }
      );
    }
  }
};
