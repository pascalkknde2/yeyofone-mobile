//! Stored AI notes (transcript, talk stats and Claude's notes) for calls and voicemails, with
//! full-text search over what was said. Values are JSON produced by the `yeyofone-ai` crate;
//! this module stores them as opaque text.

use crate::call_history::{HistoryError, SqliteCallHistory};
use rusqlite::{Connection, OptionalExtension, params};

pub(crate) fn migrate(db: &Connection) -> Result<(), HistoryError> {
    db.execute_batch(
        "CREATE TABLE IF NOT EXISTS ai_notes (
            item_id TEXT PRIMARY KEY,
            source TEXT NOT NULL CHECK(source IN ('call','voicemail')),
            status TEXT NOT NULL CHECK(status IN ('queued','transcribing','summarising','done','failed')),
            error TEXT,
            transcript TEXT,
            stats TEXT,
            notes TEXT,
            model TEXT,
            updated_at_ms INTEGER NOT NULL
        );
        CREATE VIRTUAL TABLE IF NOT EXISTS ai_notes_search USING fts5(item_id UNINDEXED, body);",
    )
    .map_err(|_| HistoryError::StorageUnavailable)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AiRecord {
    pub item_id: String,
    pub source: String,
    pub status: String,
    pub error: Option<String>,
    pub transcript: Option<String>,
    pub stats: Option<String>,
    pub notes: Option<String>,
    pub model: Option<String>,
    pub updated_at_ms: i64,
}

/// One item's notes status, without the large text columns.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AiStatusRow {
    pub item_id: String,
    pub source: String,
    pub status: String,
    pub error: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SearchHit {
    pub item_id: String,
    pub source: String,
    pub snippet: String,
}

fn valid_item(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 128
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"-:_.".contains(&b))
}

fn valid_source(source: &str) -> bool {
    source == "call" || source == "voicemail"
}

/// Turns free text into an FTS5 query that matches every word (as a prefix), so user input
/// can't use FTS5 operators.
pub fn fts_query(text: &str) -> Option<String> {
    let terms: Vec<String> = text
        .split(|c: char| !c.is_alphanumeric())
        .filter(|t| !t.is_empty())
        .take(8)
        .map(|t| format!("\"{}\"*", t.chars().take(40).collect::<String>()))
        .collect();
    (!terms.is_empty()).then(|| terms.join(" "))
}

const COLUMNS: &str =
    "item_id, source, status, error, transcript, stats, notes, model, updated_at_ms";

fn record(row: &rusqlite::Row<'_>) -> rusqlite::Result<AiRecord> {
    Ok(AiRecord {
        item_id: row.get(0)?,
        source: row.get(1)?,
        status: row.get(2)?,
        error: row.get(3)?,
        transcript: row.get(4)?,
        stats: row.get(5)?,
        notes: row.get(6)?,
        model: row.get(7)?,
        updated_at_ms: row.get(8)?,
    })
}

