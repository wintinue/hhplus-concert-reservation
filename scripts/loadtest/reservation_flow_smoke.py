#!/usr/bin/env python3
import argparse
import json
import random
import string
import sys
import time
import urllib.error
import urllib.request


def request(method, url, headers=None, body=None, timeout=10.0):
    payload = None
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return {
                "ok": True,
                "status": response.getcode(),
                "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
                "body": json.loads(raw) if raw else None,
            }
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        return {
            "ok": False,
            "status": exc.code,
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
            "error_body": raw,
        }


def random_suffix(length=10):
    chars = string.ascii_lowercase + string.digits
    return "".join(random.choice(chars) for _ in range(length))


def create_user(base_url):
    email = f"loadtest-{random_suffix()}@hhplus.kr"
    result = request(
        "POST",
        f"{base_url}/api/v1/auth/signup",
        body={"email": email, "name": "loadtester", "password": "password123"},
    )
    if not result["ok"]:
        raise RuntimeError(f"signup failed: {result}")
    token = result["body"]["accessToken"]
    return {"email": email, "headers": {"Authorization": f"Bearer {token}"}}


def charge_points(base_url, headers, amount):
    result = request(
        "POST",
        f"{base_url}/api/v1/users/me/points/charges",
        headers=headers,
        body={"amount": amount},
    )
    if not result["ok"]:
        raise RuntimeError(f"point charge failed: {result}")
    return result


def issue_queue_token(base_url, headers, concert_id):
    result = request(
        "POST",
        f"{base_url}/api/v1/queue/tokens",
        headers=headers,
        body={"concertId": concert_id},
    )
    if not result["ok"]:
        raise RuntimeError(f"queue issue failed: {result}")
    queue_token = result["body"]["queueToken"]
    queue_headers = dict(headers)
    queue_headers["X-Queue-Token"] = queue_token
    return result, queue_headers


def get_seats(base_url, headers, schedule_id):
    result = request("GET", f"{base_url}/api/v1/schedules/{schedule_id}/seats", headers=headers)
    if not result["ok"]:
        raise RuntimeError(f"seat fetch failed: {result}")
    return result


def hold_seat(base_url, headers, schedule_id, seat_id):
    result = request(
        "POST",
        f"{base_url}/api/v1/reservations/holds",
        headers=headers,
        body={"scheduleId": schedule_id, "seatIds": [seat_id]},
    )
    if not result["ok"]:
        raise RuntimeError(f"hold failed: {result}")
    return result


def create_reservation(base_url, headers, hold_id):
    result = request(
        "POST",
        f"{base_url}/api/v1/reservations",
        headers=headers,
        body={"holdId": hold_id},
    )
    if not result["ok"]:
        raise RuntimeError(f"reservation create failed: {result}")
    return result


def pay_reservation(base_url, headers, reservation_id, amount, method):
    result = request(
        "POST",
        f"{base_url}/api/v1/payments",
        headers=headers,
        body={"reservationId": reservation_id, "amount": amount, "method": method},
    )
    if not result["ok"]:
        raise RuntimeError(f"payment failed: {result}")
    return result


def run_flow(base_url, concert_id, schedule_id, seat_id, charge_amount):
    random.seed(time.time_ns())
    account = create_user(base_url)
    charge = charge_points(base_url, account["headers"], charge_amount)
    queue_result, queue_headers = issue_queue_token(base_url, account["headers"], concert_id)
    seats = get_seats(base_url, queue_headers, schedule_id)
    target = next((item for item in seats["body"]["items"] if item["seatId"] == seat_id), None)
    if target is None:
        raise RuntimeError(f"seat {seat_id} not found")
    hold = hold_seat(base_url, queue_headers, schedule_id, seat_id)
    reservation = create_reservation(base_url, queue_headers, hold["body"]["holdId"])
    payment = pay_reservation(
        base_url,
        queue_headers,
        reservation["body"]["reservationId"],
        reservation["body"]["totalAmount"],
        "CARD",
    )
    return {
        "user": account["email"],
        "queue": queue_result,
        "charge": charge,
        "seat": target,
        "hold": hold,
        "reservation": reservation,
        "payment": payment,
    }


def parse_args():
    parser = argparse.ArgumentParser(description="Run repeatable reservation hold/pay smoke flow")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--concert-id", type=int, default=1)
    parser.add_argument("--schedule-id", type=int, default=1)
    parser.add_argument("--seat-id", type=int, default=1)
    parser.add_argument("--charge-amount", type=int, default=100000)
    return parser.parse_args()


def main():
    args = parse_args()
    result = run_flow(args.base_url, args.concert_id, args.schedule_id, args.seat_id, args.charge_amount)
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
