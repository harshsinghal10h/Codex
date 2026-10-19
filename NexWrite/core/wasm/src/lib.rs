// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use wasm_bindgen::prelude::*;

/// Validate JS types and original UTF-16 before crossing the canonical UTF-8 boundary.
#[wasm_bindgen]
pub fn check_json(text: JsValue, doc_version: JsValue, locale: JsValue) -> Result<String, JsValue> {
    let text: js_sys::JsString = text
        .dyn_into()
        .map_err(|_| JsValue::from_str("text must be a string"))?;
    let units = (0..text.length()).map(|index| text.char_code_at(index) as u16);
    let text = char::decode_utf16(units)
        .collect::<Result<String, _>>()
        .map_err(|_| JsValue::from_str("text contains an unpaired UTF-16 surrogate"))?;
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
    let result = nw_engine::Engine::new()
        .and_then(|engine| engine.check(&text, doc_version as u64, &locale))
        .and_then(|suggestions| serde_json::to_string(&suggestions).map_err(|e| e.to_string()));
    result.map_err(|error| JsValue::from_str(&error))
}
