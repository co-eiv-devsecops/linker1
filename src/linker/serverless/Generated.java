package linker.serverless;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor as excluded from JaCoCo's line-coverage counting.
 * JaCoCo's built-in {@code AnnotationGeneratedFilter} excludes any bytecode
 * element annotated with something whose simple name contains "Generated"
 * (retention {@code CLASS} or {@code RUNTIME}, not {@code SOURCE}) -- no
 * JaCoCo-specific dependency needed, just this marker. Used on
 * {@link LinkLambdaHandler}'s no-arg constructor, which wires a real MySQL
 * connection and has no meaningful unit test without a live database (same
 * rationale as {@code Main.class}'s whole-class exclusion, applied here at
 * constructor granularity so the rest of the class still counts normally).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.CONSTRUCTOR)
@interface Generated {
}
