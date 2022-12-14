(ns untitled.crud_http
  "all functions return http format maps with keys (status, headers, body)"
  (:require [clojure.java.jdbc :as j]
            [compojure.core :refer :all]
            [ring.util.http-response :refer :all]
            [untitled.db :refer [*db*]]
            [untitled.crud_util :as util]))


;; READ
(defn list-patients
  "list all patients from db table patients
  Arguments:
  - no
  Returns:
  - hash-map with status 200/ and user hash-map in body
  - empty body if no users"
  []
  (ok (util/list-patients)))


;; SEARCH / FILTER
(defn
  ^{:example '(validate-search-patients {:sex "male" :address "USA"})}
  validate-search-patients
  "search by one or more params / also works like filter / handle not valid keywords
  with keyword validation
   Arguments:
   - hash-map of params (values in String format)
   Returns:
   - hash-map with :status 200 and :body with list of hash-maps found user/users / empty if no user found
   - hash-map with :status 400 and :body with errors hash-map"
  [query]
  (let [query (util/parse-string-args query)
        valid-errors (util/false-validations query (select-keys util/default-schema (keys query)))]
    (if (empty? valid-errors)
      (ok (util/search-patients query))
      (bad-request {:error-type :invalid-keys
                    :value valid-errors}))))


;; GET patient by id
(defn
  ^{:example '(get-patient 3)}
  get-patient
  "get patient by id
  Arguments:
  - int id
  Returns:
  - hash-map with :status 200 and :body with hash-map found user
  - hash-map with :status 400 and :body fail"
  [id]
  (if-let [patient (util/get-patient id)]
    (ok patient)
    (bad-request "fail")))


;; CREATE PATIENT
(defn
  ^{:example '(add-patient util/acrobat_for_add)}
  add-patient
  "add a new patient
  Arguments:
  - hash-map of params (values in String format) (all params REQUIRED)
  Returns:
  - hash-map with :status 201 and :body with hash-map - created user
  - hash-map with :status 400 and :body with errors"
  [patient]
  (let [patient (util/parse-string-args patient)
        valid-errors (util/false-validations patient (dissoc util/default-schema :id))]
    (if (empty? valid-errors)
      (if (empty? (util/search-patients  (select-keys patient [:medical_policy])))
         (let [new-patient (first (j/insert! *db* :patients patient))]
           (created "/patient" new-patient))
         (bad-request {:error-type :already-exists}))
      (bad-request {:error-type :invalid-keys
                    :value      valid-errors}))))


; UPDATE
(defn
  ^{:example '(update-patient 3 {:sex "male"})}
  update-patient
  "update patient found by id with some params
  Arguments:
  - int id
  - hash-map params
  Returns:
  - hash-map with :status 201 and :body nil
  - hash-map with :status 400 and :body with fail"
  [id update]
  (let [response (util/update-patient id (util/parse-string-args update))]
    (if (map? response)
      (bad-request response)
      (ok))))


;; DELETE
(defn
  ^{:example '(delete-patient 3)}
  delete-patient
  "add patient by its params
  Arguments:
  - int id
  Returns:
  - hash-map with :status 200 if deleted
  - hash-map with :status 400 and :body with fail"
  [id]
  (if (util/delete-patient id)
    (ok)
    (bad-request "fail")))
