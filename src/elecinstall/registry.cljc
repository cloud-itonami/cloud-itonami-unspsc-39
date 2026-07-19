(ns elecinstall.registry
  "Pure-function domain logic for the independent solar and
  EV-charging install-and-diagnostics coordination actor -- site/
  circuit verification, cumulative committed-load recompute against a
  circuit's own rated capacity, fault-type validation, voltage
  plausibility validation, and current plausibility validation, plus
  draft repair-schedule/commissioning-record construction.

  Per docs/adr/0001-architecture.md Decision 1, this vertical has NO
  pre-existing `kotoba-lang/elecinstall`-style capability library to
  wrap (verified: no such repo exists). The domain logic therefore
  lives here as pure functions, re-verified INDEPENDENTLY by
  `elecinstall.governor` -- the same 'ground truth, not self-report'
  discipline established across this fleet (most directly
  `cloud-itonami-isic-3091`'s `motomfg.registry`): never trust a
  proposal's own self-reported cumulative committed load when the
  inputs needed to recompute it independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real field-service system. It builds the DRAFT record an
  installer/coordinator would keep (a scheduled repair/install visit,
  a coordinated commissioning), not the act of energizing/
  de-energizing a live circuit or issuing an interconnection-approval/
  commissioning-authority certification (this actor NEVER does any of
  those -- see README `What this actor does NOT do`).

  SAFETY GROUNDING: the permanent block on directly actuating a
  circuit (`elecinstall.governor/circuit-actuate-blocked-violations`)
  is grounded in OSHA 29 CFR 1910.333 (Selection and use of work
  practices -- live parts must be de-energized before work unless
  infeasible, and locked out/tagged per 1910.333(b)) and NFPA 70E
  (Standard for Electrical Safety in the Workplace) -- both real,
  verified 2026-07-19 against osha.gov / nfpa.org. This actor is
  never the one that determines a circuit is safely de-energized or
  performs the lockout/tagout -- that is the qualified electrician's
  physical act, always outside this actor's authority."
  )

;; ----------------------------- constants -----------------------------

(def valid-fault-types
  "The closed set of fault-type classifications a panel/charger
  diagnostic reading may report. Anything else is a fabricated/
  unrecognized fault type -- the governor HARD-holds rather than let
  an invented fault type through."
  #{:none :ground-fault :arc-fault :insulation-breakdown
    :inverter-fault :connector-fault :overcurrent-trip :thermal-anomaly})

(def voltage-min-v
  "Physical floor for a panel/charger voltage sensor reading."
  0)

(def voltage-max-v
  "Physical ceiling for a panel/charger voltage sensor reading -- spans
  residential/commercial low-voltage (120/240/480V) through DC
  fast-charging bus voltages; a reading above this is implausible/
  fabricated sensor data, not a real installation."
  1000)

(def current-min-a
  "Physical floor for a panel/charger current sensor reading."
  0)

(def current-max-a
  "Physical ceiling for a panel/charger current sensor reading -- spans
  residential branch circuits through DC fast-charger draw; a reading
  above this is implausible/fabricated sensor data."
  500)

;; ----------------------------- site checks -----------------------------

(defn site-verified?
  "Ground-truth check: has `site`'s own record been marked verified
  (i.e. it has actually been surveyed, not merely referenced from an
  unverified repair request)? A pure predicate over the site's own
  permanent field -- no proposal inspection needed."
  [site]
  (true? (:verified? site)))

