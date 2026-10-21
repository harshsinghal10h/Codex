<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Privacy principles

Local-only analysis is the default and the complete Task 0 execution path. No account, analytics, training, advertising or remote text processing is implemented. Text is sensitive input data. The CLI prints a report only when its caller requests it; no background content logging or telemetry is permitted. Users control their terminal/files and should avoid uploading private documents as issue fixtures.

Future cloud and LLM processing requires explicit informed opt-in, disclosure of recipient/purpose/region/retention, revocation, consent records and a usable local path. Telemetry must be opt-in with local payload preview, content-free diagnostics and no training on user data. Local-only mode must make zero external calls from analysis. Build-time dependency downloads are distinct from analysis-time traffic.

Sensitive fields (passwords, payment, OTP, secure/incognito contexts) must remain inactive on future host surfaces. Keyboard full access must be optional where platforms allow it. Keep private mode, per-app/site overrides, delete/export controls and least privilege. Team analytics require k-anonymity >=5 and no text. Default authorship stores event kinds/lengths, not keystroke content; replay requires separate consent.

No production retention/deletion service or compliance certification exists. Before deployment, document data flows, processors, deletion/export <=30 days, encryption/key handling, regional controls, age/education consent and applicable GDPR/UK GDPR, CCPA/CPRA, India DPDP and other obligations with qualified review. A principle document is not evidence of regulatory compliance.
