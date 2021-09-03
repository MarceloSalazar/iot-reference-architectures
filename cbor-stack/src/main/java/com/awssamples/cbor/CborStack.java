package com.awssamples.cbor;

import com.aws.samples.cdk.constructs.iam.policies.LambdaPolicies;
import com.aws.samples.cdk.helpers.*;
import com.aws.samples.cdk.stacktypes.JavaGradleStack;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iot.CfnTopicRule;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;

public class CborStack extends software.amazon.awscdk.core.Stack implements JavaGradleStack {
    public static final String CBOR_MESSAGE = "CborMessage";
    public static final String JSON_MESSAGE = "JsonMessage";
    private static final String HANDLER_PACKAGE = "com.awssamples.iot.cbor.handler.handlers";
    private static final String OUTPUT_TOPIC = "OutputTopic";
    private static final String CBOR_INPUT_TOPIC = String.join("/", "cbor", "input");
    private static final String CBOR_OUTPUT_TOPIC = String.join("/", "json", "output");
    private static final String JSON_INPUT_TOPIC = String.join("/", "json", "input");
    private static final String JSON_OUTPUT_TOPIC = String.join("/", "cbor", "output");
    // Amazon Ion event handler
    private static final String CBOR_EVENT_HANDLER = String.join(".", HANDLER_PACKAGE, "HandleCborEvent");
    // JSON event handler
    private static final String JSON_EVENT_HANDLER = String.join(".", HANDLER_PACKAGE, "HandleJsonEvent");
    private static final Duration LAMBDA_FUNCTION_TIMEOUT = Duration.seconds(10);
    private final String projectDirectory;
    private final String outputArtifactName;

    public static void main(String[] args) {
        new CborStack(CdkHelper.getApp(), CdkHelper.getStackName());

        CdkHelper.getApp().synth();
    }

    public CborStack(final Construct parent, final String name) {
        super(parent, name);

        projectDirectory = "../" + name + "/";
        outputArtifactName = name + "-all.jar";

        // Build all of the necessary JARs
        build();

        // Build the properties required for both Lambda functions
        FunctionProps.Builder lambdaFunctionPropsBuilder = FunctionProps.builder()
                .runtime(Runtime.JAVA_11)
                .memorySize(1024)
                .timeout(Duration.seconds(10));

        // Resources to convert an Amazon Ion message to JSON
        Role cborMessageRole = RoleHelper.buildPublishToTopicRole(this, CBOR_MESSAGE, CBOR_OUTPUT_TOPIC, List.empty(), List.empty(), LambdaPolicies.LAMBDA_SERVICE_PRINCIPAL);
        Map<String, String> cborLambdaEnvironment = getCborLambdaEnvironment();
        Function cborMessageFunction = LambdaHelper.buildLambda(this, CBOR_MESSAGE, cborMessageRole, cborLambdaEnvironment, getAssetCode(), CBOR_EVENT_HANDLER, Option.of(lambdaFunctionPropsBuilder));
        CfnTopicRule cborMessageTopicRule = RulesEngineSqlHelper.buildSelectAllBinaryIotEventRule(this, CBOR_MESSAGE, cborMessageFunction, CBOR_INPUT_TOPIC);
        IotHelper.allowIotTopicRuleToInvokeLambdaFunction(this, cborMessageTopicRule, cborMessageFunction, CBOR_MESSAGE);

        // Resources to convert a JSON message to Amazon Ion
        Role jsonMessageRole = RoleHelper.buildPublishToTopicRole(this, JSON_MESSAGE, JSON_OUTPUT_TOPIC, List.empty(), List.empty(), LambdaPolicies.LAMBDA_SERVICE_PRINCIPAL);
        Map<String, String> jsonLambdaEnvironment = getJsonLambdaEnvironment();
        Function jsonMessageFunction = LambdaHelper.buildLambda(this, JSON_MESSAGE, jsonMessageRole, jsonLambdaEnvironment, getAssetCode(), JSON_EVENT_HANDLER, Option.of(lambdaFunctionPropsBuilder));
        CfnTopicRule jsonMessageTopicRule = RulesEngineSqlHelper.buildSelectAllIotEventRule(this, JSON_MESSAGE, jsonMessageFunction, JSON_INPUT_TOPIC);
        IotHelper.allowIotTopicRuleToInvokeLambdaFunction(this, jsonMessageTopicRule, jsonMessageFunction, JSON_MESSAGE);
    }

    private Map<String, String> getCborLambdaEnvironment() {
        return HashMap.of(OUTPUT_TOPIC, CBOR_OUTPUT_TOPIC);
    }

    private Map<String, String> getJsonLambdaEnvironment() {
        return HashMap.of(OUTPUT_TOPIC, JSON_OUTPUT_TOPIC);
    }

    @Override
    public String getProjectDirectory() {
        return projectDirectory;
    }

    @Override
    public String getOutputArtifactName() {
        return outputArtifactName;
    }
}
