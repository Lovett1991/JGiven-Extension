package com.tngtech.jgiven.impl;

import com.alexlovett.jgivenextension.ExtendedScenarioTest;
import com.alexlovett.jgivenextension.UniqueStepScenarioCaseModel;
import com.tngtech.jgiven.report.model.InvocationMode;
import com.tngtech.jgiven.report.model.NamedArgument;
import com.tngtech.jgiven.report.model.ScenarioModel;
import com.tngtech.jgiven.report.model.StepModel;
import com.tngtech.jgiven.report.model.Word;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static lombok.AccessLevel.PRIVATE;

public class ExtendedScenarioModeBuilder extends ScenarioModelBuilder implements ExtendedScenarioTest.StageChangeListener {
    private StepNode rootNode = StepNode.builder().build();
    private boolean isNewStage = false;
    private UniqueStepScenarioCaseModel scenarioCaseModel;

    @Override
    @SneakyThrows
    public void scenarioStarted( String description ) {
        super.scenarioStarted(description);
        Field scenarioModelField = ScenarioModelBuilder.class.getDeclaredField("scenarioModel");
        Field scenarioCaseModelField = ScenarioModelBuilder.class.getDeclaredField( "scenarioCaseModel");

        scenarioCaseModel = new UniqueStepScenarioCaseModel();
        scenarioCaseModelField.setAccessible(true);
        scenarioCaseModelField.set(this, scenarioCaseModel);

        scenarioModelField.setAccessible(true);
        ScenarioModel scenarioModel = ((ScenarioModel)scenarioModelField.get(this));
        scenarioModel.clearCases();
        scenarioModel.addCase(scenarioCaseModel);

    }

    @Override
    public void stageChanged() {
        rootNode.neuterChildren();
        rootNode.addChild(StepNode.builder().build());
        isNewStage = true;
    }

    @Builder
    @AllArgsConstructor(access = PRIVATE)
    private static class StepNode {
        private final StepModel model;
        private final Method method;
        private StepNode parent;
        @Builder.Default
        private List<StepNode> children = new LinkedList<>();
        @Getter
        @Builder.Default
        private boolean fertile = true;

        @AllArgsConstructor
        @Getter
        enum Status {
            FAILED(0),
            PENDING(1),
            SKIPPED(2),
            PASSED(3);

            private final int rank;
        }

        public StepNode neuter() {
            if (!isRoot()) {
                this.fertile = false;
            }
            return this;
        }

        public StepNode neuterChildren() {
            children.forEach(child -> {
                child.neuter();
                child.neuterChildren();
            });
            return this;
        }

        public StepNode addChild(StepNode child) {
            child.parent = this;
            children.add(child);
            return this;
        }

        private boolean isRoot() {
            return parent == null;
        }

        public Optional<StepModel> getModel() {
            return Optional.ofNullable(model);
        }

        public Optional<Method> getMethod() {
            return Optional.ofNullable(method);
        }

        public Optional<StepNode> getParent() {
            return Optional.ofNullable(parent);
        }

        public StepNode getRoot() {
            return Optional.ofNullable(parent)
                    .orElse(this);
        }

        public StepNode getLast() {
            return children.stream()
                    .reduce((first, second) -> second)
                    .map(StepNode::getLast)
                    .orElse(this);
        }

        public StepNode getLastFertile() {
            return getAllNodes().stream()
                    .filter(StepNode::isFertile)
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException("Should be at least 1 fertile node"));
        }

        public List<StepNode> getAllNodes() {
            return Stream.concat(
                    Stream.of(this),
                    children.stream()
                            .map(StepNode::getAllNodes)
                            .flatMap(Collection::stream)
            ).collect(Collectors.toList());
        }

