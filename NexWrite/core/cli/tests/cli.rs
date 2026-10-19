// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use std::{
    fs,
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

fn check(bytes: &[u8], options: &[&str]) -> std::process::Output {
    let path = std::env::temp_dir().join(format!(
        "nw-test-{}-{}.txt",
        std::process::id(),
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    fs::write(&path, bytes).unwrap();
    let output = Command::new(env!("CARGO_BIN_EXE_nw"))
        .arg("check")
        .arg(&path)
        .args(options)
        .output()
        .unwrap();
    fs::remove_file(path).unwrap();
    output
}

#[test]
fn actual_json_cli_and_document_version() {
    let output = check(
        "👩‍💻  سلام".as_bytes(),
        &["--format", "json", "--doc-version", "7"],
    );
    assert!(output.status.success());
    assert!(output.stderr.is_empty());
    let suggestions: Vec<nw_schema::Suggestion> = serde_json::from_slice(&output.stdout).unwrap();
    assert_eq!(suggestions.len(), 1);
    assert_eq!(suggestions[0].doc_version, 7);
    assert_eq!(suggestions[0].span.utf16, [5, 7]);
}

#[test]
fn malformed_input_and_options_fail_without_echoing_text() {
    for (bytes, options) in [
        (&[0xff][..], vec!["--format", "json"]),
        (&b"private input"[..], vec!["--format", "xml"]),
        (
            &b"private input"[..],
            vec!["--format", "json", "--doc-version", "-1"],
        ),
        (
            &b"private input"[..],
            vec!["--format", "json", "--locale", "fr"],
        ),
        (
            &b"private input"[..],
            vec!["--format", "json", "--format", "json"],
        ),
        (&b"private input"[..], vec!["--format"]),
    ] {
        let output = check(bytes, &options);
        assert!(!output.status.success());
        assert!(output.stdout.is_empty());
        assert!(!String::from_utf8_lossy(&output.stderr).contains("private input"));
    }
    let output = Command::new(env!("CARGO_BIN_EXE_nw"))
        .args(["check", "nonexistent-nw-input", "--format", "json"])
        .output()
        .unwrap();
    assert!(!output.status.success());
}

#[cfg(unix)]
#[test]
fn non_utf8_paths_work_and_non_utf8_commands_or_options_fail_cleanly() {
    use std::{ffi::OsString, os::unix::ffi::OsStringExt};
    let mut path = std::env::temp_dir()
        .join(format!(
            "nw-path-{}-{}-",
            std::process::id(),
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ))
        .into_os_string()
        .into_vec();
    path.push(0xff);
    let path = OsString::from_vec(path);
    fs::write(&path, b"One  two").unwrap();
    let output = Command::new(env!("CARGO_BIN_EXE_nw"))
        .arg("check")
        .arg(&path)
        .args(["--format", "json"])
        .output()
        .unwrap();
    fs::remove_file(path).unwrap();
    assert!(output.status.success());
    assert!(output.stderr.is_empty());
    let suggestions: Vec<nw_schema::Suggestion> = serde_json::from_slice(&output.stdout).unwrap();
    assert_eq!(suggestions.len(), 1);
    assert_eq!(suggestions[0].original, "  ");

    let invalid = OsString::from_vec(vec![0xff]);
    for args in [
        vec![invalid.clone()],
        vec![
            "check".into(),
            "unused".into(),
            invalid.clone(),
            "json".into(),
        ],
        vec!["check".into(), "unused".into(), "--format".into(), invalid],
    ] {
        let output = Command::new(env!("CARGO_BIN_EXE_nw"))
            .args(args)
            .output()
            .unwrap();
        assert_eq!(output.status.code(), Some(2));
        assert!(output.stdout.is_empty());
        let diagnostic = String::from_utf8(output.stderr).unwrap();
        assert!(diagnostic.starts_with("nw: "));
        assert!(diagnostic.contains("valid Unicode"));
        assert!(!diagnostic.contains("panicked"));
    }
}
