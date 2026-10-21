// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use nw_engine::{Engine, MAX_ANALYSIS_UTF8_BYTES};
use nw_schema::InputFormat;
use serde_json::json;
use std::{env, ffi::OsString, fs, io::Read, process::ExitCode, time::Instant};

fn run() -> Result<(), String> {
    let args: Vec<_> = env::args_os().skip(1).collect();
    let command = args
        .first()
        .map(|arg| arg.to_str().ok_or("command must use valid Unicode"))
        .transpose()?;
    match command {
        Some("schema") if args.len() == 1 => println!("{}", nw_schema::SCHEMA),
        Some("schema") if args.len() == 2 && args[1] == "analysis" => println!("{}", nw_schema::ANALYSIS_SCHEMA),
        Some("rules") if args.get(1).and_then(|arg| arg.to_str()) == Some("test") && args.len() == 2 => {
            let cases = nw_rules::Rule::compile(nw_rules::BUNDLED_RULE)?.test_examples()?;
            println!("{}", json!({"rules": 1, "cases_passed": cases}));
        }
        Some("bench") if args.len() == 1 => bench()?,
        Some("bench") if args.len() == 2 && args[1] == "analysis" => analysis_bench()?,
        Some("analyze") if args.len() >= 2 => analyze(&args[1..])?,
        Some("check") if args.len() >= 2 => {
            let (mut format, mut locale, mut version) = (None, None, None);
            let (options, remainder) = args[2..].as_chunks::<2>();
            for option in options {
                let name = option[0].to_str().ok_or("check option names must use valid Unicode")?;
                let value = option[1].to_str().ok_or("check option values must use valid Unicode")?;
                match name {
                    "--format" if format.is_none() => format = Some(value),
                    "--locale" if locale.is_none() => locale = Some(value),
                    "--doc-version" if version.is_none() => version = Some(value.parse::<u64>().map_err(|_| "invalid document version")?),
                    _ => return Err("unknown or repeated check option".into()),
                }
            }
            if !remainder.is_empty() || format != Some("json") {
                return Err("check requires --format json; options require values".into());
            }
            let bytes = fs::read(&args[1]).map_err(|e| format!("cannot read input file: {e}"))?;
            let text = std::str::from_utf8(&bytes).map_err(|_| "input must be valid UTF-8")?;
            let result = Engine::new()?.check(text, version.unwrap_or(0), locale.unwrap_or("en-US"))?;
            println!("{}", serde_json::to_string(&result).map_err(|e| e.to_string())?);
        }
        _ => return Err(concat!(
            "usage: nw check FILE --format json [--doc-version N] [--locale en-US]\n",
            "       nw analyze FILE --format json [--input-format plain|markdown] [--doc-version N] [--locale en-US]\n",
            "       nw rules test | nw schema [analysis] | nw bench [analysis]"
        ).into()),
    }
    Ok(())
}

fn analyze(args: &[OsString]) -> Result<(), String> {
    let (mut format, mut locale, mut version, mut input_format) = (None, None, None, None);
    let (options, remainder) = args[1..].as_chunks::<2>();
    for option in options {
        let name = option[0]
            .to_str()
            .ok_or("analysis option names must use valid Unicode")?;
        let value = option[1]
            .to_str()
            .ok_or("analysis option values must use valid Unicode")?;
        match name {
            "--format" if format.is_none() => format = Some(value),
            "--locale" if locale.is_none() => locale = Some(value),
            "--doc-version" if version.is_none() => {
                version = Some(
                    value
                        .parse::<u64>()
                        .map_err(|_| "invalid document version")?,
                )
            }
            "--input-format" if input_format.is_none() => input_format = Some(value),
            _ => return Err("unknown or repeated analyze option".into()),
        }
    }
    if !remainder.is_empty() || format != Some("json") {
        return Err("analyze requires --format json; options require values".into());
    }
    let input_format = match input_format.unwrap_or("plain") {
        "plain" => InputFormat::Plain,
        "markdown" => InputFormat::Markdown,
        _ => return Err("unsupported input format; expected plain or markdown".into()),
    };
    let mut bytes = vec![];
    fs::File::open(&args[0])
        .and_then(|file| {
            file.take((MAX_ANALYSIS_UTF8_BYTES + 1) as u64)
                .read_to_end(&mut bytes)
        })
        .map_err(|error| format!("cannot read input file: {error}"))?;
    if bytes.len() > MAX_ANALYSIS_UTF8_BYTES {
        return Err("analysis input exceeds 1048576 UTF-8 byte limit".into());
    }
    let text = std::str::from_utf8(&bytes).map_err(|_| "input must be valid UTF-8")?;
    let report = Engine::new()?.analyze(
        text,
        version.unwrap_or(0),
        locale.unwrap_or("en-US"),
        input_format,
    )?;
    println!(
        "{}",
        serde_json::to_string(&report).map_err(|error| error.to_string())?
    );
    Ok(())
}

fn analysis_bench() -> Result<(), String> {
    let engine = Engine::new()?;
    let mut measurements = vec![];
    for input_format in [InputFormat::Plain, InputFormat::Markdown] {
        for words in [1_000, 5_000] {
            let text = "The cat sat by the log and saw the dog. ".repeat(words / 10);
            let text = if input_format == InputFormat::Markdown {
                format!("{text}\n\n`excluded  code`\n")
            } else {
                text
            };
            for _ in 0..5 {
                std::hint::black_box(engine.analyze(&text, 0, "en-US", input_format)?);
            }
            let mut samples = vec![];
            for _ in 0..50 {
                let start = Instant::now();
                std::hint::black_box(engine.analyze(
                    std::hint::black_box(&text),
                    0,
                    "en-US",
                    input_format,
                )?);
                samples.push(start.elapsed().as_secs_f64() * 1_000.0);
            }
            samples.sort_by(f64::total_cmp);
            measurements.push(json!({"input_format": input_format, "fixture_prose_words": words, "source_utf8_bytes": text.len(), "iterations": 50, "p50_ms": samples[24], "p95_ms": samples[47]}));
        }
    }
    println!(
        "{}",
        json!({"scope": "current-host bounded source projection, Unicode segmentation, metric estimates and one spacing rule; excludes compilation, file I/O and engine initialization", "incremental_implemented": false, "measurements": measurements})
    );
    Ok(())
}

fn bench() -> Result<(), String> {
    let engine = Engine::new()?;
    let mut measurements = vec![];
    for words in [1_000, 5_000] {
        let text = (0..words)
            .map(|i| if i % 10 == 0 { "word  " } else { "word " })
            .collect::<String>();
        for _ in 0..10 {
            std::hint::black_box(engine.check(&text, 0, "en-US")?);
        }
        let mut samples = vec![];
        for _ in 0..100 {
            let start = Instant::now();
            std::hint::black_box(engine.check(std::hint::black_box(&text), 0, "en-US")?);
            samples.push(start.elapsed().as_secs_f64() * 1_000.0);
        }
        samples.sort_by(f64::total_cmp);
        let budget = if words == 1_000 { 30.0 } else { 300.0 };
        measurements.push(json!({"words": words, "iterations": 100, "p50_ms": samples[49], "p95_ms": samples[94], "desktop_reference_budget_ms": budget, "within_reference_budget": samples[94] <= budget}));
    }
    println!(
        "{}",
        json!({"scope": "current-host full-check; single spacing rule; excludes compilation and file I/O", "incremental_implemented": false, "measurements": measurements})
    );
    Ok(())
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("nw: {error}");
            ExitCode::from(2)
        }
    }
}
