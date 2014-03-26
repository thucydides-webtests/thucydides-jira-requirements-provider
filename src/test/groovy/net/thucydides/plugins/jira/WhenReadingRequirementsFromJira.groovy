package net.thucydides.plugins.jira

import net.thucydides.core.util.MockEnvironmentVariables
import net.thucydides.plugins.jira.requirements.JIRARequirementsProvider
import net.thucydides.plugins.jira.service.JIRAConfiguration
import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration
import spock.lang.Specification

class WhenReadingRequirementsFromJira extends Specification {


    JIRAConfiguration configuration
    def environmentVariables = new MockEnvironmentVariables()
    def requirementsProvider

    def setup() {
        environmentVariables.setProperty('jira.url','https://wakaleo.atlassian.net')
        environmentVariables.setProperty('jira.username','bruce')
        environmentVariables.setProperty('jira.password','batm0bile')
        environmentVariables.setProperty('jira.project','TRAD')

        configuration = new SystemPropertiesJIRAConfiguration(environmentVariables)
        requirementsProvider = new JIRARequirementsProvider(configuration)
    }

    def "'Requirements can be loaded from the Epic/Story JIRA card structure"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
        when:
            def requirements = requirementsProvider.getRequirements();
        then:
            requirements.size() > 0
    }


}
