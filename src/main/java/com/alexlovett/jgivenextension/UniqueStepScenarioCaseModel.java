package com.alexlovett.jgivenextension;

import com.tngtech.jgiven.report.model.ScenarioCaseModel;
import com.tngtech.jgiven.report.model.StepModel;

import java.util.Arrays;

public class UniqueStepScenarioCaseModel extends ScenarioCaseModel {

    @Override
    public void addStep( StepModel stepModel ) {
        //NoOp
    }

    public void addSteps ( StepModel... stepModels) {
        Arrays.asList(stepModels).forEach(super::addStep);
    }
}
