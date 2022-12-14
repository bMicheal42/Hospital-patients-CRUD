(ns untitled.crud_util
  "util functions for crud operations
  functions for repl development"
  (:require [clojure.java.jdbc :as j]
            [compojure.core :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.coercions :refer :all]
            [untitled.db :refer [*db*]])
  (:import
    (java.time LocalDate)
    (java.sql Date)))

(defn
  ^{:example '(example #'example)}
  example-fn
  "Shows an example given a variable
  Arguments:
 - '#arg
 - var arg
 Returns:
 - example code for eval"
  [var]
  (:example (meta var)))

(defmacro example [function]
  "Get :example from function's name meta
  Arguments:
  - function name
  Returns:
  - example code for eval"
  `(example-fn (var ~function)))

(def keywords #{:id :address :sex :birthdate :full_name :medical_policy})

(defn
  ^{:example '(date "2020-01-01")}
  date
  "wrapper for repl/tests to convert string date like 2022-04-08 in Date format
  Arguments:
  - string (date in humanised format)
  Returns:
  - date in Date format"
  [date]
  (try
    (Date/valueOf (LocalDate/parse date))
    (catch Exception e nil)))


(def
  ;^{:private true}
  igor {:full_name      "Igor Alexandrovich Senushkin",
        :birthdate      "1992-08-24",
        :sex            "male",
        :address        "Yerevan",
        :medical_policy "2535223335203525"})

(def
  ;^{:private true}
  ivan {:full_name "Igor Alexandrovich Senushkin",
        :birthdate "2002-01-10",
        :address "Yerevan",
        :sex     "molle"
        :medical_policy "2533335203525"})

(def
  acrobat_for_add {:full_name "Igor Alexandrovich Senushkin",
                   :birthdate "2002-01-10",
                   :address "Yerevan",
                   :sex     "molle"
                   :acrobat "inavalid key"
                   :medical_policy "2533335203525"})

(def
  ^{:private true}
  acrobat {:id 4,
           :full_name "Igor Alexandrovich Senushkin",
           :birthdate (date "2002-01-10"),
           :address "Yerevan",
           :sex     "molle"
           :acrobat "inavalid key"
           :medical_policy "2533335203525"})


(defn list-patients
  "list all patients from db table patients
  Arguments:
 - no
 Returns:
 - list of hash-maps with all users
 - empty if no users"
  []
  (j/query *db* ["select * from patients ORDER BY id ASC"]))


;; Helper validators
(defn- is-string? [x]
  (and (string? x) (< (count (str x)) 50)))

(defn- is-policy? [x]
  (and (integer? x) (= (count (str x)) 16)))

(defn- is-sex? [x]
  (or (= x "male") (= x "female")))

(defn- is-a-date? [x]
  (inst? x))


(def
  ^{:example '((:sex default-schema) "male")}
  default-schema
  "default static scheme of patient params and its fuction validators"
  {:id             integer?
   :address        is-string?
   :sex            is-sex?
   :birthdate      is-a-date?
   :full_name      is-string?
   :medical_policy is-policy?})

;; TODO 2nd release filter by birthdate '<' / '>'
;(def
;  ^{:example '((:birthdate search-schema) {:method :less :value (date "1990-01-01")})}
;  search-schema
;  "Updated static scheme with reloaded birthdat method
;  because search query :birthdate can be {:method :less :value ...} for example."
;  (merge
;    default-schema
;    {:birthdate
;     (fn [x] (or (is-a-date? x)
;                 (and (map? x)
;                      (:method x)
;                      (#{:less :more :equals} (:method x))
;                      (:value x)
;                      (is-a-date? (:value x)))))}))



(defn parse-string-args
  "Return params in expecting format
  (id like int / birthdate like Java Date / medical policy like long)"
  [query]
  (let [query (if (:id query) (update-in query [:id] as-int) query)
        query (if (:birthdate query) (update-in query [:birthdate] date) query)
        query (if (:medical_policy query) (update-in query [:medical_policy] as-int) query)]
    query))

(defn-
  ^{:example '(validate-schema acrobat)}
  validate-schema
  "get all paramas as true/false
 Arguments:
 - 1st option: patient as hash-map
 - 2nd option: patient as hash-map, validation schema
 Returns:
 - hash-map with params as keys and true/false as value"
  ([patient]
   (validate-schema patient default-schema))
  ([patient schema]
   (into {}
         (merge
           ;; Apply validator from valid keys.
           (map (fn [[key validator]] {key (validator (key patient))}) schema)
           ;; Detect invalid keys.
           (into {} (map (fn [[key _]] [key false])
                         (filter #(not (contains? keywords (first %))) patient)))))))

(defn
  ^{:example '(false-validations acrobat)}
  false-validations
  "get list of ivalid params
  Arguments:
  - hash-map - user with params
  Returns:
  - list of ivalid params"
  ([patient] (false-validations patient default-schema))
  ([patient schema]
   (keys (filter #(not (second %)) (validate-schema patient schema)))))


;; TODO 2nd release filter by birthdate '<' / '>'
;(defn-
;  ^{:example '(birthdate-search-method (merge ivan {:birthdate {:method :less}}))}
;  birthdate-search-method
;  "form birthdate sring for sql comparison query
; Arguments:
; - 1st option: Date format
; - 2nd option: hash-map with less/more/equals key
; Returns:
; - string < / > / ="
;  [query]
;  (let [birthdate (:birthdate query)]
;    (cond
;      (map? birthdate) ({:less "<" :more ">" :equals "="} (:method birthdate))
;      (inst? birthdate) "=")))


(defn
  ^{:example '(search-patients {:sex "male" :medical_policy 1231321402253324})}
  search-patients
  "search by one or more params / also works like filter / handle not valid keywords
  no keyword validation
   Arguments:
   - hash-map of params (in expecting formats)
   Returns:
   - list of hash-maps with found users
   - empty if no users found"
  [params]
  (if (empty? params)
    (list-patients)
    (let [query [(->>
                  (for [keyword keywords]
                    (when (keyword params)
                      (str (name keyword) " = ?")))
                  (apply vector)
                  (remove nil?)
                  (interpose " and ")
                  (apply str)
                  (str "select * from patients where "))]
          values (->>
                  (for [keyword keywords]
                    (when (keyword params)
                      (keyword params)))
                  (remove nil?)
                  (into []))]
     (j/query *db* (concat query values)))))


(defn
  ^{:example '(get-patient 3)}
  get-patient
  "get patient by id
  Arguments:
  - int id
  Returns:
  - hash-map with a user
  - nil if no user found / wrong id format"
  [id]
  (if (integer? id)
    (first (search-patients {:id id}))))


(defn
  ^{:example '(update-patient 15 {:sex "trans" :address "Moscow" :medical_policy 23})}
  update-patient
  "update patient found by id with some params
  Arguments:
  - int id
  - hash-map params
  Returns:
  - (1) if success
  - nil if no update done / wrong id format"
  [id update]
  (if (integer? id)
    (let [p (get-patient id)
          valid-errors (false-validations (merge p update))]
      (if (not (empty? update))
        (if p
          (if (empty? valid-errors)
            (j/update! *db* :patients update ["id = ?" id])
            {:error-type :invalid-keys, :value valid-errors})
          {:error-type :no-such-patient, :value (str "id = " id)})
        {:error-type :empty-update}))))







;[id update]
;(if (integer? id)
;  (let [p (get-patient id)
;        valid-errors (false-validations (merge p update))]
;    (if (and (not (empty? update)) p (empty? valid-errors))
;      (j/update! *db* :patients update ["id = ?" id]))))
(defn
  ^{:example '(delete-patient 3)}
  delete-patient
  "add patient by its params
  Arguments:
  - int id
  Returns:
  - true if patient was deleted / fasle if not
  - nil if wrong id format"
  [id]
  (when (integer? id)
    (not (= 0 (first (j/delete! *db* :patients ["id = ?" id]))))))