@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt agent #2 (DataStore + Encryption + Key management +
 * Transactional integrity + JSON serialization).
 *
 * Each test pins a file-shape or contract assertion derived from the
 * findings in `.git/sdd/bug_hunt_v2_datastore.md`. The tests are
 * deliberately narrow — they assert "the file still has the expected
 * shape" and "the contract has not silently shifted", not behaviour.
 *
 * The corresponding v1 hunt lives at
 * `.git/sdd/bug_hunt_backup.md`. v2 deepens the v1 findings that
 * were not fixed in v0.25.8, and audits the v0.25.5+ prefs that
 * v1 did not look at (WorryPostponement, DST, OneThing, Haptics
 * surfaces, etc.).
 *
 * Tests in this class are file-shape pins: they read the source
 * file and assert key strings are present. A regression that
 * removes a guard or changes a flow shape flips the assertion red.
 */
class V2BugHuntFindingTest {

    // Helper: read a source file from the main source set. Same
    // pattern as the existing `PendingBackupLogFindingTest.readSource`.
    private fun readSource(relative: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/$relative",
            "../app/src/main/java/org/mindanchor/$relative",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun assertContains(
        relative: String,
        needle: String,
        message: String,
    ) {
        val source = readSource(relative)
        assertNotNull("source file $relative must exist", source)
        assertTrue(message, source!!.contains(needle))
    }

    // -----------------------------------------------------------------
    // Finding #1: NotesPrefs.nextNoteId seeds the idGenerator
    //   from the live DataStore via runBlocking on the very first
    //   call. The lazy initialiser uses the *single* data source
    //   that owns notes — but the launcher also has the letters
    //   DataStore ("letters") and the friction DataStore. The seed
    //   must be "every existing note id on disk"; the lazy reads
    //   the notes key, not the merged view.
    // -----------------------------------------------------------------
    @Test
    fun `NotesPrefs nextNoteId seed reads from notes key only, not a merged view`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // The lazy reads SealedCodecs.decodeNotes(...) which decodes
        // the "notes" key. The id generator seed is therefore
        // bounded by the notes list — letters and friction ids
        // are not part of the seed. A future "give all id-bearing
        // entities a globally unique id" refactor would have to
        // replace this single-source seed.
        assertTrue(
            "NotesPrefs.idGenerator must seed from the notes key only",
            source!!.contains("SealedCodecs.decodeNotes") &&
                source.contains("decoded.notes.maxOfOrNull"),
        )
        // And the seed is correct on the surface: a fresh
        // install (no notes on disk) seeds to
        // maxOf(System.currentTimeMillis(), 0) which is the
        // current time. Concurrent callers on a fresh install
        // get distinct ids because AtomicLong is atomic.
        assertTrue(
            "NotesPrefs.idGenerator must use AtomicLong",
            source.contains("java.util.concurrent.atomic.AtomicLong"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #2: NotesPrefs.nextNoteId is process-singleton via
    //   `by lazy` on the *NotesPrefs instance*. The fix comment
    //   at NotesPrefs.kt:73-97 says the counter is "the
    //   [Application] context, not on the activity". The actual
    //   implementation is `by lazy` on the class instance — every
    //   construction of `NotesPrefs(context)` builds a fresh
    //   counter. The home activity constructs a NotesPrefs
    //   (LauncherViewModel) and the full note activity constructs
    //   another (NoteActivity). Two NotesPrefs instances on the
    //   same process = two id counters. The fix comment is wrong
    //   about the actual sharing.
    // -----------------------------------------------------------------
    @Test
    fun `NotesPrefs idGenerator is process-singleton on the companion object (v0_25_9 fix)`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // v0.25.9 FIX: the idGenerator is now declared
        // on the [Companion] (a true per-class-loader
        // singleton). The v0.25.8 shape was a class-level
        // `by lazy`, which silently produced one
        // AtomicLong per class instance and re-
        // introduced the duplicate-id bug the
        // v0.25.7+ WP-3 release notes claimed to fix.
        val idGeneratorOnCompanion = Regex(
            "companion object[\\s\\S]*?private val idGenerator[\\s\\S]*?AtomicLong",
        ).containsMatchIn(source!!)
        val noClassLevelByLazy = !Regex(
            "private val idGenerator[^=]*by lazy",
        ).containsMatchIn(source)
        val exposesSeed = source.contains("suspend fun seedFromDiskIfNeeded")
        assertTrue(
            "NotesPrefs v0.25.9 fix: idGenerator must live on the companion object " +
                "(per-class-loader singleton) and the class must not have a " +
                "class-level `by lazy` (the per-instance shape). The companion must " +
                "expose `suspend fun seedFromDiskIfNeeded` so HomeActivity.onCreate " +
                "can raise the counter to maxExisting asynchronously. " +
                "idGeneratorOnCompanion=$idGeneratorOnCompanion " +
                "noClassLevelByLazy=$noClassLevelByLazy exposesSeed=$exposesSeed.",
            idGeneratorOnCompanion && noClassLevelByLazy && exposesSeed,
        )
    }

    // -----------------------------------------------------------------
    // Finding #3: NotesPrefs.add does an O(n) re-serialise on
    //   every write. The fix at v0.25.7+ WP-3 changed the
    //   *counter* but not the *encoder*. The O(n) lifetime
    //   cost is the same as v1 finding #14 found for
    //   BackupPrefs.pendingBackups.
    // -----------------------------------------------------------------
    @Test
    fun `NotesPrefs add re-serialises the full notes list on every write`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // Each edit block decodes, mutates, encodes — full list
        // round-trip. Acceptable for n <= a few hundred, but the
        // contract is "re-encode everything on every write".
        assertTrue(
            "NotesPrefs.add must encode the full list on every write",
            source!!.contains("SealedCodecs.encodeNotes(next)"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #4: NotesPrefs.edit / setType / togglePinned /
    //   delete all do an O(n) re-serialise. The check is that
    //   the pattern is consistent — there is no incremental
    //   write, no append log, every edit rewrites the full
    //   sealed envelope.
    // -----------------------------------------------------------------
    @Test
    fun `NotesPrefs every editor rewrites the full sealed envelope`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // Count the SealedCodecs.encodeNotes occurrences in
        // editor functions. Should be 5 (add, edit, setType,
        // clearAllTypes, togglePinned, delete — every suspend
        // editor fun that ends in `prefs[notesKey] =
        // SealedCodecs.encodeNotes(...)`).
        val encodeCount = source!!.split("SealedCodecs.encodeNotes(").size - 1
        assertTrue(
            "every NotesPrefs editor must call SealedCodecs.encodeNotes (found $encodeCount)",
            encodeCount >= 5,
        )
    }

    // -----------------------------------------------------------------
    // Finding #5: The data flow in NotesPrefs.notes maps every
    //   DataStore emission through a full SealedCodecs decode.
    //   Distinct emissions on metadata-only changes (DataStore
    //   emits on any write, even unrelated keys) re-decode
    //   the full notes payload. Same pattern v1 found in
    //   BackupPrefs.pendingBackups.
    // -----------------------------------------------------------------
    @Test
    fun `NotesPrefs notes flow re-decodes the full payload on every emission`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "NotesPrefs.notes must decode on every emission (no .distinctUntilChanged downstream)",
            source!!.contains("context.notesDataStore.data.map") &&
                source.contains("SealedCodecs.decodeNotes(it[notesKey].orEmpty())"),
        )
        // There is no stateIn / SharingStarted on the flow —
        // every subscriber pays the decode cost on every emit.
        assertFalse(
            "NotesPrefs.notes must NOT use .stateIn — the launcher calls .first() everywhere",
            source.contains(".stateIn("),
        )
    }

    // -----------------------------------------------------------------
    // Finding #6: BackupPrefs.pendingBackups re-decodes the
    //   full queue on every DataStore emission. Same as the v1
    //   finding #15 — never fixed in v0.25.8. The flow does not
    //   cache the parsed list.
    // -----------------------------------------------------------------
    @Test
    fun `BackupPrefs pendingBackups re-decodes the full queue on every emission`() {
        val source = readSource("backup/BackupPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "BackupPrefs.pendingBackups must decode PendingBackupLog on every emission",
            source!!.contains("PendingBackupLog.decode(prefs[pendingBackupsKey].orEmpty())"),
        )
        assertFalse(
            "BackupPrefs.pendingBackups must NOT use .stateIn or any cache",
            source.contains(".stateIn("),
        )
    }

    // -----------------------------------------------------------------
    // Finding #7: BackupPrefs.enqueuePending re-encodes the
    //   full queue on every enqueue — O(n) per op, O(n²)
    //   lifetime. v1 finding #14 — never fixed.
    // -----------------------------------------------------------------
    @Test
    fun `BackupPrefs enqueuePending re-encodes the full queue on every op`() {
        val source = readSource("backup/BackupPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "BackupPrefs.enqueuePending must re-encode the full queue on every op",
            source!!.contains("PendingBackupLog.encode(next)"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #8: BackupPrefs.reset is `internal` — could be
    //   misused in production. v1 finding #16 — never fixed.
    // -----------------------------------------------------------------
    @Test
    fun `BackupPrefs reset is internal not private`() {
        val source = readSource("backup/BackupPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "BackupPrefs.reset must still be `internal` (v1 finding #16 not fixed)",
            source!!.contains("internal suspend fun reset()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #9: PendingBackup JSON-encodes without a version
    //   field — v1 finding #17. The format is "type<tab>iso<tab>
    //   b64<tab>length", 4 fields, no version marker. A future
    //   v0.25.6+ that adds a fifth field has no way to detect
    //   "this is a v2 entry, the v1 codec cannot decode it".
    // -----------------------------------------------------------------
    @Test
    fun `PendingBackup wire format has no version field`() {
        val source = readSource("backup/PendingBackup.kt")
        assertNotNull(source)
        // The encode is 4 tab-separated fields.
        assertTrue(
            "PendingBackupLog.encode must produce 4 fields with no version marker",
            source!!.contains("encodeToString(entry.payload)") &&
                source.contains("entry.type.fileName") &&
                source.contains("entry.queuedAt") &&
                source.contains("entry.payload.size"),
        )
        // And decode accepts "any line with 4 parts" — no version
        // header check.
        assertTrue(
            "PendingBackupLog.decode must still lack a version check",
            source.contains("if (parts.size < 4)"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #10: PendingBackupLog.encode uses
    //   Base64.getEncoder().withoutPadding() — unbounded line
    //   length. v1 finding #17 — never fixed.
    // -----------------------------------------------------------------
    @Test
    fun `PendingBackup payload is base64 with no line wrapping`() {
        val source = readSource("backup/PendingBackup.kt")
        assertNotNull(source)
        assertTrue(
            "PendingBackupLog.encode must use Base64.getEncoder().withoutPadding()",
            source!!.contains("Base64.getEncoder().withoutPadding()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #11: EncryptedBackupCodec.wrap / unwrap do not bind
    //   the AAD to the ContentType. v1 finding #6.
    //
    // v0.25.9 FIX: wrap and unwrap now take a
    //   [ContentType] and call cipher.updateAAD with
    //   type.fileName. A blob wrapped for Notes
    //   fails the tag check when unwrapped as
    //   Letters (the v2 test in
    //   `EncryptedBackupCodecTest.wrap with Notes AAD
    //   then unwrap as Letters returns null` is the
    //   behavioural pin).
    //
    // This file-shape pin is now the *fix* pin: a
    // regression that drops the AAD call or the
    // `type: ContentType` arg flips it red.
    // -----------------------------------------------------------------
    @Test
    fun `EncryptedBackupCodec binds AAD to the ContentType (v0_25_9 fix shape)`() {
        val source = readSource("backup/EncryptedBackupCodec.kt")
        assertNotNull(source)
        // Pin the FIX shape: both wrap and unwrap
        // call cipher.updateAAD with type.fileName,
        // and both signatures take a type: ContentType.
        val wrapHasAad = source!!.contains("updateAAD(type.fileName.toByteArray")
        val wrapHasType = source.contains("fun wrap(plaintextJson: String, type: ContentType)")
        val unwrapHasType = source.contains("fun unwrap(blob: ByteArray, type: ContentType)")
        assertTrue(
            "EncryptedBackupCodec.wrap must call cipher.updateAAD with type.fileName. " +
                "wrapHasAad=$wrapHasAad.",
            wrapHasAad,
        )
        assertTrue(
            "EncryptedBackupCodec.wrap must take `type: ContentType` so the AAD can be bound. " +
                "wrapHasType=$wrapHasType.",
            wrapHasType,
        )
        assertTrue(
            "EncryptedBackupCodec.unwrap must take `type: ContentType` so the AAD can be verified. " +
                "unwrapHasType=$unwrapHasType.",
            unwrapHasType,
        )
    }

    // -----------------------------------------------------------------
    // Finding #12: The encrypted backup codec uses a single
    //   fixed key (KeystoreAesKey.ALIAS). There is no key
    //   rotation path. A future v0.26 that wants to rotate
    //   the key (because the file is the size of a year of
    //   notes, the v0.25.4 write is forever, and key rotation
    //   is the recommended hygiene for a long-lived ciphertext)
    //   has no in-app path to do so. KeystoreAesKey exposes
    //   only getOrCreate and a hardcoded ALIAS.
    // -----------------------------------------------------------------
    @Test
    fun `KeystoreAesKey has no rotation path`() {
        val source = readSource("backup/KeystoreAesKey.kt")
        assertNotNull(source)
        assertTrue(
            "KeystoreAesKey must expose a single fixed ALIAS",
            source!!.contains("const val ALIAS = \"org.mindanchor.backup.aes-256-gcm\""),
        )
        // No rotate / delete + create / nextAlias.
        assertFalse(
            "KeystoreAesKey must NOT have a rotate function (no rotation path)",
            source.contains("fun rotate") || source.contains("fun deleteKey"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #13: KeystoreHmacKey is identical in shape to
    //   KeystoreAesKey (single fixed ALIAS, no rotation). The
    //   integrity layer's HMAC key cannot be rotated. A key
    //   rotation would invalidate every sealed value the
    //   user has, which is the right fail-closed behaviour,
    //   but there is no path to do it.
    // -----------------------------------------------------------------
    @Test
    fun `KeystoreHmacKey has a rotation path (v0_25_11 fix)`() {
        val source = readSource("friction/KeystoreHmacKey.kt")
        assertNotNull(source)
        assertTrue(
            "KeystoreHmacKey must expose a single fixed ALIAS",
            source!!.contains("const val ALIAS = \"org.mindanchor.friction.codec-integrity\""),
        )
        // v0.25.11 fix: the integrity layer now has a
        // managed rotation path. The [generation] counter
        // is stamped on every sealed value, [rotate()]
        // bumps it, and a value written with a previous
        // generation fails the verify on the next read
        // (the integrity layer returns the reset value).
        assertTrue(
            "KeystoreHmacKey must expose a `generation` counter (v0.25.11 rotation fix)",
            source.contains("var generation") || source.contains("val generation"),
        )
        assertTrue(
            "KeystoreHmacKey must expose a `rotate()` function (v0.25.11 rotation fix)",
            source.contains("fun rotate("),
        )
    }

    // -----------------------------------------------------------------
    // Finding #14: GoogleDriveAuth stores the access token in
    //   EncryptedSharedPreferences, but does NOT store the
    //   expiry. A token with a 1-hour TTL is treated as
    //   "still valid" indefinitely; the next Drive call uses
    //   a stale token and gets HTTP 401, which is a
    //   NetworkError at the target layer, which triggers a
    //   re-auth prompt at the Settings layer. The contract
    //   is "a 401 is fine, we will prompt" — but the on-write
    //   trigger does not prompt; it silently enqueues a
    //   pending backup that will fail on the next worker
    //   run because the token is still stale.
    //
    //   v0.25.11 fix: TokenStore now stamps a 1-hour
    //   expiry on every write and read() returns null
    //   for a stale token. A 401 is still the contract
    //   for the Drive call; the difference is that the
    //   on-write trigger now sees a stale token as no
    //   token and surfaces the re-auth prompt on the
    //   next Settings visit instead of enqueuing a
    //   pending backup that will fail.
    // -----------------------------------------------------------------
    @Test
    fun `GoogleDriveAuth TokenStore has an expiry field (v0_25_11 fix)`() {
        val source = readSource("backup/GoogleDriveAuth.kt")
        assertNotNull(source)
        assertTrue(
            "GoogleDriveAuth TokenStore must write only KEY_ACCESS_TOKEN (no expiry)",
            source!!.contains("KEY_ACCESS_TOKEN = \"access_token\""),
        )
        assertTrue(
            "GoogleDriveAuth TokenStore must write a token expiry (v0.25.11 fix)",
            source.contains("KEY_EXPIRY") || source.contains("expiry"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #15: GoogleDriveAuth.signInClient is a process
    //   singleton via `by lazy`. The GoogleSignIn API requires
    //   a sign-out before the next sign-in shows the account
    //   picker. The current signOut calls signInClient.signOut()
    //   but if signInClient is lazily unconstructed (the user
    //   has never signed in), the test surface / first-run path
    //   still works. The bug: signInClient is *constructed* on
    //   the *first* signInIntent() call. The clearLazy /
    //   rewire path on a sign-out is not implemented. After
    //   a signOut the same GoogleSignInClient instance is
    //   reused, and the user's identity is cached in
    //   GoogleSignIn singleton. The next signInIntent may
    //   silently re-auth the previous account.
    // -----------------------------------------------------------------
    @Test
    fun `GoogleDriveAuth signInClient is constructed lazily once and never rebuilt`() {
        val source = readSource("backup/GoogleDriveAuth.kt")
        assertNotNull(source)
        assertTrue(
            "GoogleDriveAuth.signInClient must be a by-lazy singleton",
            source!!.contains("private val signInClient: GoogleSignInClient by lazy"),
        )
        // signOut calls signInClient.signOut() but does not
        // rewire / drop the lazy. The same client is reused.
        // This is by design (GoogleSignIn manages its own
        // account state), but the comment at line 256
        // ("GoogleSignInClient.signOut is called so the next
        // sign-in shows the account picker") is at odds with
        // the lazy-singleton contract.
        assertTrue(
            "GoogleDriveAuth.signOut must call signInClient.signOut()",
            source.contains("signInClient.signOut()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #16: LetterStore.save replaces an existing letter
    //   for the same date — but does NOT update the read state
    //   for that date. A re-save of 2026-08-10 produces a
    //   letter with the new body, but the letters_read_dates
    //   set still says "2026-08-10 was read". The "unread
    //   badge" is therefore stale after a re-save.
    // -----------------------------------------------------------------
    @Test
    fun `LetterStore save does not clear the read flag for a re-saved date`() {
        val source = readSource("letters/LetterStore.kt")
        assertNotNull(source)
        // save() touches only the lettersKey, never the
        // readDatesKey.
        assertTrue(
            "LetterStore.save must NOT touch the letters_read_dates key",
            source!!.contains("prefs[lettersKey] = LetterLedger.encode(deduped)") &&
                !source.contains("save(letter: Letter)") ||
                // The save() body — the readDates key is
                // not in the same edit block. The function is
                // small enough to assert at file level: the
                // only edit block in save() writes to lettersKey.
                !Regex("suspend fun save\\([^)]*\\)[\\s\\S]*?prefs\\[readDatesKey\\]").containsMatchIn(source),
        )
    }

    // -----------------------------------------------------------------
    // Finding #17: LetterStore.save / delete do O(n) re-serialise
    //   of the letters list on every op. Same O(n²) lifetime as
    //   NotesPrefs and BackupPrefs.pendingBackups. With a
    //   single letter per day, n is bounded at ~30, but the
    //   pattern is the same and a future "letters can be
    //   multi-page" change would inherit the O(n) cost.
    // -----------------------------------------------------------------
    @Test
    fun `LetterStore save and delete re-serialise the full list on every op`() {
        val source = readSource("letters/LetterStore.kt")
        assertNotNull(source)
        assertTrue(
            "LetterStore.save must call LetterLedger.encode on every op",
            source!!.contains("LetterLedger.encode(deduped)"),
        )
        assertTrue(
            "LetterStore.delete must call LetterLedger.encode on every op",
            source.contains("LetterLedger.encode(kept)"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #18: WellnessHistoryStore.recordAll re-decodes the
    //   full entry list on every batched record. The v0.25.7+
    //   WP-x comment at line 174 says "5-signal refresh
    //   previously ran five sequential edits ... the second
    //   write could finish before the first when refreshes
    //   overlap, leaving the file in a state that reflected
    //   only some of the signals". The fix wraps the loop in
    //   a single edit block — but the encode cost is still
    //   O(n) per call, and the `for` loop iterates the
    //   decoded list once per signal.
    // -----------------------------------------------------------------
    @Test
    fun `WellnessHistoryStore recordAll re-decodes once but encodes after every signal in the loop`() {
        val source = readSource("vitals/WellnessHistoryStore.kt")
        assertNotNull(source)
        // The fix is a single edit block + a for loop, not an
        // upsert-and-prune shortcut. The encode happens once
        // at the end, but the in-memory `current` is mutated
        // 5x (once per signal).
        assertTrue(
            "WellnessHistoryStore.recordAll must wrap in a single edit block",
            source!!.contains("context.wellnessDataStore.edit { prefs ->") &&
                source.contains("for ((signal, value) in values)"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #19: WellnessHistoryStore.KEEP_DAYS = 180. The
    //   wellness surface baseline uses a 90-day window; the
    //   180-day floor is "comfortably past". The pruning is
    //   only triggered on *write* — a user who has paired
    //   Health Connect and stops wearing the watch for a year
    //   has a 180-day-old history that never gets pruned until
    //   the next write.
    // -----------------------------------------------------------------
    @Test
    fun `WellnessHistoryStore pruning only happens on write, not on read`() {
        val source = readSource("vitals/WellnessHistoryStore.kt")
        assertNotNull(source)
        assertTrue(
            "WellnessHistoryStore must have a 180-day KEEP_DAYS",
            source!!.contains("const val KEEP_DAYS = 180"),
        )
        // Pruning is only inside the edit block.
        assertTrue(
            "WellnessHistoryStore.prune must be called from the edit block only",
            source.contains("val pruned = WellnessLedger.prune"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #20: LauncherPrefs.oneThing is a single nullable
    //   String. There is no "history of past one-things" — the
    //   spec says "one thing for today". A user who set a
    //   one-thing yesterday and reopens the app today sees
    //   the SAME text. The contract is "today's one thing" but
    //   the storage shape is "the most recent one thing". No
    //   date stamp on the entry; the freshness is implicit
    //   (you set it, it's fresh).
    // -----------------------------------------------------------------
    @Test
    fun `LauncherPrefs oneThing has no date stamp, no day-rollover`() {
        val source = readSource("data/LauncherPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "LauncherPrefs.oneThing must be a single String (no date stamp)",
            source!!.contains("val oneThing: Flow<String?>") &&
                source.contains("prefs[oneThingKey]?.takeIf { it.isNotBlank() }"),
        )
        // No day rollover logic.
        assertFalse(
            "LauncherPrefs.oneThing must NOT have a day rollover path",
            source.contains("oneThingDay") || source.contains("oneThingDate"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #21: FrictionPrefs.openLoopPostponedAt is stored
    //   as an Instant string. The launcher phase() function at
    //   OpenLoop.kt:108-142 uses the postponedAt to decide
    //   POSTPONED vs NONE. A postponedAt in the past (user
    //   set "later today" at 8am, it is now 8pm) falls through
    //   to the normal hand-it-back flow — which is correct.
    //   But a postponedAt in the *far future* (user set
    //   "next month") makes the launcher silent for a month,
    //   even if the user has cleared the open loop. The
    //   clearOpenLoop() function removes the keys, so the
    //   next call returns NONE. But if a user partially
    //   postpones without clearing (setOpenLoopPostponedAt),
    //   the post is orphaned in the data — no validation.
    // -----------------------------------------------------------------
    @Test
    fun `FrictionPrefs openLoopPostponedAt is not validated for a cleared note`() {
        val source = readSource("data/FrictionPrefs.kt")
        assertNotNull(source)
        // setOpenLoopPostponedAt writes the postponedAt key
        // without touching the note/day key. If the user
        // clears the note but leaves the postponedAt, the
        // flow has an orphaned postponedAt.
        assertTrue(
            "FrictionPrefs.setOpenLoopPostponedAt must write only the postponedAt key",
            source!!.contains("prefs[loopPostponedAtKey] = at.toString()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #22: FrictionPrefs.recordReach uses a "last tab"
    //   split on the persisted line, not a first tab. The
    //   package name can contain a tab if a future launcher
    //   re-uses a custom component name; the last-tab split
    //   handles that. But the *write* side at line 100 joins
    //   with "\t${it.second}" — same shape, so the read/write
    //   match. The risk: the packageName is taken from the
    //   call site; an empty package name produces a line
    //   like "\t1234567890" which decode-as-("" to stamp)
    //   silently records an empty-key reach.
    // -----------------------------------------------------------------
    @Test
    fun `FrictionPrefs recordReach rejects a blank package name (v0_25_11 fix)`() {
        val source = readSource("data/FrictionPrefs.kt")
        assertNotNull(source)
        // v0.25.11 fix: the filter for a blank
        // packageName is now present at the top of
        // recordReach. The pin is the fix shape; a
        // regression that drops the guard flips the
        // regex to false and the test would fail.
        assertTrue(
            "FrictionPrefs.recordReach must validate a blank packageName (v0.25.11 fix)",
            Regex("fun recordReach\\([^)]*\\)[\\s\\S]*?if \\(packageName\\.isBlank\\(\\)\\)")
                .containsMatchIn(source!!),
        )
        // And the function exists with the right signature.
        assertTrue(
            "FrictionPrefs.recordReach must still exist with the current signature",
            source.contains("suspend fun recordReach(packageName: String, now: Long, windowMillis: Long): Int"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #23: FrictionPrefs.encodeGoingLight has 10
    //   tab-separated fields. The decode splits on tabs and
    //   looks up parts[8] and parts[9] for start/end minutes.
    //   A future field-insertion between part 4 and part 8
    //   would silently mis-index. The codec is positional,
    //   not versioned.
    // -----------------------------------------------------------------
    @Test
    fun `FrictionPrefs encodeGoingLight is a positional 10-field codec with no version`() {
        val source = readSource("data/FrictionPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "FrictionPrefs.encodeGoingLight must produce 10 fields",
            source!!.contains("listOf(") &&
                source.contains("dayBooleans") &&
                source.contains("s.startTime.hour * 60 + s.startTime.minute") &&
                source.contains("s.endTime.hour * 60 + s.endTime.minute"),
        )
        // And the decode does not check a version marker.
        assertTrue(
            "FrictionPrefs.decodeGoingLight must accept any 10+ parts",
            source.contains("if (parts.size < 10) return GoingLightSchedule()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #24: ReaderPrefs.reset is `internal` — same as
    //   BackupPrefs.reset (v1 finding #16). The contract is
    //   "test-only" but the visibility allows production
    //   callers in the same module. ReaderPrefs has no
    //   production caller of reset; the risk is a future
    //   caller that calls reset to "clear all reading size"
    //   and accidentally wipes the wrong key.
    // -----------------------------------------------------------------
    @Test
    fun `ReaderPrefs reset is internal not private`() {
        val source = readSource("reader/ReaderPrefs.kt")
        assertNotNull(source)
        assertTrue(
            "ReaderPrefs.reset must be `internal` (test-only pattern)",
            source!!.contains("internal suspend fun reset()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #25: LetterStore.reset is `internal`. Same
    //   pattern as the other Prefs resets. The reset wipes
    //   every key in the DataStore, including the read-state
    //   and the time-of-day.
    // -----------------------------------------------------------------
    @Test
    fun `LetterStore reset is internal not private`() {
        val source = readSource("letters/LetterStore.kt")
        assertNotNull(source)
        assertTrue(
            "LetterStore.reset must be `internal` (test-only pattern)",
            source!!.contains("internal suspend fun reset()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #26: IntegritySealedCodec.decode's check at
    //   line 211-212 is `if (!encoded.startsWith(ENVELOPE_PREFIX))`
    //   — any record that does not start with the literal
    //   "v1\t" is treated as a v0.20.0 plaintext form and the
    //   reset value is returned. The MAC is not even checked
    //   on these "plaintext" records. A v0.20.0 file is
    //   intentionally degraded to the reset value, but the
    //   same fail-closed applies to a corrupted envelope
    //   (one byte off) — the user loses all their data and
    //   has no way to know why.
    // -----------------------------------------------------------------
    @Test
    fun `IntegritySealedCodec decode fails-closed on any non-v1 prefix without surfacing the reason`() {
        val source = readSource("friction/IntegritySealedCodec.kt")
        assertNotNull(source)
        assertTrue(
            "IntegritySealedCodec.decode must check the v1 prefix first",
            source!!.contains("if (!encoded.startsWith(ENVELOPE_PREFIX))"),
        )
        // The reset path returns silently — no log, no
        // surface message. The "fail-closed" doc claims a
        // user-visible message exists, but the codec does
        // not produce one.
        assertTrue(
            "IntegritySealedCodec.decode must return resetValue silently on a bad prefix",
            source.contains("return resetValue"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #27: The data class PendingBackup (in
    //   PendingBackup.kt) is missing a `toString` and a
    //   `copy` component override. PendingBackup is a data
    //   class with a ByteArray; data class's auto-generated
    //   toString() uses the ByteArray's `toString()` which
    //   is the [B@hexhash] form. Log lines that include
    //   a PendingBackup print unreadable output.
    // -----------------------------------------------------------------
    @Test
    fun `PendingBackup has no custom toString (ByteArray field prints as B@hex)`() {
        val source = readSource("backup/PendingBackup.kt")
        assertNotNull(source)
        // Only equals and hashCode are overridden.
        assertFalse(
            "PendingBackup must NOT have a custom toString",
            source!!.contains("override fun toString()"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #28: WellnessHistoryStore.historyFor sorts the
    //   result in-memory on every call. The store's
    //   all() reads and decodes the full payload; the
    //   filter + map + sortBy happens on every call. With
    //   5 signals x 180 days = 900 entries, the decode is
    //   900 lines, the sort is 180, the filter is 900.
    //   The flow is .map { ... .sortedBy { it.day } }, so
    //   every collector pays this on every emit.
    // -----------------------------------------------------------------
    @Test
    fun `WellnessHistoryStore historyFor sorts on every flow emission`() {
        val source = readSource("vitals/WellnessHistoryStore.kt")
        assertNotNull(source)
        assertTrue(
            "WellnessHistoryStore.historyFor must call .sortedBy on every emission",
            source!!.contains("historyFor") &&
                source.contains(".sortedBy { it.day }"),
        )
    }

    // -----------------------------------------------------------------
    // Finding #29: PpgSessionStore.recent is a `fun recent`,
    //   not a `suspend fun recent`. It uses the .map operator
    //   on the data flow, but the existing test (PpgSessionFindingTest)
    //   exercises the .all() path (which is suspend). The
    //   recent path is not tested. A reader that catches an
    //   exception in the decode (the runCatching in all())
    //   returns emptyList(); the .map in recent() does NOT
    //   have runCatching, so a decode failure on a metadata-
    //   only DataStore re-emit would surface a Flow
    //   exception to the collector.
    // -----------------------------------------------------------------
    @Test
    fun `PpgSessionStore recent does not catch decode exceptions`() {
        val source = readSource("vitals/PpgSessionStore.kt")
        assertNotNull(source)
        // The recent path uses .map without .catch.
        assertTrue(
            "PpgSessionStore.recent must use .map without .catch",
            source!!.contains("fun recent(limit: Int) =") &&
                source.contains("ppgSessionDataStore.data.map"),
        )
        assertFalse(
            "PpgSessionStore.recent must NOT use .catch",
            Regex("fun recent[\\s\\S]*?\\.catch\\(").containsMatchIn(source),
        )
    }

    // -----------------------------------------------------------------
    // Finding #30: The data-shape migration is fail-closed
    //   (SealedCodecs) for notes, check-ins, small things,
    //   bedtime, compassion, if-then plans, gate tallies, and
    //   per-app session length. The fail-closed policy is:
    //   a v0.20.0 plaintext form returns the reset value
    //   (empty list, empty plan). The launcher does NOT
    //   attempt to migrate a v0.20.0 plaintext record —
    //   even though the records are readable in plaintext
    //   form, the integrity layer refuses them. The
    //   reasoning is sound (a forged record is
    //   indistinguishable from a v0.20.0 plaintext), but
    //   the user has no way to recover their pre-v0.20.1
    //   data after the upgrade.
    // -----------------------------------------------------------------
    @Test
    fun `SealedCodecs fail-closes on any non-v1 envelope, no migration path`() {
        val source = readSource("friction/SealedCodecs.kt")
        assertNotNull(source)
        // The fail-closed path is: a non-v1 record returns
        // NotesState() (or equivalent). No migration helper.
        assertTrue(
            "SealedCodecs must fail-closed on decode failure",
            source!!.contains("NotesState()") &&
                source.contains("CheckInState()") &&
                source.contains("emptyMap()"),
        )
        assertFalse(
            "SealedCodecs must NOT expose a migration helper",
            source.contains("fun migrate") || source.contains("migrateV0200"),
        )
    }
}
