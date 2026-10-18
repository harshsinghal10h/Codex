/** Opt-in single-tenant read-only WebDAV file listing. No UI credential collection. */
export async function listNextcloudFiles(tenantId, env = process.env) {
  if (!env.NEXTCLOUD_BASE_URL || !env.NEXTCLOUD_TENANT_ID ||
      !env.NEXTCLOUD_USERNAME || !env.NEXTCLOUD_APP_PASSWORD ||
      tenantId !== env.NEXTCLOUD_TENANT_ID) {
    return { configured: false, reason: 'Nextcloud is not configured for this organization', files: [] };
  }
  const origin = new URL(env.NEXTCLOUD_BASE_URL);
  if (origin.protocol !== 'https:' || origin.username || origin.password || origin.search || origin.hash)
    throw new Error('Nextcloud requires an HTTPS URL without embedded credentials');
  const path = '/remote.php/dav/files/' + encodeURIComponent(env.NEXTCLOUD_USERNAME) + '/';
  const url = new URL(path, origin.origin);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 7000);
  try {
    const basic = Buffer.from(env.NEXTCLOUD_USERNAME + ':' + env.NEXTCLOUD_APP_PASSWORD).toString('base64');
    const response = await fetch(url, {
      method: 'PROPFIND', redirect: 'error', signal: controller.signal,
      headers: {
        Authorization: 'Basic ' + basic,
        Depth: '1', 'Content-Type': 'application/xml',
      },
      body: '<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>',
    });
    if (response.status !== 207) throw new Error('Nextcloud WebDAV returned HTTP ' + response.status);
    const xml = await response.text();
    // This minimal adapter extracts escaped display names; do not advertise full DAV feature parity.
    const values = [...xml.matchAll(/<(?:\w+:)?displayname[^>]*>([^<]*)<\/(?:\w+:)?displayname>/gi)]
      .map(m => m[1].replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>'))
      .filter(Boolean).slice(0, 100);
    return { configured: true, files: values.map(name => ({ name })) };
  } finally { clearTimeout(timeout); }
}
