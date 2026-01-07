# AI Agent Service - Automated Full-Stack Application Generator

Generate complete, production-ready web applications from simple JSON requirements in minutes.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-17-red.svg)](https://angular.io/)

## 🚀 What It Does

The AI Agent Service is an **automated application generator** that:

1. **Takes JSON requirements** describing your app (entities, fields, relationships)
2. **Generates complete full-stack code** (Backend: Spring Boot + Angular Frontend)
3. **Validates the generated code** for common errors
4. **Deploys to production** automatically via SSH + Docker
5. **Provides a live, working application** with SSL in 3-5 minutes

### Live Examples

See what the agent can create:
- **Project Manager:** https://project-manager.ai-alpine.ch
- **Tasks App:** https://tasks-app.ai-alpine.ch
- **Todo App:** https://todo-app.ai-alpine.ch

---

## ✨ Features

### Code Generation
- ✅ **Backend (Spring Boot 3.x + Java 21)**
  - JPA Entities with jakarta.persistence
  - Spring Data JPA Repositories
  - REST Controllers with full CRUD operations
  - PostgreSQL database integration

- ✅ **Frontend (Angular 17 + TypeScript)**
  - Standalone components
  - Reactive forms with two-way binding
  - HTTP services with Observables
  - Bootstrap 5 responsive UI
  - Routing and navigation

### Automation
- ✅ **Code Quality**
  - Automatic markdown removal from AI output
  - Code validation before deployment
  - Consistent naming conventions
  - Generic example code detection and removal

- ✅ **Deployment**
  - SSH-based deployment to any Linux server
  - Docker Compose orchestration
  - Traefik reverse proxy with automatic SSL
  - Health checks and monitoring ready

### Architecture
- ✅ **Production-Ready**
  - Environment-based configuration
  - Docker containerization
  - PostgreSQL database per app
  - Shared infrastructure (Traefik, monitoring)

---

## 📋 Prerequisites

### Development Machine
- **Java 21** (OpenJDK)
- **Maven 3.9+**
- **Python 3.11+**
- **Git**
- **SSH client**

### Production Server (e.g., Hetzner, DigitalOcean, AWS)
- **Ubuntu 24.04** (or similar Linux)
- **Docker** + **Docker Compose**
- **Traefik** (for reverse proxy + SSL)
- **SSH access** with key-based authentication
- **Wildcard DNS** or individual A records

### API Access
- **Anthropic API Key** (for Claude AI)

---

## 🛠️ Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/agent-service.git
cd agent-service
```

### 2. Configure Anthropic API

```bash
# Set API key as environment variable
export ANTHROPIC_API_KEY="sk-ant-api03-..."

# Or add to ~/.bashrc for persistence
echo 'export ANTHROPIC_API_KEY="sk-ant-api03-..."' >> ~/.bashrc
source ~/.bashrc
```

### 3. Setup Python Environment

```bash
cd python
python3 -m venv venv
source venv/bin/activate
pip install anthropic==0.40.0
deactivate
cd ..
```

### 4. Configure Application

Edit `src/main/resources/application.yml`:

```yaml
anthropic:
  api-key: ${ANTHROPIC_API_KEY}

python:
  script-path: ${user.home}/IdeaProjects/agent-service/python
  venv-path: ${user.home}/IdeaProjects/agent-service/python/venv

template:
  repo-path: ${user.home}/IdeaProjects/spring-angular-hetzner-template

output:
  apps-path: ${user.home}/IdeaProjects/generated-apps

deployment:
  server:
    host: ${DEPLOYMENT_SERVER_HOST:your-server-ip}
    user: ${DEPLOYMENT_SERVER_USER:root}
    key-path: /home/user/.ssh/agent_deploy_rsa  # Absolute path!
    target-dir: /opt/apps

server:
  port: 8081
```

### 5. Setup Template Repository

Clone the base template:

```bash
cd ~/IdeaProjects
git clone https://github.com/your-org/spring-angular-hetzner-template.git
```

The template should contain:
- Minimal `docker-compose.prod.yml` (backend, frontend, postgres only)
- Angular 17 project structure
- Spring Boot 3.x backend structure
- Dockerfiles for both

### 6. Setup SSH Key for Deployment

Generate a dedicated deployment key:

```bash
# Create RSA key in PEM format (required for JSch)
ssh-keygen -t rsa -b 4096 -m PEM -f ~/.ssh/agent_deploy_rsa -N ""

# Copy public key to server
ssh-copy-id -i ~/.ssh/agent_deploy_rsa.pub root@your-server-ip

# Test connection
ssh -i ~/.ssh/agent_deploy_rsa root@your-server-ip "echo 'SSH OK'"
```

**Important:** Key must be:
- RSA format (not ED25519)
- PEM format (not OpenSSH)
- No passphrase (for automation)

### 7. Setup Production Server

On your production server:

```bash
# Install Docker + Docker Compose
curl -fsSL https://get.docker.com | sh
apt-get install docker-compose-plugin -y

# Create app directory
mkdir -p /opt/apps

# Setup Traefik (if not already running)
# See: https://doc.traefik.io/traefik/getting-started/quick-start/

# Create Docker network for apps
docker network create spring-angular-app_app-network
```

### 8. Build and Run

```bash
cd ~/IdeaProjects/agent-service

# Build
mvn clean install -DskipTests

# Run
mvn spring-boot:run
```

The agent service will start on `http://localhost:8081`

---

## 📖 Usage

### API Endpoint

**POST** `/api/agent/generate`

**Content-Type:** `application/json`

### Request Format

```json
{
  "appName": "my-app",
  "description": "Brief description of the app",
  "entities": [
    {
      "name": "EntityName",
      "description": "What this entity represents",
      "fields": [
        {
          "name": "fieldName",
          "type": "String|Integer|Long|Boolean|LocalDate|LocalDateTime|Double|Float",
          "required": true|false
        }
      ]
    }
  ]
}
```

### Example: Blog Application

```bash
curl -X POST http://localhost:8081/api/agent/generate \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "blog",
    "description": "Simple blog application",
    "entities": [
      {
        "name": "Post",
        "description": "A blog post",
        "fields": [
          {"name": "title", "type": "String", "required": true},
          {"name": "content", "type": "String", "required": true},
          {"name": "author", "type": "String", "required": true},
          {"name": "publishedDate", "type": "LocalDate", "required": false}
        ]
      },
      {
        "name": "Comment",
        "description": "A comment on a post",
        "fields": [
          {"name": "text", "type": "String", "required": true},
          {"name": "authorName", "type": "String", "required": true},
          {"name": "createdAt", "type": "LocalDateTime", "required": true}
        ]
      }
    ]
  }'
```

### Response

```json
{
  "message": "✅ App 'blog' created and deployed!\n✅ Deployed successfully: https://blog.your-domain.com"
}
```

### What Gets Generated

For each entity, the agent creates:

**Backend:**
- `Entity.java` - JPA Entity with fields
- `EntityRepository.java` - Spring Data JPA Repository
- `EntityController.java` - REST Controller with CRUD endpoints

**Frontend:**
- `entity.component.ts` - Angular component with CRUD logic
- `entity.component.html` - Bootstrap 5 UI template
- `entity.service.ts` - HTTP service for API calls

**Infrastructure:**
- `app.routes.ts` - Angular routing configuration
- `home.component` - Landing page with navigation
- `nav.component` - Navigation bar
- `.env` - Environment configuration
- Dockerfiles and docker-compose.prod.yml

### Generated API Endpoints

For entity "Post":

```
GET    /api/posts          # Get all posts
GET    /api/posts/{id}     # Get post by ID
POST   /api/posts          # Create new post
PUT    /api/posts/{id}     # Update post
DELETE /api/posts/{id}     # Delete post
```

---

## 🏗️ Architecture

### Component Overview

```
┌─────────────────────────────────────────────────┐
│                  User / API Client               │
└───────────────────┬─────────────────────────────┘
                    │ POST /api/agent/generate
                    │ (JSON Requirements)
                    ▼
┌─────────────────────────────────────────────────┐
│           AgentController (REST API)             │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│          ProjectGenerator (Orchestrator)         │
│  ┌──────────────────────────────────────────┐  │
│  │ 1. Clone Template                         │  │
│  │ 2. Generate Backend (per entity)          │  │
│  │ 3. Generate Frontend (per entity)         │  │
│  │ 4. Cleanup Generic Files                  │  │
│  │ 5. Configure Routing                      │  │
│  │ 6. Validate Code                          │  │
│  │ 7. Initialize Git                         │  │
│  │ 8. Deploy                                 │  │
│  └──────────────────────────────────────────┘  │
└──┬──────────┬──────────┬──────────┬────────────┘
   │          │          │          │
   ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ Claude │ │  Git   │ │Validate│ │ Deploy │
│Service │ │Service │ │Service │ │Service │
└────┬───┘ └────────┘ └────────┘ └───┬────┘
     │                                 │
     ▼                                 ▼
┌─────────────┐                  ┌─────────────┐
│   Python    │                  │  SSH + Git  │
│Claude Client│                  │   + Docker  │
└─────────────┘                  └──────┬──────┘
                                        │
                                        ▼
                              ┌──────────────────┐
                              │ Production Server│
                              │  - Docker        │
                              │  - Traefik       │
                              │  - PostgreSQL    │
                              └──────────────────┘
```

### Technology Stack

**Agent Service:**
- Spring Boot 3.2.1 (Java 21)
- Anthropic Claude API (via Python)
- JSch 0.2.20 (SSH automation)
- JGit 6.10.0 (Git operations)

**Generated Applications:**
- Backend: Spring Boot 3.x + PostgreSQL 16
- Frontend: Angular 17 + TypeScript + Bootstrap 5
- Deployment: Docker + Traefik + Let's Encrypt

---

## 🧪 Testing

### Health Check

```bash
curl http://localhost:8081/api/agent/health
# Response: "Agent Service is running!"
```

### Test Claude Integration

```bash
curl -X POST http://localhost:8081/api/agent/test \
  -H "Content-Type: text/plain" \
  -d "Generate a simple Java Hello World class"
```

### Test Git Operations

```bash
curl -X POST "http://localhost:8081/api/agent/test-git?appName=test-app-1"
# Creates: ~/IdeaProjects/generated-apps/test-app-1 with template + git repo
```

### Generate Test Application

```bash
curl -X POST http://localhost:8081/api/agent/generate \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "test-notes",
    "description": "Simple notes app",
    "entities": [{
      "name": "Note",
      "description": "A note",
      "fields": [
        {"name": "title", "type": "String", "required": true},
        {"name": "content", "type": "String", "required": true}
      ]
    }]
  }'
```

After 3-5 minutes, check:
- Backend API: `https://test-notes.your-domain.com/api/notes`
- Frontend: `https://test-notes.your-domain.com`

---

## 📁 Project Structure

```
agent-service/
├── src/main/java/com/alpine/agent/
│   ├── controller/
│   │   └── AgentController.java        # REST API endpoints
│   ├── model/
│   │   ├── AppRequirements.java        # Request DTO
│   │   ├── EntitySpec.java             # Entity specification
│   │   └── FieldSpec.java              # Field specification
│   └── service/
│       ├── ProjectGenerator.java       # Main orchestrator
│       ├── ClaudeService.java          # AI code generation
│       ├── CodeCleaningService.java    # Post-processing
│       ├── GitService.java             # Git operations
│       ├── DeploymentService.java      # SSH + Docker
│       └── ValidationService.java      # Code validation
├── src/main/resources/
│   └── application.yml                 # Configuration
├── python/
│   ├── venv/                           # Python virtual environment
│   └── claude_client.py                # Claude API wrapper
├── pom.xml                             # Maven dependencies
└── README.md
```

---

## 🔧 Configuration Reference

### Application Properties

| Property | Description | Default |
|----------|-------------|---------|
| `anthropic.api-key` | Anthropic API key | `${ANTHROPIC_API_KEY}` |
| `python.script-path` | Path to Python scripts | `${user.home}/IdeaProjects/agent-service/python` |
| `python.venv-path` | Path to Python venv | `${user.home}/IdeaProjects/agent-service/python/venv` |
| `template.repo-path` | Path to template repo | `${user.home}/IdeaProjects/spring-angular-hetzner-template` |
| `output.apps-path` | Output directory for generated apps | `${user.home}/IdeaProjects/generated-apps` |
| `deployment.server.host` | Server IP or hostname | `${DEPLOYMENT_SERVER_HOST}` |
| `deployment.server.user` | SSH user | `${DEPLOYMENT_SERVER_USER:root}` |
| `deployment.server.key-path` | SSH private key path | `/home/user/.ssh/agent_deploy_rsa` |
| `deployment.server.target-dir` | Target directory on server | `/opt/apps` |
| `server.port` | Agent service port | `8081` |

### Environment Variables

```bash
# Required
export ANTHROPIC_API_KEY="sk-ant-api03-..."

# Optional (can be set in application.yml)
export DEPLOYMENT_SERVER_HOST="your-server-ip"
export DEPLOYMENT_SERVER_USER="root"
```

---

## 🐛 Troubleshooting

### Common Issues

#### 1. "Auth fail" during SSH deployment

**Symptom:** Deployment fails with "Auth fail" error

**Causes:**
- SSH key not in PEM format
- Key has passphrase
- Public key not in server's authorized_keys
- Wrong username

**Solutions:**
```bash
# 1. Verify key format
head -1 ~/.ssh/agent_deploy_rsa
# Should show: -----BEGIN RSA PRIVATE KEY-----

# 2. Test SSH manually
ssh -i ~/.ssh/agent_deploy_rsa root@your-server "echo OK"

# 3. Recreate key in correct format
ssh-keygen -t rsa -b 4096 -m PEM -f ~/.ssh/agent_deploy_rsa -N ""
ssh-copy-id -i ~/.ssh/agent_deploy_rsa.pub root@your-server
```

#### 2. Container unhealthy / Traefik 404

**Symptom:** App deployed but returns 404

**Causes:**
- Health check failing
- Container not in correct Docker network
- Traefik labels incorrect

**Solutions:**
```bash
# 1. Check container health
ssh your-server "cd /opt/apps/your-app && docker compose ps"

# 2. Check health manually
ssh your-server "docker exec your-app-frontend curl -f http://localhost:80/health"

# 3. Verify network
ssh your-server "docker network inspect spring-angular-app_app-network"

# 4. Check Traefik logs
ssh your-server "docker logs traefik 2>&1 | grep your-app"

# 5. Force recreation
ssh your-server "cd /opt/apps/your-app && docker compose down && docker compose up -d --force-recreate"
```

#### 3. Frontend build fails with "Property does not exist"

**Symptom:** TypeScript compilation errors during Docker build

**Cause:** Component and template have inconsistent method/property names

**Solution:** This should be caught by validation. If it occurs:
1. Check generated files in `~/IdeaProjects/generated-apps/your-app/frontend/src/app/components/`
2. Manually fix inconsistencies
3. Report as bug (shouldn't happen with current implementation)

#### 4. Backend compilation fails

**Symptom:** Maven build fails during Docker build

**Common causes:**
- Wrong imports (javax.persistence instead of jakarta.persistence)
- Missing getters/setters
- Wrong method names (isCompleted() instead of getCompleted())

**Solution:**
1. Check validation output - should catch these
2. Review generated files in `~/IdeaProjects/generated-apps/your-app/backend/src/main/java/`
3. Manually fix and rebuild:
```bash
cd ~/IdeaProjects/generated-apps/your-app
# Fix issues
cd backend && mvn clean package
```

#### 5. Claude generates "item-list" or generic examples

**Symptom:** Validation fails with "Generic example component detected"

**Cause:** Claude occasionally generates example code despite strict prompts

**Solution:**
- Cleanup service should automatically delete these files
- If they persist, manually delete:
```bash
rm -rf ~/IdeaProjects/generated-apps/your-app/frontend/src/app/components/item*
rm -rf ~/IdeaProjects/generated-apps/your-app/frontend/src/app/services/item*
```
- Then re-run validation

---

## 📊 Performance

### Generation Times

| Phase | Duration |
|-------|----------|
| Template clone | 2-3 seconds |
| Backend generation (per entity) | 10-15 seconds |
| Frontend generation (per entity) | 15-20 seconds |
| Cleanup + routing | 1-2 seconds |
| Validation | 2-3 seconds |
| Git operations | 1-2 seconds |
| SSH copy to server | 5-10 seconds |
| Docker build | 120-150 seconds |
| Container startup | 30-40 seconds |
| **Total (2-entity app)** | **3-5 minutes** |

### Resource Usage

**Development Machine:**
- Spring Boot Agent: ~300 MB RAM
- Maven build: ~500 MB RAM
- Total: ~1 GB RAM recommended

**Production Server (per app):**
- Spring Boot backend: ~200-300 MB RAM
- PostgreSQL: ~50-100 MB RAM
- nginx frontend: ~10-20 MB RAM
- Total: ~300-500 MB RAM per app

**Recommended Server:**
- 2 vCPU
- 4 GB RAM
- 40 GB SSD
- Can host 5-10 small applications

---

## 🔒 Security

### Current Implementation

✅ **Implemented:**
- SSH key-based authentication
- Docker network isolation
- Environment variables for secrets
- Traefik SSL/TLS (Let's Encrypt)
- Health checks

⚠️ **Not Implemented (Production Gaps):**
- API authentication (currently public)
- CORS configuration
- Rate limiting
- Input validation/sanitization
- Database backups
- Audit logging

### Production Recommendations

Before deploying to production:

1. **Add API Authentication**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
            )
            .oauth2Login()
            .build();
    }
}
```

2. **Configure CORS**
```java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                .allowedOrigins("https://your-domain.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
        }
    };
}
```

3. **Add Input Validation**
```java
@PostMapping("/api/projects")
public ResponseEntity<Project> create(@Valid @RequestBody Project project) {
    // @Valid triggers validation
}
```

4. **Setup Database Backups**
```bash
# Cron job for PostgreSQL backup
0 2 * * * docker exec postgres-container pg_dump -U user dbname > /backups/$(date +\%Y\%m\%d).sql
```

---

## 🤝 Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup

```bash
# Clone your fork
git clone https://github.com/your-username/agent-service.git
cd agent-service

