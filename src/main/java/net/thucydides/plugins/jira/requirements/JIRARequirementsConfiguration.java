package net.thucydides.plugins.jira.requirements;

public enum JIRARequirementsConfiguration {

    /**
     * The custom field used to store the story narrative description.
     */
    JIRA_CUSTOM_NARRATIVE_FIELD("jira.custom.narrative.field"),
    JIRA_CUSTOM_FIELD("jira.custom.field"),

    /**
     * Issue marked by this label will be excluded from requirements.
     */
    JIRA_EXCLUDE_REQUIREMENT_LABEL("jira.exclude.requirement.label");

    private final String name;

    public String getName() {
        return name;
    }

    JIRARequirementsConfiguration(String name) {
        this.name = name;
    }
}

