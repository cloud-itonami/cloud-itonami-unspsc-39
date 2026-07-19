(ns elecinstall.governor
  "Electrical Install Governor -- the independent compliance layer
  that earns the DiagnosticsAdvisor the right to commit. The advisor
  has no notion of whether a site it wants to schedule a repair/
  install visit against has actually been surveyed/registered,
  whether a circuit it wants to issue a commissioning record against
  has actually been diagnostic-tested/registered, whether a repair
  proposal secretly tries to ACTUATE (rather than merely draft-
  schedule) a circuit, whether a proposal secretly tries to self-issue
  an interconnection-approval/commissioning-authority CERTIFICATION
  (an authority this actor never holds), whether a commissioning
  proposal's own claimed load would blow through the circuit's own
  logged rated capacity, or when an act stops being a coordination
  proposal and becomes direct circuit control, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:electrical-install-governor` (see
  docs/adr/0001-architecture.md).

  Checks below, ALL HARD violations except the confidence/high-stakes
  gate (SOFT -- asks a human to look, and the human may approve):

    1. Request-level propose-only  -- did the CALLER's own request
                                       actually declare `:effect
                                       :propose`? Any other value is a
                                       mis-wired/compromised caller
                                       trying to bypass proposal-only
                                       mode -- HARD, unconditional,
                                       evaluated BEFORE anything else.
    2. Closed op allowlist         -- is `:op` one of the four ops this
                                       actor is authorized to coordinate?
                                       Anything else -- HARD hold.
    3. Closed effect allowlist     -- is the PROPOSAL's own `:effect`
                                       (what would actually commit) one
                                       of the four propose-shaped
                                       effects? A proposal effect
                                       outside this set (e.g. a
                                       hallucinated `:breaker/close` or
                                       `:charger-relay/actuate`) is the
                                       'direct circuit control' scope
                                       violation this actor must NEVER
                                       perform -- HARD, PERMANENT,
                                       unconditional.
    4. Circuit-actuate blocked     -- for `:schedule-repair`, does the
                                       proposal's own `:value` declare
                                       `:actuate-circuit? true`?
                                       Directly energizing/
                                       de-energizing or actuating a
                                       circuit/breaker is this actor's
                                       other permanent scope boundary
                                       (see README `What this actor
                                       does NOT do`, grounded in OSHA
                                       29 CFR 1910.333's de-
                                       energization/lockout-tagout
                                       requirement and NFPA 70E) --
                                       HARD, PERMANENT, unconditional.
                                       NO phase and NO human approval
                                       can ever override this (see
                                       `elecinstall.phase`: this op is
                                       never a member of any phase's
                                       `:auto` set either -- two
                                       independent layers agree).
    5. Certification authority
       blocked                     -- ANY proposal (any op) whose own
                                       `:value`/`:patch` declares
                                       `:issue-certification? true` is
                                       attempting to self-issue a
                                       commissioning/interconnection-
                                       approval certification mark --
                                       an authority exclusively
                                       reserved to the AHJ inspector /
                                       utility interconnection
                                       approval, never this actor --
                                       HARD, PERMANENT, unconditional.
    6. Site not verified/
       registered                  -- for `:schedule-repair`,
                                       INDEPENDENTLY verify the
                                       referenced site's own
                                       `:verified?` AND `:registered?`
                                       are both true
                                       (`elecinstall.registry/site-
                                       ready?`) -- never trust the
                                       advisor's own rationale about
                                       verification/registration
                                       status. Grounded in this
                                       blueprint's own HARD invariant
                                       ('site/circuit record must be
                                       independently verified/
                                       registered before any action').
    7. Already scheduled           -- for `:schedule-repair`, refuses
                                       to schedule the SAME repair
                                       record twice, off a dedicated
                                       `:scheduled?` fact (never a
                                       `:status` value).
    8. Circuit not verified/
       registered                  -- for `:issue-commissioning-
                                       record`, INDEPENDENTLY verify
                                       the referenced circuit's own
                                       `:verified?` AND `:registered?`
                                       are both true
                                       (`elecinstall.registry/circuit-
                                       ready?`) -- never trust the
                                       advisor's own rationale. Also
                                       part of the 'site/circuit
                                       record' HARD invariant: a
                                       circuit's own verified/
                                       registered status is as much a
                                       ground-truth fact as a site's
                                       own.
    9. Load exceeds rated
       capacity                    -- for `:issue-commissioning-
                                       record`, INDEPENDENTLY
                                       recompute whether the circuit's
                                       own recorded
                                       `:committed-load-amps` plus the
                                       proposal's own claimed
                                       `:load-amps` would exceed the
                                       circuit's own recorded
                                       `:rated-capacity-amps`
                                       (`elecinstall.registry/load-
                                       exceeds-rated-capacity?`) --
                                       ground truth from the circuit's
                                       own permanent fields, never a
                                       self-reported load claim.
   10. Invalid fault-type          -- for `:log-diagnostic-reading`,
                                       if the patch declares a
                                       `:fault-type` outside the
                                       closed known set
                                       (`elecinstall.registry/fault-
                                       type-valid?`), the circuit
                                       record is rejected rather than
                                       let a fabricated fault type
                                       through.
   11. Invalid voltage             -- for `:log-diagnostic-reading`,
                                       if the patch declares a
                                       `:voltage-v` that is not a
                                       physically plausible reading
                                       (`elecinstall.registry/
                                       voltage-valid?`), the circuit
                                       record is rejected rather than
                                       let a fabricated/sensor-error
                                       reading through.
   12. Invalid current             -- for `:log-diagnostic-reading`,
                                       if the patch declares a
                                       `:current-a` that is not a
                                       physically plausible reading
                                       (`elecinstall.registry/
                                       current-valid?`), the circuit
                                       record is rejected rather than
                                       let fabricated/sensor-error
                                       data through.
   13. Confidence floor / high-
       stakes gate                  -- LLM confidence below threshold,
                                       OR the proposal's own `:stake` is
                                       in `high-stakes`
                                       (`:coordination/safety-concern`,
                                       ALWAYS set for `:flag-safety-
                                       concern`) -- escalate to a human
                                       qualified electrician. SOFT: the
                                       human may approve."
  (:require [elecinstall.registry :as registry]
            [elecinstall.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed allowlist of coordination proposals this actor may ever
  route -- see README `What this actor does`."
  #{:log-diagnostic-reading :schedule-repair
    :flag-safety-concern :issue-commissioning-record})

(def allowed-proposal-effects
  "The closed allowlist of SSoT-mutation effects a proposal may declare
  -- all four are propose-shaped drafts, NEVER a direct circuit-
  control effect."
  #{:circuit/upsert :repair/schedule
    :safety-concern/flag :commissioning/propose})

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Safety concerns are the one op in this domain that always demands
  human eyes regardless of confidence."
  #{:coordination/safety-concern})

;; ----------------------------- checks -----------------------------

(defn- no-propose-effect-violations
  "HARD, unconditional, evaluated first: the caller's own request MUST
  declare `:effect :propose` -- any other value is a mis-wired or
  compromised caller trying to bypass proposal-only mode."
  [{:keys [effect]}]
  (when (not= effect :propose)
    [{:rule :not-propose-effect
      :detail (str "request :effect は :propose のみ許可 (受信値: " (pr-str effect) ")")}]))

(defn- unknown-op-violations
  "HARD: `:op` must be one of the closed allowlist this actor
  coordinates -- never route an unrecognized operation."
  [{:keys [op]}]
  (when-not (contains? allowed-ops op)
    [{:rule :unknown-op
      :detail (str op " はこの actor が扱う操作の許可リストに無い")}]))

(defn- circuit-control-blocked-violations
  "HARD, PERMANENT: the proposal's own `:effect` -- what would actually
  commit -- must be within the closed propose-shaped effect allowlist.
  Anything else (direct circuit control, a fabricated actuation
  effect) is this actor's central scope boundary."
  [proposal]
  (when-not (contains? allowed-proposal-effects (:effect proposal))
    [{:rule :circuit-control-blocked
      :detail (str "proposal :effect (" (pr-str (:effect proposal))
                   ") は回路への直接操作に該当する可能性があり、恒久的に禁止")}]))

(defn- circuit-actuate-blocked-violations
  "HARD, PERMANENT, unconditional: a `:schedule-repair` proposal whose
  own `:value` declares `:actuate-circuit? true` is attempting to
  directly energize/de-energize or actuate a circuit/breaker -- this
  actor may only ever propose/schedule a DRAFT repair/install visit,
  never actuate a circuit directly (grounded in OSHA 29 CFR 1910.333's
  de-energization/lockout-tagout requirement and NFPA 70E). No
  override, ever."
  [{:keys [op]} proposal]
  (when (and (= op :schedule-repair)
             (true? (:actuate-circuit? (:value proposal))))
    [{:rule :circuit-actuate-blocked
      :detail "回路への直接操作(actuate)提案は恒久的に禁止 -- 提案(draft)のみ許可"}]))

(defn- certification-authority-blocked-violations
  "HARD, PERMANENT, unconditional: ANY proposal (any op) whose own
  `:value`/`:patch` declares `:issue-certification? true` is attempting
  to self-issue a commissioning/interconnection-approval certification
  mark -- an authority exclusively reserved to the AHJ inspector /
  utility interconnection approval, never this actor. No phase and no
  human approval can ever override this."
  [proposal]
  (let [payload (or (:value proposal) (:patch proposal))]
    (when (true? (:issue-certification? payload))
      [{:rule :certification-authority-blocked
        :detail "系統連系承認・コミッショニング認定の自己発行提案は恒久的に禁止 -- 認可当局の専権事項"}])))

(defn- site-not-verified-violations
  "For `:schedule-repair`, INDEPENDENTLY verify the referenced site
  exists and is both `:verified?` AND `:registered?` -- never trust
  the advisor's own report. This is the HARD invariant ('site/circuit
  record must be independently verified/registered before any
  action')."
  [{:keys [op]} proposal st]
  (when (= op :schedule-repair)
    (let [site-id (:site-id (:value proposal))
          s (and site-id (store/site st site-id))]
      (when-not (and s (registry/site-ready? s))
        [{:rule :site-not-verified
          :detail (str site-id " は未検証または未登録、もしくは存在しない -- 検証済み・登録済みサイト記録が無い状態での修理/設置訪問予定提案")}]))))

(defn- already-scheduled-violations
  "For `:schedule-repair`, refuses to schedule the SAME repair record
  twice, off a dedicated `:scheduled?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :schedule-repair)
    (when (store/repair-already-scheduled? st subject)
      [{:rule :already-scheduled
        :detail (str subject " は既にスケジュール済み")}])))

