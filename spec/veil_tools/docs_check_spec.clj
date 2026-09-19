(ns veil-tools.docs-check-spec
  (:require [speclj.core :refer :all]
            [veil-tools.docs-check :as docs-check]))

(describe "extract-tasks"
  (it "extracts symbol keys from bb.edn tasks map"
    (let [bb-edn "{:tasks {play {} spec {} qa {}}}"
          result (docs-check/extract-tasks bb-edn)]
      (should= ["play" "qa" "spec"] result)))

  (it "excludes keyword keys"
    (let [bb-edn "{:tasks {:init {} play {}}}"
          result (docs-check/extract-tasks bb-edn)]
      (should= ["play"] result)))

  (it "excludes symbols starting with dash"
    (let [bb-edn "{:tasks {-helper {} play {}}}"
          result (docs-check/extract-tasks bb-edn)]
      (should= ["play"] result)))

  (it "returns sorted task names"
    (let [bb-edn "{:tasks {z {} a {} m {}}}"
          result (docs-check/extract-tasks bb-edn)]
      (should= ["a" "m" "z"] result))))

(describe "mentioned-tasks"
  (it "finds tasks mentioned as 'bb <task>'"
    (let [md-text "Run bb play first. Then bb spec."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"play" "spec"} result)))

  (it "finds tasks at end of sentence with period"
    (let [md-text "Finish with bb play."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"play"} result)))

  (it "finds tasks in backticks"
    (let [md-text "Run `bb qa` first."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"qa"} result)))

  (it "finds tasks at end of line"
    (let [md-text "bb qa"
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"qa"} result)))

  (it "extracts hyphenated names like 'bb qa-all'"
    (let [md-text "Run bb qa-all first."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"qa-all"} result)))

  (it "rejects digit suffixes like 'bb qa2'"
    (let [md-text "Run bb qa2 first."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{} result)))

  (it "rejects prose like 'The qa step'"
    (let [md-text "The qa step runs before play."
          result (docs-check/mentioned-tasks md-text)]
      (should= #{} result)))

  (it "deduplicates mentions"
    (let [md-text "bb spec and bb spec again"
          result (docs-check/mentioned-tasks md-text)]
      (should= #{"spec"} result))))

(describe "task-findings"
  (it "reports no findings when all tasks are mentioned"
    (let [bb-edn "{:tasks {play {} spec {} qa {}}}"
          md-text "Run bb play, bb spec, and bb qa."
          result (docs-check/task-findings bb-edn md-text)]
      (should= [] result)))

  (it "reports one finding for each unmentioned task"
    (let [bb-edn "{:tasks {play {} uber {}}}"
          md-text "bb play"
          result (docs-check/task-findings bb-edn md-text)]
      (should= ["task \"uber\" is not mentioned in docs/testing.md"] result)))

  (it "reports findings in task-name order"
    (let [bb-edn "{:tasks {play {} uber {} clean {} update-tools {}}}"
          md-text "bb play"
          result (docs-check/task-findings bb-edn md-text)]
      (should= ["task \"clean\" is not mentioned in docs/testing.md"
                "task \"uber\" is not mentioned in docs/testing.md"
                "task \"update-tools\" is not mentioned in docs/testing.md"]
               result)))

  (it "ignores keyword entries and private tasks"
    (let [bb-edn "{:tasks {:init {} :requires {} -helper {} play {}}}"
          md-text "bb play"
          result (docs-check/task-findings bb-edn md-text)]
      (should= [] result))))

(describe "extract-feature-description"
  (it "extracts text between Feature: and Scenario:"
    (let [feature "Feature: test\n  description line\nScenario: first"
          result (docs-check/extract-feature-description feature)]
      (should= "description line" result)))

  (it "extracts text between Feature: and Background:"
    (let [feature "Feature: test\n  description\nBackground:\n  setup"
          result (docs-check/extract-feature-description feature)]
      (should= "description" result)))

  (it "trims leading/trailing whitespace per line"
    (let [feature "Feature: test\n  line 1  \n  line 2  \nScenario: s"
          result (docs-check/extract-feature-description feature)]
      (should= "line 1\nline 2" result)))

  (it "handles feature with no scenarios"
    (let [feature "Feature: test\n  description"
          result (docs-check/extract-feature-description feature)]
      (should= "description" result)))

  (it "handles feature with no description"
    (let [feature "Feature: test\nScenario: s"
          result (docs-check/extract-feature-description feature)]
      (should= "" result))))

(describe "extract-qa-none-line"
  (it "extracts 'QA: none' line with reason from description"
    (let [feature "Feature: test\n  Description line\n  QA: none - no key input\nScenario: s"
          result (docs-check/extract-qa-none-line feature)]
      (should= "QA: none - no key input" result)))

  (it "returns nil if no QA: none line in description"
    (let [feature "Feature: test\n  Description line\nScenario: s"
          result (docs-check/extract-qa-none-line feature)]
      (should-be-nil result)))

  (it "ignores QA: none line after first scenario"
    (let [feature "Feature: test\n  Description\nScenario: s\n  QA: none - ignored"
          result (docs-check/extract-qa-none-line feature)]
      (should-be-nil result)))

  (it "handles case with extra whitespace"
    (let [feature "Feature: test\n  QA:  none  -  reason\nScenario: s"
          result (docs-check/extract-qa-none-line feature)]
      (should= "QA: none - reason" result))))

(describe "qa-findings"
  (it "reports no findings when feature has procedure"
    (let [added-features [{:path "specs/features/door.feature" :text "Feature: door"}]
          procedures #{"specs/qa/door.edn"}
          result (docs-check/qa-findings added-features procedures)]
      (should= [] result)))

  (it "reports no findings when feature has opt-out with reason"
    (let [feature "Feature: door\n  QA: none - no key input"
          added-features [{:path "specs/features/door.feature" :text feature}]
          procedures #{}
          result (docs-check/qa-findings added-features procedures)]
      (should= [] result)))

  (it "reports finding when feature has neither procedure nor opt-out"
    (let [feature "Feature: door\n  Some description"
          added-features [{:path "specs/features/door.feature" :text feature}]
          procedures #{}
          result (docs-check/qa-findings added-features procedures)]
      (should= ["feature \"door\" has no QA procedure and no \"QA: none\" line"] result)))

  (it "reports finding when opt-out has no reason"
    (let [feature "Feature: door\n  QA: none"
          added-features [{:path "specs/features/door.feature" :text feature}]
          procedures #{}
          result (docs-check/qa-findings added-features procedures)]
      (should= ["feature \"door\" has a \"QA: none\" opt-out with no reason"] result)))

  (it "reports finding when opt-out has only dash"
    (let [feature "Feature: door\n  QA: none -"
          added-features [{:path "specs/features/door.feature" :text feature}]
          procedures #{}
          result (docs-check/qa-findings added-features procedures)]
      (should= ["feature \"door\" has a \"QA: none\" opt-out with no reason"] result)))

  (it "extracts slug from feature file path"
    (let [feature "Feature: door"
          added-features [{:path "specs/features/my-feature.feature" :text feature}]
          procedures #{"specs/qa/my-feature.edn"}
          result (docs-check/qa-findings added-features procedures)]
      (should= [] result)))

  (it "handles multiple added features"
    (let [feature1 "Feature: door\n  QA: none - no input"
          feature2 "Feature: save\n  Some desc"
          added-features [{:path "specs/features/door.feature" :text feature1}
                          {:path "specs/features/save.feature" :text feature2}]
          procedures #{}
          result (docs-check/qa-findings added-features procedures)]
      (should= ["feature \"save\" has no QA procedure and no \"QA: none\" line"] result))))
