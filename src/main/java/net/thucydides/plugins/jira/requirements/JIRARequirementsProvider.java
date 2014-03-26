package net.thucydides.plugins.jira.requirements;

import ch.lambdaj.function.convert.Converter;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.model.TestTag;
import net.thucydides.core.requirements.RequirementsTagProvider;
import net.thucydides.core.requirements.model.Requirement;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.plugins.jira.client.JerseyJiraClient;
import net.thucydides.plugins.jira.domain.IssueSummary;
import net.thucydides.plugins.jira.service.JIRAConfiguration;
import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static ch.lambdaj.Lambda.convert;
import static net.thucydides.plugins.jira.requirements.JIRARequirementsConfiguration.JIRA_CUSTOM_FIELD;
import static net.thucydides.plugins.jira.requirements.JIRARequirementsConfiguration.JIRA_CUSTOM_NARRATIVE_FIELD;

/**
 * Integrate Thucydides reports with requirements, epics and stories in a JIRA server.
 */
public class JIRARequirementsProvider implements RequirementsTagProvider {

    private List<Requirement> requirements = null;
    private final JerseyJiraClient jiraClient;
    private final String projectKey;
    private final EnvironmentVariables environmentVariables;

    private List<String> requirementsLinks = ImmutableList.of("Epic Link");
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(JIRARequirementsProvider.class);

    public JIRARequirementsProvider() {
        this(new SystemPropertiesJIRAConfiguration(Injectors.getInjector().getInstance(EnvironmentVariables.class)),
             Injectors.getInjector().getInstance(EnvironmentVariables.class));
    }

    public JIRARequirementsProvider(JIRAConfiguration jiraConfiguration) {
        this(jiraConfiguration, Injectors.getInjector().getInstance(EnvironmentVariables.class));
    }

    public JIRARequirementsProvider(JIRAConfiguration jiraConfiguration, EnvironmentVariables environmentVariables) {
        logConnectionDetailsFor(jiraConfiguration);
        projectKey = jiraConfiguration.getProject();
        this.environmentVariables = environmentVariables;
        jiraClient = new ConfigurableJiraClient(jiraConfiguration.getJiraUrl(),
                                                jiraConfiguration.getJiraUser(),
                                                jiraConfiguration.getJiraPassword(),
                                                projectKey).usingCustomFields(customFieldsDefinedIn(environmentVariables));
    }

    private List<String> definedCustomFields() {
        List<String> customFields = Lists.newArrayList();
        int customFieldIndex = 1;
        while(addCustomFieldIfDefined(environmentVariables, customFields, customFieldNumber(customFieldIndex++)));
        return customFields;
    }

    private List<String> customFieldsDefinedIn(EnvironmentVariables environmentVariables) {
        List<String> customFields = Lists.newArrayList();
        addCustomFieldIfDefined(environmentVariables, customFields,
                                JIRARequirementsConfiguration.JIRA_CUSTOM_NARRATIVE_FIELD.getName());
        customFields.addAll(definedCustomFields());
        return customFields;
    }

    private String customFieldNumber(int customFieldIndex) {
        return JIRA_CUSTOM_FIELD.getName() + "." + customFieldIndex;
    }

    private boolean addCustomFieldIfDefined(EnvironmentVariables environmentVariables,
                                         List<String> customFields,
                                         String customField) {
        String customFieldName = environmentVariables.getProperty(customField);
        if (StringUtils.isNotEmpty(customFieldName)) {
            customFields.add(customFieldName);
            return true;
        }
        return false;
    }

    private void logConnectionDetailsFor(JIRAConfiguration jiraConfiguration) {
        logger.debug("JIRA URL: {0}", jiraConfiguration.getJiraUrl());
        logger.debug("JIRA project: {0}", jiraConfiguration.getProject());
        logger.debug("JIRA user: {0}", jiraConfiguration.getJiraUser());
    }

    private String getProjectKey() {
        return projectKey;
    }

    @Override
    public List<Requirement> getRequirements() {
        if (requirements == null) {
            List<IssueSummary> rootRequirementIssues = Lists.newArrayList();
            try {
                rootRequirementIssues = jiraClient.findByJQL(rootRequirementsJQL());
            } catch (JSONException e) {
                logger.warn("No root requirements found", e);
            }
            requirements = convert(rootRequirementIssues, toRequirements());
        }
        return requirements;
    }

    private Converter<IssueSummary, Requirement> toRequirements() {
        return new Converter<IssueSummary, Requirement>() {
            @Override
            public Requirement convert(IssueSummary issue) {
                Requirement requirement = requirementFrom(issue);
                List<Requirement> childRequirements = findChildrenFor(requirement, 0);
                return requirement.withChildren(childRequirements);
            }
        };
    }

