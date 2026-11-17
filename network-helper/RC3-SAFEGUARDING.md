# RC3 proxy safeguarding draft

This change has not been deployed or enabled on any server. PulseNetwork now includes an
independent **Paper 1.21.4** test safeguarding listener as well as the Velocity gate.
PulseAddon targets Paper 26.2 and cannot supply local protection to a 1.21.4 minigame server.
The new backend listener covers local chat, direct-message commands, commands with text,
signs, books, anvil result clicks and the helper's Java/Bedrock text prompts before delivery.
The proxy separately protects cross-server relay. A proxy cannot retract origin-server
chat: enable and accept the appropriate backend listener too. The NeoForge adapter is unchanged.

## Configuration and scope

Production is inert by default. Missing or any value other than the exact string `test`
for `safeguarding.environment` keeps filtering, journal, commands and outbound alerts off.
On a **test proxy only**, these properties enable filtering/logging for the named test
backends. No runtime settings or files were changed by this draft.

```properties
safeguarding.environment=test
safeguarding.test-backends=minigamestest,survivaltest
safeguarding.filter=true
safeguarding.logging=true
safeguarding.retention-days=90
safeguarding.extra-words=
safeguarding.discord-webhook-env=
safeguarding.readyapp-endpoint=
safeguarding.readyapp-token-env=
```

Filtering and logging default true inside that explicit test scope. Retention defaults
to the **90-day proposal pending Aurora's policy decision**, and accepts 1–90 days. Empty
test-backends means nobody. These properties are startup snapshots; changing them needs
a separately authorised restart. The owner is Aurora; review by 24 November 2026 before
any rollout. Keep safeguarding enforcement as a permanent control, and remove the test
restriction only after signed policy, client/server acceptance and explicit approval.

The deterministic filter blocks the whole message and gently explains why. It recognises
word boundaries, leetspeak, repeated letters, separated profanity/slurs, email/phone and
spoken digits, common UK address/postcode shapes, handles and common off-platform requests.
Welfare, threat and grooming-pattern matches are URGENT, PII/contact is HIGH, language is
LOW. These are triage labels, never findings about a person. It cannot infer ages, intent,
relationships or multi-message grooming. Slang, other languages, image content and novel
obfuscation need human reporting and review. The addon and proxy have independently scoped
rules; calibrate changes in both to prevent drift.

Existing authentication, carrier/current-connection verification, replay checks, backend
allowlists and rate limits run as before. Spoofed client packets are consumed without
logging attacker-supplied text or generating safeguarding alerts. Authentic monitored
messages are journalled before the rate limit/fanout (including blocked messages); the
`allowed` journal outcome means admitted by the safety filter, not proof of recipient
delivery. Public and party fanout both pass the filter. There is no permission bypass. When a proxy
is in the explicit test profile, public/party delivery between scoped test and unscoped
backends is held in both directions; production-to-production edges remain unchanged.
Private and party text on scoped test origins is screened/logged then held until a shared
cross-layer mute/friend/slow-mode contract is verified. This prevents proxy-owned commands
from bypassing backend restrictions. Proxy command interception also blocks PM aliases,
including namespaces, before another Velocity plugin can consume them. Own proxy text
commands are screened; unknown command arguments are withheld rather than collecting passwords.

## Private review and alerts

Only `pulsenetwork.safeguarding.review` may read:

- `/networkflagged`: latest 20 flagged records, with severity, reason and reference.
- `/networkcontext <reference>`: five preceding and five following messages in the same
  backend/channel, including other players. Party channels are keyed by leader UUID.
- `/networkhistory <UUID>`: latest 20 records for that player across monitored channels.

The queue is the retained journal's flagged view. It survives restart. Staff record review,
resolution and escalation in ReadyApp; this draft does not remove or mark flags resolved,
and the latest-20 view has no pagination. Older evidence is available to the safeguarding
lead through the protected journal. Context includes only messages received by this proxy;
local-only chat is absent. Review commands print plain components without click/format parsing.

URGENT hits immediately tell online staff with the review permission. Discord is optional:
put the webhook URL in a private server environment variable, and configure only its **name**
in `safeguarding.discord-webhook-env`. The code accepts only HTTPS Discord webhook paths,
never follows redirects, disables mentions, and sends only reference/backend/reason codes.
No player identity or chat text is sent to Discord. No webhook URL is in the repository.

