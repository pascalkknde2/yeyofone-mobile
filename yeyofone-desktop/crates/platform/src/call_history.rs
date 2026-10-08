use rusqlite::{Connection, OptionalExtension, params};
use yeyofone_application::calls::{CallHistoryEntry, CallStatus};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HistoryError {
    InvalidInput,
    StorageUnavailable,
}
impl std::fmt::Display for HistoryError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(match self {
            Self::InvalidInput => "Invalid call history input",
            Self::StorageUnavailable => "Call history storage unavailable",
        })
    }
}
impl std::error::Error for HistoryError {}

pub struct SqliteCallHistory(Connection);

impl SqliteCallHistory {
    pub fn open(path: &std::path::Path) -> Result<Self, HistoryError> {
        let db = Connection::open(path).map_err(|_| HistoryError::StorageUnavailable)?;
        db.busy_timeout(std::time::Duration::from_secs(3))
            .map_err(|_| HistoryError::StorageUnavailable)?;
        db.execute_batch(
            "CREATE TABLE IF NOT EXISTS call_history (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                direction TEXT NOT NULL CHECK(direction IN ('incoming','outgoing')),
                remote_party TEXT NOT NULL,
                state TEXT NOT NULL,
                reason TEXT,
                sip_code INTEGER,
                started_at_ms INTEGER NOT NULL,
                ended_at_ms INTEGER,
                duration_seconds INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS call_history_started
                ON call_history(started_at_ms DESC);
            CREATE TABLE IF NOT EXISTS recording_storage (
                history_id TEXT PRIMARY KEY,
                endpoint TEXT,
                object_path TEXT,
                verified INTEGER NOT NULL DEFAULT 0,
                deleting INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            PRAGMA journal_mode=WAL;",
        )
        .map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(Self(db))
    }

    pub fn record(&mut self, call: &CallStatus, updated_at_ms: i64) -> Result<(), HistoryError> {
        if call.id.is_empty()
            || call.id.len() > 64
            || !call
                .id
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
            || call.account_id.is_empty()
            || call.account_id.len() > 64
            || call.destination.len() > 512
        {
            return Err(HistoryError::InvalidInput);
        }
        let record_id = format!("{}:{}", call.id, call.started_at_ms);
        let ended_at = (call.state == "ended").then_some(updated_at_ms);
        self.0
            .execute(
                "INSERT INTO call_history (
                    id, session_id, account_id, direction, remote_party, state,
                    reason, sip_code, started_at_ms, ended_at_ms, duration_seconds
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)
                ON CONFLICT(id) DO UPDATE SET
                    account_id=excluded.account_id,
                    direction=excluded.direction,
                    remote_party=excluded.remote_party,
                    state=excluded.state,
                    reason=excluded.reason,
                    sip_code=excluded.sip_code,
                    ended_at_ms=COALESCE(call_history.ended_at_ms, excluded.ended_at_ms),
                    duration_seconds=excluded.duration_seconds
                WHERE call_history.state IS NOT excluded.state
                   OR call_history.reason IS NOT excluded.reason
                   OR call_history.sip_code IS NOT excluded.sip_code
                   OR call_history.duration_seconds IS NOT excluded.duration_seconds
                   OR call_history.ended_at_ms IS NULL AND excluded.ended_at_ms IS NOT NULL",
                params![
                    record_id,
                    call.id,
                    call.account_id,
                    call.direction,
                    call.caller.as_deref().unwrap_or(&call.destination),
                    call.state,
                    call.reason,
                    call.sip_code,
                    call.started_at_ms,
                    ended_at,
                    call.duration_seconds.min(i64::MAX as u64) as i64,
                ],
            )
            .map_err(|_| HistoryError::StorageUnavailable)?;
        self.0
            .execute(
                "DELETE FROM call_history WHERE id NOT IN
                    (SELECT id FROM call_history ORDER BY started_at_ms DESC, id DESC LIMIT 5000)",
                [],
            )
            .map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }

    pub fn recording_id(&self, history_id: &str) -> Result<Option<String>, HistoryError> {
        self.0
            .query_row(
                "SELECT session_id, started_at_ms FROM call_history WHERE id=?1",
                [history_id],
                |row| {
                    Ok(format!(
                        "{}-{}",
                        row.get::<_, String>(0)?,
                        row.get::<_, i64>(1)?
                    ))
                },
            )
            .optional()
            .map_err(|_| HistoryError::StorageUnavailable)
    }

