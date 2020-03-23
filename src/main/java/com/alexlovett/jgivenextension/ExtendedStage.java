package com.alexlovett.jgivenextension;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.Hidden;
import com.tngtech.jgiven.impl.ScenarioBase;

public class ExtendedStage<SELF extends ExtendedStage<?>> extends Stage<SELF> {

    private ScenarioBase scenarioBase;

    public SELF an() {
        return self();
    }

    public SELF a() { return self(); }

    public SELF the() {
        return self();
    }

    public SELF that() { return self(); }

    public SELF is() { return self(); }

    @Hidden
    public SELF withScenarioBase(ScenarioBase scenarioBase) {
        this.scenarioBase = scenarioBase;
        return self();
    }

    protected <T extends NestedStage<T,SELF>> T nest(Class<T> clazz) {
        return scenarioBase
                .addStage(clazz)
                .withReverse(self())
                .withScenarioBase(scenarioBase);
    }

    public static class NestedStage<NESTED extends ExtendedStage<?>, SELF> extends ExtendedStage<NESTED> {
        private SELF reverse;
        private ScenarioBase scenarioBase;

        @Hidden
        public NESTED withReverse(SELF reverse) {
            this.reverse = reverse;
            return self();
        }

    }
}
