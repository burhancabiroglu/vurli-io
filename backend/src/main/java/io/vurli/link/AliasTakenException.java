package io.vurli.link;

/**
 * The requested alias is already used by another link, including deleted ones:
 * codes are never handed out twice.
 */
public class AliasTakenException extends RuntimeException {

	private final String alias;

	public AliasTakenException(String alias) {
		super("Alias is already taken: " + alias);
		this.alias = alias;
	}

	public String alias() {
		return this.alias;
	}

}
