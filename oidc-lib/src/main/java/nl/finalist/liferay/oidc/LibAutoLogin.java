package nl.finalist.liferay.oidc;

import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import nl.finalist.liferay.oidc.providers.UserInfoProvider;

import org.apache.commons.lang3.StringUtils;

/**
 * AutoLogin for OpenID Connect 1.0 This class should be used in tandem with the
 * OpenIDConnectFilter. That filter will do the OAuth conversation and set a
 * session attribute containing the UserInfo object (the claims). This AutoLogin
 * will use the claims to find a corresponding Liferay user or create a new one
 * if none found.
 */
public class LibAutoLogin {
	
	private final LiferayAdapter liferay;

	public LibAutoLogin(LiferayAdapter liferay) {
		this.liferay = liferay;
		liferay.info("Initialized LibAutoLogin with Liferay API: "
				+ liferay.getClass().getName());
	}

	public String[] doLogin(HttpServletRequest request,
			HttpServletResponse response) {
		String[] userResponse = null;

		long companyId = liferay.getCompanyId(request);

		OIDCConfiguration oidcConfiguration = liferay
				.getOIDCConfiguration(companyId);

		if (oidcConfiguration.isEnabled()) {
			HttpSession session = request.getSession();
			
			Map<String, String> userInfo = (Map<String, String>) session
					.getAttribute(LibFilter.OPENID_CONNECT_SESSION_ATTR);

			UserInfoProvider provider = ProviderFactory
					.getOpenIdProvider(oidcConfiguration.providerType());

			if (userInfo == null) {
				// Normal flow, apparently no current OpenID conversation
				liferay.trace("No current OpenID Connect conversation, no auto login");
			} else if (StringUtils.isBlank(provider.getEmail(userInfo))) {
				liferay.error("Unexpected: OpenID Connect UserInfo does not contain email field. "
						+ "Cannot correlate to Liferay user. UserInfo: "
						+ userInfo);
			} else {
				liferay.trace("Found OpenID Connect session attribute, userinfo: "
						+ userInfo);
				String emailAddress = provider.getEmail(userInfo);
				String givenName = provider.getFirstName(userInfo);
				String familyName = provider.getLastName(userInfo);
				
				String goesternID = provider.getGoesternID(userInfo);

				String userId = liferay.createOrUpdateUser(companyId,
						emailAddress, givenName, familyName, goesternID);
				liferay.trace("Returning credentials for userId " + userId
						+ ", email: " + emailAddress);

				userResponse = new String[] { userId,
						UUID.randomUUID().toString(), "false" };
			}
		} else {
			liferay.trace("OpenIDConnectAutoLogin not enabled for this virtual instance. Will skip it.");
		}

		return userResponse;
	}
}
