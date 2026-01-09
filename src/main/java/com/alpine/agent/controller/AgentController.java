package com.alpine.agent.controller;

import com.alpine.agent.model.AppRequirements;
import com.alpine.agent.service.ProjectGenerator;
import com.alpine.agent.service.RequirementsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for AI Agent Service with Hybrid Natural Language Support
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    @Autowired
    private ProjectGenerator projectGenerator;

    @Autowired
    private RequirementsParser requirementsParser;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Agent Service is running!");
    }

    /**
     * Generate app from structured JSON requirements (original method)
     * 
     * Example:
     * POST /api/agent/generate
     * {
     *   "appName": "blog",
     *   "description": "Blog application",
     *   "entities": [...]
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<String> generateApp(@RequestBody AppRequirements requirements) {
        try {
            log.info("Received request to generate app: {}", requirements.getAppName());
            String result = projectGenerator.generateApp(requirements);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating app", e);
            return ResponseEntity.internalServerError()
                    .body("Error generating app: " + e.getMessage());
        }
    }

    /**
     * Generate app from natural language - HYBRID APPROACH
     * - Tries one-shot parsing if input is clear
     * - Falls back to conversation if unclear
     * 
     * Example (clear input):
     * POST /api/agent/generate-from-text
     * Body: "Create a blog with posts (title, content, author, published) and comments (text, authorName)"
     * 
     * Response:
     * {
     *   "complete": true,
     *   "message": "✅ App 'blog' created and deployed!",
     *   "appUrl": "https://blog.ai-alpine.ch"
     * }
     * 
     * Example (unclear input):
     * POST /api/agent/generate-from-text
     * Body: "I need a CRM system"
     * 
     * Response:
     * {
     *   "complete": false,
     *   "question": "What entities do you need in your CRM? For example: Customers, Contacts, Deals?",
     *   "sessionId": "abc-123-def"
     * }
     */
    @PostMapping("/generate-from-text")
    public ResponseEntity<Map<String, Object>> generateFromText(@RequestBody String naturalLanguageInput) {
        try {
            log.info("Received natural language request: {}", naturalLanguageInput);

            // Step 1: Parse (hybrid approach)
            RequirementsParser.ParseResult parseResult = requirementsParser.parse(naturalLanguageInput);

            Map<String, Object> response = new HashMap<>();

            if (parseResult.isComplete()) {
                // Input was clear enough - generate directly
                log.info("Input is complete, generating app...");
                
                AppRequirements requirements = parseResult.getRequirements();
                String result = projectGenerator.generateApp(requirements);
                
                response.put("complete", true);
                response.put("message", result);
                response.put("appName", requirements.getAppName());
                response.put("appUrl", "https://" + requirements.getAppName() + ".ai-alpine.ch");
                
            } else {
                // Input unclear - start conversation
                log.info("Input incomplete, starting conversation...");
                
                response.put("complete", false);
                response.put("question", parseResult.getQuestion());
                response.put("sessionId", parseResult.getSessionId());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing natural language input", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Continue conversation for unclear requirements
     * 
     * Example:
     * POST /api/agent/continue-conversation
     * {
     *   "sessionId": "abc-123-def",
     *   "message": "I need Customers with name, email, phone and Deals with title, amount, stage"
     * }
     * 
     * Response (if still need more info):
     * {
     *   "complete": false,
     *   "question": "Should a Deal be connected to a Customer?",
     *   "sessionId": "abc-123-def"
     * }
     * 
     * Response (if ready):
     * {
     *   "complete": true,
     *   "message": "✅ App 'crm' created and deployed!",
     *   "appUrl": "https://crm.ai-alpine.ch"
     * }
     */
    @PostMapping("/continue-conversation")
    public ResponseEntity<Map<String, Object>> continueConversation(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String userMessage = request.get("message");

            if (sessionId == null || userMessage == null) {
                throw new Exception("sessionId and message are required");
            }

            log.info("Continue conversation {}: {}", sessionId, userMessage);

            // Continue parsing conversation
            RequirementsParser.ParseResult parseResult = requirementsParser.continueConversation(sessionId, userMessage);

            Map<String, Object> response = new HashMap<>();

            if (parseResult.isComplete()) {
                // Conversation complete - generate app
                log.info("Conversation complete, generating app...");
                
                AppRequirements requirements = parseResult.getRequirements();
                String result = projectGenerator.generateApp(requirements);
                
                response.put("complete", true);
                response.put("message", result);
                response.put("appName", requirements.getAppName());
                response.put("appUrl", "https://" + requirements.getAppName() + ".ai-alpine.ch");
                
            } else {
                // Need more info - continue conversation
                log.info("Asking next question...");
                
                response.put("complete", false);
                response.put("question", parseResult.getQuestion());
                response.put("sessionId", sessionId);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error continuing conversation", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Parse natural language to JSON (preview only, don't generate)
     * Useful for testing what Claude extracts
     * 
     * Example:
     * POST /api/agent/parse-preview
     * Body: "Create a task manager with tasks (title, description, priority, completed)"
     * 
     * Response:
     * {
     *   "appName": "task-manager",
     *   "description": "Task management application",
     *   "entities": [
     *     {
     *       "name": "Task",
     *       "description": "A task",
     *       "fields": [...]
     *     }
     *   ]
     * }
     */
    @PostMapping("/parse-preview")
    public ResponseEntity<Map<String, Object>> parsePreview(@RequestBody String naturalLanguageInput) {
        try {
            log.info("Parse preview request: {}", naturalLanguageInput);

            RequirementsParser.ParseResult parseResult = requirementsParser.parse(naturalLanguageInput);

            Map<String, Object> response = new HashMap<>();

            if (parseResult.isComplete()) {
                response.put("complete", true);
                response.put("requirements", parseResult.getRequirements());
            } else {
                response.put("complete", false);
                response.put("question", parseResult.getQuestion());
                response.put("sessionId", parseResult.getSessionId());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error parsing preview", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
