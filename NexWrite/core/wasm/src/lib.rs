// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use wasm_bindgen::prelude::*;

/// Validate JS types and original UTF-16 before crossing the canonical UTF-8 boundary.
#[wasm_bindgen]
pub fn check_json(text: JsValue, doc_version: JsValue, locale: JsValue) -> Result<String, JsValue> {
    let text = source_string(text, None)?;
    let (doc_version, locale) = settings(doc_version, locale)?;
    let result = nw_engine::Engine::new()
        .and_then(|engine| engine.check(&text, doc_version, &locale))
        .and_then(|suggestions| serde_json::to_string(&suggestions).map_err(|e| e.to_string()));
    result.map_err(|error| JsValue::from_str(&error))
}

/// Additive source analysis. All spans refer to the unchanged, strictly decoded source.
#[wasm_bindgen]
pub fn analyze_json(
    text: JsValue,
    doc_version: JsValue,
    locale: JsValue,
    input_format: JsValue,
) -> Result<String, JsValue> {
    let text = source_string(text, Some(nw_engine::MAX_ANALYSIS_UTF8_BYTES))?;
    let (doc_version, locale) = settings(doc_version, locale)?;
    let format = input_format
        .as_string()
        .ok_or_else(|| JsValue::from_str("input format must be a string"))?;
    let format = match format.as_str() {
        "plain" => nw_schema::InputFormat::Plain,
        "markdown" => nw_schema::InputFormat::Markdown,
        _ => {
            return Err(JsValue::from_str(
                "unsupported input format; expected plain or markdown",
            ));
        }
    };
    nw_engine::Engine::new()
        .and_then(|engine| engine.analyze(&text, doc_version, &locale, format))
        .and_then(|report| serde_json::to_string(&report).map_err(|error| error.to_string()))
        .map_err(|error| JsValue::from_str(&error))
}

fn source_string(text: JsValue, limit: Option<usize>) -> Result<String, JsValue> {
    let text: js_sys::JsString = text
        .dyn_into()
        .map_err(|_| JsValue::from_str("text must be a string"))?;
    if limit.is_some_and(|maximum| text.length() as usize > maximum) {
        return Err(JsValue::from_str(
            "analysis input exceeds 1048576 UTF-8 byte limit",
        ));
    }
    let units = (0..text.length()).map(|index| text.char_code_at(index) as u16);
    let mut output = String::new();
    for character in char::decode_utf16(units) {
        let character = character
            .map_err(|_| JsValue::from_str("text contains an unpaired UTF-16 surrogate"))?;
        if limit.is_some_and(|maximum| output.len() + character.len_utf8() > maximum) {
            return Err(JsValue::from_str(
                "analysis input exceeds 1048576 UTF-8 byte limit",
            ));
        }
        output.push(character);
    }
    Ok(output)
}

fn settings(doc_version: JsValue, locale: JsValue) -> Result<(u64, String), JsValue> {
    let locale = locale
        .as_string()
        .ok_or_else(|| JsValue::from_str("locale must be a string"))?;
    let doc_version = doc_version
        .as_f64()
        .ok_or_else(|| JsValue::from_str("document version must be a number"))?;
    if !doc_version.is_finite()
        || doc_version.fract() != 0.0
        || !(0.0..=9_007_199_254_740_991.0).contains(&doc_version)
    {
        return Err(JsValue::from_str(
            "document version must be a nonnegative safe integer",
        ));
    }
    Ok((doc_version as u64, locale))
}
