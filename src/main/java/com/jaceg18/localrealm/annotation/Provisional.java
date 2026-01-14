package com.jaceg18.localrealm.annotation;
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD
})
public @interface Provisional {

    String reason();

    Confidence confidence() default Confidence.MEDIUM;

    String expiresBy() default "";

    String replacement() default "";

    String owner() default "Jace";

    enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }
}
