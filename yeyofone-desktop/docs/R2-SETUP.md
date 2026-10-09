# Cloudflare R2 recording storage

YeyoFone records WAV files locally, then backs up completed calls to a private R2 bucket through the included authenticated Cloudflare Worker. Local files remain available offline. The desktop connection token is stored in macOS Keychain, never in frontend storage or the public settings file.

## Details to provide

Send the **Cloudflare Account ID** and **bucket name** if you want help configuring the deployment. After deployment, send the **Worker URL**. None of these is a secret.

You do **not** need to create or send an R2 Access Key ID, Secret Access Key, global API key, or account API token. The Worker accesses R2 using a bucket binding. Enter your private Worker connection token directly in Cloudflare and the app rather than sending it in chat.

## 1. Create the private bucket

1. Sign in to https://dash.cloudflare.com/ and select your account.
2. Open **Storage & databases → R2 object storage** (or search for R2).
3. Enable R2 if it is not already enabled, then choose **Create bucket**.
4. Name it `yeyofone-recordings`, or choose a name and update `bucket_name` in the Worker configuration below.
5. Keep the bucket private: do not enable an `r2.dev` public URL or public custom domain.
6. Copy your **Account ID** from the R2 overview/account dashboard.

Cloudflare instructions: https://developers.cloudflare.com/r2/buckets/create-buckets/

## 2. Configure and deploy the Worker

From the project root:

```sh
cd yeyofone-desktop/cloudflare/recordings-worker
npm ci
npx wrangler login
```

Approve Cloudflare's browser login. Edit `wrangler.jsonc`:

- Set `r2_buckets[0].bucket_name` to your exact bucket name.
- If you have multiple Cloudflare accounts, add a top-level `"account_id": "YOUR_ACCOUNT_ID"`.
- Keep the binding name `RECORDINGS`.

Then deploy:

```sh
npm run deploy
```

Copy the HTTPS URL printed by Wrangler, such as:

```text
https://yeyofone-recordings.your-subdomain.workers.dev
```

The Worker denies all requests until its connection secret is configured.

Cloudflare binding instructions: https://developers.cloudflare.com/r2/api/workers/workers-api-usage/

## 3. Set the connection token privately

Generate a random token. On macOS, this command copies it directly to your clipboard without printing it:

```sh
openssl rand -hex 32 | pbcopy
```

Store a copy in your password manager. In the Worker directory, run:

```sh
npx wrangler secret put RECORDINGS_TOKEN
```

Paste the token at the prompt. Alternatively, in the Cloudflare dashboard open **Workers & Pages → yeyofone-recordings → Settings → Variables and Secrets**, add a **Secret** named `RECORDINGS_TOKEN`, and deploy the change.

Cloudflare secret instructions: https://developers.cloudflare.com/workers/configuration/secrets/

## 4. Connect YeyoFone

1. Open **Call History → Cloudflare R2 storage**.
2. Paste the Worker URL (the origin only, without `/health`, a bucket name, or query parameters).
3. Paste the same private connection token.
4. Enable **Automatically back up completed recordings** if desired.
5. Choose **Test and save connection**.
6. Choose **Back up now** to upload existing completed recordings.

A successful upload shows **Backed up to R2** beside the recording. To inspect the object in Cloudflare, open the bucket's Objects tab. Audio objects use:

```text
recordings/<installation-id>/<recording-id>.wav
```

## Playback and deletion

- Playback uses a valid local WAV if present. If it is missing, the native app downloads its R2 copy into the local recording folder before playback; cloud tokens never enter audio URLs.
- **Delete recording** is available in both the recording row and the playback player. Confirming deletes the cloud copy first, then the local file; the call-history entry stays.
- If cloud deletion fails, the local file stays and the deletion is marked pending. Choose **Retry deletion** or **Back up now** after reconnecting. Automatic backup also retries pending deletions when enabled.
- Pending/deleted audio is excluded from playback and backup, preventing accidental re-upload.
- Changing the Worker URL does not migrate existing recordings. Reconnect the original URL and token to access or delete its cloud copies.

## Operational limits

- Automatic backup retries every 30 seconds while the desktop app is running, after a call has ended. It pauses on a failed operation and retries on a later cycle. Disable the checkbox and save to pause automatic backup.
- Single recordings are limited to **100 MiB** by the app and Worker. WAVs above this size stay local; compression/multipart uploads are not implemented.
- Backups cover audio files. Call metadata and the recording-to-object mapping remain in the local `call-history.sqlite3` database. Preserve that database and `recording-cloud.json` for full recovery. This implementation does not yet provide a cross-device cloud history index.
- This Worker uses one private owner token; it is intended for your own installation(s), not a shared multi-user service. A multi-user deployment should add user authentication and per-user authorization.
- macOS Keychain is supported. Other desktop platforms retain the existing project's secure-vault limitation.

## Validation

```sh
bun test cloudflare/recordings-worker/worker.test.js frontend/tests/recordingPeaks.test.ts
cargo test -p yeyofone-platform -p yeyofone-desktop --features native-voip --locked
```

From the Worker directory, `npm run test:integration` exercises upload, download and deletion in Cloudflare’s local R2 simulator; `npx wrangler deploy --dry-run` verifies bundling without deploying. A real R2 round trip still requires your bucket, deployed Worker, and connection token.
