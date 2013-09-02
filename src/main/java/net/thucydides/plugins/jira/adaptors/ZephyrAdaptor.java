package net.thucydides.plugins.jira.adaptors;

import ch.lambdaj.function.convert.Converter;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.Story;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.model.TestResult;
import net.thucydides.core.model.TestStep;
import net.thucydides.core.model.TestTag;
import net.thucydides.core.reports.adaptors.TestOutcomeAdaptor;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.plugins.jira.client.JerseyJiraClient;
import net.thucydides.plugins.jira.domain.IssueSummary;
import net.thucydides.plugins.jira.service.JIRAConfiguration;
import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ch.lambdaj.Lambda.convert;

/**
 * Read manual test results from the JIRA Zephyr plugin.
 */
public class ZephyrAdaptor implements TestOutcomeAdaptor {

    private static final String ZEPHYR_REST_API = "rest/zephyr/1.0";

    private final static Map<String, TestResult> TEST_STATUS_MAP
            = ImmutableMap.of("PASS", TestResult.SUCCESS,
                              "FAIL", TestResult.FAILURE,
                              "WIP", TestResult.PENDING,
                              "BLOCKED", TestResult.SKIPPED,
                              "UNEXECUTED", TestResult.IGNORED);

    private final JerseyJiraClient jiraClient;
    private final String jiraProject;

    public ZephyrAdaptor() {
        this(new SystemPropertiesJIRAConfiguration(Injectors.getInjector().getInstance(EnvironmentVariables.class)));
    }

    public ZephyrAdaptor(JIRAConfiguration jiraConfiguration) {
        jiraClient = new JerseyJiraClient(jiraConfiguration.getJiraUrl(),
                                          jiraConfiguration.getJiraUser(),
                                          jiraConfiguration.getJiraPassword());
        jiraProject = jiraConfiguration.getProject();
    }

    @Override
    public List<TestOutcome> loadOutcomes() throws IOException {
        List<IssueSummary> manualTests = null;
        try {
            manualTests = jiraClient.findByJQL("type=Test and project=" + jiraProject);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return convert(manualTests, toTestOutcomes());
    }

    private Converter<IssueSummary, TestOutcome> toTestOutcomes() {
        return new Converter<IssueSummary, TestOutcome>() {

            @Override
            public TestOutcome convert(IssueSummary issue) {

                try {
                    List<IssueSummary> associatedIssues = getLabelsWithMatchingIssues(issue);
                    TestOutcome outcome = TestOutcome.forTestInStory(issue.getSummary(), storyFrom(associatedIssues));
                    outcome = outcomeWithTagsForIssues(outcome, associatedIssues);
                    addTestStepsTo(outcome, issue.getId());
                    return outcome.asManualTest();
                } catch (JSONException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        };
    }


    private void addTestStepsTo(TestOutcome outcome, Long id) throws JSONException {
        WebTarget target = jiraClient.buildWebTargetFor(ZEPHYR_REST_API + "/teststep/" + id);
        Response response = target.request().get();
        jiraClient.checkValid(response);

        String jsonResponse = response.readEntity(String.class);
        JSONArray stepObjects = new JSONArray(jsonResponse);

        outcome.clearStartTime();
        for (int i = 0; i < stepObjects.length(); i++) {
            JSONObject step = stepObjects.getJSONObject(i);
            TestRecord testRecord = getTestRecordFor(id);
            outcome.recordStep(fromJson(step, testRecord));
            outcome.setStartTime(testRecord.executionDate);
        }
    }

    private TestStep fromJson(JSONObject step, TestRecord testRecord) throws JSONException {
        String stepDescription = step.getString("htmlStep");
        return TestStep.forStepCalled(stepDescription).withResult(testRecord.testResult);
    }

    class TestRecord {
        public final TestResult testResult;
        public final DateTime executionDate;

        TestRecord(TestResult testResult, DateTime executionDate) {
            this.testResult = testResult;
            this.executionDate = executionDate;
        }
    }

    private TestRecord getTestRecordFor(Long id) throws JSONException {
        WebTarget target = jiraClient.buildWebTargetFor(ZEPHYR_REST_API + "/schedule").queryParam("issueId", id);
        Response response = target.request().get();
        jiraClient.checkValid(response);

        String jsonResponse = response.readEntity(String.class);
        JSONObject testSchedule = new JSONObject(jsonResponse);
        JSONArray schedules = testSchedule.getJSONArray("schedules");
        JSONObject statusMap = testSchedule.getJSONObject("status");

        if (schedules.length() > 0) {
            JSONObject latestSchedule = schedules.getJSONObject(0);
            String executionStatus = latestSchedule.getString("executionStatus");
            DateTime executionDate = parser().parse(latestSchedule.getString("executedOn"));
            return new TestRecord(getTestResultFrom(executionStatus, statusMap), executionDate);
        } else {
            return new TestRecord(TestResult.PENDING, null);
        }
    }

    private ZephyrDateParser parser() {
        return new ZephyrDateParser(new DateTime());
    }

    private TestResult getTestResultFrom(String executionStatus, JSONObject statusMap) throws JSONException {
        JSONObject status = statusMap.getJSONObject(executionStatus);
        if (status != null) {
            String statusName = status.getString("name");
            if (TEST_STATUS_MAP.containsKey(statusName)) {
                return TEST_STATUS_MAP.get(statusName);
            }
        }
        return TestResult.PENDING;
    }

    private TestOutcome outcomeWithTagsForIssues(TestOutcome outcome, List<IssueSummary> associatedIssues) {
        List<String> issueKeys = Lists.newArrayList();
        for(IssueSummary issue : associatedIssues) {
            issueKeys.add(issue.getKey());
        }
        outcome.addIssues(issueKeys);
        return outcome;
    }

    private Story storyFrom(List<IssueSummary> associatedIssues) {
        Optional<Story> associatedStory = storyAssociatedByLabels(associatedIssues);
        return associatedStory.or(Story.called("Manual tests"));
    }

    private Optional<Story> storyAssociatedByLabels(List<IssueSummary> associatedIssues) {
        if (!associatedIssues.isEmpty()) {
            return Optional.of(Story.called(associatedIssues.get(0).getSummary()));
        }
        return Optional.absent();
    }

    private List<IssueSummary> getLabelsWithMatchingIssues(IssueSummary issue) throws JSONException {
        List<IssueSummary> matchingIssues = Lists.newArrayList();
        for(String label : issue.getLabels()) {
            List<IssueSummary> matchingAssociatedIssues = jiraClient.findByJQL("key=" + label);
            for(IssueSummary issueSummary : matchingAssociatedIssues) {
                matchingIssues.add(issueSummary);
            }
        }
        return ImmutableList.copyOf(matchingIssues);
    }

    @Override
    public List<TestOutcome> loadOutcomesFrom(File file) throws IOException {
        return loadOutcomes();
    }
}
