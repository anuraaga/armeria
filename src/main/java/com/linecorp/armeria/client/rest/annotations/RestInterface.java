package com.linecorp.armeria.client.rest.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates the annotated interface can be converted to a REST API interface.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RestInterface {
}
