#!/usr/bin/env python3
"""Validate Mineogenesis CSV exports and summarize independent seeded boundaries."""

import argparse
import csv
from dataclasses import dataclass
import math
from pathlib import Path
import re
import statistics
import sys

MAX_BYTES = 8 * 1024 * 1024
FIELDS = ("generation,best_fitness,mean_fitness,worst_fitness,successful_candidates,population,success_rate,"
          "episode_tick_budget,generation_ticks,total_ticks,total_successes,seed,objective,mutation_probability,"
          "mutation_sigma,network_topology,fitness_version,fixture_id,export_version,configured_generations").split(",")
IDENTITY = ("objective", "population", "episode_tick_budget", "mutation_probability", "mutation_sigma",
            "network_topology", "fitness_version", "fixture_id")
GROUP = (*IDENTITY, "configured_generations", "completed_generations", "complete")
METRICS = ("best_fitness_change", "first_success_rate", "final_success_rate", "overall_success_rate", "total_ticks")


@dataclass(frozen=True)
class Run:
    path: Path
    values: dict

    def identity(self):
        return tuple(self.values[key] for key in (*IDENTITY, "seed"))


def integer(row, key, low, high):
    value = int(row[key])
    if not low <= value <= high:
        raise ValueError(f"{key} outside {low}..{high}")
    return value


def number(row, key, low=0, high=math.inf):
    value = float(row[key])
    if not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f"invalid {key}")
    return value


def read_run(path):
    path = Path(path)
    if path.stat().st_size > MAX_BYTES:
        raise ValueError(f"{path}: export exceeds 8 MiB")
    previous_ticks = previous_successes = 0
    first = constants = None
    best_ever = 0
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS:
            raise ValueError(f"{path}: unsupported CSV columns/version")
        for index, row in enumerate(reader, 1):
            try:
                if None in row or None in row.values() or index > 10000:
                    raise ValueError("invalid row length or too many generations")
                population = integer(row, "population", 2, 256)
                budget = integer(row, "episode_tick_budget", 1, 12000)
                generation = integer(row, "generation", 1, 10000)
                configured = integer(row, "configured_generations", 1, 10000)
                seed = integer(row, "seed", -(1 << 63), (1 << 63) - 1)
                successes = integer(row, "successful_candidates", 0, population)
                ticks = integer(row, "generation_ticks", population, population * budget)
                total_ticks = integer(row, "total_ticks", 1, 256 * 12000 * 10000)
                total_successes = integer(row, "total_successes", 0, 256 * 10000)
                probability = number(row, "mutation_probability", 0, 1)
                sigma = number(row, "mutation_sigma", 0, 8)
                rate = number(row, "success_rate", 0, 1)
                if row["export_version"] != "1" or row["fitness_version"] != "1" or row["network_topology"] != "12-8-6":
                    raise ValueError("unsupported export, fitness or network version")
                if row["objective"] not in {"EXPLORATION", "OAK_LOG_ACQUISITION"} or not re.fullmatch(r"[0-9a-f]{64}", row["fixture_id"]):
                    raise ValueError("unsupported objective or fixture fingerprint")
                limit = budget if row["objective"] == "EXPLORATION" else 111.5
                best, mean, worst = (number(row, key, 0, limit + 1e-9) for key in ("best_fitness", "mean_fitness", "worst_fitness"))
                if worst > mean + 1e-9 or mean > best + 1e-9 or abs(rate - successes / population) > 1e-9:
                    raise ValueError("inconsistent fitness or success rate")
                if row["objective"] == "EXPLORATION" and (successes != 0 or ticks != population * budget):
                    raise ValueError("exploration cannot collect or end episodes early")
                if generation != index or generation > configured or total_ticks != previous_ticks + ticks or total_successes != previous_successes + successes:
                    raise ValueError("inconsistent generation order or cumulative counters")
                current = dict(zip(IDENTITY, (row["objective"], population, budget, probability, sigma,
                                             row["network_topology"], 1, row["fixture_id"])))
                current.update(seed=seed, configured_generations=configured)
                if constants is not None and current != constants:
                    raise ValueError("configuration changes inside an export")
                constants = current
                if first is None:
                    first = (best, rate)
                best_ever = max(best_ever, best)
                previous_ticks, previous_successes = total_ticks, total_successes
            except (ValueError, TypeError, OverflowError) as error:
                raise ValueError(f"{path}: row {index}: {error}") from error
    if first is None:
        raise ValueError(f"{path}: export has no completed generations")
    values = dict(constants, completed_generations=index, complete=int(index == configured),
                  first_best_fitness=first[0], best_ever_fitness=best_ever, best_fitness_change=best_ever - first[0],
                  first_success_rate=first[1], final_success_rate=rate,
                  overall_success_rate=total_successes / (population * index),
                  total_successes=total_successes, total_ticks=total_ticks)
    return Run(path, values)


def summarize(runs):
    groups, seen = {}, set()
    for run in runs:
        if run.identity() in seen:
            raise ValueError(f"{run.path}: duplicate seed/configuration/fixture; choose one export per independent run")
        seen.add(run.identity())
        key = tuple(run.values[column] for column in GROUP)
        groups.setdefault(key, []).append(run)
    result = []
    for key, members in sorted(groups.items()):
        row = dict(zip(GROUP, key))
        row["runs"] = len(members)
        row["seeds"] = ";".join(str(r.values["seed"]) for r in sorted(members, key=lambda r: r.values["seed"]))
        for metric in METRICS:
            data = [r.values[metric] for r in members]
            row[metric + "_mean"] = statistics.mean(data)
            row[metric + "_sample_sd"] = statistics.stdev(data) if len(data) > 1 else ""
        result.append(row)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path, help="completed-generation CSV exports (one per independent seeded run)")
    parser.add_argument("--per-run", action="store_true", help="emit one summary row per input instead of grouping compatible seeds")
    args = parser.parse_args(argv)
    try:
        runs = [read_run(path) for path in args.files]
        grouped = summarize(runs)  # Validate independence even for per-run output.
        rows = [dict(file=str(run.path), **run.values) for run in runs] if args.per_run else grouped
        for run in runs:
            if not run.values["complete"]:
                print(f"{run.path}: partial boundary ({run.values['completed_generations']}/{run.values['configured_generations']} generations)", file=sys.stderr)
        writer = csv.DictWriter(sys.stdout, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)
    except (OSError, ValueError, csv.Error, UnicodeError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    main()
