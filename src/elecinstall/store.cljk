(ns elecinstall.store
  "SSoT for the independent solar and EV-charging install-and-
  diagnostics coordination actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every
  `cloud-itonami-*` actor in this fleet uses.

  Scope note: like its siblings (`cloud-itonami-isic-3091`'s own
  `motomfg.store`), this build ships a single `MemStore` backend only
  (atom of EDN) -- the deterministic default for dev/tests/demo, no
  deps. Per docs/adr/0001-architecture.md Decision 1, this vertical is
  self-contained (no external field-service capability library, no
  jurisdiction-scoped Datomic-parity requirement driving a second
  backend); a `langchain.db`-backed store can be added later behind
  the same protocol without changing any caller.

  Four kinds of entity live here:
    - `circuits`           -- the central entity. A panel/charger
                             circuit's own rated-capacity/committed-
                             load/fault-type record. `:verified?`
                             marks whether the circuit's own claims
                             have actually been diagnostic-tested
                             (never inferred from a routine intake
                             patch); `:registered?` marks whether it
                             is on file in the operator's circuit
                             registry; `:committed-load-amps` tracks
                             the circuit's own cumulative-committed
                             ground truth.
    - `sites`               -- an installation site's own record
                             (residential/commercial/fleet-depot).
                             `:verified?`/`:registered?` track whether
                             it has actually been surveyed/registered
                             and is on file -- the same ground-truth
                             discipline as `circuits`.
    - `repairs`             -- a scheduled repair/install-visit DRAFT
                             against a site (`elecinstall.registry`'s
                             `register-repair`). Dedicated
                             `:scheduled?` double-schedule guard
                             (never a `:status` value -- the same
                             discipline every prior governor's guards
                             establish, informed by
                             `cloud-itonami-isic-6492`'s
                             status-lifecycle bug, ADR-2607071320).
    - `commissionings`      -- a proposed commissioning-record DRAFT
                             (`elecinstall.registry`'s
                             `register-commissioning`).

  Plus a generic `records` map (id -> raw record) used only for
  direct, domain-agnostic `commit-record!` calls (a record with no
  `:effect` key) -- the store-level primitive every sibling actor's
  own MemStore exposes underneath its domain-specific commit dispatch.

  The ledger stays append-only: 'which circuit was logged, which
  repair was scheduled against a verified/registered site, which
  commissioning was coordinated and at what independently-recomputed
  cumulative load, approved by whom, which safety concern was
  flagged' is always a query over an immutable log -- the audit trail
  an operator or downstream customer trusting this coordinator needs."
  (:require [elecinstall.registry :as registry]))

