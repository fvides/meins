(ns meins.ui.photos
  (:require [meins.ui.shared :refer [view text touchable-opacity alert
                                     scroll image dimensions]]
            [cljs-react-navigation.reagent :refer [stack-navigator stack-screen]]
            [re-frame.core :refer [reg-sub subscribe]]
            ["@matthiasn/cameraroll" :as cam-roll]
            ["react-native-super-grid" :as rn-super-grid]
            [meins.ui.colors :as c]
            [meins.helpers :as h]
            [reagent.core :as r]
            [meins.ui.db :refer [emit]]
            [clojure.set :as set]))

(def flat-grid (r/adapt-react-class (.-FlatGrid rn-super-grid)))
(def screen-width (.-width (.get dimensions "window")))
(def img-dimension (js/Math.floor (/ (- screen-width 10) 4)))

(defn card [photo]
  (let [;all-timestamps (subscribe [:all-timestamps])
        all-timestamps (r/atom #{})]
    (fn [photo]
      (let [photo (:item photo)
            uri (-> photo :node :image :uri)
            node (:node photo)
            loc (:location node)
            lat (:latitude loc)
            lon (:longitude loc)
            img (:image node)
            ts (.floor js/Math (* 1000 (:timestamp node)))
            imported (not (contains? (set/union @all-timestamps) ts))
            filename (str (h/img-fmt ts) "_" (:fileName img))
            import (fn [_]
                     (let [entry {:latitude  lat
                                  :longitude lon
                                  :location  loc
                                  :md        ""
                                  :tags      #{"#import"}
                                  :perm_tags #{"#photo"}
                                  :mentions  #{}
                                  :media     (dissoc node :location)
                                  :img_file  filename
                                  :timestamp ts}]
                       ;(alert (str entry))
                       (emit [:entry/new entry])))
            hide (fn [_]
                   (let [entry {:timestamp  ts
                                :entry-type :hide}]
                     (emit [:entry/hide entry])))]
        [touchable-opacity {:on-press import
                            :style    {:flex 1}}
         [image {:style  {:width           img-dimension
                          :height          img-dimension
                          :resizeMode      "cover"
                          :backgroundColor "black"}
                 :source {:uri uri}}]]))))

(defn photos-tab []
  (let [local (r/atom {})
        get-fn #(let [params (clj->js {:first      1000
                                       :groupTypes "All"
                                       :assetType  "Photos"})
                      photos-promise (.getPhotos cam-roll params)]
                  (.then photos-promise
                         (fn [r]
                           (let [parsed (js->clj r :keywordize-keys true)]
                             (swap! local assoc-in [:photos] parsed)))))]
    (emit [:photos/import {:n 1000}])
    (fn []
      (get-fn)
      [flat-grid
       {:itemDimension img-dimension
        :items         (->> @local :photos :edges vec clj->js)
        :style         {:flex             1
                        :background-color "black"
                        :margin-top       50}
        :spacing       2
        :renderItem    (fn [item]
                         (let [item (js->clj item :keywordize-keys true)]
                           (r/as-element
                             [card item])))}])))
