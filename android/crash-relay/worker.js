/**
 * Crash relay: receives crash-<ts>.log uploads from the Android app and files
 * them as GitHub issues. Deduplicates on the client-computed X-SC-Hash —
 * repeat reports land as comments on the existing issue.
 *
 * Deploy: see README.md. Secrets: GH_TOKEN (repo-scoped, issues:write),
 * RELAY_KEY (must match the app's BuildConfig.CRASH_RELAY_KEY).
 */
export default {
  async fetch(request, env) {
    if(request.method !== 'POST') {
      return new Response('method not allowed', { status: 405 });
    }
    if(env.RELAY_KEY && request.headers.get('X-SC-Key') !== env.RELAY_KEY) {
      return new Response('unauthorized', { status: 401 });
    }

    const body = await request.text();
    if(!body.trim()) {
      return new Response('empty body', { status: 400 });
    }
    if(body.length > 512 * 1024) {
      return new Response('too large', { status: 413 });
    }

    const version = (request.headers.get('X-SC-Version') || 'unknown').slice(0, 80);
    const device = (request.headers.get('X-SC-Device') || 'unknown').slice(0, 120);
    const hash = (request.headers.get('X-SC-Hash') || '').slice(0, 32);
    const summary = (request.headers.get('X-SC-Summary') || 'Severed Chains crash').slice(0, 160);

    const ghHeaders = {
      'Authorization': `Bearer ${env.GH_TOKEN}`,
      'Accept': 'application/vnd.github+json',
      'User-Agent': 'sc-crash-relay',
      'X-GitHub-Api-Version': '2022-11-28',
    };
    const fenced = '~~~\n' + body + '\n~~~';

    // Dedup: the hash is embedded in the issue body, so a repeat crash finds
    // the existing issue and lands as a comment instead of a new issue.
    if(hash) {
      const q = encodeURIComponent(`repo:${env.GH_REPO} is:issue ${hash}`);
      const res = await fetch(`https://api.github.com/search/issues?q=${q}&per_page=1`, { headers: ghHeaders });
      if(res.ok) {
        const found = await res.json();
        if(found.total_count > 0) {
          const issue = found.items[0];
          await fetch(issue.comments_url, {
            method: 'POST',
            headers: ghHeaders,
            body: JSON.stringify({
              body: `Repeat report — ${device}, ${version}.\n\n<details><summary>crash log</summary>\n\n${fenced}\n</details>`,
            }),
          });
          return Response.json({ issue: issue.html_url, deduplicated: true });
        }
      }
    }

    const created = await fetch(`https://api.github.com/repos/${env.GH_REPO}/issues`, {
      method: 'POST',
      headers: ghHeaders,
      body: JSON.stringify({
        title: `[crash] ${summary}`,
        labels: ['crash', 'auto-reported'],
        body: `**Device:** ${device}\n**Version:** ${version}\n**Dedup hash:** \`${hash}\`\n\n<details><summary>crash log</summary>\n\n${fenced}\n</details>`,
      }),
    });
    if(!created.ok) {
      return new Response('github ' + created.status + ': ' + await created.text(), { status: 502 });
    }
    const issue = await created.json();
    return Response.json({ issue: issue.html_url, deduplicated: false });
  },
};
