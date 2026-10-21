// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, readonly title: string,
    readonly retryAfter?: number) { super(code); }
}

export function aborted(signal: AbortSignal): void {
  if (signal.aborted) throw new ApiError(499, "request_canceled", "Request canceled");
}
