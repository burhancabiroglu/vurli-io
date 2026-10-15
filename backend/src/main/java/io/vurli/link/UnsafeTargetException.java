package io.vurli.link;

/** The target is on a list of dangerous sites (K24). */
public class UnsafeTargetException extends RuntimeException {

	public UnsafeTargetException() {
		super("Target is listed as unsafe");
	}

}
