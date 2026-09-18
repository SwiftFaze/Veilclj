(ns veil.mods.registry
  "Registry accessors for mod content.")

(defn empty-registry
  "Create an empty registry with no load order or content."
  []
  {:load-order [] :content {}})

(defn load-order
  "Get the load order (vector of mod ids) from a registry."
  [registry]
  (:load-order registry))

(defn entry
  "Get a specific content entry by type and id, or nil if not found.
  Returns {:mod :file :value} or nil."
  [registry type id]
  (get-in registry [:content type id]))

(defn content-ids
  "Get all registered IDs for a specific content type.
  Returns a set of IDs, empty set if type not found."
  [registry type]
  (set (keys (get-in registry [:content type] {}))))

(defn register-entry
  "Register a single entry in the registry.
  For internal use by the loader."
  [registry type id mod file value]
  (assoc-in registry [:content type id] {:mod mod :file file :value value}))

(defn with-load-order
  "Create/update registry with a load order.
  For internal use by the loader."
  [registry load-order]
  (assoc registry :load-order load-order))
