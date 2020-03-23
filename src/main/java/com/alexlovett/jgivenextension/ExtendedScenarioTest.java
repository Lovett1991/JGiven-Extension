package com.alexlovett.jgivenextension;

import com.tngtech.jgiven.impl.ExtendedScenarioModeBuilder;
import com.tngtech.jgiven.impl.Scenario;
import com.tngtech.jgiven.junit.ScenarioTest;
import lombok.SneakyThrows;

import java.lang.reflect.Field;

public abstract class ExtendedScenarioTest<GIVEN, WHEN, THEN> extends ScenarioTest<GIVEN, WHEN, THEN> {

    private StageChangeListener stageChangeListener;

    @Override
    @SneakyThrows
    protected Scenario<GIVEN, WHEN, THEN> createScenario() {
        Scenario scenario = super.createScenario();
        Field modelBuilder = scenario.getClass().getSuperclass().getDeclaredField("modelBuilder");
        modelBuilder.setAccessible(true);

        stageChangeListener = new ExtendedScenarioModeBuilder();
        modelBuilder.set(scenario, stageChangeListener);
        return scenario;
    }

    public interface StageChangeListener {
        void stageChanged();
    }

    private <T> T wrapStage(T stage){
        stageChangeListener.stageChanged();
        if (stage instanceof ExtendedStage) {
            ((ExtendedStage) stage).withScenarioBase(getScenario());
        }
        return stage;
    }

    @Override
    public GIVEN given() {
        return wrapStage(super.given());
    }

    @Override
    public WHEN when() {
        return wrapStage(super.when());
    }

    @Override
    public THEN then() {
        return wrapStage(super.then());
    }
}
