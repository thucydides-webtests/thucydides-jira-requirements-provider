package net.thucydides.plugins.jira.requirements.parallel;

public interface LoopBody <T>
{
    void run(T i);
}
