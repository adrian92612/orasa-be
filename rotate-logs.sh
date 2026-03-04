#!/bin/bash
# Configuration
BASE_DIR="/home/ubuntu/orasa-deploy"
ORASA_DIR="$BASE_DIR/orasa"
LOG_DIR="$BASE_DIR/logs"
RETENTION_DAYS=7

# Ensure log directory exists
mkdir -p "$LOG_DIR"

# Step 1: Extract logs from the last 24 hours
DATE=$(date +%Y-%m-%d)
echo "Extracting logs for $DATE..."

cd "$ORASA_DIR"
# Use docker-compose to get logs for all services since 24h ago
docker compose logs --no-color --since 24h > "$LOG_DIR/orasa-$DATE.log"

# Step 2: Delete logs older than 7 days
echo "Cleaning up old logs..."
find "$LOG_DIR" -name "*.log" -type f -mtime +$RETENTION_DAYS -delete

echo "Done. Log saved to $LOG_DIR/orasa-$DATE.log"
