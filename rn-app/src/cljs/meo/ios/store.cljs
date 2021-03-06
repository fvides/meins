(ns meo.ios.store
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [matthiasn.systems-toolbox.component :as st]
            [glittershark.core-async-storage :as as]
            [clojure.data.avl :as avl]
            [cljs.core.async :refer [<!]]
            [meo.helpers :as h]))

(defn persist [{:keys [msg-type current-state put-fn msg-payload msg-meta]}]
  (let [{:keys [timestamp vclock id]} msg-payload
        last-vclock (:global-vclock current-state)
        instance-id (str (:instance-id current-state))
        offset (inc (or (get last-vclock instance-id) 0))
        new-vclock {instance-id offset}
        id (or id (st/make-uuid))
        prev (dissoc (get-in current-state [:entries timestamp])
                     :id :last-saved :vclock)
        entry (merge prev msg-payload {:last-saved (st/now)
                                       :id         (str id)
                                       :vclock     (merge vclock new-vclock)})
        entry (h/remove-nils entry)
        new-state (-> current-state
                      (assoc-in [:entries timestamp] entry)
                      (update-in [:all-timestamps] conj timestamp)
                      (assoc-in [:vclock-map offset] entry)
                      (assoc-in [:global-vclock] new-vclock))]
    (when-not (= :entry/sync msg-type)
      (put-fn (with-meta [:entry/sync entry] msg-meta)))
    (when-not (= prev (dissoc msg-payload :id :last-saved :vclock))
      (go (<! (as/set-item timestamp entry)))
      (go (<! (as/set-item :global-vclock last-vclock)))
      (go (<! (as/set-item :timestamps (:all-timestamps new-state))))
      {:new-state new-state})))

(defn hide [{:keys [current-state msg-payload]}]
  (let [ts (:timestamp msg-payload)
        new-state (update-in current-state [:hide-timestamps] conj ts)]
    (go (<! (as/set-item :hide-timestamps (:hide-timestamps new-state))))
    {:new-state new-state}))

(defn detail [{:keys [current-state msg-payload]}]
  (let [new-state (assoc-in current-state [:entry-detail] msg-payload)]
    {:new-state new-state}))

(defn theme [{:keys [current-state msg-payload]}]
  (let [new-state (assoc-in current-state [:active-theme] msg-payload)]
    (go (<! (as/set-item :active-theme msg-payload)))
    {:new-state new-state}))

(defn current-activity [{:keys [current-state msg-payload]}]
  (let [new-state (assoc-in current-state [:current-activity] msg-payload)]
    {:new-state new-state}))

(defn sync-start [{:keys [current-state msg-payload put-fn]}]
  (let [vclock-map (:vclock-map current-state)
        ;latest-synced (:latest-synced current-state)
        ;newer-than (:newer-than msg-payload latest-synced)
        instance-id (str (:instance-id current-state))
        offset (get-in msg-payload [:newer-than-vc instance-id])
        [_offset entry] (avl/nearest vclock-map > offset)
        new-state (assoc-in current-state [:latest-synced] offset)]
    (go (<! (as/set-item :latest-synced offset)))
    (if entry (put-fn [:sync/entry entry])
              (put-fn [:sync/done]))
    {:new-state new-state}))

(defn load-state [{:keys [cmp-state put-fn]}]
  (go
    (try
      (let [latest-vclock (second (<! (as/get-item :global-vclock)))]
        (put-fn [:debug/latest-vclock latest-vclock])
        (swap! cmp-state assoc-in [:global-vclock] latest-vclock))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (go
    (try
      (let [active-theme (second (<! (as/get-item :active-theme)))]
        (swap! cmp-state assoc-in [:active-theme] (or active-theme :light)))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (go
    (try
      (let [secrets (second (<! (as/get-item :secrets)))]
        (when secrets
          (swap! cmp-state assoc-in [:secrets] secrets)))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (go
    (try
      (let [latest-synced (second (<! (as/get-item :latest-synced)))]
        (put-fn [:debug/latest-synced latest-synced])
        (swap! cmp-state assoc-in [:latest-synced] latest-synced))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (go
    (try
      (let [instance-id (str (or (second (<! (as/get-item :instance-id)))
                                 (st/make-uuid)))
            timestamps (second (<! (as/get-item :timestamps)))
            sorted (apply sorted-set timestamps)]
        (swap! cmp-state assoc-in [:instance-id] instance-id)
        (swap! cmp-state assoc-in [:all-timestamps] sorted)
        (<! (as/set-item :instance-id instance-id))
        #_(doseq [ts timestamps]
            (let [entry (second (<! (as/get-item ts)))
                  offset (get-in entry [:vclock instance-id])]
              (swap! cmp-state assoc-in [:entries ts] entry)
              (when offset
                (swap! cmp-state assoc-in [:vclock-map offset] entry)))))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (go
    (try
      (let [hide-timestamps (second (<! (as/get-item :hide-timestamps)))]
        (when hide-timestamps
          (swap! cmp-state assoc-in [:hide-timestamps] hide-timestamps)))
      (catch js/Object e
        (put-fn [:debug/error {:msg e}]))))
  (put-fn [:debug/state-fn-complete])
  {})

(defn state-reset [{:keys [cmp-state put-fn]}]
  (let [new-state {:entries       (avl/sorted-map)
                   :latest-synced 0}]
    (go (<! (as/clear)))
    (load-state {:cmp-state cmp-state :put-fn put-fn})
    {:new-state new-state}))

(defn set-secrets [{:keys [current-state msg-payload]}]
  (let [new-state (assoc-in current-state [:secrets] msg-payload)]
    (go (<! (as/set-item :secrets msg-payload)))
    {:new-state new-state}))

(defn state-fn [put-fn]
  (let [state (atom {:entries         (avl/sorted-map)
                     :active-theme    :light
                     ;:all-timestamps (avl/sorted-set)
                     :all-timestamps  (sorted-set)
                     :hide-timestamps (sorted-set)
                     :vclock-map      (avl/sorted-map)
                     :latest-synced   0})]
    (load-state {:cmp-state state
                 :put-fn    put-fn})
    {:state state}))

(defn cmp-map [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    state-fn
   :handler-map {:entry/persist    persist
                 :entry/new        persist
                 :entry/hide       hide
                 :entry/sync       persist
                 :entry/detail     detail
                 :sync/initiate    sync-start
                 :sync/next        sync-start
                 :state/load       load-state
                 :state/reset      state-reset
                 :secrets/set      set-secrets
                 :theme/active     theme
                 :activity/current current-activity}})
