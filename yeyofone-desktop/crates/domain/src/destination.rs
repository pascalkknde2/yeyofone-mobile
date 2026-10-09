//! Deliberately bounded dialing subset; no network or credentials.
#[derive(Debug, PartialEq, Eq)]
pub struct Destination {
    pub kind: &'static str,
    pub normalized: String,
}
/// Numeric extensions (1–6 digits), phone numbers (7–15 digits or +),
/// and explicit SIP URIs. Never infer a country code or silently discard letters.
pub fn parse_destination(input: &str) -> Result<Destination, crate::DomainError> {
    let bad = crate::DomainError::InvalidInput;
    if input.len() > 512 || input.chars().any(char::is_control) {
        return Err(bad);
    }
    let input = input.trim();
    if input.starts_with("sip:") || input.starts_with("sips:") {
        let (scheme, address) = input.split_once(':').ok_or(bad)?;
        let (user, authority) = address.split_once('@').ok_or(bad)?;
        if user.is_empty()
            || user.len() > 128
            || !user
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"-_.+".contains(&b))
        {
            return Err(bad);
        }
        // Headers, passwords, display names and URI parameters are unsupported.
        let (host, port) = match authority.split_once(':') {
            Some((h, p)) => (h, Some(p)),
            None => (authority, None),
        };
        if host.is_empty()
            || host.len() > 253
            || !host.split('.').all(|label| {
                !label.is_empty()
                    && label.len() <= 63
                    && !label.starts_with('-')
                    && !label.ends_with('-')
                    && label
                        .bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b == b'-')
            })
        {
            return Err(bad);
        }
        let suffix = if let Some(port) = port {
            if port.is_empty() || !port.bytes().all(|b| b.is_ascii_digit()) {
                return Err(bad);
            }
            let n = port.parse::<u16>().map_err(|_| bad)?;
            if n == 0 {
                return Err(bad);
            }
            format!(":{n}")
        } else {
            String::new()
        };
        return Ok(Destination {
            kind: "sip_uri",
            normalized: format!("{scheme}:{user}@{}{suffix}", host.to_ascii_lowercase()),
        });
    }
    // SIP PBX feature codes (for example *97 or *98) route to server-hosted
    // services such as voicemail. Keep the grammar limited to DTMF characters.
    if (2..=32).contains(&input.len())
        && input
            .bytes()
            .all(|b| b.is_ascii_digit() || b == b'*' || b == b'#')
        && (input.contains('*') || input.contains('#'))
    {
        return Ok(Destination {
            kind: "service_code",
            normalized: input.to_string(),
        });
    }
    if input.is_empty()
        || !input
            .bytes()
            .all(|b| b.is_ascii_digit() || b"+ ().-".contains(&b))
    {
        return Err(bad);
    }
    let normalized: String = input.chars().filter(|c| !" ().-".contains(*c)).collect();
    let digits = normalized.strip_prefix('+').unwrap_or(&normalized);
    if digits.is_empty() || digits.len() > 15 || !digits.bytes().all(|b| b.is_ascii_digit()) {
        return Err(bad);
    }
    Ok(Destination {
        kind: if normalized.starts_with('+') || digits.len() > 6 {
            "phone_number"
        } else {
            "extension"
        },
        normalized,
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preserves_extensions_and_normalizes_phone_formatting() {
        for (raw, kind, value) in [
            ("1005", "extension", "1005"),
            ("001005", "extension", "001005"),
            ("+44 (7700) 900-010", "phone_number", "+447700900010"),
            ("07700 900010", "phone_number", "07700900010"),
        ] {
            let d = parse_destination(raw).unwrap();
            assert_eq!((d.kind, d.normalized.as_str()), (kind, value));
        }
    }
    #[test]
    fn normalizes_explicit_uri() {
        assert_eq!(
            parse_destination("sips:1005@Example.COM:05061")
                .unwrap()
                .normalized,
            "sips:1005@example.com:5061"
        );
    }
    #[test]
    fn rejects_ambiguous_and_injected_inputs() {
        for raw in [
            "",
            "+",
            "12+34",
            "1005abc",
            "sip:user:password@example.com",
            "sip:a@x?Subject=hello",
            "sip:a@x;transport=udp",
            "sip:a@x:0",
            "sip:a@x:65536",
            "sip:a@-bad.example",
            "sip:a@x\r\nFrom:evil",
            "Name <sip:a@x>",
            "١٠٠٥",
            "1234567890123456",
            "sip:a@[::1]",
        ] {
            assert!(parse_destination(raw).is_err(), "{raw}");
        }
        assert!(parse_destination(&"1".repeat(513)).is_err());
    }
}
