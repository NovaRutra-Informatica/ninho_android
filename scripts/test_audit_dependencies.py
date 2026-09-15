import unittest
import audit_dependencies as audit_module


class DependencyAuditTests(unittest.TestCase):
    def inventory(self, gradle="9.7.1"):
        return {"gradle": gradle, "packages": [{"name": "test:package", "version": "1.0"}], "scopes": ["releaseRuntimeClasspath"], "errors": []}

    def clean_response(self, endpoint, body):
        return {"results": [
            {"vulns": [{"id": audit_module.CONTROL_ADVISORY}]} if query == audit_module.CONTROL_QUERY else {}
            for query in body["queries"]
        ]}

    def test_known_vulnerable_gradle_is_rejected_even_when_osv_does_not_index_it(self):
        report = audit_module.audit(self.inventory("9.1.0"), self.clean_response)
        self.assertEqual(len(report["findings"][0]["vulnerabilities"]), 2)

    def test_patched_gradle_and_packages_pass(self):
        self.assertEqual(audit_module.audit(self.inventory(), self.clean_response)["findings"], [])

    def test_positive_control_is_not_counted_as_project_vulnerability(self):
        report = audit_module.audit(self.inventory(), self.clean_response)
        self.assertEqual(report["packagesChecked"], 1)
        self.assertIn(audit_module.CONTROL_ADVISORY, report["positiveControl"])

    def test_failed_positive_control_cannot_report_clean(self):
        with self.assertRaisesRegex(RuntimeError, "positive control"):
            audit_module.audit(self.inventory(), lambda endpoint, body: {"results": [{} for _ in body["queries"]]})

    def test_missing_batch_results_cannot_report_clean(self):
        with self.assertRaisesRegex(RuntimeError, "incomplete batch"):
            audit_module.audit(self.inventory(), lambda endpoint, body: {"results": []})

    def test_resolution_failure_is_not_silently_skipped(self):
        inventory = self.inventory()
        inventory["errors"] = [{"error": "repository unavailable"}]
        with self.assertRaisesRegex(RuntimeError, "resolution errors"):
            audit_module.audit(inventory, self.clean_response)

    def test_vulnerability_on_later_page_is_included(self):
        def pages(endpoint, body):
            if len(body["queries"]) == 1:
                return {"results": [{"vulns": [{"id": "GHSA-later-page"}]}]}
            return {"results": [{"next_page_token": "next"}, {"vulns": [{"id": audit_module.CONTROL_ADVISORY}]}]}
        report = audit_module.audit(self.inventory(), pages)
        self.assertEqual(report["findings"][0]["vulnerabilities"][0]["id"], "GHSA-later-page")

    def test_repeated_pagination_token_fails_instead_of_hanging(self):
        def pages(endpoint, body):
            return {"results": [{"next_page_token": "next"} for _ in body["queries"]]}
        with self.assertRaisesRegex(RuntimeError, "repeated"):
            audit_module.audit(self.inventory(), pages)


if __name__ == "__main__":
    unittest.main()