(defn- circuit-not-verified-violations
  "For `:issue-commissioning-record`, INDEPENDENTLY verify the
  referenced circuit exists and is both `:verified?` AND
  `:registered?` -- never trust the advisor's own report. Also part of
  the 'site/circuit record must be independently verified/registered
  before any action' HARD invariant."
  [{:keys [op]} proposal st]
  (when (= op :issue-commissioning-record)
    (let [circuit-id (:circuit-id (:value proposal))
          c (and circuit-id (store/circuit st circuit-id))]
      (when-not (and c (registry/circuit-ready? c))
        [{:rule :circuit-not-verified
          :detail (str circuit-id " は未検証または未登録、もしくは存在しない -- 検証済み・登録済み回路記録が無い状態でのコミッショニング記録提案")}]))))

(defn- load-exceeds-rated-capacity-violations
  "For `:issue-commissioning-record`, INDEPENDENTLY recompute whether
  the circuit's own recorded cumulative-committed load plus the
  proposal's own claimed load would exceed the circuit's own recorded
  `:rated-capacity-amps` -- ground truth from the circuit's own
  permanent fields, never a self-reported load claim."
  [{:keys [op]} proposal st]
  (when (= op :issue-commissioning-record)
    (let [{:keys [circuit-id load-amps]} (:value proposal)
          c (and circuit-id (store/circuit st circuit-id))]
      (when (and c (registry/load-exceeds-rated-capacity? c load-amps))
        [{:rule :load-exceeds-rated-capacity
          :detail (str circuit-id " の記録済み定格容量(" (:rated-capacity-amps c)
                       "A)を、既存コミット済み負荷(" (:committed-load-amps c 0.0)
                       "A)+今回申請(" load-amps "A)が超過")}]))))

