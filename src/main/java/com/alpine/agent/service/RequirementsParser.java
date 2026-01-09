package com.alpine.agent.service;

import com.alpine.agent.model.AppRequirements;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intelligent hybrid requirements parser
 * - Tries one-shot parsing first
 * - Falls back to conversational mode if unclear
 */
@Service
public class RequirementsParser {

    private static final Logger log = LoggerFactory.getLogger(RequirementsParser.class);

    @Autowired
    private ClaudeService claudeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Session storage for conversational mode
    private final Map<String, ConversationSession> sessions = new HashMap<>();

    /**
     * Result of parsing attempt
     */
    public static class ParseResult {
        private boolean complete;
        private AppRequirements requirements;
        private String question;
        private String sessionId;

        public ParseResult(boolean complete, AppRequirements requirements, String question, String sessionId) {
            this.complete = complete;
            this.requirements = requirements;
            this.question = question;
            this.sessionId = sessionId;
        }

        public boolean isComplete() { return complete; }
        public AppRequirements getRequirements() { return requirements; }
        public String getQuestion() { return question; }
        public String getSessionId() { return sessionId; }
    }

    /**
     * Conversation session
     */
    private static class ConversationSession {
        String sessionId;
        String initialInput;
        StringBuilder conversationHistory;
        AppRequirements partialRequirements;

        ConversationSession(String sessionId, String initialInput) {
            this.sessionId = sessionId;
            this.initialInput = initialInput;
            this.conversationHistory = new StringBuilder();
            this.partialRequirements = new AppRequirements();
        }
    }

    /**
     * Parse natural language - intelligent hybrid approach
     */
    public ParseResult parse(String naturalLanguageInput) throws Exception {
        log.info("Analyzing input: {}", naturalLanguageInput);

        // Step 1: Ask Claude to analyze if input is complete
        String analysisPrompt = buildAnalysisPrompt(naturalLanguageInput);
        String analysisResponse = claudeService.generateCode(analysisPrompt);

        log.info("Analysis response: {}", analysisResponse);

        // Check if Claude thinks it's complete
        if (analysisResponse.toLowerCase().contains("\"complete\": true")) {
            // Try to parse directly
            try {
                AppRequirements requirements = parseDirectly(naturalLanguageInput);
                return new ParseResult(true, requirements, null, null);
            } catch (Exception e) {
                log.warn("Direct parsing failed, falling back to conversation: {}", e.getMessage());
            }
        }

        // Not complete - start conversation
        String sessionId = UUID.randomUUID().toString();
        ConversationSession session = new ConversationSession(sessionId, naturalLanguageInput);
        sessions.put(sessionId, session);

        // Generate first question
        String question = generateFirstQuestion(naturalLanguageInput);

        return new ParseResult(false, null, question, sessionId);
    }

    /**
     * Continue conversation
     */
    public ParseResult continueConversation(String sessionId, String userResponse) throws Exception {
        ConversationSession session = sessions.get(sessionId);
        if (session == null) {
            throw new Exception("Session not found: " + sessionId);
        }

        // Add to conversation history
        session.conversationHistory.append("User: ").append(userResponse).append("\n");

        // Ask Claude to either extract more info or ask next question
        String continuePrompt = buildContinuePrompt(session, userResponse);
        String claudeResponse = claudeService.generateCode(continuePrompt);

        log.info("Continue response: {}", claudeResponse);

        // Check if we have enough information
        if (claudeResponse.contains("\"status\": \"complete\"")) {
            // Parse final requirements
            AppRequirements requirements = extractRequirements(session);
            sessions.remove(sessionId);
            return new ParseResult(true, requirements, null, null);
        } else {
            // Extract next question
            String nextQuestion = extractQuestion(claudeResponse);
            session.conversationHistory.append("Assistant: ").append(nextQuestion).append("\n");
            return new ParseResult(false, null, nextQuestion, sessionId);
        }
    }

    /**
     * Build analysis prompt - is the input complete?
     */
    private String buildAnalysisPrompt(String input) {
        return String.format("""
            You are an expert software architect analyzing requirements.
            
            USER INPUT:
            %s
            
            TASK:
            Analyze if this input contains enough information to generate a complete application.
            
            Consider complete if it specifies:
            1. Clear app purpose/domain
            2. Main entities (at least 1-2)
            3. Key fields for entities (explicit or clearly implied)
            
            Consider incomplete if:
            1. Too vague ("make an app")
            2. Missing entity details
            3. Unclear relationships
            4. Ambiguous requirements
            
            Respond with JSON:
            {
              "complete": true/false,
              "confidence": 0-100,
              "reason": "explanation",
              "missingInfo": ["what's unclear"]
            }
            
            Only JSON, no explanation.
            """, input);
    }

