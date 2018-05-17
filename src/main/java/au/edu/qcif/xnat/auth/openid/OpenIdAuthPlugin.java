/*
 *Copyright (C) 2018 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 *
 *This program is free software: you can redistribute it and/or modify
 *it under the terms of the GNU General Public License as published by
 *the Free Software Foundation; either version 2 of the License, or
 *(at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License along
 *with this program; if not, write to the Free Software Foundation, Inc.,
 *51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package au.edu.qcif.xnat.auth.openid;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xnat.security.XnatLogoutSuccessHandler;
import org.nrg.xnat.security.XnatSecurityExtension;
import org.nrg.xnat.security.provider.AuthenticationProviderConfigurationLocator;
import org.nrg.xnat.security.provider.ProviderAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * XNAT Authentication plugin.
 * 
 * @author <a href='https://github.com/shilob'>Shilo Banihit</a>
 * 
 */
@XnatPlugin(value = "xnat-openid-auth-plugin", name = "XNAT OpenID Authentication Provider Plugin")
@EnableWebSecurity
@EnableOAuth2Client
@Component
@Slf4j
public class OpenIdAuthPlugin implements XnatSecurityExtension {


	@Autowired
	public void setAuthenticationProviderConfigurationLocator(
			final AuthenticationProviderConfigurationLocator locator) {
		_locator = locator;
		loadProps();
	}

	public boolean isEnabled(String providerId) {
		getEnabledProviders();
		for (String provider : _enabledProviders) {
			if (provider.equals(providerId)) {
				return true;
			}
		}
		return false;
	}

	public String getProperty(String providerId, String propName) {
		loadProps();
		return _props.getProperty(getPropertyPrefix(providerId) + propName);
	}

	private void loadProps() {
		if (_props == null && _locator != null) {
			final Map<String, ProviderAttributes> openIdProviders = _locator.getProviderDefinitionsByType("openid");
			if (null == openIdProviders) {
				throw new RuntimeException(
                        "This plugin requires one OpenID provider but found none!! " +
 						"Please verify properties file in {xnat.home}/config/auth/xxx-provider.properties.");
			}
			if (openIdProviders.size() == 0) {
				throw new RuntimeException("You must configure an OpenID provider");
			}
			if (openIdProviders.size() > 1) {
				throw new RuntimeException(
						"This plugin currently only supports one OpenID provider at a time, but I found "
								+ openIdProviders.size() + " providers defined: "
								+ StringUtils.join(openIdProviders.keySet(), ", "));
			}
			_props = _locator.getProviderDefinitionByType("openid", openIdProviders.keySet().iterator().next())
					.getProperties();
			_inst = this;
		}
	}

	private static String getPropertyPrefix(String providerId) {
        return _id + "." + providerId + ".";
    }

    //TODO: Fix this to handle more than one configured provider. Assumes only one is configured
    private static String getPropertyPrefix() {
	    return getPropertyPrefix(getConfig().getProperty("enabled"));
    }

	public Properties getProps() {
		return _props;
	}

	public String[] getEnabledProviders() {
		if (_enabledProviders == null) {
			_enabledProviders = _props.getProperty("enabled").split(",");
		}
		return _enabledProviders;
	}


	@Bean
	@Scope("prototype")
	public OpenIdConnectFilter createFilter() {
		OpenIdConnectFilter filter = new OpenIdConnectFilter(getProps().getProperty("preEstablishedRedirUri"), this);
		return filter;
	}

	public void configure(final HttpSecurity http) throws Exception {
		this.http = http;
		http.addFilterAfter(new OAuth2ClientContextFilter(), AbstractPreAuthenticatedProcessingFilter.class)
				.addFilterAfter(createFilter(), OAuth2ClientContextFilter.class);

	}

	private AuthenticationProviderConfigurationLocator _locator;
    private static String LOGOUT_URL;
	private static final String _id = "openid";
	private static final String _logoutUrlKey = "logoutUrl";
    private static final String _defaultSecureLogoutUrl = "/app/template/Login.vm";
    private static final String _defaultOpenLogoutUrl = "/";
	private Properties _props;
	private String[] _enabledProviders;
	private static OpenIdAuthPlugin _inst;
	private boolean isFilterConfigured = false;
	private HttpSecurity http;

