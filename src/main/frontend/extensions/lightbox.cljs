(ns frontend.extensions.lightbox
  (:require [cljs-bean.core :as bean]
            [frontend.util :as util]
            [promesa.core :as p]))

(defn preview-images!
  [images]
  (p/let [_ (util/js-load$ (str util/JS_ROOT "/photoswipe.umd.min.js"))
          _ (util/js-load$ (str util/JS_ROOT "/photoswipe-lightbox.umd.min.js"))]
    (let [options {:dataSource images :pswpModule js/window.PhotoSwipe :showHideAnimationType "fade"}
          ^js lightbox (js/window.PhotoSwipeLightbox. (bean/->js options))]
      (doto lightbox
        (.init)
        (.loadAndOpen 0)))))
