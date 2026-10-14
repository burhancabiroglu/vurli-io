package io.vurli.link.internal;

/**
 * Produces candidate short codes for links without a custom alias.
 */
@FunctionalInterface
public interface CodeGenerator {

	String next();

}
