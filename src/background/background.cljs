(ns background.background
  (:require [background.core :as core])
  (:require-macros [chromex.support :refer [runonce]]))

(runonce
 (core/init!))
