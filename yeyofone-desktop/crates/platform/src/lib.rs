//! Platform boundary. OS adapters are deliberately absent until their requested phases.
//! Do not substitute plaintext files or in-memory production credentials for OS keychains.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DesktopPlatform {
    MacOs,
    Windows,
    Linux,
    Unsupported,
}
pub fn current_platform() -> DesktopPlatform {
    if cfg!(target_os = "macos") {
        DesktopPlatform::MacOs
    } else if cfg!(target_os = "windows") {
        DesktopPlatform::Windows
    } else if cfg!(target_os = "linux") {
        DesktopPlatform::Linux
    } else {
        DesktopPlatform::Unsupported
    }
}

pub mod accounts;
pub mod call_history;
