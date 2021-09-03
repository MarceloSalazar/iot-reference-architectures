package com.awssamples;

import com.aws.samples.cdk.helpers.CdkHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Construct;

import java.lang.reflect.Constructor;

import static io.vavr.API.*;
import static io.vavr.Predicates.instanceOf;

public class MasterApp {
    public static final MasterInjector masterInjector = DaggerMasterInjector.create();
    private static final Logger log = LoggerFactory.getLogger(MasterApp.class);

    public static void main(final String argv[]) {
        if (argv.length != 2) {
            throw new RuntimeException("Two arguments are required. The first argument is the class to use for the stack. The second argument is the name to use for the stack.");
        }

        String className = argv[0];
        String stackName = argv[1];

        CdkHelper.setStackName(stackName);

        Try<? extends Class<?>> tryStackClass = Try.of(() -> Class.forName(className))
                .onFailure(ClassNotFoundException.class, MasterApp::logThrowable);

        if (tryStackClass.isFailure()) {
            log.error("Failed to get the stack class, exiting.");
            exitWithFailure();
        }

        Class stackClass = tryStackClass.get();

        Try<Constructor> tryStackClassConstructor = Try.of(() -> stackClass.getConstructor(Construct.class, String.class))
                .onFailure(NoSuchMethodException.class, MasterApp::logThrowable);

        if (tryStackClassConstructor.isFailure()) {
            log.error("Failed to get the stack class constructor, exiting.");
            exitWithFailure();
        }

        Constructor stackClassConstructor = tryStackClassConstructor.get();

        App app = new App();

        Try<Void> stackConstructorInvocation = Try.run(() -> stackClassConstructor.newInstance(app, stackName));

        if (stackConstructorInvocation.isSuccess()) {
            app.synth();
            return;
        }

        Match(stackConstructorInvocation.getCause()).of(
                Case($(instanceOf(RuntimeException.class)), MasterApp::logThrowable),
                Case($(), MasterApp::logThrowable));

        exitWithFailure();
    }

    private static void exitWithFailure() {
        System.exit(1);
    }

    private static Void logThrowable(Throwable throwable) {
        log.error("Exception: " + throwable.getCause());
        return null;
    }
}
