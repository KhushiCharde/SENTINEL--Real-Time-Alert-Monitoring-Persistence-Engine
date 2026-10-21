# SENTINEL — Real-Time Alert Monitoring

A lightweight Core Java CLI that follows server log files, categorizes health events,
aggregates repeated errors with a HashMap, escalates severity, and saves history in SQLite through JDBC.
No Spring, web server, cloud account, or external database server required.

## Start on Windows (PowerShell / VS Code terminal)

1. Install Java 17 or newer. A JRE can run the included JAR; a **JDK** is needed to edit/rebuild.
2. Extract the ZIP completely, open the `sentinel` folder, and open a terminal in that folder.
3. Check `java -version`, then run:

```powershell
.\run.bat demo
```

The included compiled `sentinel.jar` and `lib/` dependencies are ready to run.
Do not run from inside the ZIP, and keep the `lib` folder beside the JAR.

Expected key output:

```text
ALERT [LOW] ... count=1
ALERT [MEDIUM] ... count=3
ALERT [HIGH] ... count=5
ALERT [CRITICAL] ... count=10
Events: 10 | Alert events: 10 | Emitted: 4 | Suppressed: 6 | Invalid: 0
Alert-notification reduction: 60.0%
```

Each demo run adds ten synthetic records to the database. Session counters reset on restart;
stored history is preserved. Run `history` to inspect earlier records.

```powershell
.\run.bat history
.\run.bat read samples/server.log
```

## Demonstrate real-time monitoring

Open two terminals in the extracted `sentinel` folder.
In terminal 1:

```powershell
.\run.bat watch samples/live.log --from-end
```

In terminal 2:

```powershell
.\simulate.bat
```

The producer appends ten synthetic errors, one per second. The monitor picks up complete
lines within its 250 ms poll interval, emits new/escalated alerts, and prints a dashboard
roughly every five seconds. Stop the monitor with Ctrl+C. Run the monitor first.
The monitor waits if the file does not exist yet. `--from-end` skips existing content
when the watcher starts; newly created files are read from the beginning. Omit it to read existing content before following new lines.
Use a fresh log file for a clean demo if needed: `watch samples/new.log --from-end`
and `simulate.bat samples/new.log`.

Linux/macOS equivalents: `sh run.sh demo`, `sh run.sh history`,
`sh run.sh watch samples/live.log --from-end`, `sh simulate.sh`.

## Input format

UTF-8 text; one newline-terminated record per line:

```text
2026-10-20T10:00:01Z|web-01|database|ERROR|Database connection pool exhausted
```

Fields: ISO-8601 timestamp with UTC/offset, server, component, level, message.
Levels: INFO, WARN, ERROR, FATAL (case-insensitive). Messages may contain pipes;
the first four fields may not. Blank lines and lines beginning with `#` are ignored.
Malformed records are reported and skipped. Live records over 64 KiB are discarded.
Use your server application's logging configuration or a small adapter to produce this format.

Categories are keyword-based: DATABASE, MEMORY, DISK, CPU, NETWORK, APPLICATION.
This classifies existing log messages; it does not directly sample CPU, RAM, temperature,
or disk metrics. The supplied input data and producer are synthetic demonstrations.
The dashboard summarizes observed alerts, not independently verified server availability.

## Deduplication and escalation

The HashMap key is `(server, component, original level, exact trimmed message)`.
Different servers, levels, components, and messages never merge. Volatile request IDs or
numbers inside a message produce different signatures; this version intentionally avoids
fuzzy normalization.

For each signature, a **fixed 60-second window starts when its first event is processed**.
Incoming repeats increment its count. Only the first occurrence and severity changes
produce alert notifications. A quiet window expires and disappears from the active dashboard;
the next occurrence starts again at LOW. This is expiry, not proof of recovery.

| Count inside window | Severity |
| --- | --- |
| 1–2 | LOW |
| 3–4 | MEDIUM |
| 5–9 | HIGH |
| 10+ | CRITICAL |

