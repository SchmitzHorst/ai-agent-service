package com.alpine.agent.model;

import java.util.List;

public class AppRequirements {
    private String appName;
    private String description;
    private List<EntitySpec> entities;

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<EntitySpec> getEntities() { return entities; }
    public void setEntities(List<EntitySpec> entities) { this.entities = entities; }
}

