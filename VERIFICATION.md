# Verification

Validated on OpenJDK 17, Linux. All checks below passed.
Windows launchers are supplied but were not executed on Windows in this environment.

- Demo: 10 events, 4 notifications, 60.0% suppression
- Restart: ten events and four transitions retained in SQLite
- Sample replay: all 9 events categorized, including INFO and FATAL
- Malformed input: skipped with a diagnostic; following valid input retained
- Live follow: newly created file, partial UTF-8, 10 appended events, four transitions
- Live follow: file replacement detected and new file consumed
- Live follow: visible truncation detected and input resumed
- CLI rejects missing files/options, invalid windows, and unknown commands
- Packaged JAR launches with bundled runtime libraries

CoreChecks also passed: exact threshold boundaries, server/level isolation, immediate FATAL escalation, exact window expiry/reset, SQLite reopen, parser validation, and terminal-control sanitization.

Run `test.bat` or `sh test.sh` to repeat the dependency-free core checks with a JDK installed.
