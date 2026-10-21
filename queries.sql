-- Open data/sentinel.db in SQLite or a SQLite viewer.
SELECT * FROM events ORDER BY id DESC LIMIT 20;
SELECT server, category, COUNT(*) AS occurrences FROM events WHERE level != 'INFO' GROUP BY server, category;
SELECT e.server, e.message, t.previous_severity, t.new_severity, t.occurrence_count
FROM alert_transitions t JOIN events e ON e.id=t.event_id ORDER BY t.id;
