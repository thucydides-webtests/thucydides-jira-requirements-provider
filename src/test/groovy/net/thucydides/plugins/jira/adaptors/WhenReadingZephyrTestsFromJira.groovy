package net.thucydides.plugins.jira.adaptors

import net.thucydides.core.model.TestResult
import net.thucydides.core.util.MockEnvironmentVariables
import net.thucydides.plugins.jira.service.JIRAConfiguration

import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import spock.lang.Specification

class WhenReadingZephyrTestsFromJira extends Specification {


    JIRAConfiguration configuration
    def environmentVariables = new MockEnvironmentVariables()
//    def requirementsProvider

    def setup() {
        environmentVariables.setProperty('jira.url','http://54.253.114.157:8082')
        environmentVariables.setProperty('jira.username','john.smart')
        environmentVariables.setProperty('jira.password','arv-uf-gis-bo-gl')
        environmentVariables.setProperty('jira.project','PAV')

        configuration = new SystemPropertiesJIRAConfiguration(environmentVariables)
    }

    def "should read manual test results from Zephyr tests"() {
        given:
            def zephyrAdaptor = new ZephyrAdaptor(configuration)
        when:
            def outcomes = zephyrAdaptor.loadOutcomes()
        then:
            !outcomes.isEmpty()
        and:
            outcomes.each { assert it.isManual() }
    }

    def "should get the user story associated with the tests via a label with the user story key"() {
        given:
            def zephyrAdaptor = new ZephyrAdaptor(configuration)
        when:
            def outcomes = zephyrAdaptor.loadOutcomes()
            def sampleTest = outcomes.find { it.title.contains 'Testing some stuff'}
        then:
            sampleTest.userStory.name == 'Cats File User Story 1'
    }

    def "should get the latest test result for a manual test"() {
        given:
            def zephyrAdaptor = new ZephyrAdaptor(configuration)
        when:
            def outcomes = zephyrAdaptor.loadOutcomes()
            def sampleTest = outcomes.find { it.title.contains 'Testing some stuff'}
        then:
            sampleTest.isManual()
            sampleTest.result == TestResult.SUCCESS
            sampleTest.startTime != null

    }

    def "should load the test steps for a manual test"() {
        given:
            def zephyrAdaptor = new ZephyrAdaptor(configuration)
        when:
            def outcomes = zephyrAdaptor.loadOutcomes()
            def sampleTest = outcomes.find { it.title.contains 'Testing some stuff'}
        then:
            sampleTest.isManual()
            sampleTest.testSteps.size() == 2
        and:
            sampleTest.testSteps[0].description.contains("Do something")
            sampleTest.testSteps[1].description.contains("Do something else")
    }

    def "should record the execution time of a manual test"() {
        given:
            def zephyrAdaptor = new ZephyrAdaptor(configuration)
        when:
            def outcomes = zephyrAdaptor.loadOutcomes()
            def sampleTest = outcomes.find { it.title.contains 'Testing some stuff'}
        then:
            sampleTest.startTime == timeAt("28/Aug/13 11:03 AM")
    }

    def "should understand Zephyr's quirky date formatting"() {
        given:
            def today = new DateTime(2013,1,1,0,0)
            def ZephyrDateParser parser = new ZephyrDateParser(today)
        when:
            def parsedDate = parser.parse(zephyrDate)
        then:
            parsedDate == expectedDate
        where:
            zephyrDate          | expectedDate
            "26/Jul/13 4:03 PM" | new DateTime(2013,7,26,16,3)
            "Today 9:13 AM"     | new DateTime(2013,1,1,9,13)


    }


    DateTime timeAt(String date) {
        return DateTime.parse(date, DateTimeFormat.forPattern("d/MMM/yy hh:mm a"))
    }
}
