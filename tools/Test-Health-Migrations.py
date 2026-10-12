"""Exercise actual Room migration SQL with SQLite; no user's database is opened."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
generated = root / "PhoneApp/app/build/generated/ksp/debug/java/com/healthsync/phone/data/FitnessDatabase_Impl.java"
module = root / "PhoneApp/app/src/main/java/com/healthsync/phone/di/AppModule.kt"
legacy_source = root / "PhoneApp/app/src/main/java/com/healthsync/phone/data/LegacyDatabasePreservation.kt"

def quoted_sql(text):
    return [json.loads('"' + value + '"') for value in re.findall(r'execSQL\("((?:\\.|[^"\\])*)"\)', text)]

create_statements = [sql for sql in quoted_sql(generated.read_text(encoding="utf-8")) if sql.startswith("CREATE TABLE IF NOT EXISTS")]
assert len(create_statements) >= 7, "Generate the current Room implementation before checking migrations"
migrations = module.read_text(encoding="utf-8")
sql5to6 = quoted_sql(migrations.split("val migration5To6", 1)[1].split("val migration4To5", 1)[0])
sql4to5 = quoted_sql(migrations.split("val migration4To5", 1)[1].split("val migration6To7", 1)[0])
sql6to7 = quoted_sql(migrations.split("val migration6To7", 1)[1].split("@Provides", 1)[0])
assert len(sql6to7) == 5, "Version 7 adds provenance without replacing existing history"
assert "migration6To7" in migrations.split(".addMigrations(", 1)[1].split(")", 1)[0]

def fresh_database():
    connection = sqlite3.connect(":memory:")
    for sql in create_statements:
        connection.execute(sql)
    return connection

expected = fresh_database()
tables = ["heart_rate", "steps", "step_buckets", "spo2", "sleep", "notification_history", "workouts"]
legacy = legacy_source.read_text(encoding="utf-8")
legacy_tables = re.findall(r'"(\w+)"', legacy.split("applicationTables = listOf(", 1)[1].split(")", 1)[0])
schema6_sql = [json.loads('"' + sql + '"') for sql in re.findall(r'"(CREATE TABLE IF NOT EXISTS [^"\n]+)"', legacy)]
rename_sql = next(sql for sql in quoted_sql(legacy) if sql.startswith("ALTER TABLE"))
archive_template = re.search(r'val archive = "([^"]+)"', legacy).group(1)
assert tables == legacy_tables
assert "fallbackToDestructiveMigration" not in migrations
for version in (1, 2, 3):
    assert f"migration{version}To6 = LegacyDatabasePreservation.migration({version})" in migrations
    assert f"migration{version}To6" in migrations.split(".addMigrations(", 1)[1].split(")", 1)[0]

def table_exists(database, table):
    return database.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", (table,)).fetchone() is not None

def index_schema(database, table):
    result = []
    for index in database.execute(f"PRAGMA index_list(`{table}`)"):
        _, name, unique, origin, partial = index
        result.append((unique, origin, partial, database.execute(f"PRAGMA index_info(`{name}`)").fetchall()))
    return sorted(result)

def check_path(start_version):
    database = fresh_database()
    for table, columns in {
        "heart_rate": ["source", "sourcePackage", "sourceLabel"],
        "sleep": ["source", "estimatedMinutes", "stagesAvailable"],
        "spo2": ["source", "sourcePackage", "sourceLabel"],
        "workouts": ["distanceSource", "caloriesEstimated"],
    }.items():
        for column in columns:
            database.execute(f"ALTER TABLE {table} DROP COLUMN {column}")
    if start_version == 4:
        database.execute("ALTER TABLE workouts DROP COLUMN durationSeconds")
        for sql in sql4to5:
            database.execute(sql)
    database.execute("INSERT INTO heart_rate (bpm, accuracy, timestamp) VALUES (75, 3, 1000000)")
    database.execute("INSERT INTO steps (dayKey, steps, calories, timestamp) VALUES ('2026-10-12', 1234, 42, 1000000)")
    database.execute("INSERT INTO spo2 (percentage, timestamp) VALUES (98, 1000000)")
    database.execute("INSERT INTO sleep (startTime, endTime, deepMinutes, lightMinutes, remMinutes, awakeMinutes) VALUES (1000000, 3700000, 10, 20, 10, 5)")
    database.execute("INSERT INTO workouts (activityType, startTime, endTime, distanceMeters, calories, avgHeartRate, steps, swingsCount, routeJson, durationSeconds) VALUES ('Walk', 1000000, 1060000, 100, 12, 75, 150, 0, '[]', 60)")
    database.execute("INSERT INTO notification_history (packageName, appLabel, title, text, timestamp) VALUES ('example.app', 'Example', 'Title', 'Message', 1000000)")
    for sql in sql5to6:
        database.execute(sql)
    for sql in sql6to7:
        database.execute(sql)
    for table in tables:
        actual_columns = database.execute(f"PRAGMA table_info({table})").fetchall()
        expected_columns = expected.execute(f"PRAGMA table_info({table})").fetchall()
        assert actual_columns == expected_columns, f"Schema mismatch after {start_version}->7: {table}"
        assert index_schema(database, table) == index_schema(expected, table), (start_version, table, "indices")
        assert database.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == expected.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall()
    assert database.execute("SELECT COUNT(*) FROM spo2").fetchone()[0] == 0
    assert database.execute("SELECT COUNT(*) FROM sleep").fetchone()[0] == 0
    assert database.execute("SELECT percentage FROM legacy_spo2_unverified").fetchone() == (98.0,)
    assert database.execute("SELECT deepMinutes FROM legacy_sleep_unverified").fetchone() == (10,)
    assert database.execute("SELECT bpm, accuracy, source, sourcePackage, sourceLabel FROM heart_rate").fetchone() == (75, 3, "WATCH_SENSOR", "", "")
    assert database.execute("SELECT steps FROM steps").fetchone() == (1234,)
    assert database.execute("SELECT activityType, durationSeconds, distanceSource, caloriesEstimated FROM workouts").fetchone() == ("Walk", 60, "UNKNOWN", 1)
    assert database.execute("SELECT text FROM notification_history").fetchone() == ("Message",)
    database.close()
    return {"from": start_version, "to": 7, "schema_matches": True, "history_preserved": True, "unverified_records_archived": True}

def check_source_migration():
    database = sqlite3.connect(":memory:")
    for sql in schema6_sql:
        database.execute(sql)
    database.execute("INSERT INTO heart_rate (id, bpm, accuracy, timestamp) VALUES (9, 75, 3, 1000000)")
    database.execute("INSERT INTO spo2 (id, percentage, timestamp, source) VALUES (8, 98, 900000, 'WATCH_SENSOR')")
    database.execute("CREATE TABLE legacy_spo2_unverified (original_value REAL)")
    database.execute("INSERT INTO legacy_spo2_unverified VALUES (97)")
    original = {table: database.execute(f"SELECT * FROM `{table}`").fetchall() for table in tables}
    for sql in sql6to7:
        database.execute(sql)
    for table in tables:
        assert database.execute(f"PRAGMA table_info(`{table}`)").fetchall() == expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(), ("6->7", table, "schema")
        original_width = len(original[table][0]) if original[table] else 0
        assert [row[:original_width] for row in database.execute(f"SELECT * FROM `{table}`").fetchall()] == original[table], (table, "record values retained")
    assert database.execute("SELECT source, sourcePackage, sourceLabel FROM heart_rate WHERE id=9").fetchone() == ("WATCH_SENSOR", "", "")
    assert database.execute("SELECT source, sourcePackage, sourceLabel FROM spo2 WHERE id=8").fetchone() == ("WATCH_SENSOR", "", "")
    assert database.execute("SELECT original_value FROM legacy_spo2_unverified").fetchone() == (97,)
    # Imported measurements preserve the app's capture time and do not claim sensor accuracy.
    database.execute("INSERT INTO heart_rate (bpm, accuracy, timestamp, source, sourcePackage, sourceLabel) VALUES (72, 0, 800000, 'WATCH_HEALTH_APP', 'vendor.health', 'Watch Health')")
    database.execute("INSERT INTO spo2 (percentage, timestamp, source, sourcePackage, sourceLabel) VALUES (99, 800000, 'WATCH_HEALTH_APP', 'vendor.health', 'Watch Health')")
    assert database.execute("SELECT bpm, accuracy, timestamp, sourcePackage, sourceLabel FROM heart_rate WHERE source='WATCH_HEALTH_APP'").fetchone() == (72, 0, 800000, "vendor.health", "Watch Health")
    assert database.execute("SELECT percentage, timestamp, sourcePackage, sourceLabel FROM spo2 WHERE source='WATCH_HEALTH_APP'").fetchone() == (99.0, 800000, "vendor.health", "Watch Health")
    database.close()
    return {"from": 6, "to": 7, "schema_matches": True, "history_and_archives_preserved": True, "imported_provenance_and_capture_time_retained": True}

def run_legacy_migration(database, version):
    # Execute production table names, archive template, RENAME and CREATE SQL.
    # SQLite ALTER has no IF EXISTS, so mirror only the production existence guard.
    for table in legacy_tables:
        if not table_exists(database, table):
            continue
        archive = archive_template.replace("${fromVersion}", str(version)).replace("$table", table)
        assert not table_exists(database, archive), "Production migration rejects archive collisions"
        database.execute(rename_sql.replace("$table", table).replace("$archive", archive))
    for sql in schema6_sql:
        database.execute(sql)
    for sql in sql6to7:
        database.execute(sql)

def check_legacy_path(version, fixture):
    database = sqlite3.connect(":memory:")
    selected = {"empty": [], "sparse": ["heart_rate", "spo2"], "all": tables}[fixture]
    if fixture != "empty":
        database.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        database.execute("INSERT INTO room_master_table VALUES (42, 'original_unknown_identity')")
    database.execute("CREATE TABLE original_extra_data (entry TEXT)")
    database.execute("INSERT INTO original_extra_data VALUES ('unrelated original data')")
    original = {}
    for table in selected:
        # Deliberately incompatible old shapes: migration must not assume their meaning.
        database.execute(f"CREATE TABLE `{table}` (old_id INTEGER PRIMARY KEY AUTOINCREMENT, arbitrary_value BLOB, optional_note TEXT)")
        database.execute(f"INSERT INTO `{table}` (arbitrary_value, optional_note) VALUES (?, ?)", (b"\x00\xfforiginal", "original\nvalue"))
        database.execute(f"INSERT INTO `{table}` (arbitrary_value, optional_note) VALUES (?, ?)", (None, None))
        database.execute(f"CREATE UNIQUE INDEX `old_{table}_index` ON `{table}` (optional_note)")
        if table == "workouts":
            database.execute("CREATE TRIGGER original_workout_trigger AFTER INSERT ON workouts BEGIN INSERT INTO original_extra_data VALUES ('old workout inserted'); END")
        original[table] = {
            "columns": database.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
            "indices": index_schema(database, table),
            "rows": database.execute(f"SELECT * FROM `{table}` ORDER BY old_id").fetchall(),
        }
    database.execute(f"PRAGMA user_version = {version}")
    database.commit()
    database.execute("BEGIN")
    with database:
        run_legacy_migration(database, version)
        for table in tables:
            assert database.execute(f"PRAGMA table_info(`{table}`)").fetchall() == expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(), (version, fixture, table, "columns/defaults/primary keys")
            assert index_schema(database, table) == index_schema(expected, table), (version, fixture, table, "indices")
            assert database.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == expected.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall()
            assert database.execute(f"SELECT COUNT(*) FROM `{table}`").fetchone() == (0,), "No guessed active health recovery"
            archive = f"legacy_v{version}_{table}"
            if table not in original:
                assert not table_exists(database, archive), "Absent tables do not get invented archives"
                continue
            before = original[table]
            assert database.execute(f"PRAGMA table_info(`{archive}`)").fetchall() == before["columns"]
            assert index_schema(database, archive) == before["indices"]
            assert database.execute(f"SELECT * FROM `{archive}` ORDER BY old_id").fetchall() == before["rows"]
            database.execute(f"INSERT INTO `{archive}` (optional_note) VALUES ('later archive insert')")
            assert database.execute(f"SELECT MAX(old_id) FROM `{archive}`").fetchone() == (3,), "Autoincrement state preserved"
        if fixture != "empty":
            assert database.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone() == ("original_unknown_identity",), "Leave identity management to Room"
        else:
            assert not table_exists(database, "room_master_table"), "Migration must not invent Room metadata"
        # Room performs its generated identity statements after validating the migration.
        for sql in quoted_sql(generated.read_text(encoding="utf-8")):
            if "room_master_table" in sql and not sql.startswith("DROP"):
                database.execute(sql)
        assert database.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()[0] != "original_unknown_identity"
        expected_extra = [("unrelated original data",)] + ([("old workout inserted",)] if "workouts" in original else [])
        assert database.execute("SELECT entry FROM original_extra_data ORDER BY rowid").fetchall() == expected_extra, "Original trigger and unrelated data preserved"
    database.close()
    return {"from": version, "to": 7, "fixture": fixture, "schema_matches": True, "original_rows_columns_indices_preserved": True, "no_guessed_health_recovery": True}

def check_archive_collision():
    database = sqlite3.connect(":memory:")
    database.execute("CREATE TABLE heart_rate (original_value BLOB)")
    database.execute("INSERT INTO heart_rate VALUES (X'00FF')")
    database.execute("CREATE TABLE workouts (original_value TEXT)")
    database.execute("INSERT INTO workouts VALUES ('original workout')")
    database.execute("CREATE TABLE legacy_v1_workouts (archived_value TEXT)")
    database.execute("INSERT INTO legacy_v1_workouts VALUES ('pre-existing archive')")
    database.commit()
    try:
        database.execute("BEGIN")
        with database:
            run_legacy_migration(database, 1)
    except AssertionError:
        pass
    else:
        raise AssertionError("Archive collision must stop migration without overwriting records")
    assert database.execute("SELECT original_value FROM heart_rate").fetchone() == (b"\x00\xff",)
    assert database.execute("SELECT original_value FROM workouts").fetchone() == ("original workout",)
    assert database.execute("SELECT archived_value FROM legacy_v1_workouts").fetchone() == ("pre-existing archive",)
    assert not table_exists(database, "legacy_v1_heart_rate"), "Failed migration must roll back earlier renames"
    database.close()
    return {"archive_collision_rejected": True, "original_records_preserved": True, "partial_migration_rolled_back": True}

def generated_queries(dao):
    source = generated.parent / f"{dao}_Impl.java"
    return [json.loads('"' + value + '"') for value in re.findall(r'final String _sql = "((?:\\.|[^"\\])*)";', source.read_text(encoding="utf-8"))]

def check_captured_source_queries():
    database = fresh_database()
    for row in [
        (1, 75, 3, 900000, "WATCH_SENSOR", "", ""),
        (2, 72, 0, 800000, "WATCH_HEALTH_APP", "vendor.health", "Watch Health"),
        (3, 71, 0, 950000, "WATCH_SENSOR", "", ""),
        (4, 72, 0, 975000, "WATCH_HEALTH_APP", "", ""),
        (5, 72, 3, 1000000, "LEGACY_UNVERIFIED", "", ""),
        (6, 72, 0, 900000, "WATCH_HEALTH_APP", "vendor.health", "Watch Health"),
    ]:
        database.execute("INSERT INTO heart_rate VALUES (?, ?, ?, ?, ?, ?, ?)", row)
    for row in [
        (1, 98, 900000, "WATCH_SENSOR", "", ""),
        (2, 99, 800000, "WATCH_HEALTH_APP", "vendor.health", "Watch Health"),
        (3, 99, 975000, "WATCH_HEALTH_APP", "", ""),
        (4, 99, 1000000, "LEGACY_UNVERIFIED", "", ""),
        (5, 99, 900000, "WATCH_HEALTH_APP", "vendor.health", "Watch Health"),
    ]:
        database.execute("INSERT INTO spo2 VALUES (?, ?, ?, ?, ?, ?)", row)
    heart_queries = generated_queries("HeartRateDao")
    oxygen_queries = generated_queries("SpO2Dao")
    latest_heart = next(sql for sql in heart_queries if "LIMIT 1" in sql)
    latest_oxygen = next(sql for sql in oxygen_queries if "LIMIT 1" in sql)
    assert database.execute(latest_heart).fetchone()[0] == 1, "Prefer sensor on equal capture times; unreliable/legacy rows stay excluded"
    assert database.execute(latest_oxygen).fetchone()[0] == 1, "Keep legacy oxygen excluded; prefer sensor on equal capture times"
    heart_history = next(sql for sql in heart_queries if sql.startswith("SELECT *") and "timestamp >= ?" in sql)
    oxygen_history = next(sql for sql in oxygen_queries if sql.startswith("SELECT *") and "timestamp >= ?" in sql)
    assert sorted(row[0] for row in database.execute(heart_history, (700000,))) == [1, 2, 6]
    assert sorted(row[0] for row in database.execute(oxygen_history, (700000,))) == [1, 2, 5]
    workout_heart = next(sql for sql in heart_queries if "timestamp BETWEEN ? AND ?" in sql)
    assert [row[0] for row in database.execute(workout_heart, (700000, 850000))] == [2], "Workout queries accept captured app HR without inventing accuracy"
    heart_dedup = next(sql for sql in heart_queries if sql.startswith("SELECT EXISTS"))
    oxygen_dedup = next(sql for sql in oxygen_queries if sql.startswith("SELECT EXISTS"))
    assert database.execute(heart_dedup, (800000, 72, "WATCH_HEALTH_APP", "vendor.health")).fetchone() == (1,)
    assert database.execute(heart_dedup, (800000, 72, "WATCH_HEALTH_APP", "other.app")).fetchone() == (0,)
    assert database.execute(heart_dedup, (950000, 71, "WATCH_SENSOR", "")).fetchone() == (0,), "Unreliable legacy sensor row cannot suppress a valid replay"
    assert database.execute(oxygen_dedup, (800000, 99, "WATCH_HEALTH_APP", "vendor.health")).fetchone() == (1,)
    assert database.execute(oxygen_dedup, (800000, 99, "WATCH_SENSOR", "")).fetchone() == (0,)
    database.close()
    return {"generated_room_queries_verified": True, "captured_app_records_included": True, "invalid_and_legacy_records_excluded": True, "dedup_includes_source_identity": True}

print(json.dumps({"sqlite_version": sqlite3.sqlite_version, "migrations": [check_path(4), check_path(5), check_source_migration()],
                  "legacy_preservation": [check_legacy_path(v, fixture) for v in (1, 2, 3) for fixture in ("empty", "sparse", "all")],
                  "archive_collision": check_archive_collision(), "captured_source_queries": check_captured_source_queries()}, indent=2))