(defn- invalid-fault-type-violations
  "For `:log-diagnostic-reading`, if the patch declares a
  `:fault-type` outside the closed known set, reject rather than let a
  fabricated fault type through."
  [{:keys [op]} proposal]
  (when (= op :log-diagnostic-reading)
    (let [fault-type (:fault-type (:value proposal))]
      (when (and (some? fault-type) (not (registry/fault-type-valid? fault-type)))
        [{:rule :invalid-fault-type
          :detail (str fault-type " は既知の fault-type 値ではない")}]))))

(defn- invalid-voltage-violations
  "For `:log-diagnostic-reading`, if the patch declares a `:voltage-v`
  that is not a physically plausible rating, reject rather than let
  fabricated/sensor-error data through."
  [{:keys [op]} proposal]
  (when (= op :log-diagnostic-reading)
    (let [v (:voltage-v (:value proposal))]
      (when (and (some? v) (not (registry/voltage-valid? v)))
        [{:rule :invalid-voltage
          :detail (str v "V は物理的に妥当な voltage-v の範囲外")}]))))

(defn- invalid-current-violations
  "For `:log-diagnostic-reading`, if the patch declares a `:current-a`
  that is not a physically plausible reading, reject rather than let
  fabricated/sensor-error data through."
  [{:keys [op]} proposal]
  (when (= op :log-diagnostic-reading)
    (let [i (:current-a (:value proposal))]
      (when (and (some? i) (not (registry/current-valid? i)))
        [{:rule :invalid-current
          :detail (str i "A は物理的に妥当な current-a の範囲外")}]))))

(defn check
  "Censors a DiagnosticsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (no-propose-effect-violations request)
                           (unknown-op-violations request)
                           (circuit-control-blocked-violations proposal)
                           (circuit-actuate-blocked-violations request proposal)
                           (certification-authority-blocked-violations proposal)
                           (site-not-verified-violations request proposal st)
                           (already-scheduled-violations request st)
                           (circuit-not-verified-violations request proposal st)
                           (load-exceeds-rated-capacity-violations request proposal st)
                           (invalid-fault-type-violations request proposal)
                           (invalid-voltage-violations request proposal)
                           (invalid-current-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
