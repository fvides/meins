(ns meins.ui.settings.health
  (:require [meins.ui.colors :as c]
            [meins.ui.shared :refer [view settings-list cam text settings-list-item fa-icon
                                     touchable-opacity]]
            [meins.ui.db :refer [emit]]
            [re-frame.core :refer [subscribe]]))

(defn start-watching [])

(defn import-item [msg-type label icon-name]
  (let [theme (subscribe [:active-theme])
        n 365
        click (fn [_] (emit [msg-type {:n n}]))
        auto-check (fn [_]
                     (emit [:cmd/schedule-new
                            {:timeout (* 15 60 1000)
                             :message [msg-type {:n n}]
                             :id      msg-type
                             :repeat  true
                             :initial true}]))]
    (fn [msg-type label icon-name]
      (let [item-bg (get-in c/colors [:button-bg @theme])
            text-color (get-in c/colors [:btn-text @theme])]
        [view {:style {:margin-top       3
                       :width            "100%"
                       :background-color item-bg
                       :justify-content  "space-between"
                       :align-items      "center"
                       :flex-direction   "row"}}
         [touchable-opacity {:on-press click
                             :style    {:text-align       :left
                                        :display          :flex
                                        :flex-direction   :row
                                        :margin-top       3
                                        :padding          16
                                        :background-color item-bg
                                        :justify-content  "flex-start"
                                        :align-items      :center
                                        :height           50}}
          [view {:style {:width      44
                         :text-align :center}}
           [fa-icon {:name  icon-name
                     :size  20
                     :style {:color      text-color
                             :text-align :center}}]]
          [text {:style {:color       text-color
                         :font-size   14
                         :margin-left 20}}
           label]]
         [touchable-opacity {:on-press auto-check
                             :style    {:width       80
                                        :height      50
                                        :display     :flex
                                        :align-items :center}}
          [fa-icon {:name  "refresh"
                    :size  20
                    :style {:color      text-color
                            :text-align :center
                            :padding    16}}]]]))))

(defn health-settings [_]
  (let [theme (subscribe [:active-theme])]
    (fn [{:keys [screenProps navigation] :as props}]
      (let [{:keys [navigate goBack]} navigation
            bg (get-in c/colors [:list-bg @theme])]
        [view {:style {:flex-direction   "column"
                       :padding-top      10
                       :padding-bottom   10
                       :height           "100%"
                       :background-color bg}}
         [import-item :healthkit/weight "Weight" "balance-scale"]
         [import-item :healthkit/bp "Blood Pressure" "heartbeat"]
         [import-item :healthkit/exercise "Exercise" "forward"]
         [import-item :healthkit/steps "Steps" "forward"]
         [import-item :healthkit/energy "Energy" "bolt"]
         [import-item :healthkit/sleep "Sleep" "bed"]
         [import-item :healthkit/hrv "Heart Rate Variability" "heartbeat"]]))))
