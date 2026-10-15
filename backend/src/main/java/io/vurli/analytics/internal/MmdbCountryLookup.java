package io.vurli.analytics.internal;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.regex.Pattern;

import com.maxmind.db.Reader;

/**
 * Countries from a MaxMind DB file: the DB-IP country database (K44), or MaxMind's own
 * GeoLite2 Country, which has the same layout. The file is read into memory once.
 */
public class MmdbCountryLookup implements CountryLookup, Closeable {

	private static final Pattern ISO_CODE = Pattern.compile("[A-Z]{2}");

	private final Reader reader;

	public MmdbCountryLookup(File database) throws IOException {
		this.reader = new Reader(database, Reader.FileMode.MEMORY);
	}

	@Override
	public String country(InetAddress address) {
		if (address == null) {
			return null;
		}
		try {
			Map<?, ?> record = this.reader.get(address, Map.class);
			if (record != null && record.get("country") instanceof Map<?, ?> country
					&& country.get("iso_code") instanceof String code && ISO_CODE.matcher(code).matches()) {
				return code;
			}
			return null;
		}
		catch (IOException | RuntimeException ex) {
			return null;
		}
	}

	public String databaseType() {
		return this.reader.getMetadata().databaseType();
	}

	@Override
	public void close() throws IOException {
		this.reader.close();
	}

}
