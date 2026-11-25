import Foundation

/// One person on a conference as the organiser shares them with everyone else on it.
struct RosterMember: Codable, Equatable {
    let ext: String
    let name: String
    let calling: Bool
}

/// Who is on a conference, sent by the organiser to each person on it so everyone sees the same
/// conference screen. `host` is the organiser's extension and is also one of `members`.
/// The JSON matches the Android app's (`application/vnd.yeyofone.conference+json`).
struct ConferenceRoster: Codable, Equatable {
    var v = 1
    let host: String
    var ended = false
    let members: [RosterMember]

    init(host: String, members: [RosterMember], ended: Bool = false) {
        self.host = host
        self.members = members
        self.ended = ended
    }

    func json() -> Data? { try? JSONEncoder().encode(self) }

    /// A valid roster from received bytes, or nil. Names are cleaned and the list is capped.
    static func parse(_ data: Data) -> ConferenceRoster? {
        guard let roster = try? JSONDecoder().decode(ConferenceRoster.self, from: data), roster.v == 1,
              isExtension(roster.host) else { return nil }
        let members = roster.members.prefix(8).compactMap { member -> RosterMember? in
            guard isExtension(member.ext) else { return nil }
            let name = String(member.name.filter { !$0.isNewline && $0.unicodeScalars.allSatisfy { !CharacterSet.controlCharacters.contains($0) } }
                .trimmingCharacters(in: .whitespaces).prefix(40))
            return RosterMember(ext: member.ext, name: name.isEmpty ? member.ext : name, calling: member.calling)
        }
        return ConferenceRoster(host: roster.host, members: members, ended: roster.ended)
    }

    private static func isExtension(_ text: String) -> Bool {
        (1...32).contains(text.count) && text.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || ".+_-".contains($0)) }
    }
}
