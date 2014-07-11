(ns tenancy.handler
  (:use (incanter core stats charts io datasets))
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response response]]
            [ring.middleware.json :as middleware]
            [monger.core :as mongo]
            [monger.collection :as mc]))


(let [db_string "mongo-connection-string"
      {:keys [conn db]} (mongo/connect-via-uri db_string)
      collections [:users :roles :contacts :contact_groups :research_types 
                   :data_sources :data_types :taxonomies :rated_items :coverage_lists 
                   :disclosures :disclosure_groups :workflows :products :product_groups 
                   :templates :documents]]

  (defn find-user-by-token
    [token]
    (mc/find-one db "users" {:Auth.RequestToken token}))
 
  (defn find-one
    [col query [filt]] 
    (if (mc/exists? db col) 
      (mc/find-one db col query (or filt []))))

  (defn find-all
   ([col] 
    (if
      (mc/exists? db col)
      (mc/find-maps db col)
      nil))
   ([col query & filt]
    (if 
      (mc/exists? db col)
      (mc/find-maps db col query (or filt {})) 
      nil)))
  
  (defn db-count
    [col query]
    (if
      (mc/exists? db col)
      (mc/count db col query)
      nil))
  
  (defn get-tenancies
    []
    (find-all "tenancies"))
  
  (defn get-rated-items
    "Return all rated items"
    ([]
     (find-all "rated_items"))
    ([query]
     (find-all "rated_items" query)))

  (defn get-taxonomies
    []
    (find-all "taxonomies"))

  (defn get-users
    []
    (find-all "users"))

  (defn get-workflows
    []
    (find-all "workflows"))

  (defn get-datatypes
    []
    (find-all "data_types"))

  (defn get-primitives
    ([]
      (map #(keyword (get % :DataTypeName)) 
           (sort-by :DataTypeName (get-primitives ["DataTypeName"]))))
    ([filt]
      (find-all "data_types" {:DataTypeClassification "primitive"} (or filt []))))

  (defn get-research-types
  	"Return all research types"
  	[]
  	(find-all "research_types"))

	(defn filter-nils
  	"Filter out maps with nil map-values"
  	[coll]
  	(filter #(= (.indexOf (vals %) nil) -1) coll))

	(defn extract-rated-item-datatype-info
  	[rated-item]
  	(for [datapoint (keys rated-item)
          :let [{ClientID :ClientID
                {{Primitive :DataTypeName} :Primitive}
                :DataType} (rated-item datapoint)]]
    {:ClientID ClientID :Primitive Primitive}))

	(defn primitive-utilization-per-tenant
  	([rated-items]
  		(let [base (filter-nils (reduce into 
                   (map extract-rated-item-datatype-info rated-items)))
            data (group-by :ClientID base)]
      	(zipmap
       		(keys data)
       		(merge (map #(let [primitive_gp (group-by :Primitive %)]
                        (into {} (for [[k v] primitive_gp] (assoc {} (keyword k) (count v))))) 
              (vals data))))))
  
  	([rated-items vis]
   		(let [primitives (into {} (pmap #(assoc {} % 0) (get-primitives)))
            base (primitive-utilization-per-tenant rated-items)
         		flattened (for [[k v] base] (conj primitives (assoc v :ClientID k)))]
     		(incanter.core/with-data
        	(->> (incanter.core/dataset (into [] (keys (first flattened))) (into [] flattened))
          		 (incanter.core/view))))))
 
  (defn get-tenancy-totals
    ([]
     (reduce into {} (pmap #(let [tenant-id (% :ClientID)]
             (assoc {} 
               (keyword tenant-id) 
               (get-tenancy-totals tenant-id))) (get-tenancies))))
    ([tenant-id]
     (let [query {:ClientID tenant-id}
           totals (reduce into {} (pmap #(assoc {} % (db-count (name %) query)) collections))]
       totals)))

  (defroutes api-routes
  	(GET "/" request (response {:Response "Welcome"}))
  	(context "/tenants"
      []
      (GET "/" request (response (get-tenancies)))
      (GET "/usage" [] (str "Received"))
      (GET "/totals" [] (response (get-tenancy-totals))))
  	(context "/tenants/:tenant-id"
      [tenant-id]
      (GET "/" request (str request))
      (GET "/totals" [] (response (-> tenant-id (get-tenancy-totals))))
      (GET "/usage" [] (response 
                         (primitive-utilization-per-tenant 
                           (get-rated-items {:ClientID tenant-id}))))))
  
  (def app
    (-> (handler/api api-routes)
        (middleware/wrap-json-body)
        (middleware/wrap-json-response))))
