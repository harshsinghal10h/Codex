// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { buildApp } from "./app.js";
import { loadConfig } from "./config.js";

try {
  const config = loadConfig();
  const app = await buildApp(config);
  await app.listen({ host: config.host, port: config.port });
  // Only process state is logged. Request bodies, URLs, keys and provider errors are not logged.
  console.info("NexWrite API listening; request logging is disabled.");
  for (const signal of ["SIGINT", "SIGTERM"] as const) process.once(signal, () => { void app.close().then(() => process.exit(0)); });
} catch {
  console.error("NexWrite API could not start. Check the server configuration and local engine installation.");
  process.exitCode = 1;
}
