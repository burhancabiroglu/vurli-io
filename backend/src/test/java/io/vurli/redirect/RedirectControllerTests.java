package io.vurli.redirect;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.vurli.analytics.ClickRecorder;
import io.vurli.link.LinkResolver;
import io.vurli.ratelimit.ClientAddresses;
import io.vurli.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@WithMockUser
class RedirectControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LinkResolver resolver;

	@MockitoBean
	private RateLimiter rateLimiter;

	@MockitoBean
	private ClientAddresses clientAddresses;

	@MockitoBean
	private ClickRecorder clicks;

	@Test
	void databaseFailureNeverRedirects() throws Exception {
		given(this.resolver.resolve("kafe-menu")).willThrow(new DataAccessResourceFailureException("down"));

		this.mvc.perform(get("/kafe-menu"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}

}
