package com.alpine.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Validate generated application
     */
    public ValidationResult validateApp(String appPath) {
        log.info("Validating app at: {}", appPath);

        ValidationResult result = new ValidationResult();

        // 1. Check required files exist
        validateRequiredFiles(appPath, result);

        // 2. Validate Java files
        validateJavaFiles(appPath, result);

        // 3. Validate TypeScript files
        validateTypeScriptFiles(appPath, result);

        // 4. Check for common issues
        validateCommonIssues(appPath, result);

        log.info("Validation complete: {} errors, {} warnings",
                result.getErrors().size(),
                result.getWarnings().size()
        );

        return result;
    }

    /**
     * Check required files exist
     */
    private void validateRequiredFiles(String appPath, ValidationResult result) {
        String[] requiredFiles = {
                "backend/pom.xml",
                "backend/src/main/java",
                "backend/src/main/resources/application.properties",
                "frontend/package.json",
                "frontend/src/app",
                "docker-compose.prod.yml",
                ".env.example"
        };

        for (String file : requiredFiles) {
            Path path = Paths.get(appPath, file);
            if (!Files.exists(path)) {
                result.addError("Required file missing: " + file);
            }
        }
    }

    /**
     * Validate Java files
     */
    private void validateJavaFiles(String appPath, ValidationResult result) {
        try {
            Path javaDir = Paths.get(appPath, "backend/src/main/java");

            if (!Files.exists(javaDir)) {
                return;
            }

            Files.walk(javaDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFile -> validateJavaFile(javaFile, result));

        } catch (Exception e) {
            result.addError("Failed to validate Java files: " + e.getMessage());
        }
    }

    /**
     * Validate single Java file
     */
    private void validateJavaFile(Path javaFile, ValidationResult result) {
        try {
            String content = Files.readString(javaFile);
            String fileName = javaFile.getFileName().toString();

            // Check package declaration
            if (!content.contains("package ")) {
                result.addError(fileName + ": Missing package declaration");
            }

            // Check for markdown artifacts
            if (content.contains("```")) {
                result.addError(fileName + ": Contains markdown backticks");
            }

            // Check for class/interface declaration
            if (!content.contains("class ") && !content.contains("interface ")) {
                result.addError(fileName + ": Missing class/interface declaration");
            }

            // Check for proper braces
            long openBraces = content.chars().filter(ch -> ch == '{').count();
            long closeBraces = content.chars().filter(ch -> ch == '}').count();

            if (openBraces != closeBraces) {
                result.addError(fileName + ": Mismatched braces");
            }

            // Check imports
            if (content.contains("import javax.persistence")) {
                result.addWarning(fileName + ": Uses old javax.persistence (should be jakarta.persistence)");
            }

            // Check for TODO/FIXME
            if (content.contains("TODO") || content.contains("FIXME")) {
                result.addWarning(fileName + ": Contains TODO/FIXME comments");
            }

            // Check for methods that don't match entity fields
            if (fileName.endsWith("Controller.java")) {
                validateControllerMethods(javaFile, content, fileName, result);
            }

        } catch (Exception e) {
            result.addError("Failed to validate " + javaFile.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Validate controller methods match entity fields
     */
    private void validateControllerMethods(Path javaFile, String content, String fileName, ValidationResult result) {
        try {
            // Parse entity name from controller
            String entityName = fileName.replace("Controller.java", "");

            // Try to find corresponding entity file
            Path entityFile = Paths.get(
                    javaFile.getParent().getParent().toString(),
                    "model",
                    entityName + ".java"
            );

            if (Files.exists(entityFile)) {
                String entityContent = Files.readString(entityFile);

                // Check if getDescription() is called but field doesn't exist
                if (content.contains("getDescription()") &&
                        !entityContent.contains("String description")) {
                    result.addError(fileName + ": Calls getDescription() but field doesn't exist in " + entityName);
                }

                // Check for isCompleted() which should be getCompleted()
                if (content.contains("isCompleted()") &&
                        entityContent.contains("Boolean completed")) {
                    result.addError(fileName + ": Uses isCompleted() for Boolean field (should be getCompleted())");
                }
            }
        } catch (Exception e) {
            // Ignore validation errors for controller methods
        }
    }

    /**
     * Validate TypeScript files
     */
    private void validateTypeScriptFiles(String appPath, ValidationResult result) {
        try {
            Path tsDir = Paths.get(appPath, "frontend/src/app");

            if (!Files.exists(tsDir)) {
                return;
            }

            Files.walk(tsDir)
                    .filter(p -> p.toString().endsWith(".ts"))
                    .forEach(tsFile -> validateTypeScriptFile(tsFile, result));

        } catch (Exception e) {
            result.addError("Failed to validate TypeScript files: " + e.getMessage());
        }
    }

    /**
     * Validate single TypeScript file
     */
    private void validateTypeScriptFile(Path tsFile, ValidationResult result) {
        try {
            String content = Files.readString(tsFile);
            String fileName = tsFile.getFileName().toString();

            // Check for generic example components (Claude sometimes generates these)
            if (fileName.contains("item-list") || content.contains("ItemListComponent") ||
                content.contains("ItemService") || fileName.contains("item.component")) {
                result.addError(fileName + ": Generic example component detected - should be entity-specific");
                return; // Skip further validation for example components
            }

            // Check for markdown artifacts
            if (content.contains("```")) {
                result.addError(fileName + ": Contains markdown backticks");
            }

            // Check for proper syntax
            long openBraces = content.chars().filter(ch -> ch == '{').count();
            long closeBraces = content.chars().filter(ch -> ch == '}').count();

            if (openBraces != closeBraces) {
                result.addError(fileName + ": Mismatched braces");
            }

            // Check imports for components
            if (fileName.endsWith(".component.ts")) {
                validateComponent(content, fileName, result);
            }

            // Check for services
            if (fileName.endsWith(".service.ts")) {
                if (!content.contains("@Injectable")) {
                    result.addError(fileName + ": Missing @Injectable decorator");
                }
            }

        } catch (Exception e) {
            result.addError("Failed to validate " + tsFile.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Validate Angular component
     */
    private void validateComponent(String content, String fileName, ValidationResult result) {
        if (!content.contains("@Component")) {
            result.addError(fileName + ": Missing @Component decorator");
        }
        
        // Extract expected class name from file name
        // e.g., "project.component.ts" → "ProjectComponent"
        // e.g., "item-list.component.ts" → "ItemListComponent"
        String baseName = fileName.replace(".component.ts", "");
        String expectedClassName = kebabToPascalCase(baseName) + "Component";

        if (!content.contains("export class " + expectedClassName)) {
            result.addError(fileName + ": Component class name doesn't match file name (expected: " + expectedClassName + ")");
        }

        // Check templateUrl matches file name
        String expectedTemplate = "./" + fileName.replace(".ts", ".html");
        if (!content.contains("templateUrl: '" + expectedTemplate + "'") &&
                !content.contains("templateUrl: \"" + expectedTemplate + "\"")) {
            result.addError(fileName + ": templateUrl doesn't match file name (expected: " + expectedTemplate + ")");
        }
    }

    /**
     * Convert kebab-case to PascalCase
     * e.g., "item-list" → "ItemList"
     * e.g., "project" → "Project"
     */
    private String kebabToPascalCase(String kebabCase) {
        return Arrays.stream(kebabCase.split("-"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining());
    }

    /**
     * Check for common issues
     */
    private void validateCommonIssues(String appPath, ValidationResult result) {
        // Check docker-compose.prod.yml
        try {
            Path dockerCompose = Paths.get(appPath, "docker-compose.prod.yml");
            if (Files.exists(dockerCompose)) {
                String content = Files.readString(dockerCompose);

                if (content.contains("traefik:")) {
                    result.addWarning("docker-compose.prod.yml contains Traefik (should use external)");
                }

                if (!content.contains("external: true")) {
                    result.addWarning("docker-compose.prod.yml network might not be external");
                }
            }
        } catch (Exception e) {
            result.addWarning("Could not validate docker-compose.prod.yml: " + e.getMessage());
        }
    }
}

/**
 * Validation Result
 */
class ValidationResult {
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (!errors.isEmpty()) {
            sb.append("Errors:\n");
            errors.forEach(e -> sb.append("  ❌ ").append(e).append("\n"));
        }

        if (!warnings.isEmpty()) {
            sb.append("Warnings:\n");
            warnings.forEach(w -> sb.append("  ⚠️ ").append(w).append("\n"));
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("✅ No issues found\n");
        }

        return sb.toString();
    }
}
