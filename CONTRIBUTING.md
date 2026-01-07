# Contributing to AI Agent Service

Thank you for your interest in contributing! This document provides guidelines for contributing to the project.

---

## 🤝 How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with:

1. **Clear title** describing the problem
2. **Steps to reproduce** the issue
3. **Expected behavior** vs actual behavior
4. **Environment details:**
   - OS (Linux/macOS/Windows)
   - Java version (`java -version`)
   - Maven version (`mvn -version`)
   - Python version (`python --version`)
5. **Logs** or error messages
6. **Screenshots** if applicable

**Example:**
```markdown
Title: Frontend generation fails for Boolean fields

Steps to reproduce:
1. Create entity with Boolean field
2. Generate app via API
3. Frontend build fails

Expected: Component compiles
Actual: TypeScript error about isCompleted()

Environment:
- Ubuntu 24.04
- Java 21.0.1
- Maven 3.9.6
- Python 3.11.7

Error:
Property 'isCompleted' does not exist on type 'TaskComponent'
```

---

### Suggesting Features

For feature requests, create an issue with:

1. **Use case** - What problem does this solve?
2. **Proposed solution** - How should it work?
3. **Alternatives** - Other approaches considered?
4. **Additional context** - Screenshots, examples, etc.

---

### Pull Requests

We welcome pull requests! Please follow these steps:

#### 1. Fork & Clone

```bash
# Fork on GitHub, then:
git clone https://github.com/YOUR-USERNAME/agent-service.git
cd agent-service
```

#### 2. Create Feature Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-fix
```

**Branch naming:**
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring
- `test/` - Test additions/updates

#### 3. Make Changes

**Code Style:**
- Follow existing code style
- Use meaningful variable names
- Add comments for complex logic
- Keep methods focused (single responsibility)

**Java:**
```java
// Good
private String generateEntity(EntitySpec entity) {
    log.info("Generating entity: {}", entity.getName());
    String prompt = buildEntityPrompt(entity);
    return claudeService.generateCode(prompt);
}

// Avoid
private String gen(EntitySpec e) {
    return cs.gen(buildPrompt(e)); // No logging, unclear
}
```

#### 4. Test Thoroughly

```bash
# Run unit tests
mvn test

# Run integration test
mvn verify

# Test manually with real generation
curl -X POST http://localhost:8081/api/agent/generate \
  -H "Content-Type: application/json" \
  -d @test-requirements.json
```

#### 5. Commit

**Commit message format:**
```
<type>: <short description>

<optional longer description>

<optional footer>
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `style:` - Formatting (no code change)
- `refactor:` - Code restructuring
- `test:` - Adding tests
- `chore:` - Maintenance

**Examples:**
```bash
git commit -m "feat: add support for LocalDateTime fields"

git commit -m "fix: resolve SSH authentication with ED25519 keys

- Updated JSch to version 0.2.20
- Added PEM format key generation docs
- Updated deployment guide"

git commit -m "docs: improve troubleshooting section in README"
```

#### 6. Push & Create PR

```bash
git push origin feature/your-feature-name
```

Then create Pull Request on GitHub with:

- **Title:** Clear, descriptive
- **Description:**
  - What does this PR do?
  - Why is this change needed?
  - How was it tested?
  - Screenshots (if UI changes)
- **Related Issues:** Link to issue number (#123)

**PR Template:**
```markdown
## Description
Brief description of changes

## Motivation
Why is this change needed?

## Changes
- Added X
- Modified Y
- Fixed Z

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manually tested generation
- [ ] Documentation updated

## Screenshots (if applicable)

## Related Issues
Fixes #123
```

---

## 📋 Development Setup

### Prerequisites

- Java 21
- Maven 3.9+
- Python 3.11+
- Git
- Anthropic API key

### Initial Setup

```bash
# 1. Clone
git clone https://github.com/your-username/agent-service.git
cd agent-service

# 2. Setup Python
cd python
python3 -m venv venv
source venv/bin/activate
pip install anthropic==0.40.0
deactivate
cd ..

# 3. Configure
export ANTHROPIC_API_KEY="your-key-here"

# 4. Build
mvn clean install -DskipTests

# 5. Run
mvn spring-boot:run
```

---

## 🧪 Testing Guidelines

### Unit Tests

Place tests in `src/test/java`:

```java
@SpringBootTest
class ProjectGeneratorTest {
    
    @Autowired
    private ProjectGenerator generator;
    
    @Test
    void testEntityGeneration() {
        EntitySpec entity = new EntitySpec();
        entity.setName("TestEntity");
        // ... test logic
        assertNotNull(result);
    }
}
```

### Integration Tests

Test complete workflows:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AgentControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testFullAppGeneration() {
        AppRequirements req = new AppRequirements();
        // ... setup
        
        ResponseEntity<String> response = restTemplate
            .postForEntity("/api/agent/generate", req, String.class);
            
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

---

## 📝 Documentation

### Code Documentation

Add JavaDoc for public methods:

```java
/**
 * Generates a Spring Boot entity class from specification.
 * 
 * @param appPath Path to the application directory
 * @param entity Entity specification with name and fields
 * @throws Exception if generation or file writing fails
 */
private void generateEntity(String appPath, EntitySpec entity) throws Exception {
    // ...
}
```

### README Updates

When adding features, update:
- Feature list in README
- Usage examples
- Configuration options
- Troubleshooting section

---

## 🎯 Areas for Contribution

### High Priority

- [ ] Add relationship support (OneToMany, ManyToMany)
- [ ] Improve validation error messages
- [ ] Add authentication templates
- [ ] Create web UI for requirements input
- [ ] Add test generation

### Medium Priority

- [ ] Support for more field types (BigDecimal, UUID, etc.)
- [ ] Custom templates per app type
- [ ] Rollback mechanism for failed deployments
- [ ] Health monitoring dashboard
- [ ] API documentation generation

### Low Priority / Nice to Have

- [ ] GraphQL API option
- [ ] Multi-cloud deployment (AWS, GCP)
- [ ] Kubernetes manifest generation
- [ ] Email notification on deployment
- [ ] Slack/Discord integration

---

## ❓ Questions?

- **GitHub Discussions:** For general questions
- **GitHub Issues:** For bugs and features
- **Email:** support@your-domain.com

---

## 📜 Code of Conduct

### Our Standards

- Be respectful and inclusive
- Welcome newcomers
- Focus on constructive feedback
- Accept criticism gracefully
- Prioritize community wellbeing

### Unacceptable Behavior

- Harassment or discrimination
- Trolling or insulting comments
- Personal or political attacks
- Publishing others' private information
- Unprofessional conduct

### Enforcement

Violations may result in:
1. Warning
2. Temporary ban
3. Permanent ban

Report issues to: conduct@your-domain.com

---

## 🏆 Recognition

Contributors will be:
- Listed in README.md
- Mentioned in release notes
- Credited in documentation

Thank you for contributing! 🎉
