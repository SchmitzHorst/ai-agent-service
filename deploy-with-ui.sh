#!/bin/bash
# Deploy Agent Service with UI to Hetzner

set -e

echo "🚀 Deploying Agent Service + UI to Hetzner..."

# Configuration
SERVER="hetzner"
REMOTE_DIR="/opt/agent-service"
LOCAL_TEMPLATE="spring-angular-hetzner-template"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Step 1: Create remote directories
echo -e "${YELLOW}📁 Creating remote directories...${NC}"
ssh $SERVER "mkdir -p $REMOTE_DIR $REMOTE_DIR/template $REMOTE_DIR/generated-apps $REMOTE_DIR/ui"

# Step 2: Copy project files to server
echo -e "${YELLOW}📤 Copying backend files to server...${NC}"
rsync -avz --exclude='target/' --exclude='generated-apps/' --exclude='node_modules/' \
    --exclude='.git/' --exclude='*.log' --exclude='ui/' \
    ./ $SERVER:$REMOTE_DIR/

# Step 3: Copy UI files
echo -e "${YELLOW}🎨 Copying UI files to server...${NC}"
if [ -f "ui/index.html" ]; then
    rsync -avz ui/ $SERVER:$REMOTE_DIR/ui/
else
    echo -e "${YELLOW}Creating UI directory and copying index.html...${NC}"
    mkdir -p ui
    cp index.html ui/
    rsync -avz ui/ $SERVER:$REMOTE_DIR/ui/
fi

# Step 4: Copy nginx config
echo -e "${YELLOW}⚙️  Copying nginx configuration...${NC}"
scp nginx.conf $SERVER:$REMOTE_DIR/

# Step 5: Copy docker-compose
echo -e "${YELLOW}🐳 Copying docker-compose configuration...${NC}"
scp docker-compose.yml $SERVER:$REMOTE_DIR/

# Step 6: Copy template repository
echo -e "${YELLOW}📋 Copying template repository...${NC}"
if [ -d "../$LOCAL_TEMPLATE" ]; then
    rsync -avz --delete ../$LOCAL_TEMPLATE/ $SERVER:$REMOTE_DIR/template/
else
    echo -e "${YELLOW}Template not found locally, checking on server...${NC}"
    ssh $SERVER "test -d $REMOTE_DIR/template/.git || git clone https://github.com/your-org/spring-angular-hetzner-template.git $REMOTE_DIR/template"
fi

# Step 7: Copy .env file
echo -e "${YELLOW}🔑 Copying environment configuration...${NC}"
if [ -f ".env" ]; then
    scp .env $SERVER:$REMOTE_DIR/
else
    echo -e "${RED}⚠️  .env file not found! Please create it from .env.example${NC}"
    exit 1
fi

# Step 8: Build Docker image on server
echo -e "${YELLOW}📦 Building backend Docker image on server...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker build -t agent-service:latest ."

# Step 9: Start services
echo -e "${YELLOW}🔄 Starting services...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker compose down && docker compose up -d"

# Step 10: Wait for startup
echo -e "${YELLOW}⏳ Waiting for services to start...${NC}"
sleep 15

# Step 11: Show status
echo -e "${YELLOW}📊 Checking status...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker compose ps"

# Step 12: Check health
echo -e "${YELLOW}🏥 Checking health...${NC}"
sleep 5
echo "Backend:"
ssh $SERVER "curl -f http://localhost:8081/api/agent/health || echo 'Backend health check failed'"
echo ""
echo "Frontend:"
ssh $SERVER "curl -f http://localhost:80/ | head -5 || echo 'Frontend health check failed'"

# Step 13: Show logs
echo -e "${YELLOW}📜 Recent logs:${NC}"
echo "=== Backend Logs ==="
ssh $SERVER "cd $REMOTE_DIR && docker compose logs --tail=20 agent-service"
echo ""
echo "=== Frontend Logs ==="
ssh $SERVER "cd $REMOTE_DIR && docker compose logs --tail=10 agent-ui"

echo ""
echo -e "${GREEN}✅ Deployment complete!${NC}"
echo -e "${GREEN}🌐 UI available at: https://agent.ai-alpine.ch${NC}"
echo -e "${GREEN}🔌 API available at: https://agent.ai-alpine.ch/api/agent/health${NC}"
echo ""
echo "Commands:"
echo "  View backend logs:  ssh $SERVER 'cd $REMOTE_DIR && docker compose logs -f agent-service'"
echo "  View UI logs:       ssh $SERVER 'cd $REMOTE_DIR && docker compose logs -f agent-ui'"
echo "  Restart backend:    ssh $SERVER 'cd $REMOTE_DIR && docker compose restart agent-service'"
echo "  Restart UI:         ssh $SERVER 'cd $REMOTE_DIR && docker compose restart agent-ui'"
echo "  Stop all:           ssh $SERVER 'cd $REMOTE_DIR && docker compose down'"
echo ""
echo "Update UI only (fast!):"
echo "  scp ui/index.html $SERVER:$REMOTE_DIR/ui/ && ssh $SERVER 'docker restart agent-ui'"
