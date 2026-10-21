// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use std::{
    fs,
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

fn run(bytes: &[u8], options: &[&str]) -> std::process::Output {
    let path = std::env::temp_dir().join(format!(
        "nw-analysis-{}-{}.txt",
        std::process::id(),
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    fs::write(&path, bytes).unwrap();
    let result = Command::new(env!("CARGO_BIN_EXE_nw"))
        .arg("analyze")
        .arg(&path)
        .args(options)
        .output()
        .unwrap();
    fs::remove_file(path).unwrap();
    result
}

#[test]
fn actual_cli_default_plain_explicit_markdown_and_analysis_schema() {
    let plain = run(
        b"One  two. `code  words`",
        &["--format", "json", "--doc-version", "19"],
    );
    assert!(plain.status.success());
    assert!(plain.stderr.is_empty());
    let plain: nw_schema::AnalysisResult = serde_json::from_slice(&plain.stdout).unwrap();
    assert_eq!(plain.input_format, nw_schema::InputFormat::Plain);
    assert_eq!(plain.suggestions.len(), 2);
    assert_eq!(plain.doc_version, 19);
    let markdown = run(
        b"One  two. `code  words`",
        &["--format", "json", "--input-format", "markdown"],
    );
    assert!(markdown.status.success());
    let markdown: nw_schema::AnalysisResult = serde_json::from_slice(&markdown.stdout).unwrap();
    assert_eq!(markdown.input_format, nw_schema::InputFormat::Markdown);
    assert_eq!(markdown.suggestions.len(), 1);
    for args in [vec!["schema"], vec!["schema", "analysis"]] {
        let output = Command::new(env!("CARGO_BIN_EXE_nw"))
            .args(&args)
            .output()
            .unwrap();
        assert!(output.status.success());
        let value: serde_json::Value = serde_json::from_slice(&output.stdout).unwrap();
        assert_eq!(
            value["title"],
            if args.len() == 1 {
                "Suggestion"
            } else {
                "AnalysisResult"
            }
        );
    }
}

#[test]
fn malformed_options_utf8_and_oversize_fail_without_partial_reports() {
    for options in [
        vec![],
        vec!["--format"],
        vec!["--format", "xml"],
        vec!["--format", "json", "--unknown", "plain"],
        vec!["--format", "json", "--input-format", "html"],
        vec![
            "--format",
            "json",
            "--input-format",
            "plain",
            "--input-format",
            "plain",
        ],
        vec!["--format", "json", "--locale", "en-US", "--locale", "en-US"],
        vec!["--format", "json", "--format", "json"],
        vec![
            "--format",
            "json",
            "--doc-version",
            "0",
            "--doc-version",
            "0",
        ],
        vec!["--format", "json", "--doc-version", "-1"],
        vec!["--format", "json", "--doc-version", "9007199254740992"],
        vec!["--format", "json", "--locale", "fr"],
    ] {
        let result = run(b"private input", &options);
        assert_eq!(result.status.code(), Some(2));
        assert!(result.stdout.is_empty());
        assert!(!String::from_utf8_lossy(&result.stderr).contains("private input"));
    }
    for bytes in [vec![0xff], vec![b'.'; 1_048_577]] {
        let result = run(&bytes, &["--format", "json"]);
        assert_eq!(result.status.code(), Some(2));
        assert!(result.stdout.is_empty());
    }
    let at_limit = run(&vec![b'.'; 1_048_576], &["--format", "json"]);
    assert!(at_limit.status.success());
}

#[cfg(unix)]
#[test]
fn analyze_retains_native_paths_and_rejects_non_unicode_options() {
    use std::{ffi::OsString, os::unix::ffi::OsStringExt};
    let path =
        std::env::temp_dir().join(format!("nw-analysis-native-path-{}-", std::process::id()));
    let mut bytes = path.into_os_string().into_vec();
    // APFS requires valid UTF-8 filenames; Linux filesystems also admit raw bytes.
    if cfg!(target_os = "macos") {
        bytes.extend_from_slice("é👩‍💻".as_bytes());
    } else {
        bytes.push(0xff);
    }
    let path = OsString::from_vec(bytes);
    fs::write(&path, b"One  two").unwrap();
    let success = Command::new(env!("CARGO_BIN_EXE_nw"))
        .arg("analyze")
        .arg(&path)
        .args(["--format", "json"])
        .output()
        .unwrap();
    assert!(success.status.success());
    for args in [
        [OsString::from_vec(vec![0xff]), "json".into()],
        ["--format".into(), OsString::from_vec(vec![0xff])],
    ] {
        let error = Command::new(env!("CARGO_BIN_EXE_nw"))
            .arg("analyze")
            .arg(&path)
            .args(args)
            .output()
            .unwrap();
        assert_eq!(error.status.code(), Some(2));
        assert!(error.stdout.is_empty());
        assert!(
            String::from_utf8(error.stderr)
                .unwrap()
                .contains("valid Unicode")
        );
    }
    fs::remove_file(path).unwrap();
}
