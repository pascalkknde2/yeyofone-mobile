//! Whisper speech-to-text models the app can download, pinned by size and SHA-256.

use serde::Serialize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WhisperModel {
    pub id: &'static str,
    pub label: &'static str,
    pub file: &'static str,
    pub bytes: u64,
    pub sha256: &'static str,
}

impl WhisperModel {
    pub fn url(&self) -> String {
        format!(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/{}",
            self.file
        )
    }
}

/// From the Git LFS pointers of huggingface.co/ggerganov/whisper.cpp (checked 2026-10-12).
pub const MODELS: [WhisperModel; 3] = [
    WhisperModel {
        id: "base",
        label: "Base · multilingual · 148 MB",
        file: "ggml-base.bin",
        bytes: 147_951_465,
        sha256: "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
    },
    WhisperModel {
        id: "base.en",
        label: "Base · English only · 148 MB",
        file: "ggml-base.en.bin",
        bytes: 147_964_211,
        sha256: "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
    },
    WhisperModel {
        id: "small",
        label: "Small · multilingual, more accurate · 488 MB",
        file: "ggml-small.bin",
        bytes: 487_601_967,
        sha256: "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
    },
];

pub fn find(id: &str) -> Option<&'static WhisperModel> {
    MODELS.iter().find(|m| m.id == id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_is_well_formed() {
        for model in MODELS {
            assert_eq!(model.sha256.len(), 64);
            assert!(model.sha256.bytes().all(|b| b.is_ascii_hexdigit()));
            assert!(model.url().ends_with(model.file));
            assert_eq!(find(model.id), Some(&model));
        }
        assert_eq!(find("large"), None);
    }
}