        public List<StepModel> toSteps() {
            if(isRoot()) {
                return children.stream()
                        .map(StepNode::toSteps)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }

            return children.stream()
                    .collect(new Collector<StepNode, List<StepModel>, List<StepModel>>() {

                        @Override
                        public Supplier<List<StepModel>> supplier() {
                            return ArrayList::new;
                        }

                        @Override
                        public BiConsumer<List<StepModel>, StepNode> accumulator() {
                            return (models, node) -> {
                                StepModel model = node.getModel().get();
                                List<StepModel> nestedSteps = Optional.of(node)
                                        .map(StepNode::toSteps)
                                        .filter(steps -> !steps.isEmpty())
                                        .orElse(null);
                                if (model.getWords().stream().anyMatch(Word::isIntroWord) || models.isEmpty()){
                                    model.setNestedSteps(nestedSteps);
                                    models.add(model);
                                    return;
                                }

                                StepModel previousModel = models.get(models.size()-1);
                                // If previous step already has nested steps then add a new model to models for formatting
                                previousModel.setNestedSteps(nestedSteps);
                                previousModel.setDurationInNanos(previousModel.getDurationInNanos() + model.getDurationInNanos());
                                previousModel.addWords(model.getWords().stream().toArray(Word[]::new));
                                if (Status.valueOf(model.getStatus().name()).getRank() <
                                        Status.valueOf(previousModel.getStatus().name()).getRank()) {
                                    previousModel.setStatus(model.getStatus());
                                }
                            };
                        }

                        @Override
                        public BinaryOperator<List<StepModel>> combiner() {
                            return (modelsOne, modelsTwo) -> {
                                modelsOne.addAll(modelsTwo);
                                return modelsOne;
                            };
                        }

                        @Override
                        public Function<List<StepModel>, List<StepModel>> finisher() {
                            return models -> models;
                        }

                        @Override
                        public Set<Characteristics> characteristics() {
                            return Collections.emptySet();
                        }
                    });

        }
    }

    @AllArgsConstructor
    private static class ClassExplorer implements Iterator<Class> {

        private Class clazz;

        @Override
        public boolean hasNext() {
            return clazz != Object.class;
        }

        @Override
        public Class next() {
            Class clazz = this.clazz;
            this.clazz = clazz.getSuperclass();
            return clazz;
        }
    }

    private boolean isChild(StepNode potentialParent, StepNode potentialChild){

        if (potentialParent.isRoot()) {
            return true;
        }

        Optional<Method> parentMethod = potentialParent.getMethod();
        if (!parentMethod.isPresent()) {
            return true;
        }

        Class parentClass = parentMethod.get().getDeclaringClass();
        Class parentReturnType = parentMethod.get().getReturnType();
        if (parentClass.equals(parentReturnType)) {
            return false;
        }

        Collection<Method> parentMethods = stream(spliteratorUnknownSize(new ClassExplorer(parentClass), ORDERED), false)
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());

        return potentialChild.getMethod()
                .filter(parentMethods::contains)
                .isPresent();

    }

    @Override
    StepModel createStepModel(Method paramMethod, List<NamedArgument> arguments, InvocationMode mode ) {
        StepModel model = super.createStepModel(paramMethod, arguments, mode);
        StepNode node = StepNode.builder().model(model).method(paramMethod).build();

        StepNode previous = rootNode.getLastFertile();
        if (!isChild(previous, node)){
            previous.neuter();
            previous = previous.getParent().orElseThrow(() -> new IllegalStateException("Root node cannot be infertile"));
        }

//        if(model.getWords().stream().anyMatch(Word::isIntroWord) && !isNewStage) {
//            previous.neuter();
//            previous = rootNode.getLastFertile();
//        }

        previous.addChild(node);
        return model;
    }

    @Override
    public void stepMethodFinished( long durationInNanos, boolean hasNestedSteps ) {
        isNewStage = false;
        super.stepMethodFinished(durationInNanos, hasNestedSteps);
    }

    @Override
    public void stepMethodFailed( Throwable t ) {
        super.stepMethodFailed(t);
        isNewStage = false;
    }

    @Override
    public void scenarioFinished() {
        scenarioCaseModel.addSteps(rootNode.toSteps().stream().toArray(StepModel[]::new));
        super.scenarioFinished();
    }
}
