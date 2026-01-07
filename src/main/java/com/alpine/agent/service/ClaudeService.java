package com.alpine.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    @Value("${python.script-path}")
    private String pythonScriptPath;

    @Value("${python.venv-path}")
    private String venvPath;

    @Autowired
    private CodeCleaningService codeCleaningService;  // ← Neu!

    public String generateCode(String prompt) {
        try {
            log.info("Calling Claude API with prompt: {}", prompt);

            ProcessBuilder pb = new ProcessBuilder(
                    venvPath + "/bin/python3",
                    pythonScriptPath + "/claude_client.py",
                    prompt
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String result = reader.lines()
                    .collect(Collectors.joining("\n"));

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script failed with exit code: " + exitCode);
            }

            log.info("Claude API response received");

            // ← Neu: Code cleaning!
            String cleanedCode = codeCleaningService.cleanCode(result);

            // Validate
            if (!codeCleaningService.isValidJavaCode(cleanedCode)) {
                log.warn("Generated code might not be valid Java!");
            }

            return cleanedCode;

        } catch (Exception e) {
            log.error("Error calling Claude API", e);
            throw new RuntimeException("Claude API call failed", e);
        }
    }
}