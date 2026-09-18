(ns veil.mods.order-spec
  (:require [speclj.core :refer :all]
            [veil.mods.order :as order]))

(defn- m [id & deps] {:id id :depends-on (vec deps)})

(describe "order"
  (it "loads nothing when there are no mods"
    (should= {:load-order []} (order/order [])))

  (it "loads each mod after its dependencies"
    (should= {:load-order ["core" "goblin-pack" "orc-pack"]}
             (order/order [(m "orc-pack" "goblin-pack") (m "goblin-pack" "core") (m "core")])))

  (it "loads mods ready at the same time alphabetically"
    (should= {:load-order ["core" "elf-pack" "goblin-pack" "zeta-pack"]}
             (order/order [(m "zeta-pack" "core") (m "core") (m "goblin-pack" "core") (m "elf-pack" "core")])))

  (it "loads core before other ready mods even when it sorts later"
    (should= {:load-order ["core" "alpha"]} (order/order [(m "alpha") (m "core")])))

  (it "orders mods without core"
    (should= {:load-order ["elf-pack" "goblin-pack"]} (order/order [(m "goblin-pack") (m "elf-pack")])))

  (it "reports a dependency on a mod that isn't present"
    (let [{:keys [error]} (order/order [(m "core") (m "broken-pack" "nonexistent-mod")])]
      (should= [:unresolved-dependency "broken-pack" "nonexistent-mod"]
               ((juxt :kind :mod :dependency) error))))

  (it "reports a cycle naming every mod that can't load"
    (let [{:keys [error]} (order/order [(m "core") (m "beta" "alpha") (m "alpha" "beta")])]
      (should= [:dependency-cycle ["alpha" "beta"] "dependency cycle among mods: alpha, beta"]
               ((juxt :kind :mods :message) error)))))

(describe "transitive-deps"
  (it "includes direct and indirect dependencies"
    (should= #{"goblin-pack" "core"}
             (order/transitive-deps [(m "core") (m "goblin-pack" "core") (m "orc-pack" "goblin-pack")]
                                    "orc-pack")))

  (it "is empty for a mod with no dependencies"
    (should= #{} (order/transitive-deps [(m "core")] "core")))

  (it "terminates on a cycle and excludes the mod itself"
    (should= #{"b"} (order/transitive-deps [(m "a" "b") (m "b" "a")] "a"))))
