package com.alpine.agent.model;

import java.util.List;

public class EntitySpec {
    private String name;
    private String description;
    private List<FieldSpec> fields;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<FieldSpec> getFields() { return fields; }
    public void setFields(List<FieldSpec> fields) { this.fields = fields; }
}