(defn site-registered?
  "Ground-truth check: does `site`'s own record carry a `:registered?`
  true flag (i.e. it is on file in the operator's site registry)?
  Scheduling a repair against a site that is not on file and
  registered is the exact scope violation this actor's HARD invariant
  ('site/circuit record must be independently verified/registered
  before any action') exists to block."
  [site]
  (true? (:registered? site)))

(defn site-ready?
  "Combined ground-truth gate: the site must be both `verified?` AND
  `registered?` before ANY repair/install visit may be scheduled
  against it."
  [site]
  (and (site-verified? site) (site-registered? site)))

;; ----------------------------- circuit checks -----------------------------

(defn circuit-verified?
  "Ground-truth check: has `circuit`'s own record been marked verified
  (i.e. its rated-capacity/committed-load claims have actually been
  diagnostic-tested, not merely logged from an unverified intake
  patch)?"
  [circuit]
  (true? (:verified? circuit)))

(defn circuit-registered?
  "Ground-truth check: is `circuit`'s own record on file in the
  operator's circuit registry? Issuing a commissioning record against
  a circuit that is not on file and registered is the exact scope
  violation this actor's HARD invariant exists to block."
  [circuit]
  (true? (:registered? circuit)))

(defn circuit-ready?
  "Combined ground-truth gate: the circuit must be both `verified?`
  AND `registered?` before ANY commissioning record may be issued
  against it."
  [circuit]
  (and (circuit-verified? circuit) (circuit-registered? circuit)))

(defn load-exceeds-rated-capacity?
  "Ground-truth check for an `:issue-commissioning-record` proposal:
  would `committed-load-amps` + `new-load-amps` exceed `circuit`'s own
  recorded `:rated-capacity-amps` (the circuit's own logged panel/
  breaker rating)? Needs no proposal inspection or stored-verdict
  lookup -- its inputs are permanent fields already on the circuit's
  own record, the same shape every sibling actor's own cost/total-
  matching check uses (mirrors `motomfg.registry/shipment-quantity-
  exceeded?`'s cumulative-shipped-units pattern)."
  [circuit new-load-amps]
  (let [capacity (:rated-capacity-amps circuit)
        so-far (:committed-load-amps circuit 0.0)]
    (and (number? capacity)
         (number? new-load-amps)
         (> (+ (double so-far) (double new-load-amps)) (double capacity)))))

(defn fault-type-valid?
  "Is `fault-type` one of the closed, known fault-type values? nil is
  treated as invalid -- a diagnostic-reading patch that declares a
  fault-type field at all must declare a real one, not omit it
  silently."
  [fault-type]
  (contains? valid-fault-types fault-type))

(defn voltage-valid?
  "Is `voltage-v` a physically plausible panel/charger voltage
  reading? Rejects nil, non-numbers, negative values, and values
  beyond `voltage-max-v`."
  [voltage-v]
  (and (number? voltage-v)
       (>= (double voltage-v) (double voltage-min-v))
       (<= (double voltage-v) (double voltage-max-v))))

(defn current-valid?
  "Is `current-a` a physically plausible panel/charger current
  reading? Rejects nil, non-numbers, negative values, and values
  beyond `current-max-a`."
  [current-a]
  (and (number? current-a)
       (>= (double current-a) (double current-min-a))
       (<= (double current-a) (double current-max-a))))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human qualified electrician's/AHJ inspector's act, not this
  actor's. And NEVER a commissioning/interconnection-approval
  certification mark -- this actor is never the AHJ/utility
  interconnection authority (see README `What this actor does NOT
  do`)."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-repair
  "Validate + construct the REPAIR-SCHEDULE DRAFT -- a proposed
  repair/install visit against a verified, registered site. Pure
  function -- does not energize/de-energize or actuate any circuit; it
  builds the RECORD a coordinator would keep. `elecinstall.governor`
  independently re-verifies the site's own verified/registered ground
  truth, and permanently blocks any attempt to directly actuate a
  circuit (see README `Actuation`), before this is ever allowed to
  commit."
  [repair-id site-id sequence]
  (when-not (and repair-id (not= repair-id ""))
    (throw (ex-info "repair: repair_id required" {})))
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "repair: site_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "repair: sequence must be >= 0" {})))
  (let [repair-number (str "RPR-" (zero-pad sequence 6))
        record {"record_id" repair-number
                "kind" "repair-schedule-draft"
                "repair_id" repair-id
                "site_id" site-id
                "immutable" true}]
    {"record" record "repair_number" repair-number
     "certificate" (unsigned-certificate "RepairSchedule" repair-number repair-number)}))

(defn register-commissioning
  "Validate + construct the COMMISSIONING-COORDINATION DRAFT -- a
  proposed commissioning record against a verified, registered
  circuit. Pure function -- does not energize the circuit or issue any
  real interconnection approval; it builds the RECORD a coordinator
  would keep. `elecinstall.governor` independently re-verifies the
  commissioning's own claimed load against
  `load-exceeds-rated-capacity?`, before this is ever allowed to
  commit."
  [commissioning-id sequence]
  (when-not (and commissioning-id (not= commissioning-id ""))
    (throw (ex-info "commissioning: commissioning_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "commissioning: sequence must be >= 0" {})))
  (let [commissioning-number (str "CMS-" (zero-pad sequence 6))
        record {"record_id" commissioning-number
                "kind" "commissioning-coordination-draft"
                "commissioning_id" commissioning-id
                "immutable" true}]
    {"record" record "commissioning_number" commissioning-number
     "certificate" (unsigned-certificate "CommissioningCoordination" commissioning-number commissioning-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
