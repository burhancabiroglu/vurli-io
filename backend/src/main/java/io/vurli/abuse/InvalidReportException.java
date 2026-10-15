package io.vurli.abuse;

/** A report form value that cannot be used; the form says which. */
public class InvalidReportException extends RuntimeException {

	public enum Field {

		LINK, DETAILS, EMAIL

	}

	private final Field field;

	public InvalidReportException(Field field) {
		super("Invalid report field: " + field);
		this.field = field;
	}

	public Field field() {
		return this.field;
	}

}
