(ns meo.ios.healthkit.bp
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.pprint :as pp]
            [meo.utils.misc :as um]
            [meo.helpers :as h]
            [meo.ios.healthkit.storage :as hs]
            [glittershark.core-async-storage :as as]
            [cljs.core.async :refer [<!]]
            [meo.ios.healthkit.common :as hc]
            [matthiasn.systems-toolbox.component :as st]))

(defn bp-cb [put-fn err res]
  (doseq [sample (js->clj res)]
    (let [bp-systolic (get-in sample ["bloodPressureSystolicValue"])
          bp-diastolic (get-in sample ["bloodPressureDiastolicValue"])
          end-date (get-in sample ["endDate"])
          end-ts (.valueOf (hc/moment end-date))
          bp-systolic (js/parseInt bp-systolic)
          bp-diastolic (js/parseInt bp-diastolic)
          entry {:timestamp     end-ts
                 :md            (str bp-systolic "/" bp-diastolic
                                     " mmHG #BP")
                 :tags          #{"#BP"}
                 :perm_tags     #{"#BP"}
                 :sample        sample
                 :custom_fields {"#BP" {:bp_systolic  bp-systolic
                                        :bp_diastolic bp-diastolic}}}]
      (put-fn (with-meta [:entry/update entry] {:silent true}))
      (put-fn [:entry/persist entry]))))

(defn hr-cb [tag put-fn err res]
  (doseq [sample (js->clj res)]
    (let [v (get-in sample ["value"])
          end-date (get-in sample ["endDate"])
          end-ts (.valueOf (hc/moment end-date))
          entry {:timestamp     end-ts
                 :md            (str v " bpm " tag)
                 :tags          #{tag}
                 :perm_tags     #{tag}
                 :sample        sample
                 :custom_fields {tag {:bpm v}}}]
      (put-fn (with-meta [:entry/update entry] {:silent true}))
      (put-fn [:entry/persist entry]))))

(defn blood-pressure [{:keys [put-fn msg-payload current-state]}]
  (let [n (:n msg-payload)
        start (or (:last-read-bp current-state)
                  (hc/days-ago n))
        now-dt (hc/date-from-ts (st/now))
        bp-opts (clj->js {:unit "mmHg" :startDate start})
        hr-opts (clj->js {:startDate start})
        init-cb (fn [err res]
                  (.getBloodPressureSamples hc/health-kit bp-opts (partial bp-cb put-fn))
                  (.getRestingHeartRate hc/health-kit hr-opts (partial hr-cb "#RHR" put-fn))
                  (.getWalkingHeartRateAverage hc/health-kit hr-opts (partial hr-cb "#WHR" put-fn)))
        new-state (assoc current-state :last-read-bp now-dt)]
    (.initHealthKit hc/health-kit hc/health-kit-opts init-cb)
    {:new-state new-state}))

(defn heart-rate-variability [{:keys [put-fn msg-payload current-state]}]
  (let [start (or (:last-read-hrv current-state)
                  (hc/days-ago (:n msg-payload)))
        now-dt (hc/date-from-ts (st/now))
        hrv-opts (clj->js {:startDate start})
        hrv-cb (fn [err res]
                 (.warn js/console res)
                 (doseq [sample (js->clj res)]
                   (.warn js/console sample)
                   (let [v (get-in sample ["value"])
                         end-date (get-in sample ["endDate"])
                         end-ts (.valueOf (hc/moment end-date))
                         v (int (* 1000 v))
                         entry {:timestamp     end-ts
                                :md            (str v " ms #HRV")
                                :tags          #{"#HRV"}
                                :perm_tags     #{"#HRV"}
                                :sample        sample
                                :custom_fields {"#HRV" {:sdnn v}}}]
                     (put-fn (with-meta [:entry/update entry] {:silent true}))
                     (put-fn [:entry/persist entry]))))
        init-cb (fn [err res]
                  (.getHeartRateVariabilitySamples hc/health-kit hrv-opts hrv-cb))
        new-state (assoc current-state :last-read-hrv now-dt)]
    (.initHealthKit hc/health-kit hc/health-kit-opts init-cb)
    {:new-state new-state}))
