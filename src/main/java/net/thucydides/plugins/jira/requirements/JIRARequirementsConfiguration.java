package net.thucydides.plugins.jira.requirements;

public enum JIRARequirementsConfiguration {

    /**
     * The custom field used to store the story narrative description.
     */
    JIRA_CUSTOM_NARRATIVE_FIELD("jira.custom.narrative.field");

    private final String name;

    public String getName() {
        return name;
    }

    JIRARequirementsConfiguration(String name) {
        this.name = name;
    }
}

