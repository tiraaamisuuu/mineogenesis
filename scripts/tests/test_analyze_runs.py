import contextlib
import csv
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "analyze-runs.py"
SPEC = importlib.util.spec_from_file_location("analyze_runs", SCRIPT)
analysis = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = analysis
SPEC.loader.exec_module(analysis)


class AnalysisTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def rows(self, seed=42, objective="OAK_LOG_ACQUISITION"):
        return [dict(zip(analysis.FIELDS, (
            generation, best, mean, 0, successes, 4, successes / 4,
            100, ticks, total_ticks, total_successes, seed, objective,
            .1, .2, "12-8-6", 1, "a" * 64, 1, 2)))
            for generation, best, mean, successes, ticks, total_ticks, total_successes
            in [(1, 10, 3, 0, 400, 400, 0), (2, 110, 30, 1, 350, 750, 1)]]

    def write(self, rows, name="trial.csv", fields=None):
        path = self.root / name
        with path.open("w", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields or analysis.FIELDS)
            writer.writeheader(); writer.writerows(rows)
        return path

    def test_collection_is_reported_separately_from_fitness(self):
        run = analysis.read_run(self.write(self.rows()))
        self.assertEqual(run.values["best_fitness_change"], 100)
        self.assertEqual(run.values["first_success_rate"], 0)
        self.assertEqual(run.values["final_success_rate"], .25)
        self.assertEqual(run.values["overall_success_rate"], .125)
        self.assertEqual(run.values["total_ticks"], 750)
        self.assertEqual(run.values["complete"], 1)

    def test_seed_groups_use_sample_sd_and_singletons_have_no_variance_estimate(self):
        first = analysis.read_run(self.write(self.rows(11), "a.csv"))
        rows = self.rows(22); rows[-1]["best_fitness"] = 100
        second = analysis.read_run(self.write(rows, "b.csv"))
        group = analysis.summarize([first, second])[0]
        self.assertEqual(group["runs"], 2)
        self.assertEqual(group["seeds"], "11;22")
        self.assertEqual(group["best_fitness_change_mean"], 95)
        self.assertAlmostEqual(group["best_fitness_change_sample_sd"], 50 ** .5)
        self.assertEqual(analysis.summarize([first])[0]["best_fitness_change_sample_sd"], "")

    def test_duplicate_exports_and_continuations_are_not_independent_replicates(self):
        full = analysis.read_run(self.write(self.rows(), "full.csv"))
        partial = analysis.read_run(self.write(self.rows()[:1], "partial.csv"))
        with self.assertRaisesRegex(ValueError, "duplicate seed"):
            analysis.summarize([partial, full])

    def test_different_fixtures_and_parameters_are_never_pooled(self):
        first = analysis.read_run(self.write(self.rows(11), "a.csv"))
        for key, value in [("fixture_id", "b" * 64), ("mutation_sigma", .5), ("episode_tick_budget", 101)]:
            rows = self.rows(22)
            for row in rows:
                row[key] = value
            second = analysis.read_run(self.write(rows, "b.csv"))
            self.assertEqual(len(analysis.summarize([first, second])), 2)

    def test_partial_exports_are_explicit_and_not_pooled_with_complete_runs(self):
        partial = analysis.read_run(self.write(self.rows(11)[:1], "partial.csv"))
        full = analysis.read_run(self.write(self.rows(22), "full.csv"))
        self.assertEqual(partial.values["complete"], 0)
        self.assertEqual(len(analysis.summarize([partial, full])), 2)
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            analysis.main([str(partial.path)])
        self.assertIn("partial boundary (1/2", err.getvalue())
        self.assertIn("complete", out.getvalue())

    def test_invalid_rows_fail_with_path_and_row_number(self):
        changes = [("mean_fitness", "NaN"), ("best_fitness", "Infinity"), ("success_rate", .5),
                   ("total_ticks", 999), ("total_successes", 5), ("generation", 3), ("population", 1),
                   ("configured_generations", 0), ("mutation_sigma", 9), ("seed", 1 << 63),
                   ("objective", "SURVIVAL"), ("fixture_id", "bad"), ("fitness_version", 2),
                   ("network_topology", "8-8-4"), ("export_version", 2), ("generation_ticks", 1)]
        for key, value in changes:
            with self.subTest(key=key):
                rows = self.rows(); rows[-1][key] = value
                with self.assertRaisesRegex(ValueError, r"trial.csv: row 2"):
                    analysis.read_run(self.write(rows))

    def test_configuration_drift_and_fitness_order_are_rejected(self):
        for key, value in [("mutation_probability", .2), ("worst_fitness", 99)]:
            rows = self.rows(); rows[-1][key] = value
            with self.assertRaises(ValueError):
                analysis.read_run(self.write(rows))

    def test_exploration_cannot_claim_collection_or_shortened_episodes(self):
        rows = self.rows(objective="EXPLORATION")
        with self.assertRaises(ValueError):
            analysis.read_run(self.write(rows))
        rows[1].update(best_fitness=20, mean_fitness=5, successful_candidates=0, success_rate=0, total_successes=0)
        with self.assertRaisesRegex(ValueError, "exploration cannot"):
            analysis.read_run(self.write(rows))
        rows[1].update(generation_ticks=400, total_ticks=800)
        self.assertEqual(analysis.read_run(self.write(rows)).values["total_successes"], 0)

    def test_empty_headers_truncated_and_oversized_files_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "no completed"):
            analysis.read_run(self.write([]))
        path = self.root / "bad.csv"
        for content in ["garbage\n", ",".join(analysis.FIELDS) + "\n1,2,3\n"]:
            path.write_text(content)
            with self.assertRaises(ValueError):
                analysis.read_run(path)
        with path.open("wb") as stream:
            stream.truncate(analysis.MAX_BYTES + 1)
        with self.assertRaisesRegex(ValueError, "exceeds"):
            analysis.read_run(path)

    def test_cli_outputs_parseable_csv_and_returns_nonzero_for_errors(self):
        path = self.write(self.rows())
        result = subprocess.run([sys.executable, str(SCRIPT), "--per-run", str(path)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        row = next(csv.DictReader(io.StringIO(result.stdout)))
        self.assertEqual(row["total_successes"], "1")
        self.assertEqual(row["file"], str(path))
        bad = subprocess.run([sys.executable, str(SCRIPT), str(path), str(path)], capture_output=True, text=True)
        self.assertEqual(bad.returncode, 2)
        self.assertEqual(bad.stdout, "")
        self.assertIn("duplicate seed", bad.stderr)


if __name__ == "__main__":
    unittest.main()
