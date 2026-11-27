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
    /// Extensions of the people talking right now, as seen by the organiser.
    var speaking: [String] = []
    /// People who hung up during the conference; they are still shown, as "Left", and can rejoin.
    var left: [RosterMember] = []

    init(host: String, members: [RosterMember], ended: Bool = false, speaking: [String] = [], left: [RosterMember] = []) {
        self.host = host
        self.members = members
        self.ended = ended
        self.speaking = speaking
        self.left = left
    }

    // Older builds don't send `ended`, `speaking` or `left`, so they are optional on the wire.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        v = try c.decode(Int.self, forKey: .v)
        host = try c.decode(String.self, forKey: .host)
        ended = try c.decodeIfPresent(Bool.self, forKey: .ended) ?? false
        members = try c.decode([RosterMember].self, forKey: .members)
        speaking = try c.decodeIfPresent([String].self, forKey: .speaking) ?? []
        left = try c.decodeIfPresent([RosterMember].self, forKey: .left) ?? []
    }

    func json() -> Data? { try? JSONEncoder().encode(self) }

    /// A valid roster from received bytes, or nil. Names are cleaned and the list is capped.
    static func parse(_ data: Data) -> ConferenceRoster? {
        guard let roster = try? JSONDecoder().decode(ConferenceRoster.self, from: data), roster.v == 1,
              isExtension(roster.host) else { return nil }
        func clean(_ list: [RosterMember]) -> [RosterMember] {
            list.prefix(8).compactMap { member -> RosterMember? in
                guard isExtension(member.ext) else { return nil }
                let name = String(member.name.filter { !$0.isNewline && $0.unicodeScalars.allSatisfy { !CharacterSet.controlCharacters.contains($0) } }
                    .trimmingCharacters(in: .whitespaces).prefix(40))
                return RosterMember(ext: member.ext, name: name.isEmpty ? member.ext : name, calling: member.calling)
            }
        }
        let members = clean(roster.members)
        let left = clean(roster.left).filter { gone in !members.contains { $0.ext == gone.ext } }
        return ConferenceRoster(host: roster.host, members: members, ended: roster.ended,
                                speaking: roster.speaking.prefix(8).filter(isExtension), left: left)
    }

    private static func isExtension(_ text: String) -> Bool {
        (1...32).contains(text.count) && text.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || ".+_-".contains($0)) }
    }
}
