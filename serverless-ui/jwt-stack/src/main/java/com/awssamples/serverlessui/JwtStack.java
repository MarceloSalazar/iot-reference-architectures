package com.awssamples.serverlessui;

import com.aws.samples.cdk.helpers.CdkHelper;
import com.aws.samples.cdk.helpers.IotHelper;
import com.aws.samples.cdk.helpers.ServerlessHelper;
import com.aws.samples.cdk.helpers.data.AwsLambdaServlet;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import com.aws.samples.cdk.stacktypes.JavaGradleStack;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.iot.CfnAuthorizer;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;

import java.util.stream.Collectors;

import static com.aws.samples.cdk.helpers.ServerlessHelper.AUTHORIZERS;

public class JwtStack extends software.amazon.awscdk.core.Stack implements JavaGradleStack {
    private static final Logger log = LoggerFactory.getLogger(JwtStack.class);
    private final String projectDirectory;
    private final String outputArtifactName;
    private final List<CfnAuthorizer> iotCustomAuthorizers;
    private HashMap<String, String> lambdaEnvironment = HashMap.empty();

    public static void main(String[] args) {
        new JwtStack(CdkHelper.getApp(), CdkHelper.getStackName());

        CdkHelper.getApp().synth();
    }

    public JwtStack(final Construct parent, final String name) {
        super(parent, name);

        projectDirectory = "./";
        outputArtifactName = name + "-all.jar";

        // Build all of the necessary JARs
        String hash = build();

        // Optional - for custom authorizer demos
        iotCustomAuthorizers = IotHelper.getIotCustomAuthorizers(this, getOutputArtifactFile(), hash);
        logCount("IoT custom authorizers", iotCustomAuthorizers);

        addAuthorizerNamesToLambdaEnvironment();

        // Build the properties required for the servlets
        FunctionProps.Builder lambdaFunctionPropsBuilder = FunctionProps.builder()
                .runtime(Runtime.JAVA_11)
                .memorySize(1024)
                .environment(lambdaEnvironment.toJavaMap())
                // Can't go higher than 30 seconds because that's API gateway's max timeout
                .timeout(Duration.seconds(30));

        List<AwsLambdaServlet> awsLambdaServlets = ServerlessHelper.getAwsLambdaServlets(this, getOutputArtifactFile(), Option.of(lambdaFunctionPropsBuilder));
        logCount("servlets", awsLambdaServlets);

        LambdaRestApi lambdaRestApi = ServerlessHelper.buildLambdaRestApiIfPossible(this, awsLambdaServlets);

        // Include the URL for the serverless UI in the output so it's easier to find later
        CfnOutputProps url = CfnOutputProps.builder()
                .exportName(String.join("-", getStackName(), "url"))
                .value(lambdaRestApi.getUrl())
                .build();

        CfnOutput output = new CfnOutput(this, String.join("-", getStackName(), "api"), url);
    }

    private void logCount(String type, List<?> list) {
        if (list.isEmpty()) {
            log.info("No " + type + " found");
        } else {
            log.info("Number of " + type + " found: " + list.size());
        }
    }

    private void addAuthorizerNamesToLambdaEnvironment() {
        String authorizers = iotCustomAuthorizers
                .map(CfnAuthorizer::getAuthorizerName)
                .collect(Collectors.joining(","));

        if (!authorizers.isEmpty()) {
            lambdaEnvironment = lambdaEnvironment.put(AUTHORIZERS, authorizers);
        }
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
