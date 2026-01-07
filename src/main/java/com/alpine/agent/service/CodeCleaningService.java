package com.alpine.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class CodeCleaningService {

    private static final Logger log = LoggerFactory.getLogger(CodeCleaningService.class);

    // Patterns für Markdown Code Blocks
    private static final Pattern MARKDOWN_JAVA = Pattern.compile("```java\\s*");
    private static final Pattern MARKDOWN_TYPESCRIPT = Pattern.compile("```typescript\\s*");
    private static final Pattern MARKDOWN_GENERIC = Pattern.compile("```\\s*");
    private static final Pattern MARKDOWN_END = Pattern.compile("\\s*```$");

    /**
     * Clean code from Claude API response
     * Removes markdown code blocks and other artifacts
     */
    public String cleanCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return rawCode;
        }

        log.debug("Cleaning code, length: {}", rawCode.length());

        String cleaned = rawCode;

        // Remove markdown code block markers
        cleaned = MARKDOWN_JAVA.matcher(cleaned).replaceAll("");
        cleaned = MARKDOWN_TYPESCRIPT.matcher(cleaned).replaceAll("");
        cleaned = MARKDOWN_GENERIC.matcher(cleaned).replaceAll("");
        cleaned = MARKDOWN_END.matcher(cleaned).replaceAll("");

        // Remove leading/trailing whitespace
        cleaned = cleaned.trim();

        // Remove common Claude preambles
        cleaned = removePreamble(cleaned);

        log.debug("Cleaned code, new length: {}", cleaned.length());

        return cleaned;
    }

    /**
     * Remove common Claude response preambles
     */
    private String removePreamble(String code) {
        // Common patterns Claude uses
        String[] preambles = {
                "Here's the code:",
                "Here is the code:",
                "Here's the Java code:",
                "Here is the Java code:",
                "Sure, here's",
                "Certainly,",
        };

        for (String preamble : preambles) {
            if (code.startsWith(preamble)) {
                code = code.substring(preamble.length()).trim();
                // Remove any following colon
                if (code.startsWith(":")) {
                    code = code.substring(1).trim();
                }
            }
        }

        return code;
    }

    /**
     * Extract only the code part from a response that might contain explanation
     */
    public String extractCode(String response) {
        if (response == null) {
            return null;
        }

        // Try to find code block
        Pattern codeBlockPattern = Pattern.compile(
                "```(?:java|typescript)?\\s*([\\s\\S]*?)```",
                Pattern.MULTILINE
        );

        var matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // No code block found, return cleaned response
        return cleanCode(response);
    }

    /**
     * Validate that cleaned code looks like valid Java
     */
    public boolean isValidJavaCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        // Basic checks
        return code.contains("package ")
                && (code.contains("class ") || code.contains("interface "))
                && code.contains("{")
                && code.contains("}");
    }
}