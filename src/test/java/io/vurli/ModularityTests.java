package io.vurli;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modüllerin yalnızca birbirinin dışa açık API'sini kullandığını ve aralarında
 * döngüsel bağımlılık olmadığını doğrular.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(VurliApplication.class);

	@Test
	void modulesRespectBoundaries() {
		this.modules.verify();
	}

}
