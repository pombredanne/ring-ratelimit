(ns ring.middleware.ratelimit-spec
  (:import [java.util Date])
  (:require [clojure.string :as string])
  (:use [speclj core]
        [ring.middleware ratelimit]
        [ring.middleware.ratelimit backend local-atom redis]
        [ring.mock request]))

(doseq [backend [(local-atom-backend) (redis-backend)]]
  (let [app (-> (fn [req] {:status 418
                           :headers {"Content-Type" "air/plane"}
                           :body "Hello"})
                (wrap-ratelimit {:limit 5
                                 :backend backend}))]

    (describe (str "ratelimit <" (last (string/split (str (type backend)) #"\.")) ">")
      (before
        (reset-limits! backend))

      (it "shows the rate limit"
        (let [rsp (-> (request :get "/") app)]
          (should= 418 (:status rsp))
          (should= "5" (get-in rsp [:headers "X-RateLimit-Limit"]))
          (should= "4" (get-in rsp [:headers "X-RateLimit-Remaining"]))))

      (it "returns 429 when there are no requests left"
        (dotimes [_ 5] (-> (request :get "/") app))
        (let [rsp (-> (request :get "/") app)]
          (should= 429 (:status rsp))
          (should= "0" (get-in rsp [:headers "X-RateLimit-Remaining"]))))

      (it "resets the limit every hour"
        (with-redefs [get-hour (fn [] (- (.getHours (Date.)) 1))]
          (dotimes [_ 5] (-> (request :get "/") app))
          (should= 429 (-> (request :get "/") app :status)))
        (should= 418 (-> (request :get "/") app :status))))))