FATAL logs become CRITICAL immediately. INFO logs are stored but do not create alerts.
The thresholds are policy choices for demonstration, not an ML model.
Window lengths are configurable:

```powershell
.\run.bat watch samples/live.log --window 120 --db data/custom.db
```

Use 1–86400 seconds. Threshold counts are defined in `Severity.java`.
Window timing uses ingestion/processing time rather than timestamps inside historical logs.
Ten same-signature events within a window generate four notifications (counts 1,3,5,10).
The measured suppression is `(10 - 4) / 10 * 100 = 60%` for **that workload**.
Real logs may produce a different reduction. Dashboard lines and malformed-input diagnostics
are excluded from this metric; it measures alert notifications, not every terminal line.

## Persistence

SQLite creates `data/sentinel.db` automatically. JDBC uses prepared statements and a transaction
per event. Every valid event is stored, including INFO and suppressed duplicates. The
`alert_transitions` table records initial alerts and severity upgrades linked to their events.
No history is silently dropped when deduplication suppresses a console notification.
A database write error stops processing with an error. See `samples/queries.sql` for SQL examples.

Restarting preserves SQL history but resets in-memory aggregation; counts do not resume.
Reading the same file twice deliberately inserts it twice. There is no persistent file-offset
checkpoint or exactly-once ingestion guarantee. `watch` handles detected replacement and
truncation, but fast copy-truncate/regrow between polls and rotation races can lose events.
An incomplete final live line waits for a newline. One process should own a watched input;
this is a single-machine, single-threaded student project, not a distributed log platform.

Active memory is limited to 10,000 signatures; exceeding that stops monitoring with a clear
error. Expired signatures are pruned. Database history grows until the user archives/deletes it.
The implementation favors easy-to-explain logic over high-throughput ingestion: each event
is committed independently and the map is scanned for expiry.

## Edit and rebuild

The project includes its complete source. With JDK 17+ installed:

```powershell
.\build.bat
.\test.bat
.\run.bat demo
```

On Linux/macOS: `sh build.sh`, `sh test.sh`.
The build scripts use `javac` and `jar`; no Maven installation is needed.
Alternatively `mvn package` produces `target/sentinel-1.0.0.jar` and `target/lib/`;
run `java -jar target/sentinel-1.0.0.jar demo` from the project directory.

| File | Responsibility |
| --- | --- |
| `Main.java` | Commands, input processing, counters |
| `LogEvent.java` | Parse input and categorize events |
| `AlertEngine.java` | HashMap signatures, fixed windows, aggregation |
| `Severity.java` | Count thresholds |
| `HistoryStore.java` | JDBC schema, transactions, history |
| `Dashboard.java` | Formatted terminal status |
| `FileTailer.java` | Follow appended UTF-8 lines |
| `SampleLogWriter.java` | Produce synthetic logs for demonstration |
| `CoreChecks.java` | Deterministic checks of core behavior |

## Dependencies

The bundled runtime libraries are Xerial SQLite JDBC 3.46.1.0 and SLF4J API/NOP 2.0.13.
They are pinned for a reproducible build; not a claim to use the newest releases.
Original downloads: https://repo.maven.apache.org/maven2/.
SQLite JDBC documentation: https://github.com/xerial/sqlite-jdbc.
SLF4J documentation: https://www.slf4j.org/.
Original upstream license/notice resources remain inside the dependency JARs;
see `THIRD_PARTY_NOTICES.md`.

## Interview explanation

“I built SENTINEL in Core Java to process server health logs. It reads a structured log
file continuously, groups matching errors using a HashMap, and escalates alerts when
occurrences cross configured thresholds. I used JDBC and SQLite to preserve every event
and severity transition, and a CLI dashboard to show active alerts. In the included
10-event repeated-error demo, it reduces alert notifications from 10 to 4, a 60% reduction.”

Only say you implemented and tested the parts you understand and can demonstrate.
