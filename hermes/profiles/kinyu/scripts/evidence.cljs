;; kinyu evidence script — cron agent はこれを実行して出力 JSON を読んで報告するだけ。
;; 判断・計算はこの script が持つ。credential を読まない。
;; 実行: nbb ~/.hermes/profiles/kinyu/scripts/evidence.cljs

(require '[clojure.string :as str]
         '["node:child_process" :as cp]
         '["node:fs" :as fs]
         '["node:path" :as path]
         '["node:os" :as os])

(defn sh [cmd cwd]
  (try (str/trim (str (cp/execSync cmd #js {:cwd cwd :timeout 15000})))
       (catch :default e (str "ERR:" (.-message e)))))

(defn probe-url [url]
  ;; curl 経由（非同期 fetch の async 境界を持ち込まない。timeout 10s）
  (let [out (sh (str "curl -sS -o /dev/null -m 10 -w '%{http_code}' " url) (os/homedir))]
    {:url url :status (if (re-matches #"\d{3}" out) (js/parseInt out 10) out)}))

(def repo-root (path/join (os/homedir) "github" "com-junkawasaki"))
(def out-dir (path/join (os/homedir) ".hermes" "profiles" "kinyu" "workspace" "findings"))

(def repos
  ["orgs/kotoba-lang/banking"
   "orgs/kotoba-lang/kessai"
   "orgs/cloud-itonami/kouza"
   "orgs/cloud-itonami/open-banking"
   "orgs/cloud-itonami/app-open-swift"
   "orgs/kotoba-lang/aml"
   "orgs/kotoba-lang/ekyc"
   "orgs/cloud-itonami/cloud-itonami-pi"
   "orgs/cloud-itonami/cloud-itonami-emi"])

(defn sh [cmd cwd]
  (try (str/trim (str (cp/execSync cmd #js {:cwd cwd :timeout 15000})))
       (catch :default e (str "ERR:" (.-message e)))))

(defn repo-probe [rel]
  (let [abs (path/join repo-root rel)]
    (if (fs/existsSync abs)
      {:path rel
       :head (subs (sh "git rev-parse HEAD" abs) 0 12)
       :dirty? (not (str/blank? (sh "git status --porcelain" abs)))
       :has-test? (some #(fs/existsSync (path/join abs %))
                        ["src/test" "test" "worker"])}
      {:path rel :head "NOT-CHECKED-OUT" :dirty? false :has-test? false})))

(defn main []
  (let [now (js/Date.)
        ts (.toISOString now)
        repos-data (mapv repo-probe repos)
        live (mapv probe-url ["https://kotobase.net/health"
                              "https://auth.kotobase.net/health"])
        ;; kotobase datom plane の生死が全 appview 本番化の前提（優先順位 ①）
        datom-plane-ok? (some #(= 200 (:status %)) live)
        result {:at ts
                :kind "kinyu-evidence"
                :repos repos-data
                :kotobase {:probes live
                           :datom-plane-ok? (boolean datom-plane-ok?)
                           :note "transact 系 401/502 は 2026-09-05 実測。/health 200 でも transact は別。transact 実測は owner 依頼 or 明示 capability のある時のみ"}
                :priority-order ["datom-plane-repair" "core-banking-prod" "bank-adapter"
                                 "kyc-aml-actor" "settlement-netting"]}]
    (fs/mkdirSync out-dir #js {:recursive true})
    (fs/writeFileSync (path/join out-dir (str "kinyu-evidence-"
                                              (.slice ts 0 10) ".json"))
                      (js/JSON.stringify (clj->js result) nil 2))
    (println (js/JSON.stringify (clj->js result) nil 2))))

(main)
