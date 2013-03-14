package net.thucydides.plugins.jira

import net.thucydides.core.model.TestOutcome
import net.thucydides.core.model.TestTag
import net.thucydides.core.requirements.model.Requirement
import net.thucydides.core.util.MockEnvironmentVariables
import net.thucydides.plugins.jira.requirements.JIRARequirementsProvider
import net.thucydides.plugins.jira.service.JIRAConfiguration
import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration
import spock.lang.Specification

class WhenReadingStoriesFromJira extends Specification {


    JIRAConfiguration configuration
    def requirementsProvider

    def setup() {
        configuration = new SystemPropertiesJIRAConfiguration()
        requirementsProvider = new JIRARequirementsProvider(configuration)
    }

    def "should read epics as the top level requirements"() {
        when:
            List<Requirement> requirements = requirementsProvider.getRequirements()
        then:
            !requirements.isEmpty()
        and:
            requirements.each { requirement -> requirement.type == 'epic' }
    }

    def "should read stories beneath the epics"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
        when:
            List<Requirement> requirements = requirementsProvider.getRequirements()
        then:
            requirements.find {epic -> !epic.children.isEmpty() }
    }

    def "should find requirements for an issue name"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
        when:
            def requirement = requirementsProvider.getRequirementFor(TestTag.withName("Bring premium listings to the attention of buyers").andType("epic"))
        then:
            requirement.isPresent() && requirement.get().getCardNumber() == "TRAD-6"
    }

    def "should find the parent requirement from a given issue"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["TRAD-1"]
        when:
            def parentRequirement = requirementsProvider.getParentRequirementOf(testOutcome)
        then:
            parentRequirement.isPresent() && parentRequirement.get().cardNumber == "TRAD-1"
    }

    def "should find tags for a given issue"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["TRAD-5"]
        when:
            def tags = requirementsProvider.getTagsFor(testOutcome)
        then:
            tags.contains(TestTag.withName("Post item for sale").andType("story")) &&
            tags.contains(TestTag.withName("Selling stuff").andType("epic"))
    }


}
