(ns frontend.components.home
  (:require [frontend.components.container :as container]
            [rum.core :as rum]))

(rum/defc home
  []
  (container/main-content))