	public static Properties getConfig() {
		return _inst.getProps();
	}

	public static String getLoginStr() {
		String[] enabledProviders = _inst.getEnabledProviders();
		String loginStr = "";
		int idx = 0;
		for (String enabledProvider : enabledProviders) {
			loginStr = loginStr + _inst.getProperty(enabledProvider, "link");
		}
		return loginStr;
	}

	public static String getUsernamePasswordStyle() {
		_inst.loadProps();
		boolean disableUsernamePassword = Boolean
				.parseBoolean(_inst.getProps().getProperty("disableUsernamePasswordLogin"));
		if (disableUsernamePassword) {
			return "display:none";
		} else {
			return "";
		}
	}

	public void configure(final AuthenticationManagerBuilder builder) throws Exception {

	}

	public String getAuthMethod() {
		return _id;
	}

	@Bean
	@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
	public OAuth2RestTemplate createRestTemplate(final OAuth2ClientContext clientContext) {
		log.debug("At create rest template...");
		ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
		HttpServletRequest request = attr.getRequest();
		// Interrogate request to get providerId (e.g. look at url if nothing
		// else)
		String providerId = request.getParameter("providerId");
		log.debug("Provider id is: " + providerId);
		request.getSession().setAttribute("providerId", providerId);
		final OAuth2RestTemplate template = new OAuth2RestTemplate(getProtectedResourceDetails(providerId),
				clientContext);
		return template;
	}

	public AuthorizationCodeResourceDetails getProtectedResourceDetails(String providerId) {
		log.debug("Creating protected resource details of provider:" + providerId);
		final String clientId = getProperty(providerId, "clientId");
		final String clientSecret = getProperty(providerId, "clientSecret");
		final String accessTokenUri = getProperty(providerId, "accessTokenUri");
		final String userAuthUri = getProperty(providerId, "userAuthUri");
		final String preEstablishedUri = getProps().getProperty("siteUrl")
				+ getProps().getProperty("preEstablishedRedirUri");
		final String[] scopes = getProperty(providerId, "scopes").split(",");
		final AuthorizationCodeResourceDetails details = new AuthorizationCodeResourceDetails();
		details.setClientId(clientId);
		details.setClientSecret(clientSecret);
		details.setAccessTokenUri(accessTokenUri);
		details.setUserAuthorizationUri(userAuthUri);
		details.setScope(Arrays.asList(scopes));
		details.setPreEstablishedRedirectUri(preEstablishedUri);
		details.setUseCurrentUri(false);
		return details;
	}

	@Bean
	public XnatLogoutSuccessHandler logoutSuccessHandler() {
		return new OpenIdLogoutSuccessHandler(true, "/", _defaultSecureLogoutUrl);
	}

	//Package private, used in LogoutSuccessHandler.
    //TODO: Add better logic here, if possible, to identify if current session/user was logged in via OpenID provider.
    static String getLogoutUrl(boolean requireLogin) {
        return OpenIdAuthPlugin.isOpenIDProviderEnabled() ?
                getOpenIDLogoutUrl(requireLogin) :
                getXnatLogutUrl(requireLogin);
    }

    private static String getOpenIDLogoutUrl(boolean requireLogin) {
	    if (null == LOGOUT_URL) {
            String logoutUrl = getConfig().getProperty(getPropertyPrefix() + _logoutUrlKey);
            if (null != logoutUrl) {
                String redirUrl = getTrimmedUrl(getConfig().getProperty("siteUrl"), getXnatLogutUrl(requireLogin));
                LOGOUT_URL = (logoutUrl + "?redirect_uri=" + redirUrl);
            }
		}
		return null == LOGOUT_URL ? getXnatLogutUrl(requireLogin) : LOGOUT_URL;
	}

	private static String getTrimmedUrl(String site, String location) {
	    if (site.endsWith("/") && location.startsWith("/")) {
	        location = location.replaceFirst("/", "");
        }
        return site + location;
    }

	private static boolean isOpenIDProviderEnabled() {
	    String enabled = getConfig().getProperty("enabled");
	    return null != enabled && !enabled.isEmpty();
    }

    private static String getXnatLogutUrl(boolean requireLogin) {
	    return requireLogin ? _defaultSecureLogoutUrl : _defaultOpenLogoutUrl;
    }
}