The existing ReadyApp `POST /safeguarding` integration was checked against `origin/dev`.
Optionally configure the complete HTTPS `/safeguarding` endpoint and the name of a private
environment variable containing a generated SERVICE API key with explicit
`safeguarding.ingest` scope. The request uses `x-mcp-token`, critical severity and
out_of_venue category. It sends reference/backend/rule metadata only. Staff review the
private journal and link a CYP/deal; no identity is guessed or automatically linked.
The ReadyApp integration does not make legacy shared MCP credentials valid for ingestion.

HTTP notifications use a bounded queue, 3s connect/5s request timeouts, no redirects and
no retries. A timeout can mean an accepted concern: check ReadyApp before manually retrying.
Discord failure does not prevent a ReadyApp attempt. Failed/overloaded notification logs
never include payloads, URLs, secrets or exception bodies; the durable local flag remains
for staff follow-up. No integration credentials were provisioned or tested externally.

## Paper 1.21.4 test backend

The Paper guard is independent of the network relay enable/bridge-secret keys, so it can
protect an isolated test backend without enabling cross-server chat. Set these YAML keys
in the plugin's private config, alongside the existing settings:

```yaml
safeguarding:
  environment: test
  backend: minigamestest
  test-backends: [minigamestest, survivaltest]
  filter: true
  logging: true
  retention-days: 90
  legacy-chat: false
  text-commands: [me, say, party, p, nick, nickname, announce]
  credential-command-owners: []
  under-18-players: []
  private:
    enabled: false
    allow-under-18-friends: false
    friend-pairs: []
  discord-webhook-env: ''
  readyapp-endpoint: ''
  readyapp-token-env: ''
```

The exact test environment plus a matching explicit backend ID/test allowlist is required.
Missing environment or backend is inert. Filtering/logging default true only within scope.
All credential-command arguments are withheld, including namespace variants; aliases such
as `/l` are not assumed to be login commands. They are held unless the resolved installed
PluginCommand's owner is on the separately inventoried `credential-command-owners` list.
An empty list grants no credential exception. Other unapproved text commands are held and
only metadata is recorded. Inventory plugin owners and aliases before acceptance.

Native chat is screened at LOWEST and rechecked at HIGHEST if another plugin changes its
text, without duplicate rate counting. Sign/book/anvil/command changes get a final check
too. The legacy event is selected separately for legacy chat providers; AdvancedChat presence
automatically selects it, avoiding double-processing and rate rejection of every message.
Custom formatters, chat games and all third-party MONITOR behaviour require acceptance;
plugins which rebroadcast privately or disregard cancellation need explicit adapters.
The helper's own Java anvil callback now honours prior cancellation, and its Bedrock text
form runs the same safety gate before a callback. Draft anvil keystrokes are not persisted;
the committed result click is the monitored submission.

Private messages are off by default for everyone, which also protects unknown ages.
For an independently accepted backend-only trial, enabling private messages still requires
an explicit mutual UUID pair in `friend-pairs`. Known under-18 UUIDs in `under-18-players`
remain off unless `allow-under-18-friends` is explicitly enabled; that exception still
requires a friend pair. No ages are guessed or fetched from production. Reply aliases `/r`, `/reply`, `/er` and `/ereply` are
held because no authoritative last-recipient contract exists. Essentials private aliases
`/emsg`, `/etell`, `/ewhisper` and `/epm` follow the same gates, including namespace prefixes.
External `/mail` and `/email` (including inbox-only commands) are screened/logged and held
until a consent-aware mail adapter is verified; adding them to public text commands cannot bypass the hold.
Their arguments are evaluated transiently for urgency, but the retained body is always
`[private mail arguments withheld]`, because `/email` may belong to an authentication/OTP plugin. The proxy test gate remains
stricter and holds all private/party text until cross-layer controls are verified.

Staff commands require `pulsenetwork.safeguarding.review` (not granted even to operators
by default). They use plain chat messages on Java and Bedrock:

