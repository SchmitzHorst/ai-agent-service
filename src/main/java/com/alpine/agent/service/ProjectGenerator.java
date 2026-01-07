package com.alpine.agent.service;

import com.alpine.agent.model.AppRequirements;
import com.alpine.agent.model.EntitySpec;
import com.alpine.agent.model.FieldSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Service
public class ProjectGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProjectGenerator.class);

    @Autowired
    private ClaudeService claudeService;

    @Autowired
    private GitService gitService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private ValidationService validationService;

    public String generateApp(AppRequirements requirements) {
        try {
            String appName = requirements.getAppName();
            log.info("Starting app generation: {}", appName);

            String appPath = gitService.cloneTemplate(appName);

            if (requirements.getEntities() != null) {
                for (EntitySpec entity : requirements.getEntities()) {
                    generateEntity(appPath, entity);
                    generateRepository(appPath, entity);
                    generateController(appPath, entity);
                    generateAngularComponent(appPath, entity);
                }

                cleanupGenericComponents(appPath);

                // Frontend konfigurieren
                configureAngularApp(appPath, requirements.getEntities());
                generateHomeComponent(appPath, appName, requirements.getEntities());
                updateAppComponent(appPath);
            }

            // Validate
            ValidationResult validation = validationService.validateApp(appPath);

            if (validation.hasErrors()) {
                return "❌ Validation failed:\n" + validation;
            }

            gitService.initGitRepo(appPath, appName);

            String deployResult = deploymentService.deployApp(appName, appPath);

            return String.format(
                    "✅ App '%s' created and deployed!\n%s\n%s",
                    appName,
                    validation.getWarnings().isEmpty() ? "" : "⚠️ " + validation,
                    deployResult
            );

        } catch (Exception e) {
            log.error("Error generating app", e);
            return "❌ Error: " + e.getMessage();
        }
    }
    
    /**
     * Generate Entity class
     */
    private void generateEntity(String appPath, EntitySpec entity) throws Exception {
        log.info("Generating entity: {}", entity.getName());

        String prompt = buildEntityPrompt(entity);
        String entityCode = claudeService.generateCode(prompt);

        String relativePath = String.format(
                "backend/src/main/java/com/example/app/model/%s.java",
                entity.getName()
        );

        gitService.writeFile(appPath, relativePath, entityCode);
        log.info("Entity generated: {}", entity.getName());
    }

    /**
     * Generate Repository interface
     */
    private void generateRepository(String appPath, EntitySpec entity) throws Exception {
        log.info("Generating repository: {}Repository", entity.getName());

        String prompt = String.format(
                "Generate a Spring Data JPA Repository interface for entity %s. " +
                        "IMPORTANT: Import from com.example.app.model.%s (NOT entity package). " +
                        "Use JpaRepository<%s, Long>. " +
                        "Add @Repository annotation. " +
                        "Package: com.example.app.repository. " +
                        "Only return the complete Java code, no explanations, no markdown.",
                entity.getName(),
                entity.getName(),
                entity.getName()
        );

        String repoCode = claudeService.generateCode(prompt);

        String relativePath = String.format(
                "backend/src/main/java/com/example/app/repository/%sRepository.java",
                entity.getName()
        );

        gitService.writeFile(appPath, relativePath, repoCode);
        log.info("Repository generated: {}Repository", entity.getName());
    }

    /**
     * Generate REST Controller
     */
    private void generateController(String appPath, EntitySpec entity) throws Exception {
        log.info("Generating controller: {}Controller", entity.getName());

        // Build field list for prompt
        StringBuilder fieldList = new StringBuilder();
        if (entity.getFields() != null) {
            for (FieldSpec field : entity.getFields()) {
                fieldList.append("- ").append(field.getName())
                        .append(" (").append(field.getType()).append(")\n");
            }
        }

        String prompt = String.format(
                "Generate a Spring Boot REST Controller for entity %s. " +
                        "IMPORTANT REQUIREMENTS:\n" +
                        "1. Import from com.example.app.model.%s (NOT entity package)\n" +
                        "2. Import repository from com.example.app.repository.%sRepository\n" +
                        "3. Inject %sRepository directly (NO Service layer)\n" +
                        "4. Use @Autowired for repository\n\n" +
                        "ENTITY FIELDS (use ONLY these fields in update method):\n" +
                        "%s\n" +
                        "CRUD ENDPOINTS:\n" +
                        "- GET /api/%ss (findAll)\n" +
                        "- GET /api/%ss/{id} (findById with Optional)\n" +
                        "- POST /api/%ss (save)\n" +
                        "- PUT /api/%ss/{id} (update - ONLY update fields that exist in the entity above!)\n" +
                        "- DELETE /api/%ss/{id} (delete)\n\n" +
                        "CRITICAL: In the PUT method, only call setter methods that correspond to the fields listed above.\n" +
                        "For Boolean fields, use get%s() NOT is%s().\n" +
                        "Do NOT invent fields like 'description' if they don't exist.\n\n" +
                        "Use @RestController, @RequestMapping, ResponseEntity, Optional.\n" +
                        "Package: com.example.app.controller.\n" +
                        "Only return complete Java code, no explanations, no markdown.",
                entity.getName(),
                entity.getName(),
                entity.getName(),
                entity.getName(),
                fieldList.toString(),
                entity.getName().toLowerCase(),
                entity.getName().toLowerCase(),
                entity.getName().toLowerCase(),
                entity.getName().toLowerCase(),
                entity.getName().toLowerCase(),
                entity.getName(),
                entity.getName()
        );

        String controllerCode = claudeService.generateCode(prompt);

        String relativePath = String.format(
                "backend/src/main/java/com/example/app/controller/%sController.java",
                entity.getName()
        );

        gitService.writeFile(appPath, relativePath, controllerCode);
        log.info("Controller generated: {}Controller", entity.getName());
    }

    /**
     * Build detailed prompt for entity generation
     */
    private String buildEntityPrompt(EntitySpec entity) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(String.format(
                "Generate a Spring Boot 3.x JPA Entity class for %s. ",
                entity.getName()
        ));

        if (entity.getDescription() != null) {
            prompt.append(entity.getDescription()).append(". ");
        }

        prompt.append("IMPORTANT: Use jakarta.persistence imports (NOT javax.persistence). ");
        prompt.append("Include: @Entity, @Id, @GeneratedValue(strategy = GenerationType.IDENTITY). ");
        prompt.append("Package: com.example.app.model. ");

        if (entity.getFields() != null && !entity.getFields().isEmpty()) {
            prompt.append("Fields: ");
            for (FieldSpec field : entity.getFields()) {
                prompt.append(String.format(
                        "%s (%s, %s), ",
                        field.getName(),
                        field.getType(),
                        field.isRequired() ? "required, use @Column(nullable=false)" : "optional"
                ));
            }
        }

        prompt.append("Include empty constructor and getters/setters for all fields. ");
        prompt.append("Only return the complete Java code, no explanations, no markdown.");

        return prompt.toString();
    }

    /**
     * Generate Angular component for entity
     */
    private void generateAngularComponent(String appPath, EntitySpec entity) throws Exception {
        log.info("Generating Angular component: {}", entity.getName());

        String componentName = entity.getName().toLowerCase();

        // Generate BOTH component and template in one call
        String componentPrompt = buildAngularComponentPrompt(entity);
        String response = claudeService.generateCode(componentPrompt);

        // Parse response to extract TypeScript and HTML
        String[] parts = response.split("=== HTML ===");

        String typescriptCode = "";
        String htmlCode = "";

        if (parts.length >= 2) {
            typescriptCode = parts[0].replace("=== TYPESCRIPT ===", "").trim();
            htmlCode = parts[1].trim();
        } else {
            // Fallback: try to extract from single response
            log.warn("Could not parse TYPESCRIPT/HTML separator, using fallback");
            typescriptCode = response;
            // Generate HTML separately as before
            generateAngularTemplate(appPath, entity);

            String componentPath = String.format(
                    "frontend/src/app/components/%s/%s.component.ts",
                    componentName, componentName
            );
            gitService.writeFile(appPath, componentPath, typescriptCode);
            generateAngularService(appPath, entity);
            return;
        }

        // Write TypeScript
        String componentPath = String.format(
                "frontend/src/app/components/%s/%s.component.ts",
                componentName, componentName
        );
        gitService.writeFile(appPath, componentPath, typescriptCode);

        // Write HTML
        String templatePath = String.format(
                "frontend/src/app/components/%s/%s.component.html",
                componentName, componentName
        );
        gitService.writeFile(appPath, templatePath, htmlCode);

        // Generate Service
        generateAngularService(appPath, entity);

        log.info("Angular component generated: {}", componentName);
    }

    /**
     * Clean up generic example components and services that Claude sometimes generates
     */
    private void cleanupGenericComponents(String appPath) throws Exception {
        log.info("=== STARTING CLEANUP ===");
        log.info("App path: {}", appPath);

        // Cleanup components
        Path componentsDir = Paths.get(appPath, "frontend/src/app/components");
        log.info("Components dir: {}", componentsDir);
        log.info("Components dir exists: {}", Files.exists(componentsDir));

        if (Files.exists(componentsDir)) {
            log.info("Cleaning components directory...");
            cleanupGenericFiles(componentsDir);
        }

        // Cleanup services
        Path servicesDir = Paths.get(appPath, "frontend/src/app/services");
        log.info("Services dir: {}", servicesDir);
        log.info("Services dir exists: {}", Files.exists(servicesDir));

        if (Files.exists(servicesDir)) {
            log.info("Cleaning services directory...");
            cleanupGenericFiles(servicesDir);
        }

        log.info("=== CLEANUP COMPLETE ===");
    }

    /**
     * Clean up generic files in a directory
     */
    private void cleanupGenericFiles(Path directory) throws Exception {
        log.info("Scanning directory: {}", directory);

        String[] genericNames = {"item", "item-list", "example", "sample", "demo"};

        List<Path> toDelete = new ArrayList<>();

        Files.walk(directory, 2)
                .forEach(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    log.debug("Checking: {}", fileName);

                    for (String genericName : genericNames) {
                        if (fileName.contains(genericName)) {
                            log.warn("FOUND GENERIC: {} (matches '{}')", fileName, genericName);
                            toDelete.add(path);
                            break;
                        }
                    }
                });

        log.info("Found {} generic files/directories to delete", toDelete.size());

        for (Path path : toDelete) {
            try {
                if (Files.isDirectory(path)) {
                    log.warn("Deleting generic directory: {}", path);
                    deleteDirectory(path);
                } else {
                    log.warn("Deleting generic file: {}", path);
                    Files.delete(path);
                }
            } catch (Exception e) {
                log.error("Failed to delete: {}", path, e);
            }
        }
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            log.error("Failed to delete: {}", p, e);
                        }
                    });
        }
    }

    /**
     * Build prompt for Angular component - STRICT VERSION
     */
    private String buildAngularComponentPrompt(EntitySpec entity) {
        String componentName = entity.getName().toLowerCase();
        String className = entity.getName() + "Component";

        StringBuilder prompt = new StringBuilder();

        prompt.append("CRITICAL: Generate COMPLETE component files for ").append(entity.getName()).append(" entity.\n");
        prompt.append("You will generate TWO files in one response:\n");
        prompt.append("1. TypeScript component file\n");
        prompt.append("2. HTML template file\n\n");

        prompt.append("=== FILE 1: TypeScript Component ===\n");
        prompt.append(String.format("Filename: %s.component.ts\n\n", componentName));

        // TypeScript structure
        prompt.append("```typescript\n");
        prompt.append("import { Component, OnInit } from '@angular/core';\n");
        prompt.append("import { CommonModule } from '@angular/common';\n");
        prompt.append("import { FormsModule } from '@angular/forms';\n");
        prompt.append(String.format("import { %sService } from '../../services/%s.service';\n\n",
                entity.getName(), componentName));

        prompt.append(String.format("export interface %s {\n", entity.getName()));
        prompt.append("  id?: number;\n");
        if (entity.getFields() != null) {
            for (FieldSpec field : entity.getFields()) {
                String tsType = mapJavaTypeToTypeScript(field.getType());
                prompt.append(String.format("  %s: %s;\n", field.getName(), tsType));
            }
        }
        prompt.append("}\n\n");

        prompt.append("@Component({\n");
        prompt.append(String.format("  selector: 'app-%s',\n", componentName));
        prompt.append("  standalone: true,\n");
        prompt.append("  imports: [CommonModule, FormsModule],\n");
        prompt.append(String.format("  templateUrl: './%s.component.html'\n", componentName));
        prompt.append("})\n");
        prompt.append(String.format("export class %s implements OnInit {\n", className));
        prompt.append(String.format("  %ss: %s[] = [];\n", componentName, entity.getName()));
        prompt.append(String.format("  selectedItem: %s | null = null;\n", entity.getName()));
        prompt.append("  isEditing = false;\n\n");
        prompt.append(String.format("  constructor(private service: %sService) {}\n\n", entity.getName()));
        prompt.append("  ngOnInit(): void {\n");
        prompt.append("    this.loadAll();\n");
        prompt.append("  }\n\n");
        prompt.append("  loadAll(): void {\n");
        prompt.append(String.format("    this.service.getAll().subscribe(data => this.%ss = data);\n", componentName));
        prompt.append("  }\n\n");
        prompt.append(String.format("  selectForEdit(item: %s): void {\n", entity.getName()));
        prompt.append("    this.selectedItem = { ...item };\n");
        prompt.append("    this.isEditing = true;\n");
        prompt.append("  }\n\n");
        prompt.append("  save(): void {\n");
        prompt.append("    if (!this.selectedItem) return;\n");
        prompt.append("    if (this.selectedItem.id) {\n");
        prompt.append("      this.service.update(this.selectedItem.id, this.selectedItem).subscribe(() => {\n");
        prompt.append("        this.loadAll();\n");
        prompt.append("        this.reset();\n");
        prompt.append("      });\n");
        prompt.append("    } else {\n");
        prompt.append("      this.service.create(this.selectedItem).subscribe(() => {\n");
        prompt.append("        this.loadAll();\n");
        prompt.append("        this.reset();\n");
        prompt.append("      });\n");
        prompt.append("    }\n");
        prompt.append("  }\n\n");
        prompt.append("  delete(id: number): void {\n");
        prompt.append("    if (confirm('Delete?')) {\n");
        prompt.append("      this.service.delete(id).subscribe(() => this.loadAll());\n");
        prompt.append("    }\n");
        prompt.append("  }\n\n");
        prompt.append("  reset(): void {\n");
        prompt.append("    this.selectedItem = null;\n");
        prompt.append("    this.isEditing = false;\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("=== FILE 2: HTML Template ===\n");
        prompt.append(String.format("Filename: %s.component.html\n\n", componentName));

        prompt.append("Generate HTML that uses:\n");
        prompt.append(String.format("- *ngFor=\"let item of %ss\"\n", componentName));
        prompt.append("- (click)=\"selectForEdit(item)\"\n");
        prompt.append("- (click)=\"delete(item.id)\"\n");
        prompt.append("- [(ngModel)]=\"selectedItem.FIELD\"\n");
        prompt.append("- (ngSubmit)=\"save()\"\n");
        prompt.append("- (click)=\"reset()\"\n");
        prompt.append("Use Bootstrap 5 classes for styling.\n\n");

        prompt.append("Return BOTH files with clear separators:\n");
        prompt.append("=== TYPESCRIPT ===\n");
        prompt.append("[typescript code here]\n");
        prompt.append("=== HTML ===\n");
        prompt.append("[html code here]\n\n");

        prompt.append("NO markdown backticks. NO explanations.");

        return prompt.toString();
    }

    /**
     * Map Java types to TypeScript types
     */
    private String mapJavaTypeToTypeScript(String javaType) {
        return switch (javaType) {
            case "String" -> "string";
            case "Integer", "Long" -> "number";
            case "Boolean" -> "boolean";
            case "LocalDate", "LocalDateTime" -> "string";
            case "Double", "Float", "BigDecimal" -> "number";
            default -> "any";
        };
    }

    /**
     * Generate Angular HTML template
     */
    private void generateAngularTemplate(String appPath, EntitySpec entity) throws Exception {
        String componentName = entity.getName().toLowerCase();

        StringBuilder fieldNames = new StringBuilder();
        if (entity.getFields() != null) {
            for (FieldSpec field : entity.getFields()) {
                fieldNames.append(field.getName()).append(", ");
            }
        }

        String prompt = String.format(
                "Generate an Angular HTML template for %s component. " +
                        "Fields: %s. " +
                        "Include: " +
                        "- Bootstrap 5 table showing all items. " +
                        "- Form for creating/editing with [(ngModel)]. " +
                        "- Edit and Delete buttons. " +
                        "Only return HTML, no markdown backticks, no explanations.",
                entity.getName(),
                fieldNames.toString()
        );

        String html = claudeService.generateCode(prompt);

        String templatePath = String.format(
                "frontend/src/app/components/%s/%s.component.html",
                componentName, componentName
        );

        gitService.writeFile(appPath, templatePath, html);
    }

    /**
     * Generate Angular service
     */
    private void generateAngularService(String appPath, EntitySpec entity) throws Exception {
        String serviceName = entity.getName().toLowerCase();

        String prompt = String.format(
                "Generate an Angular service for %s. " +
                        "EXACT CLASS NAME: %sService. " +
                        "Include: " +
                        "- @Injectable with providedIn: 'root'. " +
                        "- HttpClient in constructor. " +
                        "- Methods: getAll(), getById(id), create(item), update(id, item), delete(id). " +
                        "- API base URL: /api/%ss. " +
                        "- Return Observable for each method. " +
                        "Only return TypeScript code, no markdown backticks, no explanations.",
                entity.getName(),
                entity.getName(),
                serviceName
        );

        String serviceCode = claudeService.generateCode(prompt);

        String servicePath = String.format(
                "frontend/src/app/services/%s.service.ts",
                serviceName
        );

        gitService.writeFile(appPath, servicePath, serviceCode);
    }

    /**
     * Configure Angular app module and routing
     */
    private void configureAngularApp(String appPath, List<EntitySpec> entities) throws Exception {
        log.info("Configuring Angular app module and routing");

        generateAppRoutes(appPath, entities);
        updateAppConfig(appPath);
        generateNavigationComponent(appPath, entities);

        log.info("Angular app configured");
    }

    /**
     * Generate app.routes.ts
     */
    private void generateAppRoutes(String appPath, List<EntitySpec> entities) throws Exception {
        StringBuilder routes = new StringBuilder();

        routes.append("import { Routes } from '@angular/router';\n");
        routes.append("import { HomeComponent } from './components/home/home.component';\n");

        // Import components
        for (EntitySpec entity : entities) {
            String componentName = entity.getName() + "Component";
            String kebabName = toKebabCase(entity.getName());
            routes.append(String.format(
                    "import { %s } from './components/%s/%s.component';\n",
                    componentName, kebabName, kebabName
            ));
        }

        routes.append("\nexport const routes: Routes = [\n");
        routes.append("  { path: '', redirectTo: '/home', pathMatch: 'full' },\n");
        routes.append("  { path: 'home', component: HomeComponent },\n");

        for (EntitySpec entity : entities) {
            String componentName = entity.getName() + "Component";
            String kebabName = toKebabCase(entity.getName());
            routes.append(String.format(
                    "  { path: '%ss', component: %s },\n",
                    kebabName, componentName
            ));
        }

        routes.append("];\n");

        gitService.writeFile(
                appPath,
                "frontend/src/app/app.routes.ts",
                routes.toString()
        );
    }

    /**
     * Generate simple home component
     */
    private void generateHomeComponent(String appPath, String appName, List<EntitySpec> entities) throws Exception {
        // Home component TypeScript
        StringBuilder homeTs = new StringBuilder();
        homeTs.append("import { Component } from '@angular/core';\n");
        homeTs.append("import { RouterLink } from '@angular/router';\n\n");
        homeTs.append("@Component({\n");
        homeTs.append("  selector: 'app-home',\n");
        homeTs.append("  standalone: true,\n");
        homeTs.append("  imports: [RouterLink],\n");
        homeTs.append("  templateUrl: './home.component.html'\n");
        homeTs.append("})\n");
        homeTs.append("export class HomeComponent {\n");
        homeTs.append("  appName = '").append(appName).append("';\n");
        homeTs.append("}\n");

        gitService.writeFile(
                appPath,
                "frontend/src/app/components/home/home.component.ts",
                homeTs.toString()
        );

        // Home component HTML
        StringBuilder homeHtml = new StringBuilder();
        homeHtml.append("<div class=\"container mt-5\">\n");
        homeHtml.append("  <div class=\"text-center\">\n");
        homeHtml.append("    <h1>Welcome to {{ appName }}</h1>\n");
        homeHtml.append("    <p class=\"lead\">Manage your data</p>\n");
        homeHtml.append("  </div>\n\n");
        homeHtml.append("  <div class=\"row mt-5\">\n");

        for (EntitySpec entity : entities) {
            String kebabName = toKebabCase(entity.getName());
            homeHtml.append("    <div class=\"col-md-4\">\n");
            homeHtml.append("      <div class=\"card\">\n");
            homeHtml.append("        <div class=\"card-body\">\n");
            homeHtml.append("          <h5 class=\"card-title\">").append(entity.getName()).append("s</h5>\n");
            homeHtml.append("          <p class=\"card-text\">").append(entity.getDescription() != null ? entity.getDescription() : "Manage " + entity.getName() + "s").append("</p>\n");
            homeHtml.append("          <a routerLink=\"/").append(kebabName).append("s\" class=\"btn btn-primary\">Manage</a>\n");
            homeHtml.append("        </div>\n");
            homeHtml.append("      </div>\n");
            homeHtml.append("    </div>\n");
        }

        homeHtml.append("  </div>\n");
        homeHtml.append("</div>\n");

        gitService.writeFile(
                appPath,
                "frontend/src/app/components/home/home.component.html",
                homeHtml.toString()
        );
    }

    /**
     * Update app.config.ts
     */
    private void updateAppConfig(String appPath) throws Exception {
        String config =
                "import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';\n" +
                        "import { provideRouter } from '@angular/router';\n" +
                        "import { provideHttpClient } from '@angular/common/http';\n" +
                        "import { routes } from './app.routes';\n\n" +
                        "export const appConfig: ApplicationConfig = {\n" +
                        "  providers: [\n" +
                        "    provideZoneChangeDetection({ eventCoalescing: true }),\n" +
                        "    provideRouter(routes),\n" +
                        "    provideHttpClient()\n" +
                        "  ]\n" +
                        "};\n";

        gitService.writeFile(appPath, "frontend/src/app/app.config.ts", config);
    }

    /**
     * Generate navigation component
     */
    private void generateNavigationComponent(String appPath, List<EntitySpec> entities) throws Exception {
        // Nav TypeScript
        String navTs =
                "import { Component } from '@angular/core';\n" +
                        "import { RouterLink, RouterLinkActive } from '@angular/router';\n\n" +
                        "@Component({\n" +
                        "  selector: 'app-nav',\n" +
                        "  standalone: true,\n" +
                        "  imports: [RouterLink, RouterLinkActive],\n" +
                        "  templateUrl: './nav.component.html'\n" +
                        "})\n" +
                        "export class NavComponent {}\n";

        gitService.writeFile(
                appPath,
                "frontend/src/app/components/nav/nav.component.ts",
                navTs
        );

        // Nav HTML
        StringBuilder navHtml = new StringBuilder();
        navHtml.append("<nav class=\"navbar navbar-expand-lg navbar-dark bg-primary\">\n");
        navHtml.append("  <div class=\"container-fluid\">\n");
        navHtml.append("    <a class=\"navbar-brand\" routerLink=\"/home\">App</a>\n");
        navHtml.append("    <div class=\"collapse navbar-collapse\">\n");
        navHtml.append("      <ul class=\"navbar-nav\">\n");
        navHtml.append("        <li class=\"nav-item\">\n");
        navHtml.append("          <a class=\"nav-link\" routerLink=\"/home\" routerLinkActive=\"active\">Home</a>\n");
        navHtml.append("        </li>\n");

        for (EntitySpec entity : entities) {
            String kebabName = toKebabCase(entity.getName());
            navHtml.append("        <li class=\"nav-item\">\n");
            navHtml.append("          <a class=\"nav-link\" routerLink=\"/").append(kebabName);
            navHtml.append("s\" routerLinkActive=\"active\">").append(entity.getName()).append("s</a>\n");
            navHtml.append("        </li>\n");
        }

        navHtml.append("      </ul>\n");
        navHtml.append("    </div>\n");
        navHtml.append("  </div>\n");
        navHtml.append("</nav>\n");

        gitService.writeFile(
                appPath,
                "frontend/src/app/components/nav/nav.component.html",
                navHtml.toString()
        );
    }

    /**
     * Update app.component to use router and nav
     */
    private void updateAppComponent(String appPath) throws Exception {
        // app.component.ts
        String appComponentTs =
                "import { Component } from '@angular/core';\n" +
                        "import { RouterOutlet } from '@angular/router';\n" +
                        "import { NavComponent } from './components/nav/nav.component';\n\n" +
                        "@Component({\n" +
                        "  selector: 'app-root',\n" +
                        "  standalone: true,\n" +
                        "  imports: [RouterOutlet, NavComponent],\n" +
                        "  templateUrl: './app.component.html',\n" +
                        "  styleUrl: './app.component.css'\n" +
                        "})\n" +
                        "export class AppComponent {\n" +
                        "  title = 'app';\n" +
                        "}\n";

        gitService.writeFile(
                appPath,
                "frontend/src/app/app.component.ts",
                appComponentTs
        );

        // app.component.html
        String appComponentHtml =
                "<app-nav></app-nav>\n" +
                        "<router-outlet></router-outlet>\n";

        gitService.writeFile(
                appPath,
                "frontend/src/app/app.component.html",
                appComponentHtml
        );
    }

    /**
     * Helper: Convert to kebab-case
     */
    private String toKebabCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
