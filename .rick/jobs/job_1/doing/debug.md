# Debug Log - job_1/task1

## Summary

Created docker-compose deployment for standalone HBase 1.2 with data persistence.

## Key Fixes

### Fix 1: ZooKeeper Data Persistence
- **Problem**: After `docker compose down && up -d`, user tables were lost despite being on the named volume filesystem. `hbase:meta` table lost entries for user tables.
- **Root Cause**: ZooKeeper stores ephemeral data in `/tmp/hbase-root/` inside the container. After container recreation, ZK starts fresh with no state. HBase Master's SplitLogManager fails to properly replay WALs because it can't coordinate with stale RegionServer state in ZK, resulting in "Returning success without actually splitting" warnings.
- **Fix**: Added a second named volume `zk-data` mounted at `/tmp/hbase-root` in docker-compose.yaml to persist ZooKeeper state across container recreation.
- **Verification**: Created table, put data, ran `docker compose down && up -d`, scanned table — data persisted successfully.

### Fix 2: Test Idempotency
- **Problem**: Test 10 (write test data) failed on re-runs because `t_persist` table persisted from previous run (the persistence feature we wanted!).
- **Fix**: Split Test 10 into two phases: (1) cleanup (disable/drop, ignoring errors), (2) create/put (checking return code).

## Key Results Status

| KR | Status | Notes |
|----|--------|-------|
| KR1: docker-compose.yaml with all required elements | ✅ | Contains all ports, platform, hostname, volumes |
| KR2: Rosetta precheck | ✅ | Verified x86_64 |
| KR3: /etc/hosts entry | ⚠️ | Requires user to run: `echo '127.0.0.1 hbase' \| sudo tee -a /etc/hosts` |
| KR4: docker compose up -d success | ✅ | Container running |
| KR5: Readiness verification (curl, nc, rootdir, ps, logs) | ✅ | All checks pass |
| KR6: deploy/README.md exists | ✅ | All required sections present |

## Remaining Manual Step

The `/etc/hosts` entry requires sudo. Non-interactive environment cannot prompt for password. User must manually run:
```bash
echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts
```