    pub fn storage(&self, id: &str) -> Result<RecordingStorage, HistoryError> {
        self.0.query_row("SELECT endpoint, object_path, deleting, deleted, verified FROM recording_storage WHERE history_id=?1", [id], |row| Ok(RecordingStorage {
            endpoint: row.get(0)?, object_path: row.get(1)?, deleting: row.get(2)?, deleted: row.get(3)?, verified: row.get(4)?,
        })).optional().map(|row| row.unwrap_or_default()).map_err(|_| HistoryError::StorageUnavailable)
    }
    pub fn uploaded(
        &mut self,
        id: &str,
        endpoint: &str,
        object_path: &str,
    ) -> Result<(), HistoryError> {
        self.0.execute("INSERT INTO recording_storage(history_id,endpoint,object_path,verified) VALUES(?1,?2,?3,1) ON CONFLICT(history_id) DO UPDATE SET endpoint=excluded.endpoint, object_path=excluded.object_path, verified=1 WHERE deleted=0 AND deleting=0", params![id, endpoint, object_path]).map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }
    pub fn prepare_upload(
        &mut self,
        id: &str,
        endpoint: &str,
        object_path: &str,
    ) -> Result<(), HistoryError> {
        self.0.execute("INSERT INTO recording_storage(history_id,endpoint,object_path) VALUES(?1,?2,?3) ON CONFLICT(history_id) DO UPDATE SET endpoint=excluded.endpoint, object_path=excluded.object_path WHERE verified=0 AND deleted=0 AND deleting=0", params![id,endpoint,object_path]).map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }
    pub fn begin_delete(&mut self, id: &str) -> Result<(), HistoryError> {
        self.0.execute("INSERT INTO recording_storage(history_id,deleting) VALUES(?1,1) ON CONFLICT(history_id) DO UPDATE SET deleting=1", [id]).map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }
    pub fn finish_delete(&mut self, id: &str) -> Result<(), HistoryError> {
        self.0.execute("UPDATE recording_storage SET deleted=1, deleting=0, verified=0, endpoint=NULL, object_path=NULL WHERE history_id=?1", [id]).map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }

    pub fn recent(&self, limit: usize) -> Result<Vec<CallHistoryEntry>, HistoryError> {
        let mut query = self
            .0
            .prepare(
                "SELECT id, account_id, direction, remote_party, state, reason,
                        sip_code, started_at_ms, ended_at_ms, duration_seconds
                        , session_id
                 FROM call_history ORDER BY started_at_ms DESC, id DESC LIMIT ?1",
            )
            .map_err(|_| HistoryError::StorageUnavailable)?;
        let rows = query
            .query_map([limit.clamp(1, 1000) as i64], |row| {
                Ok(CallHistoryEntry {
                    id: row.get(0)?,
                    account_id: row.get(1)?,
                    direction: row.get(2)?,
                    remote_party: row.get(3)?,
                    state: row.get(4)?,
                    reason: row.get(5)?,
                    sip_code: row.get(6)?,
                    started_at_ms: row.get(7)?,
                    ended_at_ms: row.get(8)?,
                    duration_seconds: row.get::<_, i64>(9)?.max(0) as u64,
                    recording_id: format!(
                        "{}-{}",
                        row.get::<_, String>(10)?,
                        row.get::<_, i64>(7)?
                    ),
                    recording_available: false,
                    recording_cloud: false,
                    recording_deleted: false,
                    recording_pending_delete: false,
                })
            })
            .map_err(|_| HistoryError::StorageUnavailable)?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| HistoryError::StorageUnavailable)
    }
}

#[derive(Default, Clone)]
pub struct RecordingStorage {
    pub endpoint: Option<String>,
    pub object_path: Option<String>,
    pub verified: bool,
    pub deleting: bool,
    pub deleted: bool,
}

#[cfg(test)]
mod recording_tests {
    use super::*;
    #[test]
    fn pending_upload_and_deletion_survive_restart_and_cannot_be_resurrected() {
        let path = std::env::temp_dir().join(format!(
            "yeyofone-cloud-history-{}-{}.sqlite3",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        {
            let mut history = SqliteCallHistory::open(&path).unwrap();
            history
                .prepare_upload("call:1", "https://one.workers.dev", "/recordings/one.wav")
                .unwrap();
            let state = history.storage("call:1").unwrap();
            assert!(!state.verified);
            assert_eq!(state.object_path.as_deref(), Some("/recordings/one.wav"));
        }
        {
            let mut history = SqliteCallHistory::open(&path).unwrap();
            assert!(history.storage("call:1").unwrap().object_path.is_some());
            history
                .uploaded("call:1", "https://one.workers.dev", "/recordings/one.wav")
                .unwrap();
            assert!(history.storage("call:1").unwrap().verified);
            history.begin_delete("call:1").unwrap();
        }
        {
            let mut history = SqliteCallHistory::open(&path).unwrap();
            assert!(history.storage("call:1").unwrap().deleting);
            history.finish_delete("call:1").unwrap();
            history
                .uploaded("call:1", "https://one.workers.dev", "/recordings/one.wav")
                .unwrap();
            history
                .prepare_upload("call:1", "https://one.workers.dev", "/recordings/one.wav")
                .unwrap();
            let state = history.storage("call:1").unwrap();
            assert!(state.deleted);
            assert!(!state.verified);
            assert!(!state.deleting);
            assert!(state.object_path.is_none());
        }
        std::fs::remove_file(path).unwrap();
    }
}
