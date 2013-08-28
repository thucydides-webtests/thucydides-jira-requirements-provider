package net.thucydides.plugins.jira.adaptors;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

public class ZephyrDateParser {

    private final DateTime today;
    private final DateTime yesterday;

    public ZephyrDateParser(DateTime today) {
        this.today = today;
        this.yesterday = today.minusDays(1);
    }

    public DateTime parse(String date) {

        if (date.contains("Today")) {
            date = date.replace("Today", today.toString(DateTimeFormat.forPattern("d/MMM/yy")));
        } else if (date.contains("Yesterday")) {
            date = date.replace("Yesterday", yesterday.toString(DateTimeFormat.forPattern("d/MMM/yy")));
        }
        return DateTime.parse(date, DateTimeFormat.forPattern("d/MMM/yy hh:mm a"));
    }
}
