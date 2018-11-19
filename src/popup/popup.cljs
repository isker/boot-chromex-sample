(ns popup.popup
  (:require [popup.core :as core])
  (:require-macros [chromex.support :refer [runonce]]))

(runonce
 (core/init!))
