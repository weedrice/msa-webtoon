package com.yoordi.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "generator")
public class GeneratorProperties {

    private int eventsPerSecond = 100;
    private int userPoolSize = 1000;
    private int contentPoolSize = 500;
    private List<String> actions = List.of("view", "like");
    private double viewProbability = 0.9; // 90% view, 10% like

    public int getEventsPerSecond() {
        return eventsPerSecond;
    }

    public void setEventsPerSecond(int eventsPerSecond) {
        this.eventsPerSecond = eventsPerSecond;
    }

    public int getUserPoolSize() {
        return userPoolSize;
    }

    public void setUserPoolSize(int userPoolSize) {
        this.userPoolSize = userPoolSize;
    }

    public int getContentPoolSize() {
        return contentPoolSize;
    }

    public void setContentPoolSize(int contentPoolSize) {
        this.contentPoolSize = contentPoolSize;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public double getViewProbability() {
        return viewProbability;
    }

    public void setViewProbability(double viewProbability) {
        this.viewProbability = viewProbability;
    }
}