# Create feature branch
git checkout -b feature/my-feature

# Make changes, test thoroughly
mvn test

# Commit and push
git commit -am "Add my feature"
git push origin feature/my-feature
```

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Anthropic** for Claude AI API
- **Spring Team** for Spring Boot framework
- **Angular Team** for Angular framework
- **Traefik** for reverse proxy and SSL automation
- **Community** for Docker, PostgreSQL, and all open-source tools

---

## 📧 Support

- **Issues:** https://github.com/your-org/agent-service/issues
- **Discussions:** https://github.com/your-org/agent-service/discussions
- **Email:** support@your-domain.com

---

## 🗺️ Roadmap

### v1.1 (Next Release)
- [ ] Relationship support (OneToMany, ManyToMany)
- [ ] Web UI for requirements input
- [ ] Real-time generation progress
- [ ] Better error messages

### v1.2
- [ ] Authentication templates (OAuth2, JWT)
- [ ] Test generation (JUnit, Cypress)
- [ ] File upload handling
- [ ] Email notifications

### v2.0
- [ ] Multi-cloud deployment (AWS, GCP)
- [ ] Kubernetes manifests
- [ ] GraphQL API option
- [ ] Microservices architecture support

---

## 📚 Additional Resources

- [Lessons Learned](LESSONS_LEARNED.md) - Detailed technical insights
- [API Documentation](docs/API.md) - Complete API reference
- [Deployment Guide](docs/DEPLOYMENT.md) - Production deployment guide
- [Contributing Guidelines](CONTRIBUTING.md) - How to contribute

---

**Made with ❤️ and lots of ☕**

*Generate apps, not boilerplate.*
