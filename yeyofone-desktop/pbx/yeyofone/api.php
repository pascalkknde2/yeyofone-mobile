<?php
/*
	YeyoFone desktop API for FusionPBX: live calls and voicemail for the signed-in user, as JSON.

	Install as /var/www/fusionpbx/app/yeyofone/api.php (see ../install-yeyofone-api.sh).
	Authentication is FusionPBX's own: a user's API key, sent as the POST field "key", is checked
	by resources/check_auth.php. Each action also requires that user's FusionPBX permission:
	  live_calls       call_active_view   (calls in the user's domain)
	  voicemails       voicemail_message_view (messages in the user's own extensions' mailboxes)
	  voicemail_audio  voicemail_message_view (one of those messages, as audio)
	Only POST is accepted so the key never appears in web server logs.
*/

	if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
		http_response_code(405);
		header('Allow: POST');
		exit;
	}

//includes files
	require_once dirname(__DIR__, 2) . "/resources/require.php";
	require_once "resources/check_auth.php";

	function yeyofone_reply(int $status, $data): void {
		http_response_code($status);
		header('Content-Type: application/json; charset=utf-8');
		header('Cache-Control: no-store');
		header('X-Content-Type-Options: nosniff');
		echo json_encode(['version' => 1, 'data' => $data], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
		exit;
	}

	$domain_uuid = $_SESSION['domain_uuid'] ?? '';
	$domain_name = $_SESSION['domain_name'] ?? '';
	$user_uuid = $_SESSION['user_uuid'] ?? '';
	if ($domain_uuid === '' || $user_uuid === '') {
		yeyofone_reply(401, ['error' => 'not_signed_in']);
	}

//the extensions assigned to the signed-in user
	function yeyofone_extensions(string $domain_uuid, string $user_uuid): array {
		$database = database::new();
		$sql = "select e.extension from v_extension_users eu ";
		$sql .= "join v_extensions e on e.extension_uuid = eu.extension_uuid ";
		$sql .= "where eu.domain_uuid = :domain_uuid and eu.user_uuid = :user_uuid and e.enabled = 'true' ";
		$rows = $database->select($sql, ['domain_uuid' => $domain_uuid, 'user_uuid' => $user_uuid], 'all');
		return array_values(array_filter(array_map(fn($r) => (string)$r['extension'], is_array($rows) ? $rows : []),
			fn($e) => preg_match('/^[0-9A-Za-z*#+._-]{1,32}$/', $e)));
	}

//voicemail messages in the user's own mailboxes, optionally one message
	function yeyofone_messages(string $domain_uuid, array $extensions, ?string $message_uuid = null): array {
		if (empty($extensions)) {
			return [];
		}
		$database = database::new();
		$parameters = ['domain_uuid' => $domain_uuid];
		$names = [];
		foreach (array_values($extensions) as $i => $extension) {
			$names[] = ':ext' . $i;
			$parameters['ext' . $i] = $extension;
		}
		$sql = "select v.voicemail_id, m.voicemail_message_uuid, m.created_epoch, m.caller_id_name, ";
		$sql .= "m.caller_id_number, m.message_length, m.message_status ";
		$sql .= "from v_voicemail_messages m join v_voicemails v on v.voicemail_uuid = m.voicemail_uuid ";
		$sql .= "where m.domain_uuid = :domain_uuid and v.voicemail_id in (" . implode(', ', $names) . ") ";
		if ($message_uuid !== null) {
			$sql .= "and m.voicemail_message_uuid = :message_uuid ";
			$parameters['message_uuid'] = $message_uuid;
		}
		$sql .= "order by m.created_epoch desc limit 200 ";
		$rows = $database->select($sql, $parameters, 'all');
		return is_array($rows) ? $rows : [];
	}

	$action = $_POST['action'] ?? '';
	switch ($action) {
		case 'me':
			yeyofone_reply(200, [
				'domain' => $domain_name,
				'extensions' => yeyofone_extensions($domain_uuid, $user_uuid),
				'liveCalls' => permission_exists('call_active_view'),
				'voicemail' => permission_exists('voicemail_message_view'),
			]);

		case 'live_calls':
			if (!permission_exists('call_active_view')) {
				yeyofone_reply(403, ['error' => 'permission']);
			}
			$event_socket = event_socket::create();
			if (!$event_socket->is_connected()) {
				yeyofone_reply(503, ['error' => 'switch_unavailable']);
			}
			$results = json_decode(trim(event_socket::api('show channels as json')), true);
			$calls = [];
			foreach (($results['rows'] ?? []) as $row) {
				//the channel's domain, as FusionPBX's Active Calls page works it out
				$context = (string)($row['context'] ?? '');
				$presence = (string)($row['presence_id'] ?? '');
				$row_domain = '';
				if ($context !== '' && $context !== 'public' && $context !== 'default') {
					$row_domain = strpos($context, '@') !== false ? explode('@', $context)[1] : $context;
				}
				elseif (strpos($presence, '@') !== false) {
					$row_domain = explode('@', $presence)[1];
				}
				if ($row_domain !== $domain_name || strpos($presence, '@') === false) {
					continue;
				}
				$calls[] = [
					'uuid' => (string)($row['uuid'] ?? ''),
					'callUuid' => (string)($row['call_uuid'] ?? ''),
					'extension' => explode('@', $presence)[0],
					'direction' => (string)($row['direction'] ?? ''),
					'state' => (string)($row['callstate'] ?? ''),
					'createdEpoch' => (int)($row['created_epoch'] ?? 0),
					'callerName' => (string)($row['cid_name'] ?? ''),
					'callerNumber' => (string)($row['cid_num'] ?? ''),
					'destination' => (string)($row['dest'] ?? ''),
					'calleeName' => (string)($row['callee_name'] ?? ''),
					'calleeNumber' => (string)($row['callee_num'] ?? ''),
				];
			}
			yeyofone_reply(200, $calls);

		case 'voicemails':
			if (!permission_exists('voicemail_message_view')) {
				yeyofone_reply(403, ['error' => 'permission']);
			}
			$messages = [];
			foreach (yeyofone_messages($domain_uuid, yeyofone_extensions($domain_uuid, $user_uuid)) as $row) {
				$messages[] = [
					'uuid' => (string)$row['voicemail_message_uuid'],
					'mailbox' => (string)$row['voicemail_id'],
					'createdEpoch' => (int)$row['created_epoch'],
					'callerName' => (string)($row['caller_id_name'] ?? ''),
					'callerNumber' => (string)($row['caller_id_number'] ?? ''),
					'seconds' => (int)($row['message_length'] ?? 0),
					'status' => (string)($row['message_status'] ?? ''),
				];
			}
			yeyofone_reply(200, $messages);

		case 'voicemail_audio':
			if (!permission_exists('voicemail_message_view')) {
				yeyofone_reply(403, ['error' => 'permission']);
			}
			$uuid = (string)($_POST['uuid'] ?? '');
			if (!is_uuid($uuid)) {
				yeyofone_reply(400, ['error' => 'invalid_uuid']);
			}
			$rows = yeyofone_messages($domain_uuid, yeyofone_extensions($domain_uuid, $user_uuid), $uuid);
			if (count($rows) !== 1) {
				yeyofone_reply(404, ['error' => 'not_found']);
			}
			$settings = new settings(['domain_uuid' => $domain_uuid, 'user_uuid' => $user_uuid]);
			$base = $settings->get('switch', 'voicemail', '/var/lib/freeswitch/storage/voicemail');
			$path = $base . '/default/' . $domain_name . '/' . $rows[0]['voicemail_id'] . '/msg_' . $uuid;
			foreach (['wav' => 'audio/wav', 'mp3' => 'audio/mpeg'] as $extension => $type) {
				$file = $path . '.' . $extension;
				if (is_file($file) && filesize($file) <= 50 * 1024 * 1024) {
					http_response_code(200);
					header('Content-Type: ' . $type);
					header('Content-Length: ' . filesize($file));
					header('Cache-Control: no-store');
					header('X-Content-Type-Options: nosniff');
					readfile($file);
					exit;
				}
			}
			yeyofone_reply(404, ['error' => 'audio_missing']);

		default:
			yeyofone_reply(400, ['error' => 'unknown_action']);
	}
