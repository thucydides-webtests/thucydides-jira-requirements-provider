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

    def "should read epics as the top level requirements"() {
        when:
            List<Requirement> requirements = requirementsProvider.getRequirements()
        then:
            !requirements.isEmpty()
        and:
            requirements.each { requirement -> requirement.type == 'Epic' }
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
            def requirement = requirementsProvider.getRequirementFor(TestTag.withName("Bring premium listings to the attention of buyers").andType("Epic"))
        then:
            requirement.isPresent() && requirement.get().getCardNumber() == "TRAD-6"
    }

    def "should get the story description from a custom field if required"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["TRAD-5"]
        when:
            environmentVariables.setProperty("jira.narrative.field","User Story")
        and:
            def requirement = requirementsProvider.getParentRequirementOf(testOutcome);
        then:
            requirement.isPresent() && requirement.get().getNarrativeText() ==
"""As a seller
I want to post my items for sale online
So that buyers can view and purchase my items"""
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

    def "should return Optional.absent() when no issues are specified"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> []
        when:
            def parentRequirement = requirementsProvider.getParentRequirementOf(testOutcome)
        then:
            !parentRequirement.isPresent()
    }

    def "should return Optional.absent() for a non-existant issue"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["UNKNOWN"]
        when:
            def parentRequirement = requirementsProvider.getParentRequirementOf(testOutcome)
        then:
            !parentRequirement.isPresent()
    }

    def "should find tags for a given issue"() {
        given:
            def requirementsProvider = new JIRARequirementsProvider(configuration)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["TRAD-5"]
        when:
            def tags = requirementsProvider.getTagsFor(testOutcome)
        then:
            tags.contains(TestTag.withName("Post item for sale").andType("Story")) &&
            tags.contains(TestTag.withName("Selling stuff").andType("Epic"))
    }

}
