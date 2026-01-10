# Gitea Setup Instructions

This directory contains the configuration needed to deploy Gitea + Gitea Actions on your MacBook Air server.

## Prerequisites

- Docker and Docker Compose installed on MacBook Air
- Ports 3000 (HTTP) and 2222 (SSH) available

## Installation Steps

### 1. Copy docker-compose.yml to MacBook Air

```bash
# On MacBook Air server
mkdir -p ~/Dev/gitea
cd ~/Dev/gitea

# Copy the docker-compose.yml file from this directory to ~/Dev/gitea/
# You can scp it or copy manually
```

### 2. Start Gitea Server

```bash
cd ~/Dev/gitea
docker-compose up -d gitea

# Wait for Gitea to be healthy (~30-60 seconds)
docker-compose logs -f gitea

# You should see: "Listen: http://0.0.0.0:3000"
```

### 3. Complete Initial Setup

1. Open browser: http://localhost:3000 (or http://<macbook-ip>:3000)
2. You'll see the initial configuration page
3. **Important settings:**
   - Database Type: SQLite3 (already configured)
   - Server Domain: `localhost` (or your MacBook hostname/IP)
   - Gitea Base URL: `http://localhost:3000/` (or http://<macbook-ip>:3000/)
   - Admin Account: Create your admin user
   - ✅ **CRITICAL**: Verify "Enable Gitea Actions" is checked
4. Click "Install Gitea"

### 4. Register Gitea Actions Runner

After Gitea is installed and running:

```bash
# Step 1: Generate runner registration token in Gitea Web UI
# - Login to Gitea as admin
# - Go to: Site Administration → Actions → Runners
# - Click "Create new Runner"
# - Copy the registration token (looks like: abcd1234...)

# Step 2: Start the runner container
cd ~/Dev/gitea
docker-compose up -d gitea-runner

# Step 3: Register the runner manually
docker exec -it gitea-runner act_runner register \
  --instance http://gitea:3000 \
  --token <PASTE_TOKEN_HERE> \
  --name macbook-air-runner

# Step 4: Restart the runner
docker-compose restart gitea-runner

# Step 5: Verify runner is online
# - Go to Gitea UI: Site Administration → Actions → Runners
# - You should see "macbook-air-runner" with status "Idle" (green)
```

### 5. Create Repository

1. Login to Gitea
2. Click "+" icon → "New Repository"
3. Repository name: `base-data-etl`
4. Visibility: Private (recommended) or Public
5. **Do NOT initialize** with README, .gitignore, or license (we'll push existing code)
6. Click "Create Repository"

### 6. Push Code from Development Laptop

On your development laptop (NOT on the MacBook Air server):

```bash
cd /path/to/base-data-etl

# Add Gitea as remote
git remote add gitea http://<macbook-air-ip>:3000/<your-username>/base-data-etl.git

# Push code
git push gitea main

# Verify in Gitea UI - you should see all your code
```

## Verification

### Check Gitea is Running
```bash
curl http://localhost:3000/api/healthz
# Should return: {"status":"pass"}
```

### Check Runner is Registered
```bash
docker exec -it gitea-runner act_runner list
# Should show: "macbook-air-runner" with status "idle"
```

### View Logs
```bash
# Gitea server logs
docker-compose logs -f gitea

# Runner logs
docker-compose logs -f gitea-runner
```

## Troubleshooting

### Gitea not starting
```bash
# Check logs
docker-compose logs gitea

# Restart
docker-compose restart gitea
```

### Runner not online
```bash
# Re-register runner
docker exec -it gitea-runner act_runner register \
  --instance http://gitea:3000 \
  --token <NEW_TOKEN>

docker-compose restart gitea-runner
```

### Port conflicts
```bash
# Check what's using port 3000
lsof -i :3000

# Or use different ports in docker-compose.yml:
# ports:
#   - "3001:3000"  # HTTP
#   - "2223:2222"  # SSH
```

## Management Commands

```bash
# Start Gitea
cd ~/Dev/gitea && docker-compose up -d

# Stop Gitea
cd ~/Dev/gitea && docker-compose down

# View logs
cd ~/Dev/gitea && docker-compose logs -f

# Restart after config changes
cd ~/Dev/gitea && docker-compose restart

# Update Gitea to latest version
cd ~/Dev/gitea && docker-compose pull && docker-compose up -d
```

## Next Steps

After Gitea is running and the repository is created:
1. The CI/CD workflow files will be automatically created in the repository
2. Push to main will automatically trigger: Test → Deploy to staging
3. Weekly ETL will run every Sunday at 10:00 AM

See main README.md for the complete development workflow.
