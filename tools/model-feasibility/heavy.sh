#!/bin/sh
# heavy.sh NAME MEMMAX CMD...  — run one heavy job as a capped systemd user unit, one at a time.
set -e
NAME="$1"; MEM="$2"; shift 2
AVAIL=$(awk '/MemAvailable/ {print int($2/1024/1024)}' /proc/meminfo)
if [ "$AVAIL" -lt 5 ]; then echo "MemAvailable ${AVAIL}G < 5G, refusing"; exit 3; fi
exec flock ${ROBIA_FEAS_DIR}/.heavy.lock \
  systemd-run --user --wait --collect --unit="r3-$NAME" -p MemoryMax="$MEM" -p MemorySwapMax=0 -p Nice=10 \
  -p WorkingDirectory=${ROBIA_FEAS_DIR} "$@"
