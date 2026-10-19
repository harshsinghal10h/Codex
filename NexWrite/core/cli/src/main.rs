// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use nw_engine::Engine;
use serde_json::json;
use std::{env, fs, process::ExitCode, time::Instant};

fn run() -> Result<(), String> {
    let args: Vec<_> = env::args_os().skip(1).collect();
    let command = args
        .first()
        .map(|arg| arg.to_str().ok_or("command must use valid Unicode"))
        .transpose()?;
    match command {
        Some("schema") if args.len() == 1 => println!("{}", nw_schema::SCHEMA),
        Some("rules") if args.get(1).and_then(|arg| arg.to_str()) == Some("test") && args.len() == 2 => {
            let cases = nw_rules::Rule::compile(nw_rules::BUNDLED_RULE)?.test_examples()?;
            println!("{}", json!({"rules": 1, "cases_passed": cases}));
        }
        Some("bench") if args.len() == 1 => bench()?,
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
        _ => return Err("usage: nw check FILE --format json [--doc-version N] [--locale en-US] | rules test | schema | bench".into()),
    }
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