    /**
     * Parse directly when input is complete
     */
    private AppRequirements parseDirectly(String input) throws Exception {
        String prompt = String.format("""
            You are an expert software architect designing applications.
            
            USER REQUEST:
            %s
            
            TASK:
            Design a complete, production-ready application based on this request.
            
            CRITICAL RULE - ONLY GENERATE REQUESTED ENTITIES:
            - Generate ONLY the entities explicitly mentioned in the user request
            - DO NOT add extra entities like Orders, Alerts, Movements, Suppliers, etc.
            - DO NOT be "helpful" by adding entities you think might be needed
            - If the user says "Products and Categories", generate ONLY Products and Categories
            - Stick strictly to what was requested
            
            Use your expertise to:
            - Choose appropriate field types for the requested entities
            - Apply naming conventions (PascalCase entities, camelCase fields)
            - Include sensible required/optional settings
            - Add helpful descriptions
            
            FIELD TYPES AVAILABLE:
            String, Integer, Long, Boolean, Double, Float, LocalDate, LocalDateTime
            
            BEST PRACTICES:
            - IDs auto-generated (don't include)
            - Names/titles usually required
            - Descriptions usually optional
            - Booleans should have clear defaults
            - Use LocalDate for dates without time
            - Use LocalDateTime for timestamps
            
            Return ONLY valid JSON:
            {
              "appName": "kebab-case-name",
              "description": "Brief description",
              "entities": [
                {
                  "name": "EntityName",
                  "description": "What it represents",
                  "fields": [
                    {"name": "fieldName", "type": "String", "required": true}
                  ]
                }
              ]
            }
            
            NO markdown, NO explanations, ONLY JSON.
            """, input);

        String response = claudeService.generateCode(prompt);
        String cleanJson = cleanJsonResponse(response);
        return objectMapper.readValue(cleanJson, AppRequirements.class);
    }

    /**
     * Generate first clarifying question
     */
    private String generateFirstQuestion(String input) throws Exception {
        String prompt = String.format("""
            You are a helpful assistant gathering application requirements.
            
            USER SAID:
            %s
            
            TASK:
            This input is incomplete. Ask ONE specific, helpful question to clarify.
            
            Focus on:
            - What entities are needed (if not clear)
            - What the main purpose is (if too vague)
            - Key features they want
            
            Be conversational and helpful.
            Return ONLY the question, no JSON, no explanation.
            """, input);

        return claudeService.generateCode(prompt).trim();
    }

    /**
     * Build prompt for continuing conversation
     */
    private String buildContinuePrompt(ConversationSession session, String userResponse) {
        return String.format("""
            You are gathering requirements for an application.
            
            INITIAL REQUEST:
            %s
            
            CONVERSATION SO FAR:
            %s
            
            LATEST USER RESPONSE:
            %s
            
            TASK:
            Decide if you have enough information to generate the app, or if you need more details.
            
            If ENOUGH information:
            Respond: {"status": "complete", "summary": "brief summary of what will be built"}
            
            If NEED MORE:
            Respond: {"status": "continue", "question": "your next question"}
            
            Only ask about truly important details. Don't over-engineer.
            Typical apps need: entities, key fields, basic relationships.
            
            Return ONLY JSON.
            """,
                session.initialInput,
                session.conversationHistory.toString(),
                userResponse);
    }

    /**
     * Extract final requirements from conversation
     */
    private AppRequirements extractRequirements(ConversationSession session) throws Exception {
        String prompt = String.format("""
            You are an expert software architect.
            
            INITIAL REQUEST:
            %s
            
            CONVERSATION:
            %s
            
            TASK:
            Based on this conversation, design the complete application.
            
            Extract all entities, fields, types from the discussion.
            Apply best practices for field types and requirements.
            
            Return ONLY valid JSON:
            {
              "appName": "kebab-case-name",
              "description": "Brief description",
              "entities": [
                {
                  "name": "EntityName",
                  "description": "What it represents",
                  "fields": [
                    {"name": "fieldName", "type": "String", "required": true}
                  ]
                }
              ]
            }
            
            Field types: String, Integer, Long, Boolean, Double, Float, LocalDate, LocalDateTime
            NO markdown, ONLY JSON.
            """,
                session.initialInput,
                session.conversationHistory.toString());

        String response = claudeService.generateCode(prompt);
        String cleanJson = cleanJsonResponse(response);
        return objectMapper.readValue(cleanJson, AppRequirements.class);
    }

    /**
     * Extract question from Claude's response
     */
    private String extractQuestion(String response) {
        try {
            Map<String, Object> json = objectMapper.readValue(response, Map.class);
            return (String) json.get("question");
        } catch (Exception e) {
            // Fallback - return response as-is
            return response;
        }
    }

    /**
     * Clean JSON response
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}