    private Requirement requirementFrom(IssueSummary issue) {

        Requirement baseRequirement = Requirement.named(issue.getSummary())
                .withOptionalCardNumber(issue.getKey())
                .withType(issue.getType())
                .withNarrative(narativeTextFrom(issue))
                .withReleaseVersions(issue.getFixVersions());

        for(String fieldName : definedCustomFields()) {
            if (issue.customField(fieldName).isPresent()) {
                String value = issue.customField(fieldName).get().asString();
                String renderedValue = issue.getRendered().customField(fieldName).get();
                baseRequirement = baseRequirement.withCustomField(fieldName).setTo(value, renderedValue);
            }
        }
        return baseRequirement;
    }

    private String narativeTextFrom(IssueSummary issue) {
        Optional<String> customFieldName = Optional.fromNullable(environmentVariables.getProperty(JIRA_CUSTOM_NARRATIVE_FIELD.getName()));
        if (customFieldName.isPresent()) {
            return customFieldNameFor(issue, customFieldName.get()).or(issue.getRendered().getDescription());
        } else {
            return issue.getRendered().getDescription();
        }

    }

    private Optional<String> customFieldNameFor(IssueSummary issue, String customFieldName) {
        if (issue.customField(customFieldName).isPresent()) {
            return Optional.of(issue.customField(customFieldName).get().asString());
        } else {
            return Optional.absent();
        }
    }


    private List<Requirement> findChildrenFor(Requirement parent, int level) {
        List<IssueSummary> children = Lists.newArrayList();
        try {
            children = jiraClient.findByJQL(childIssuesJQL(parent, level));
        } catch (JSONException e) {
            logger.warn("No children found for requirement " + parent, e);
        }
        return convert(children, toRequirementsWithChildren(level));
    }

    private String childIssuesJQL(Requirement parent, int level) {
        return "'" + requirementsLinks.get(level) + "' = " + parent.getCardNumber();
    }

    private Converter<IssueSummary, Requirement> toRequirementsWithChildren(final int level) {
        return new Converter<IssueSummary,Requirement>() {

            @Override
            public Requirement convert(IssueSummary childIssue) {
                Requirement childRequirement = requirementFrom(childIssue);
                if (moreRequirements(level)) {
                    List<Requirement> grandChildren = findChildrenFor(childRequirement, 0);
                    childRequirement = childRequirement.withChildren(grandChildren);
                }
                return childRequirement;
            }
        };
    }

    private boolean moreRequirements(int level) {
        return level + 1 < requirementsLinks.size();
    }


    //////////////////////////////////////

    private String rootRequirementsJQL() {
        return "issuetype = epic and project=" + getProjectKey();
    }

    @Override
    public Optional<Requirement> getParentRequirementOf(TestOutcome testOutcome) {
        List<String> issueKeys = testOutcome.getIssueKeys();
        if (!issueKeys.isEmpty()) {
            try {
                Optional<IssueSummary> parentIssue = jiraClient.findByKey(issueKeys.get(0));
                if (parentIssue.isPresent()) {
                    return Optional.of(requirementFrom(parentIssue.get()));
                } else {
                    return Optional.absent();
                }
            } catch (JSONException e) {
                if (noSuchIssue(e)) {
                    return Optional.absent();
                } else {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            return Optional.absent();
        }
    }

    private boolean noSuchIssue(JSONException e) {
        return e.getMessage().contains("error 400");
    }

    @Override
    public Optional<Requirement> getRequirementFor(TestTag testTag) {
        for (Requirement requirement : getFlattenedRequirements()) {
            if (requirement.getType().equals(testTag.getType()) && requirement.getName().equals(testTag.getName())) {
                return Optional.of(requirement);
            }
        }
        return Optional.absent();
    }

    @Override
    public Set<TestTag> getTagsFor(TestOutcome testOutcome) {
        List<String> issues  = testOutcome.getIssueKeys();
        Set<TestTag> tags = Sets.newHashSet();
        for(String issue : issues) {
            tags.addAll(tagsFromIssue(issue));
        }
        return ImmutableSet.copyOf(tags);
    }

    private Collection<? extends TestTag> tagsFromIssue(String issueKey) {
        IssueTagReader tagReader = new IssueTagReader(jiraClient, getFlattenedRequirements(), projectKey);
        return tagReader.addIssueTags(issueKey)
                        .addRequirementTags(issueKey)
                        .addVersionTags(issueKey).getTags();
    }

    private List<Requirement> getFlattenedRequirements(){
        return getFlattenedRequirements(getRequirements());
    }

    private List<Requirement> getFlattenedRequirements(List<Requirement> someRequirements){
        List<Requirement> flattenedRequirements = Lists.newArrayList();
        for (Requirement requirement : someRequirements) {
            flattenedRequirements.add(requirement);
            flattenedRequirements.addAll(getFlattenedRequirements(requirement.getChildren()));
        }
        return flattenedRequirements;
    }
}
