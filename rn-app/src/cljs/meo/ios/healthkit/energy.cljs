(ns meo.ios.healthkit.energy
  (:require [meo.ios.healthkit.common :as hc]
            [matthiasn.systems-toolbox.component :as st]))

(defn res-cb [tag k offset put-fn err res]
  (when err (.error js/console err))
  (doseq [sample (js->clj res)]
    (let [v (get-in sample ["value"])
          end-date (get-in sample ["endDate"])
          end-ts (- (.valueOf (hc/moment end-date)) offset)
          now (st/now)
          v (int v)
          entry {:timestamp     (if (> end-ts now) (- now offset) end-ts)
                 :md            (str v " kcal " tag)
                 :tags          #{tag}
                 :perm_tags     #{tag}
                 :hidden        true
                 :sample        sample
                 :custom_fields {tag {k v}}}]
      (put-fn (with-meta [:entry/update entry] {:silent true}))
      (put-fn [:entry/persist entry]))))

(defn get-energy [{:keys [msg-payload put-fn current-state]}]
  (let [start (or (:last-read-energy current-state)
                  (hc/days-ago (:n msg-payload)))
        now-dt (hc/date-from-ts (st/now))
        opts (clj->js {:startDate start})
        basal-energy-cb (partial res-cb "#BasalEnergyBurned" :kcal 500 put-fn)
        active-energy-cb (partial res-cb "#ActiveEnergyBurned" :kcal 499 put-fn)
        init-cb (fn [err res]
                  (.getBasalEnergyBurned hc/health-kit opts basal-energy-cb)
                  (.getActiveEnergyBurned hc/health-kit opts active-energy-cb))
        new-state (assoc current-state :last-read-energy now-dt)]
    (.initHealthKit hc/health-kit hc/health-kit-opts init-cb)
    {:new-state new-state}))
