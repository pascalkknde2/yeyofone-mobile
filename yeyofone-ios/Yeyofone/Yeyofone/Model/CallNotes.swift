//
//  CallNotes.swift
//  The AI assistant's call notes: transcribed on this iPhone with Apple's speech recogniser, then
//  boiled down with plain heuristics. Mirrors yeyofone-android ai/CallNotes.kt.
//

import Foundation
import Speech

struct CallNotes: Equatable, Sendable {
    var summary: String
    var actionItems: [String]
    var transcript: String

    var isEmpty: Bool { transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}

enum NotesState: Equatable {
    case working
    case ready(CallNotes)
    case failed(String)
}

/// Keeps the sentences that carry the most frequent content words as the summary and picks out
/// sentences that sound like a commitment or request as follow-ups. Heuristics, not a minute-taker.
enum NotesSummarizer {
    private static let stopWords: Set<String> = [
        "the", "and", "that", "this", "with", "have", "for", "you", "your", "are", "was", "were", "but", "not",
        "what", "when", "where", "which", "who", "how", "can", "could", "would", "should", "will", "just", "like",
        "yeah", "okay", "well", "got", "get", "going", "gonna", "from", "they", "them", "then", "there", "here",
        "about", "because", "been", "had", "has", "his", "her", "its", "our", "out", "all", "any", "some", "very",
        "really", "also", "too", "yes", "know", "think", "mean", "right", "sure", "thanks", "thank", "hello",
    ]
    private static let followUp = try! NSRegularExpression(
        pattern: "\\b(i'?ll|i will|we'?ll|we will|let me|i'?m going to|i need to|we need to|you need to|can you|could you|"
            + "would you|please|remind me|don'?t forget|follow up|get back to|call (you )?back|send (you|me|it|them)|"
            + "email (you|me|it)|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|noon|"
            + "the end of))\\b",
        options: .caseInsensitive)
    private static let maxSummarySentences = 3
    private static let maxActionItems = 5

    static func summarize(_ segments: [String]) -> CallNotes {
        let sentences = segments.flatMap(splitSentences)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.split(whereSeparator: \.isWhitespace).count >= 3 }
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
        guard !sentences.isEmpty else {
            return CallNotes(summary: "", actionItems: [], transcript: segments.joined(separator: " ").trimmingCharacters(in: .whitespaces))
        }
        var frequency: [String: Int] = [:]
        for sentence in sentences { for word in contentWords(sentence) { frequency[word, default: 0] += 1 } }
        let scored = sentences.enumerated().map { index, sentence -> (Int, String, Double) in
            let words = contentWords(sentence)
            let total = words.reduce(0) { $0 + (frequency[$1] ?? 0) }
            let score = words.isEmpty ? 0 : Double(total) / pow(Double(words.count), 0.6)
            return (index, sentence, score)
        }
        let keep = min(sentences.count, maxSummarySentences)
        let summary = scored.sorted { $0.2 > $1.2 }.prefix(keep).sorted { $0.0 < $1.0 }
            .map { ensureEnd($0.1) }.joined(separator: " ")
        let actions = sentences.filter(isFollowUp).prefix(maxActionItems).map(ensureEnd)
        return CallNotes(summary: summary, actionItems: Array(actions), transcript: sentences.map(ensureEnd).joined(separator: " "))
    }

    private static func splitSentences(_ text: String) -> [String] {
        var result: [String] = []
        text.enumerateSubstrings(in: text.startIndex..., options: .bySentences) { sentence, _, _, _ in
            if let sentence { result.append(sentence) }
        }
        return result
    }

    private static func contentWords(_ sentence: String) -> [String] {
        sentence.lowercased().split { !($0.isLetter || $0 == "'") }
            .map(String.init).filter { $0.count >= 3 && !stopWords.contains($0) }
    }

    private static func isFollowUp(_ sentence: String) -> Bool {
        followUp.firstMatch(in: sentence, range: NSRange(sentence.startIndex..., in: sentence)) != nil
    }

    private static func ensureEnd(_ sentence: String) -> String {
        guard let last = sentence.last else { return sentence }
        return ".!?".contains(last) ? sentence : sentence + "."
    }
}

/// Transcribes a call recording entirely on the device.
enum CallNotesService {
    enum NotesError: LocalizedError {
        case notAuthorized, unavailable, emptyRecording
        var errorDescription: String? {
            switch self {
            case .notAuthorized: String(localized: "Speech recognition is turned off for YeyoFone in Settings.")
            case .unavailable: String(localized: "On-device speech recognition isn't available on this iPhone.")
            case .emptyRecording: String(localized: "The recording is empty.")
            }
        }
    }

    static func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0 == .authorized) }
        }
    }

    static func process(recording url: URL) async -> NotesState {
        do {
            try await awaitFinalized(url)
            let text = try await transcribe(url)
            return .ready(NotesSummarizer.summarize([text]))
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// The engine closes the WAV when the call disconnects; wait until its size stops changing.
    private static func awaitFinalized(_ url: URL) async throws {
        var last: Int64 = -1
        for _ in 0..<20 {
            let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap { $0 }.map(Int64.init) ?? 0
            if size > 44, size == last { return }
            last = size
            try await Task.sleep(for: .milliseconds(500))
        }
        throw NotesError.emptyRecording
    }

    private static func transcribe(_ url: URL) async throws -> String {
        guard await requestAuthorization() else { throw NotesError.notAuthorized }
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US")),
              recognizer.isAvailable, recognizer.supportsOnDeviceRecognition else { throw NotesError.unavailable }
        let request = SFSpeechURLRecognitionRequest(url: url)
        request.shouldReportPartialResults = false
        request.requiresOnDeviceRecognition = true
        request.addsPunctuation = true
        return try await withCheckedThrowingContinuation { continuation in
            var finished = false
            recognizer.recognitionTask(with: request) { result, error in
                guard !finished else { return }
                if let result, result.isFinal {
                    finished = true
                    continuation.resume(returning: result.bestTranscription.formattedString)
                } else if let error {
                    finished = true
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
