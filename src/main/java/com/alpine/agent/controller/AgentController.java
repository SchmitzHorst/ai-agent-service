package com.alpine.agent.controller;

import com.alpine.agent.model.AppRequirements;
import com.alpine.agent.service.ClaudeService;
import com.alpine.agent.service.GitService;
import com.alpine.agent.service.ProjectGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ClaudeService claudeService;

    public AgentController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/test")
    public String test(@RequestBody String prompt) {
        return claudeService.generateCode(prompt);
    }

    @GetMapping("/health")
    public String health() {
        return "Agent Service is running!";
    }

    @Autowired
    private GitService gitService;

    @PostMapping("/test-git")
    public String testGit(@RequestParam String appName) {
        try {
            // 1. Clone template
            String appPath = gitService.cloneTemplate(appName);

            // 2. Write test file
            gitService.writeFile(
                    appPath,
                    "backend/src/main/java/com/example/app/TestClass.java",
                    "public class TestClass { /* Generated */ }"
            );

            // 3. Init git repo
            gitService.initGitRepo(appPath, appName);

            return "✅ App created at: " + appPath;

        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    @Autowired
    private ProjectGenerator projectGenerator;

    @PostMapping("/generate")
    public String generateApp(@RequestBody AppRequirements requirements) {
        return projectGenerator.generateApp(requirements);
    }
}