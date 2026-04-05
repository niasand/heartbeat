export default {
  async fetch(request, env) {
    const corsHeaders = {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    // GET: retrieve all data for restore
    if (request.method === "GET") {
      try {
        const [hrResult, tsResult] = await Promise.all([
          env.DB.prepare("SELECT timestamp, heart_rate FROM heart_rates ORDER BY timestamp ASC").all(),
          env.DB.prepare("SELECT timestamp, duration_seconds, tag FROM timer_sessions ORDER BY timestamp ASC").all(),
        ]);

        return new Response(
          JSON.stringify({
            success: true,
            heartRates: hrResult.results,
            timerSessions: tsResult.results,
          }),
          { status: 200, headers: corsHeaders }
        );
      } catch (e) {
        return new Response(
          JSON.stringify({ success: false, message: e.message }),
          { status: 500, headers: corsHeaders }
        );
      }
    }

    // POST: sync data to cloud
    if (request.method === "POST") {
      try {
        const body = await request.json();
        const { heartRates = [], timerSessions = [] } = body;

        let syncedHR = 0;
        let syncedTS = 0;

        if (heartRates.length > 0) {
          const stmt = env.DB.prepare(
            `INSERT INTO heart_rates (timestamp, heart_rate) VALUES (?, ?)
             ON CONFLICT(timestamp) DO UPDATE SET heart_rate = excluded.heart_rate`
          );
          const batch = heartRates.map((r) =>
            stmt.bind(r.timestamp, r.heartRate)
          );
          await env.DB.batch(batch);
          syncedHR = heartRates.length;
        }

        if (timerSessions.length > 0) {
          const stmt = env.DB.prepare(
            `INSERT INTO timer_sessions (timestamp, duration_seconds, tag) VALUES (?, ?, ?)
             ON CONFLICT(timestamp) DO UPDATE SET duration_seconds = excluded.duration_seconds, tag = COALESCE(excluded.tag, timer_sessions.tag)`
          );
          const batch = timerSessions.map((r) =>
            stmt.bind(r.timestamp, r.durationSeconds, r.tag || null)
          );
          await env.DB.batch(batch);
          syncedTS = timerSessions.length;
        }

        return new Response(
          JSON.stringify({ success: true, syncedHeartRates: syncedHR, syncedTimerSessions: syncedTS }),
          { status: 200, headers: corsHeaders }
        );
      } catch (e) {
        return new Response(
          JSON.stringify({ success: false, message: e.message }),
          { status: 500, headers: corsHeaders }
        );
      }
    }

    // DELETE: remove records by timestamps
    if (request.method === "DELETE") {
      try {
        const body = await request.json();
        const { timestamps = [] } = body;

        let deletedCount = 0;

        if (timestamps.length > 0) {
          const stmt = env.DB.prepare(
            `DELETE FROM timer_sessions WHERE timestamp = ?`
          );
          const batch = timestamps.map((ts) => stmt.bind(ts));
          await env.DB.batch(batch);
          deletedCount = timestamps.length;
        }

        return new Response(
          JSON.stringify({ success: true, deletedCount }),
          { status: 200, headers: corsHeaders }
        );
      } catch (e) {
        return new Response(
          JSON.stringify({ success: false, message: e.message }),
          { status: 500, headers: corsHeaders }
        );
      }
    }

    return new Response(
      JSON.stringify({ success: false, message: "Method not allowed" }),
      { status: 405, headers: corsHeaders }
    );
  },
};