impl SqliteCallHistory {
    /// Marks an item queued, transcribing, summarising or failed; keeps any earlier results.
    pub fn ai_set_status(
        &mut self,
        item_id: &str,
        source: &str,
        status: &str,
        error: Option<&str>,
        now_ms: i64,
    ) -> Result<(), HistoryError> {
        if !valid_item(item_id)
            || !valid_source(source)
            || !["queued", "transcribing", "summarising", "failed"].contains(&status)
        {
            return Err(HistoryError::InvalidInput);
        }
        let error = error.map(|e| e.chars().take(300).collect::<String>());
        self.0
            .execute(
                "INSERT INTO ai_notes(item_id, source, status, error, updated_at_ms)
                 VALUES (?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(item_id) DO UPDATE SET status=?3, error=?4, updated_at_ms=?5",
                params![item_id, source, status, error, now_ms],
            )
            .map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }

    /// Saves finished notes and indexes `search_text` (the transcript and notes) for search.
    #[allow(clippy::too_many_arguments)]
    pub fn ai_save(
        &mut self,
        item_id: &str,
        source: &str,
        transcript: &str,
        stats: &str,
        notes: &str,
        model: &str,
        search_text: &str,
        now_ms: i64,
    ) -> Result<(), HistoryError> {
        if !valid_item(item_id) || !valid_source(source) {
            return Err(HistoryError::InvalidInput);
        }
        let tx = self
            .0
            .transaction()
            .map_err(|_| HistoryError::StorageUnavailable)?;
        tx.execute(
            "INSERT INTO ai_notes(item_id, source, status, error, transcript, stats, notes, model, updated_at_ms)
             VALUES (?1, ?2, 'done', NULL, ?3, ?4, ?5, ?6, ?7)
             ON CONFLICT(item_id) DO UPDATE SET status='done', error=NULL, transcript=?3,
                 stats=?4, notes=?5, model=?6, updated_at_ms=?7",
            params![item_id, source, transcript, stats, notes, model, now_ms],
        )
        .and_then(|_| tx.execute("DELETE FROM ai_notes_search WHERE item_id=?1", [item_id]))
        .and_then(|_| {
            tx.execute(
                "INSERT INTO ai_notes_search(item_id, body) VALUES (?1, ?2)",
                params![item_id, search_text],
            )
        })
        .map_err(|_| HistoryError::StorageUnavailable)?;
        tx.commit().map_err(|_| HistoryError::StorageUnavailable)
    }

    pub fn ai_get(&self, item_id: &str) -> Result<Option<AiRecord>, HistoryError> {
        self.0
            .query_row(
                &format!("SELECT {COLUMNS} FROM ai_notes WHERE item_id=?1"),
                [item_id],
                record,
            )
            .optional()
            .map_err(|_| HistoryError::StorageUnavailable)
    }

    /// Every item's status (without the large text columns), for badges and the work queue.
    pub fn ai_statuses(&self) -> Result<Vec<AiStatusRow>, HistoryError> {
        let mut query = self
            .0
            .prepare("SELECT item_id, source, status, error FROM ai_notes ORDER BY updated_at_ms DESC LIMIT 5000")
            .map_err(|_| HistoryError::StorageUnavailable)?;
        let rows = query
            .query_map([], |r| {
                Ok(AiStatusRow {
                    item_id: r.get(0)?,
                    source: r.get(1)?,
                    status: r.get(2)?,
                    error: r.get(3)?,
                })
            })
            .map_err(|_| HistoryError::StorageUnavailable)?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| HistoryError::StorageUnavailable)
    }