(defprotocol Store
  (circuit [s id])
  (all-circuits [s])
  (site [s id])
  (all-sites [s])
  (repair [s id])
  (all-repairs [s])
  (commissioning [s id])
  (safety-concerns [s] "the append-only safety-concern log")
  (ledger [s])
  (repair-history [s] "the append-only repair-schedule history (elecinstall.registry drafts)")
  (commissioning-history [s] "the append-only commissioning-coordination history (elecinstall.registry drafts)")
  (next-repair-sequence [s] "next repair-number sequence")
  (next-commissioning-sequence [s] "next commissioning-number sequence")
  (repair-already-scheduled? [s repair-id] "has this repair/install visit already been scheduled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (get-records [s] "the generic id -> raw-record map (domain-agnostic commit-record! path)")
  (with-circuits [s circuits] "replace/seed the circuit directory (map id->circuit)")
  (with-sites [s sites] "replace/seed the site directory (map id->site)"))

;; ----------------------------- demo/sample data -----------------------------

(defn- sample-circuits []
  {"circuit-001" {:id "circuit-001" :fault-type :none
                  :voltage-v 240.0 :current-a 32.0
                  :verified? true :registered? true
                  :rated-capacity-amps 200.0 :committed-load-amps 40.0
                  :last-assessed "2026-06-01"}
   "circuit-002" {:id "circuit-002" :fault-type :none
                  :voltage-v 480.0 :current-a 60.0
                  :verified? true :registered? true
                  :rated-capacity-amps 100.0 :committed-load-amps 90.0
                  :last-assessed "2026-06-01"}
   "circuit-003" {:id "circuit-003" :fault-type :ground-fault
                  :voltage-v 240.0 :current-a 28.0
                  :verified? false :registered? false
                  :rated-capacity-amps 150.0 :committed-load-amps 0.0
                  :last-assessed "2026-05-15"}})

(defn- sample-sites []
  {"site-001" {:id "site-001" :kind :residential
               :verified? true :registered? true
               :last-repair-date "2026-05-01"}
   "site-002" {:id "site-002" :kind :fleet-depot
               :verified? false :registered? false
               :last-repair-date nil}})

;; ----------------------------- shared commit logic -----------------------------

(defn- schedule-repair!
  "Backend-agnostic `:repair/schedule` -- drafts the repair-schedule
  record via `elecinstall.registry` and returns {:result .. :patch ..}
  for the caller to persist."
  [s repair-id site-id]
  (let [seq-n (next-repair-sequence s)
        result (registry/register-repair repair-id site-id seq-n)]
    {:result result
     :patch {:scheduled? true
             :repair-number (get result "repair_number")}}))

(defn- propose-commissioning!
  "Backend-agnostic `:commissioning/propose` -- drafts the
  commissioning-coordination record via `elecinstall.registry` and
  returns {:result .. :patch ..} for the caller to persist."
  [s commissioning-id]
  (let [seq-n (next-commissioning-sequence s)
        result (registry/register-commissioning commissioning-id seq-n)]
    {:result result
     :patch {:commissioning-number (get result "commissioning_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (circuit [_ id] (get-in @a [:circuits id]))
  (all-circuits [_] (sort-by :id (vals (:circuits @a))))
  (site [_ id] (get-in @a [:sites id]))
  (all-sites [_] (sort-by :id (vals (:sites @a))))
  (repair [_ id] (get-in @a [:repairs id]))
  (all-repairs [_] (sort-by :id (vals (:repairs @a))))
  (commissioning [_ id] (get-in @a [:commissionings id]))
  (safety-concerns [_] (:safety-concerns @a))
  (ledger [_] (:ledger @a))
  (repair-history [_] (:repair-history @a))
  (commissioning-history [_] (:commissioning-history @a))
  (next-repair-sequence [_] (:repair-sequence @a 0))
  (next-commissioning-sequence [_] (:commissioning-sequence @a 0))
  (repair-already-scheduled? [_ repair-id]
    (boolean (get-in @a [:repairs repair-id :scheduled?])))
  (get-records [_] (:records @a))
  (commit-record! [s {:keys [effect path value] :as record}]
    (cond
      (= effect :circuit/upsert)
      (swap! a update-in [:circuits (first path)] merge (assoc value :id (first path)))

      (= effect :repair/schedule)
      (let [repair-id (first path)
            site-id (:site-id value)
            {:keys [result patch]} (schedule-repair! s repair-id site-id)]
        (swap! a (fn [state]
                   (-> state
                       (update :repair-sequence (fnil inc 0))
                       (update-in [:repairs repair-id] merge (assoc value :id repair-id) patch)
                       (update :repair-history registry/append result)
                       (update-in [:sites site-id :last-scheduled-repair-date]
                                  (fn [_prev] (:visit-date value))))))
        result)

      (= effect :safety-concern/flag)
      (let [concern-id (first path)
            concern (assoc value :id concern-id)]
        (swap! a update :safety-concerns conj concern)
        concern)

      (= effect :commissioning/propose)
      (let [commissioning-id (first path)
            circuit-id (:circuit-id value)
            {:keys [result patch]} (propose-commissioning! s commissioning-id)]
        (swap! a (fn [state]
                   (-> state
                       (update :commissioning-sequence (fnil inc 0))
                       (update-in [:commissionings commissioning-id] merge (assoc value :id commissioning-id) patch)
                       (update :commissioning-history registry/append result)
                       (update-in [:circuits circuit-id :committed-load-amps]
                                  (fn [prev]
                                    (+ (double (or prev 0.0))
                                       (double (or (:load-amps value) 0.0))))))))
        result)

      ;; Domain-agnostic path: a raw record with an :id and no :effect
      ;; is written verbatim into the generic `records` map -- the
      ;; store-level primitive underneath the domain-specific dispatch
      ;; above (also what `logging`-style siblings expose as their own
      ;; low-level commit path).
      (and (nil? effect) (:id record))
      (swap! a assoc-in [:records (:id record)] record)

      :else nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-circuits [s circuits] (when (seq circuits) (swap! a assoc :circuits circuits)) s)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn mem-store
  "A fresh, empty MemStore."
  []
  (->MemStore (atom {:circuits {} :sites {} :repairs {} :commissionings {}
                      :records {} :safety-concerns []
                      :ledger [] :repair-sequence 0 :repair-history []
                      :commissioning-sequence 0 :commissioning-history []})))

(defn sample-data!
  "Seeds `s` (a MemStore) with a small, self-contained circuit + site
  set -- one verified+registered circuit with load headroom
  (schedulable), one verified+registered circuit that is nearly at
  rated capacity (a small new commissioning blows through its own
  logged rated-capacity ceiling -- HARD hold), one UNVERIFIED/
  unregistered circuit (blocks any commissioning coordinated against
  it); one verified+registered residential site (schedulable for a
  repair/install visit), one UNVERIFIED/unregistered fleet-depot site
  (blocks any repair scheduling against it) -- so the actor + demo +
  tests run offline. Returns `s` (thread-friendly with `->`)."
  [s]
  (with-circuits s (sample-circuits))
  (with-sites s (sample-sites))
  s)

;; ----------------------------- back-compat aliases -----------------------------
;; `get-ledger` mirrors `ledger` under the name several sibling actors'
;; own demo/test harnesses already call.

(defn get-ledger [s] (ledger s))
