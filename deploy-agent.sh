#!/bin/bash
# Deploy Agent Service to Hetzner (build on server)

set -e

echo "🚀 Deploying Agent Service to Hetzner..."

# Configuration
SERVER="hetzner"
REMOTE_DIR="/opt/agent-service"
LOCAL_TEMPLATE="spring-angular-hetzner-template"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Step 1: Create remote directory
echo -e "${YELLOW}📁 Creating remote directory...${NC}"
ssh $SERVER "mkdir -p $REMOTE_DIR $REMOTE_DIR/template $REMOTE_DIR/generated-apps"

# Step 2: Copy project files to server
echo -e "${YELLOW}📤 Copying project files to server...${NC}"
rsync -avz --exclude='target/' --exclude='generated-apps/' --exclude='node_modules/' \
    --exclude='.git/' --exclude='*.log' \
    ./ $SERVER:$REMOTE_DIR/

# Step 3: Copy template repository
echo -e "${YELLOW}📋 Copying template repository...${NC}"
if [ -d "../$LOCAL_TEMPLATE" ]; then
    rsync -avz --delete ../$LOCAL_TEMPLATE/ $SERVER:$REMOTE_DIR/template/
else
    echo -e "${YELLOW}Template not found locally, checking on server...${NC}"
    ssh $SERVER "test -d $REMOTE_DIR/template/.git || git clone https://github.com/your-org/spring-angular-hetzner-template.git $REMOTE_DIR/template"
fi

# Step 4: Copy .env file
echo -e "${YELLOW}🔑 Copying environment configuration...${NC}"
if [ -f ".env" ]; then
    scp .env $SERVER:$REMOTE_DIR/
else
    echo -e "${RED}⚠️  .env file not found! Please create it from .env.example${NC}"
    exit 1
fi

# Step 5: Build Docker image on server
echo -e "${YELLOW}📦 Building Docker image on server...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker build -t agent-service:latest ."

# Step 6: Start service
echo -e "${YELLOW}🔄 Starting Agent Service...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker compose up -d"

# Step 7: Wait for startup
echo -e "${YELLOW}⏳ Waiting for service to start...${NC}"
sleep 10

# Step 8: Show status
echo -e "${YELLOW}📊 Checking status...${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker compose ps"

# Step 9: Check health
echo -e "${YELLOW}🏥 Checking health...${NC}"
sleep 5
ssh $SERVER "curl -f http://localhost:8081/api/agent/health || echo 'Health check failed (service may still be starting)'"

# Step 10: Show logs
echo -e "${YELLOW}📜 Recent logs:${NC}"
ssh $SERVER "cd $REMOTE_DIR && docker compose logs --tail=30 agent-service"

echo ""
echo -e "${GREEN}✅ Deployment complete!${NC}"
echo -e "${GREEN}🌐 Agent Service should be available at: https://agent.ai-alpine.ch${NC}"
echo ""
echo "Commands:"
echo "  View logs:    ssh $SERVER 'cd $REMOTE_DIR && docker compose logs -f agent-service'"
echo "  Restart:      ssh $SERVER 'cd $REMOTE_DIR && docker compose restart agent-service'"
echo "  Stop:         ssh $SERVER 'cd $REMOTE_DIR && docker compose down'"
echo "  Rebuild:      ssh $SERVER 'cd $REMOTE_DIR && docker build -t agent-service:latest . && docker compose up -d'"
echo ""
echo "Test:"
echo "  curl https://agent.ai-alpine.ch/api/agent/health"
