# SENTINEL — Real-Time Alert Monitoring & Persistence Engine

**Live demo:** https://sentinel-java-production.up.railway.app/

A lightweight Core Java log monitor using HashMap deduplication, threshold-based
severity escalation, SQLite storage through JDBC, and CLI and browser dashboards.
The public web dashboard accepts predefined synthetic events; it does not measure
real server hardware or accept private log uploads.

## Repository layout

This repository keeps Java source files, runtime dependency JARs, sample logs,
launchers, and the web page directly in the root. The build and deployment
configuration supports this layout; do not move individual files without updating it.

## Run on Windows

Install Java 17 or newer, download/extract the repository, and open a terminal there:

```powershell
.\run.bat demo
.\run.bat history
.\run.bat read server.log
```

The bundled `sentinel.jar` is ready to run with the dependency JARs beside it.
To open the browser dashboard locally:

```powershell
java -cp "sentinel.jar;*" sentinel.WebServer
```

Visit http://localhost:8080. The browser UI calls the same Java aggregation,
classification, severity policy, and JDBC persistence classes as the CLI.
On Linux/macOS, use `sh run.sh demo` or `java -cp 'sentinel.jar:*' sentinel.WebServer`.

## Real-time log demonstration

Start two terminals in this folder. In the first:

```powershell
.\run.bat watch samples/live.log --from-end
```

In the second:

```powershell
.\simulate.bat
```

The synthetic producer creates/appends ten errors at one-second intervals.
The monitor polls every 250 ms and prints a dashboard roughly every five seconds.
Ctrl+C stops monitoring. `--from-end` skips a file's existing content when the
watcher starts; a file created afterward is read from the beginning.

## Log format

```text
2026-10-20T10:00:01Z|web-01|database|ERROR|Database connection pool exhausted
```

The five fields are timestamp, server, component, level, and message. Timestamp
must be ISO-8601 with a UTC/offset designation. Levels: INFO, WARN, ERROR, FATAL.
Messages may include pipes. Blank lines and lines starting with `#` are ignored;
malformed lines are reported and skipped. Live input lines over 64 KiB are discarded.

Keyword-based categories: DATABASE, MEMORY, DISK, CPU, NETWORK, APPLICATION.
This processes existing structured logs; a logging adapter is required for other formats.

## Deduplication and severity

The HashMap key contains server, component, original level, and exact trimmed message.
Each signature has a fixed window beginning when its first event is processed.
The default is 60 seconds; configure it using `--window 120` (1–86400 seconds).

| Occurrences inside a window | Severity |
| --- | --- |
| 1–2 | LOW |
| 3–4 | MEDIUM |
| 5–9 | HIGH |
| 10+ | CRITICAL |

Only first occurrences and severity changes produce console notifications. All
valid events, including duplicates and INFO, are saved. FATAL is immediately
CRITICAL. INFO does not create an alert. Quiet windows expire; expiry does not
prove recovery. Counts reset on restart, and history remains in SQLite.

The included repeated-error demo produces 10 events and 4 notifications at counts
1, 3, 5, and 10: `(10-4)/10 * 100 = 60%` suppression for that workload. This is a
measured notification reduction, not a universal performance claim. Dashboard
lines are excluded from the metric. Mixed or unique errors produce different results.

## Persistence

The CLI creates `data/sentinel.db`; the web adapter creates `data/sentinel-web.db`
by default. `--db PATH` configures CLI storage. The web adapter reads `SENTINEL_DB`.
The `events` table retains every valid event. `alert_transitions` stores initial
alerts and severity changes, linked to the triggering event. Each event is written
using prepared statements in a transaction. A write failure stops CLI processing.
`queries.sql` contains example SQL queries. `history` displays the latest 20 records.

## Rebuild and validate

A **JDK 17+** is required to compile. On Windows:

```powershell
.\build.bat
.\test.bat
```

On Linux/macOS: `sh build.sh`, `sh test.sh`.
The checks cover escalation boundaries, signature isolation, expiry/reset,
SQLite reconnect/history, malformed input, and terminal-control sanitization.
Alternatively `mvn package` builds the main program, copies dependencies to
`target/lib/`, and produces `target/sentinel-1.0.0.jar`. The standalone CoreChecks
are run using the test scripts, not automatically by Maven.

## Deploy with Railway

The root `Dockerfile` compiles the Java source and launches `sentinel.WebServer`.
The root `railway.toml` sets Docker deployment and the `/health` health check.
Connect this repository to a Railway service, configure `PORT=8080` and
`SENTINEL_DB=/data/sentinel-web.db`, attach a persistent volume at `/data`, and
create a public domain targeting port 8080. Use one replica with this SQLite design.

The already-live deployment was created independently of this GitHub repository.
Uploading files alone does not connect Railway auto-deployment to the repository.

## Source files

| File | Responsibility |
| --- | --- |
| Main.java | CLI commands, processing, counters |
| LogEvent.java | Parsing and category classification |
| AlertEngine.java | HashMap aggregation and fixed windows |
| Severity.java | Threshold policy |
| HistoryStore.java | JDBC transactions and persistent history |
| FileTailer.java | Following appended UTF-8 log lines |
| Dashboard.java | CLI status rendering |
| SampleLogWriter.java | Synthetic live producer |
| WebServer.java | Thin Java HTTP adapter and JSON endpoints |
| index.html | Browser dashboard |
| CoreChecks.java | Dependency-free validation |

## Scope and limitations

This is a single-machine student project. It has no distributed ingestion,
exactly-once guarantees, authentication for the public synthetic sandbox, or
checkpointed file offsets. Replaying a file intentionally stores it again.
The live tailer detects visible truncation and replacement, but rapid rotation
races may lose events. A final partial live line waits for a newline.
Aggregation memory is capped at 10,000 signatures. Each event is committed
independently and expiry scans the map, favoring clear logic over throughput.
The browser sandbox has a 100,000-event storage guard and a two-second demo cooldown.
Visitors share the synthetic sandbox; counters reset after restart while history
persists on the attached volume. Database history needs manual archival/retention.

Dependencies: Xerial SQLite JDBC 3.46.1.0; SLF4J API/NOP 2.0.13. These are pinned
for reproducibility. See THIRD_PARTY_NOTICES.md for upstream notices.