    /// Finished notes, newest first, for insights.
    pub fn ai_done(&self, source: &str, limit: usize) -> Result<Vec<AiRecord>, HistoryError> {
        let mut query = self
            .0
            .prepare(&format!(
                "SELECT {COLUMNS} FROM ai_notes WHERE status='done' AND source=?1
                 ORDER BY updated_at_ms DESC LIMIT ?2"
            ))
            .map_err(|_| HistoryError::StorageUnavailable)?;
        let rows = query
            .query_map(params![source, limit.clamp(1, 2000) as i64], record)
            .map_err(|_| HistoryError::StorageUnavailable)?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| HistoryError::StorageUnavailable)
    }

    /// Calls and voicemails whose transcript or notes match every word of `text`.
    pub fn ai_search(&self, text: &str, limit: usize) -> Result<Vec<SearchHit>, HistoryError> {
        let Some(query) = fts_query(text) else {
            return Ok(Vec::new());
        };
        let mut statement = self
            .0
            .prepare(
                "SELECT s.item_id, n.source, snippet(ai_notes_search, 1, '[', ']', '…', 12)
                 FROM ai_notes_search s JOIN ai_notes n ON n.item_id = s.item_id
                 WHERE ai_notes_search MATCH ?1 ORDER BY rank LIMIT ?2",
            )
            .map_err(|_| HistoryError::StorageUnavailable)?;
        let rows = statement
            .query_map(params![query, limit.clamp(1, 200) as i64], |r| {
                Ok(SearchHit {
                    item_id: r.get(0)?,
                    source: r.get(1)?,
                    snippet: r.get(2)?,
                })
            })
            .map_err(|_| HistoryError::StorageUnavailable)?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| HistoryError::StorageUnavailable)
    }

    pub fn ai_delete(&mut self, item_id: &str) -> Result<(), HistoryError> {
        self.0
            .execute("DELETE FROM ai_notes WHERE item_id=?1", [item_id])
            .and_then(|_| {
                self.0
                    .execute("DELETE FROM ai_notes_search WHERE item_id=?1", [item_id])
            })
            .map_err(|_| HistoryError::StorageUnavailable)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn store() -> (SqliteCallHistory, std::path::PathBuf) {
        let dir = std::env::temp_dir().join(format!("yeyofone-ai-notes-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join(format!(
            "{}.sqlite",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        (SqliteCallHistory::open(&path).unwrap(), path)
    }

    #[test]
    fn status_then_save_then_search() {
        let (mut db, path) = store();
        db.ai_set_status("call:1", "call", "queued", None, 1)
            .unwrap();
        db.ai_set_status("call:1", "call", "transcribing", None, 2)
            .unwrap();
        assert_eq!(db.ai_get("call:1").unwrap().unwrap().status, "transcribing");
        db.ai_save(
            "call:1",
            "call",
            "[]",
            "{}",
            "{\"summary\":\"x\"}",
            "claude-sonnet-5-5",
            "Caller: my invoice for order 1042 is wrong",
            3,
        )
        .unwrap();
        db.ai_save(
            "vm:7",
            "voicemail",
            "[]",
            "{}",
            "{}",
            "m",
            "please call me back about the delivery",
            4,
        )
        .unwrap();
        let record = db.ai_get("call:1").unwrap().unwrap();
        assert_eq!((record.status.as_str(), record.error), ("done", None));
        let hits = db.ai_search("invoice ord", 10).unwrap();
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].item_id, "call:1");
        assert!(hits[0].snippet.contains("[invoice]"));
        assert_eq!(db.ai_search("delivery", 10).unwrap()[0].source, "voicemail");
        assert!(
            db.ai_search("\"; DROP TABLE ai_notes; --", 10)
                .unwrap()
                .is_empty()
        );
        assert_eq!(db.ai_done("call", 10).unwrap().len(), 1);
        // Saving again replaces the indexed text instead of duplicating it.
        db.ai_save("call:1", "call", "[]", "{}", "{}", "m", "invoice again", 5)
            .unwrap();
        assert_eq!(db.ai_search("invoice", 10).unwrap().len(), 1);
        db.ai_delete("call:1").unwrap();
        assert!(db.ai_get("call:1").unwrap().is_none());
        assert!(db.ai_search("invoice", 10).unwrap().is_empty());
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn rejects_bad_ids_statuses_and_sources() {
        let (mut db, path) = store();
        assert_eq!(
            db.ai_set_status("bad id", "call", "queued", None, 1),
            Err(HistoryError::InvalidInput)
        );
        assert_eq!(
            db.ai_set_status("call:1", "sms", "queued", None, 1),
            Err(HistoryError::InvalidInput)
        );
        assert_eq!(
            db.ai_set_status("call:1", "call", "done", None, 1),
            Err(HistoryError::InvalidInput)
        );
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn fts_query_quotes_every_word() {
        assert_eq!(fts_query("order 1042"), Some("\"order\"* \"1042\"*".into()));
        assert_eq!(
            fts_query(" \"* OR NEAR( "),
            Some("\"OR\"* \"NEAR\"*".into())
        );
        assert_eq!(fts_query("  !! "), None);
    }
}
