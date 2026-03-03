#!/usr/bin/env bash
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-hhplus-concert-reservation-mysql-1}"
REDIS_CONTAINER="${REDIS_CONTAINER:-hhplus-concert-reservation-redis-1}"
MYSQL_DATABASE="${MYSQL_DATABASE:-hhplus}"
MYSQL_USER="${MYSQL_USER:-application}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-application}"
SCHEDULE_ID="${SCHEDULE_ID:-1}"
CONCERT_ID="${CONCERT_ID:-1}"
LOADTEST_EMAIL_PREFIX="${LOADTEST_EMAIL_PREFIX:-loadtest-}"
DEMO_POINT_BALANCE="${DEMO_POINT_BALANCE:-200000}"

read -r -d '' SQL <<SQL || true
SET FOREIGN_KEY_CHECKS = 0;

DELETE pt
FROM point_transactions pt
JOIN users u ON u.id = pt.user_id
WHERE u.email LIKE '${LOADTEST_EMAIL_PREFIX}%';

DELETE oe
FROM outbox_events oe
JOIN reservations r ON r.reservation_id = oe.aggregate_id
WHERE oe.aggregate_type = 'RESERVATION'
  AND r.schedule_id = ${SCHEDULE_ID};

DELETE bs
FROM booking_sagas bs
JOIN reservations r ON r.reservation_id = bs.reservation_id
WHERE r.schedule_id = ${SCHEDULE_ID};

DELETE p
FROM payments p
JOIN reservations r ON r.reservation_id = p.reservation_id
WHERE r.schedule_id = ${SCHEDULE_ID};

DELETE ri
FROM reservation_items ri
JOIN reservations r ON r.reservation_id = ri.reservation_id
WHERE r.schedule_id = ${SCHEDULE_ID};

DELETE r
FROM reservations r
WHERE r.schedule_id = ${SCHEDULE_ID};

DELETE shi
FROM seat_hold_items shi
JOIN seat_holds sh ON sh.hold_id = shi.hold_id
WHERE sh.schedule_id = ${SCHEDULE_ID};

DELETE sh
FROM seat_holds sh
WHERE sh.schedule_id = ${SCHEDULE_ID};

UPDATE seats
SET seat_status = 'AVAILABLE'
WHERE schedule_id = ${SCHEDULE_ID};

DELETE qs
FROM user_sessions qs
JOIN users u ON u.id = qs.user_id
WHERE u.email LIKE '${LOADTEST_EMAIL_PREFIX}%';

DELETE qt
FROM queue_tokens qt
JOIN users u ON u.id = qt.user_id
WHERE u.email LIKE '${LOADTEST_EMAIL_PREFIX}%';

DELETE up
FROM user_points up
JOIN users u ON u.id = up.user_id
WHERE u.email LIKE '${LOADTEST_EMAIL_PREFIX}%';

DELETE u
FROM users u
WHERE u.email LIKE '${LOADTEST_EMAIL_PREFIX}%';

UPDATE user_points up
JOIN users u ON u.id = up.user_id
SET up.balance = ${DEMO_POINT_BALANCE}, up.updated_at = UTC_TIMESTAMP(6)
WHERE u.email = 'demo@hhplus.kr';

SET FOREIGN_KEY_CHECKS = 1;
SQL

docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" <<SQL
${SQL}
SQL

docker exec -i "${REDIS_CONTAINER}" redis-cli DEL "queue:concert:${CONCERT_ID}:waiting" "queue:concert:${CONCERT_ID}:active" >/dev/null

printf 'reset completed for concert_id=%s schedule_id=%s\n' "${CONCERT_ID}" "${SCHEDULE_ID}"
