#!/usr/bin/env python3
import argparse
import json
import random
import statistics
import string
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def request(method, url, headers=None, body=None, timeout=10.0):
    payload = None
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
    started = time.perf_counter()
    status = None
    response_body = None
    error_body = None
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            raw = response.read().decode("utf-8")
            response_body = json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8")
        error_body = raw
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "status": "EXCEPTION",
            "elapsed_ms": (time.perf_counter() - started) * 1000,
            "error": str(exc),
        }
    return {
        "ok": status is not None and 200 <= status < 300,
        "status": status,
        "elapsed_ms": (time.perf_counter() - started) * 1000,
        "body": response_body,
        "error_body": error_body,
    }


def percentile(values, ratio):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * ratio))))
    return ordered[index]


def random_suffix(length=10):
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(length))


def create_user(base_url):
    email = f"loadtest-{random_suffix()}@hhplus.kr"
    result = request(
        "POST",
        f"{base_url}/api/v1/auth/signup",
        body={"email": email, "name": "loadtester", "password": "password123"},
    )
    if not result["ok"]:
        raise RuntimeError(f"failed to sign up user: {result}")
    token = result["body"]["accessToken"]
    auth_header = {"Authorization": f"Bearer {token}"}
    return {"email": email, "access_token": token, "headers": auth_header}


def login_demo(base_url):
    result = request(
        "POST",
        f"{base_url}/api/v1/auth/login",
        body={"email": "demo@hhplus.kr", "password": "password123"},
    )
    if not result["ok"]:
        raise RuntimeError(f"failed to login demo user: {result}")
    token = result["body"]["accessToken"]
    return {"access_token": token, "headers": {"Authorization": f"Bearer {token}"}}


def issue_queue_token(base_url, headers, concert_id=1):
    result = request(
        "POST",
        f"{base_url}/api/v1/queue/tokens",
        headers=headers,
        body={"concertId": concert_id},
    )
    if not result["ok"]:
        raise RuntimeError(f"failed to issue queue token: {result}")
    queue_token = result["body"]["queueToken"]
    merged = dict(headers)
    merged["X-Queue-Token"] = queue_token
    return {"queue_token": queue_token, "headers": merged, "body": result["body"]}


def run_concurrent(name, total_requests, workers, task_factory):
    results = []
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(task_factory, index) for index in range(total_requests)]
        for future in as_completed(futures):
            results.append(future.result())
    total_seconds = time.perf_counter() - started
    latencies = [item["elapsed_ms"] for item in results]
    failures = [item for item in results if not item["ok"]]
    return {
        "name": name,
        "total_requests": total_requests,
        "workers": workers,
        "duration_seconds": round(total_seconds, 3),
        "throughput_rps": round(total_requests / total_seconds, 2) if total_seconds else 0.0,
        "success_count": len(results) - len(failures),
        "failure_count": len(failures),
        "status_breakdown": build_status_breakdown(results),
        "latency_ms": {
            "avg": round(statistics.mean(latencies), 2) if latencies else 0.0,
            "p50": round(percentile(latencies, 0.50), 2),
            "p95": round(percentile(latencies, 0.95), 2),
            "max": round(max(latencies), 2) if latencies else 0.0,
        },
        "sample_failures": failures[:5],
    }


def build_status_breakdown(results):
    breakdown = {}
    for item in results:
        key = str(item["status"])
        breakdown[key] = breakdown.get(key, 0) + 1
    return dict(sorted(breakdown.items(), key=lambda entry: entry[0]))


def queue_issue_scenario(base_url, users, workers):
    accounts = [create_user(base_url) for _ in range(users)]

    def task(index):
        account = accounts[index]
        return request(
            "POST",
            f"{base_url}/api/v1/queue/tokens",
            headers=account["headers"],
            body={"concertId": 1},
        )

    return run_concurrent("queue-issue", users, workers, task)


def queue_poll_scenario(base_url, users, workers):
    accounts = [create_user(base_url) for _ in range(users)]
    queue_tokens = [issue_queue_token(base_url, account["headers"]) for account in accounts]

    def task(index):
        queue_token = queue_tokens[index]["queue_token"]
        return request(
            "GET",
            f"{base_url}/api/v1/queue/tokens/{urllib.parse.quote(queue_token)}",
            headers=accounts[index]["headers"],
        )

    return run_concurrent("queue-poll", users, workers, task)


def concert_read_scenario(base_url, requests_count, workers):
    demo = login_demo(base_url)

    def task(_):
        return request("GET", f"{base_url}/api/v1/concerts?page=0&size=20", headers=demo["headers"])

    return run_concurrent("concert-read", requests_count, workers, task)


def seat_read_scenario(base_url, requests_count, workers):
    account = create_user(base_url)
    queue_state = issue_queue_token(base_url, account["headers"])

    def task(_):
        return request("GET", f"{base_url}/api/v1/schedules/1/seats", headers=queue_state["headers"])

    return run_concurrent("seat-read", requests_count, workers, task)


def point_charge_scenario(base_url, requests_count, workers):
    account = create_user(base_url)

    def task(_):
        return request(
            "POST",
            f"{base_url}/api/v1/users/me/points/charges",
            headers=account["headers"],
            body={"amount": 1000},
        )

    return run_concurrent("point-charge", requests_count, workers, task)


def hold_seat_conflict_scenario(base_url, requests_count, workers):
    account = create_user(base_url)
    queue_state = issue_queue_token(base_url, account["headers"])

    def task(_):
        return request(
            "POST",
            f"{base_url}/api/v1/reservations/holds",
            headers=queue_state["headers"],
            body={"scheduleId": 1, "seatIds": [1]},
        )

    return run_concurrent("hold-seat-conflict", requests_count, workers, task)


def parse_args():
    parser = argparse.ArgumentParser(description="Basic load test runner for Step 10 assignment")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument(
        "--scenario",
        required=True,
        choices=["queue-issue", "queue-poll", "concert-read", "seat-read", "point-charge", "hold-seat-conflict"],
    )
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--workers", type=int, default=20)
    parser.add_argument("--users", type=int, default=50)
    return parser.parse_args()


def main():
    args = parse_args()
    random.seed(time.time_ns())
    if args.scenario == "queue-issue":
        summary = queue_issue_scenario(args.base_url, args.users, args.workers)
    elif args.scenario == "queue-poll":
        summary = queue_poll_scenario(args.base_url, args.users, args.workers)
    elif args.scenario == "concert-read":
        summary = concert_read_scenario(args.base_url, args.requests, args.workers)
    elif args.scenario == "point-charge":
        summary = point_charge_scenario(args.base_url, args.requests, args.workers)
    elif args.scenario == "seat-read":
        summary = seat_read_scenario(args.base_url, args.requests, args.workers)
    else:
        summary = hold_seat_conflict_scenario(args.base_url, args.requests, args.workers)

    json.dump(summary, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