- `/safetyflagged`, `/safetyhistory <UUID>`, `/safetycontext <reference>` read protected backend evidence.
- `/safetymute <UUID> <seconds>` persists a temp-mute; zero means indefinite.
- `/safetyunmute <UUID>` removes it; `/safetyslow <0..300 seconds>` persists slow mode.
- `/safetyreport <player name or UUID> <what happened>` immediately screens/logs the draft
  and alerts urgent disclosures, then asks for `/safetyreport confirm <token>` within a minute.
  Confirmation saves the actual report, returns a receipt, and tells staff. Mutes and slow
  mode do not prevent reports, and troubling disclosures are recorded rather than blocked.
  Draft submissions are limited to one confirmation offer per minute; urgent draft screening
  runs before that limiter. Expired tokens never claim a report was received.

Conventional aliases `/flagged`, `/history` and `/report` are offered when free. Preserve an
existing plugin's command owner; use the prefixed forms (or `/pulsenetwork:safetyreport`)
if an alias is already owned. Inventory existing report plugins and ensure their reports
reach safeguarding staff. The controls state is a separate private file under the 0700
journal directory; it contains UUIDs, mute expiry and slow-mode duration, never message text.
Malformed persisted mute/slow state is preserved and holds text until staff repair it;
it never silently removes restrictions. Staff control mutations are refused while storage
is unhealthy. Mute/rate/private/party rejections are audited as blocked. Storage or unexpected safety
errors hold text rather than allowing it through. Urgent metadata alerts still run on
storage failure, and staff are told that local evidence could not be saved.

The backend has a one-second attempt limiter plus staff-selected slow mode. Mutes cover
chat, text commands, signs, books, anvil/form submissions; self-reporting remains available.
No production player list or age data was used to populate any control.

## Privacy staff note draft

On approved test servers, ReadyCraft records monitored public, private, command, sign,
book, anvil/form and report
messages, including blocked attempts: Minecraft UUID, timestamp, server/channel,
message (with detected personal/contact content replaced by a withholding marker),
severity/reason codes and a random reference. There are no separate real-name, exact-age, birthdate, IP-address or device fields and no
identity lookup. Message text may still contain disclosures the heuristic does not recognise;
detected personal/contact details are withheld. Party channel identifiers use the
party leader's Minecraft UUID. Review is restricted to authorised safeguarding staff.
Command credentials and unapproved command arguments are withheld; report drafts and confirmed
reports are separate records. These records support player safety and concern review, and are proposed for deletion
after 90 days subject to Aurora confirming the policy and privacy notice.

Daily TSV journals are in the plugin data `safeguarding/` directory with POSIX 0700/0600
permissions. A non-POSIX filesystem refuses monitored logging rather than silently making
sensitive files readable. Newline/tab/backslash text is escaped. Protect the server account,
host and backups; plugin permissions cannot stop the operating-system administrator.
Logs are not copied into the ordinary server logger. Expired daily files are removed on
startup and the next record/review after UTC date rollover. Backups need the same expiry
policy separately; this draft does not mutate backup policy. A failed journal write holds
subsequent monitored messages and asks the player to contact staff. Storage failure immediately
alerts online review staff; URGENT rules still send metadata alerts even when the journal is unavailable.

Review commands synchronously scan retained daily files using bounded memory. Trial only on
low-volume test servers; measure latency/size before rollout, and use a background indexed
reviewer for larger retention volumes. Do not enable this prototype on a busy network.

## Verification and acceptance

Targeted common/Paper/Velocity tests cover fail-closed environment scope, secure retention,
context isolation, trusted and spoofed ingress, public/party filtering, and the production
inert path. The 50-message synthetic corpus has 30 labelled harmful cases and 20 benign
cases. The direct Java calibration run observed 0 false negatives out of 30 harmful examples
and 2 false positives out of 20 benign examples: a ten-digit world seed and "I live at spawn".
Those are conservative PII matches, and need staff/product calibration before rollout.
The queued Gradle test repeats these counts. This small,
hand-labelled English corpus does not estimate real-world accuracy.

Before any rollout: Aurora confirms retention and privacy text; safeguarding staff review
the word/rule lists and calibration; obtain separate test deployment/restart approval;
verify Java and Bedrock blocked/allowed public and party chat, context/permissions,
retention rollover, delivery failures, staff availability, Discord and ReadyApp routing
with synthetic messages and test credentials. Confirm local chat/DM/sign/book/anvil/command
coverage on each backend independently. Cross-layer private/party controls remain closed until their shared state is verified.
Do not enable modded backends with this Paper adapter; they need a separately accepted gate.
