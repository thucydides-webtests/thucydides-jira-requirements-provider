package net.thucydides.plugins.jira.requirements;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.plugins.jira.client.JIRAAuthenticationError;
import net.thucydides.plugins.jira.client.JIRAConfigurationError;
import net.thucydides.plugins.jira.client.JerseyJiraClient;
import net.thucydides.plugins.jira.domain.IssueSummary;
import org.json.JSONException;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A description goes here.
 * User: john
 * Date: 7/03/2014
 * Time: 2:57 PM
 */
public class ConfigurableJiraClient extends JerseyJiraClient {

    private static final String FAIL_ON_JIRA_ERROR = "thucydides.fail.on.jira.error";
    private final EnvironmentVariables environmentVariables;

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(JIRARequirementsProvider.class);

    public ConfigurableJiraClient(String url, String username, String password, String project) {
        super(url, username, password, project, customFields());
        environmentVariables = Injectors.getInjector().getInstance(EnvironmentVariables.class);
    }

    private static List<String> customFields() {
        EnvironmentVariables environmentVariables = Injectors.getInjector().getInstance(EnvironmentVariables.class);
        return Lists.newArrayList();
    }

    @Override
    public List<IssueSummary> findByJQL(String query) throws JSONException {
        try {
            return super.findByJQL(query);
        } catch(JIRAAuthenticationError authenticationError) {
            if (failOnJiraErrors()) {
                throw authenticationError;
            } else {
                logger.error("Could not connect to JIRA", authenticationError);
            }
        } catch(JIRAConfigurationError configurationError) {
            if (failOnJiraErrors()) {
                throw configurationError;
            } else {
                logger.error("Could not connect to JIRA", configurationError);
            }

        }
        return Lists.newArrayList();
    }

    private boolean failOnJiraErrors() {
        return environmentVariables.getPropertyAsBoolean(FAIL_ON_JIRA_ERROR,false);
    }

    @Override
    public Optional<IssueSummary> findByKey(String key) throws JSONException {
        try {
            return super.findByKey(key);
        } catch(JIRAAuthenticationError authenticationError) {

        } catch(JIRAConfigurationError configurationError) {

        }
        return Optional.absent();
    }
}
